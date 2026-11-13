package server

import (
	"context"
	"net/http"
	"slices"
	"strconv"
	"testing"
	"time"

	"github.com/tomhartill/kinzo/api/internal/auth"
	"github.com/tomhartill/kinzo/api/internal/store"
)

// testNumbers are fixed-code numbers, like the ones store reviewers get. 07700 900xxx is
// reserved for fiction, so it's nobody's real phone.
var testNumbers = map[string]string{"+447700900123": "123456"}

// fakeCodes stands in for Twilio: it remembers each code it "sent".
type fakeCodes struct {
	sent []sentCode
}

type sentCode struct {
	phone, channel, code string
}

func (f *fakeCodes) Send(_ context.Context, phone, channel string) (string, error) {
	f.sent = append(f.sent, sentCode{phone, channel, auth.RandomCode()})
	return strconv.Itoa(len(f.sent) - 1), nil
}

func (f *fakeCodes) Check(_ context.Context, ref, phone, code string) (bool, error) {
	i, _ := strconv.Atoi(ref)
	return f.sent[i].phone == phone && f.sent[i].code == code, nil
}

func (f *fakeCodes) last(t *testing.T) sentCode {
	t.Helper()
	if len(f.sent) == 0 {
		t.Fatal("no code was sent")
	}
	return f.sent[len(f.sent)-1]
}

// ageCodes pretends the codes sent so far went out d ago, to get past the sending limits.
func (a *testAPI) ageCodes(d time.Duration) {
	a.t.Helper()
	if _, err := a.pool.Exec(context.Background(),
		`UPDATE phone_challenges SET created_at = created_at - $1::interval`, d.String()); err != nil {
		a.t.Fatal(err)
	}
}

func (a *testAPI) phoneStart(email, phone string) phoneStartResponse {
	a.t.Helper()
	rec := a.do("POST", "/v1/auth/phone/start", "", map[string]string{"email": email, "phone": phone, "channel": "sms"})
	expectStatus(a.t, rec, http.StatusOK)
	return decode[phoneStartResponse](a.t, rec)
}

func (a *testAPI) phoneVerify(challengeID, code string) signInResponse {
	a.t.Helper()
	rec := a.do("POST", "/v1/auth/phone/verify", "", map[string]string{"challenge_id": challengeID, "code": code})
	expectStatus(a.t, rec, http.StatusOK)
	return decode[signInResponse](a.t, rec)
}

// phoneSignUp makes an account with an email and number, and returns it signed in.
func (a *testAPI) phoneSignUp(email, phone string) signInResponse {
	a.t.Helper()
	start := a.phoneStart(email, phone)
	if start.Next != "code" {
		a.t.Fatalf("next = %q, want code", start.Next)
	}
	return a.phoneVerify(start.Challenge.ChallengeID, a.codes.last(a.t).code)
}

func TestPhoneProvidersListed(t *testing.T) {
	api := newTestAPI(t)
	rec := api.do("GET", "/v1/auth/providers", "", nil)
	expectStatus(t, rec, http.StatusOK)
	got := decode[struct {
		Providers     []string `json:"providers"`
		PhoneChannels []string `json:"phone_channels"`
	}](t, rec)
	if !slices.Contains(got.Providers, "phone") || !slices.Equal(got.PhoneChannels, []string{"sms", "whatsapp"}) {
		t.Fatalf("providers = %+v", got)
	}
}

func TestPhoneSignUp(t *testing.T) {
	api := newTestAPI(t)
	if next := api.phoneStart(" Sam@Example.com ", "").Next; next != "phone" {
		t.Fatalf("new email: next = %q, want phone", next)
	}
	start := api.phoneStart("sam@example.com", "07911 123456")
	if start.Next != "code" || start.Challenge.PhoneHint != "•••• 456" || start.Challenge.ResendIn != 30 || start.Challenge.ExpiresIn != 600 {
		t.Fatalf("start = %+v", start)
	}
	sent := api.codes.last(t)
	if sent.phone != "+447911123456" || sent.channel != "sms" {
		t.Fatalf("sent = %+v", sent)
	}

	wrong := "000000"
	if sent.code == wrong {
		wrong = "111111"
	}
	rec := api.do("POST", "/v1/auth/phone/verify", "", map[string]string{"challenge_id": start.Challenge.ChallengeID, "code": wrong})
	expectStatus(t, rec, http.StatusBadRequest)
	if e := decode[codeError](t, rec); e.Error.Code != "wrong_code" || e.AttemptsLeft != 4 {
		t.Fatalf("wrong code = %+v", e)
	}

	signedIn := api.phoneVerify(start.Challenge.ChallengeID, sent.code)
	u := signedIn.User
	if !signedIn.NewUser || u.Email != "sam@example.com" || u.EmailVerified || u.Phone != "+447911123456" ||
		!slices.Equal(u.SignInMethods, []string{"phone"}) {
		t.Fatalf("signed in = %+v", signedIn)
	}

	// A code works once.
	rec = api.do("POST", "/v1/auth/phone/verify", "", map[string]string{"challenge_id": start.Challenge.ChallengeID, "code": sent.code})
	expectStatus(t, rec, http.StatusGone)
}

func TestKnownEmailUsesTheAccountsPhone(t *testing.T) {
	api := newTestAPI(t)
	first := api.phoneSignUp("sam@example.com", "+447911123456")
	api.ageCodes(time.Minute)

	// The typed number is ignored: the code goes to the one on the account.
	start := api.phoneStart("SAM@example.com", "+447911999999")
	if start.Next != "code" || api.codes.last(t).phone != "+447911123456" {
		t.Fatalf("start = %+v, sent to %s", start, api.codes.last(t).phone)
	}
	again := api.phoneVerify(start.Challenge.ChallengeID, api.codes.last(t).code)
	if again.NewUser || again.User.ID != first.User.ID {
		t.Fatalf("signed in to %s (new %v), want %s", again.User.ID, again.NewUser, first.User.ID)
	}
}

func TestProviderOnlyEmailGetsNoCode(t *testing.T) {
	api := newTestAPI(t)
	api.signIn("g-1", "Sam", "sam@example.com")
	start := api.phoneStart("sam@example.com", "+447911123456")
	if start.Next != "provider" || !slices.Equal(start.Providers, []string{"google"}) || start.Challenge != nil {
		t.Fatalf("start = %+v", start)
	}
	if len(api.codes.sent) != 0 {
		t.Fatal("a code was sent")
	}
}

func TestNewEmailWithKnownNumberSignsInToThatAccount(t *testing.T) {
	api := newTestAPI(t)
	first := api.phoneSignUp("sam@example.com", "+447911123456")
	api.ageCodes(time.Minute)
	second := api.phoneSignUp("other@example.com", "+447911123456")
	if second.NewUser || second.User.ID != first.User.ID || second.User.Email != "sam@example.com" {
		t.Fatalf("second = %+v", second)
	}
}

func TestVerifiedProviderEmailBeatsUnverifiedPhoneEmail(t *testing.T) {
	api := newTestAPI(t)
	squatter := api.phoneSignUp("sam@example.com", "+447911123456")
	google := api.signIn("g-1", "Sam", "sam@example.com")
	if !google.NewUser || google.User.ID == squatter.User.ID || google.User.Email != "sam@example.com" || !google.User.EmailVerified {
		t.Fatalf("google = %+v", google)
	}
	rec := api.do("GET", "/v1/me", squatter.Token, nil)
	expectStatus(t, rec, http.StatusOK)
	if me := decode[store.User](t, rec); me.Email != "" {
		t.Fatalf("phone account kept the email: %+v", me)
	}
}

func TestWrongCodesRunOut(t *testing.T) {
	api := newTestAPI(t)
	start := api.phoneStart("sam@example.com", "+447911123456")
	right := api.codes.last(t).code
	wrong := "000000"
	if right == wrong {
		wrong = "111111"
	}
	for left := 4; left >= 0; left-- {
		rec := api.do("POST", "/v1/auth/phone/verify", "", map[string]string{"challenge_id": start.Challenge.ChallengeID, "code": wrong})
		if left == 0 {
			expectStatus(t, rec, http.StatusGone)
		} else {
			expectStatus(t, rec, http.StatusBadRequest)
		}
	}
	rec := api.do("POST", "/v1/auth/phone/verify", "", map[string]string{"challenge_id": start.Challenge.ChallengeID, "code": right})
	expectStatus(t, rec, http.StatusGone)
}

func TestCodeSendingLimit(t *testing.T) {
	api := newTestAPI(t)
	api.phoneStart("sam@example.com", "+447911123456")
	rec := api.do("POST", "/v1/auth/phone/start", "", map[string]string{"email": "sam@example.com", "phone": "+447911123456", "channel": "whatsapp"})
	expectStatus(t, rec, http.StatusTooManyRequests)
	if e := decode[codeError](t, rec); e.Error.Code != "too_many_codes" || e.RetryIn < 1 || e.RetryIn > 30 {
		t.Fatalf("limit = %+v", e)
	}
	api.ageCodes(time.Minute)
	if start := api.phoneStart("sam@example.com", "+447911123456"); start.Next != "code" {
		t.Fatalf("resend = %+v", start)
	}
}

func TestRetryIn(t *testing.T) {
	now := time.Now()
	ago := func(d time.Duration) time.Time { return now.Add(-d) }
	cases := []struct {
		sent []time.Time
		want time.Duration
	}{
		{nil, 0},
		{[]time.Time{ago(10 * time.Second)}, 20 * time.Second},
		{[]time.Time{ago(40 * time.Second)}, 0},
		{[]time.Time{ago(50 * time.Minute), ago(40 * time.Minute), ago(30 * time.Minute), ago(20 * time.Minute), ago(10 * time.Minute)}, 10 * time.Minute},
	}
	for _, c := range cases {
		if got := retryIn(c.sent, phoneLimits, now); got != c.want {
			t.Errorf("retryIn(%v) = %v, want %v", c.sent, got, c.want)
		}
	}
}

func TestPhoneNumberChecks(t *testing.T) {
	api := newTestAPI(t)
	for phone, code := range map[string]string{"+1 202 555 0143": "unsupported_country", "12": "invalid_phone"} {
		rec := api.do("POST", "/v1/auth/phone/start", "", map[string]string{"email": "sam@example.com", "phone": phone, "channel": "sms"})
		expectStatus(t, rec, http.StatusBadRequest)
		if e := decode[codeError](t, rec); e.Error.Code != code {
			t.Errorf("%s: code = %s, want %s", phone, e.Error.Code, code)
		}
	}
	rec := api.do("POST", "/v1/auth/phone/start", "", map[string]string{"email": "not an email", "channel": "sms"})
	expectStatus(t, rec, http.StatusBadRequest)
	rec = api.do("POST", "/v1/auth/phone/start", "", map[string]string{"email": "sam@example.com", "channel": "pigeon"})
	expectStatus(t, rec, http.StatusBadRequest)
}

func TestTestNumbers(t *testing.T) {
	api := newTestAPI(t)
	start := api.phoneStart("review@example.com", "+44 7700 900123")
	if start.Next != "code" || len(api.codes.sent) != 0 {
		t.Fatalf("start = %+v, sent %d", start, len(api.codes.sent))
	}
	if signedIn := api.phoneVerify(start.Challenge.ChallengeID, "123456"); !signedIn.NewUser {
		t.Fatalf("signed in = %+v", signedIn)
	}
}

func TestAddAndRemovePhone(t *testing.T) {
	api := newTestAPI(t)
	sam := api.signIn("g-1", "Sam", "sam@example.com")
	rec := api.do("POST", "/v1/me/phone/start", sam.Token, map[string]string{"phone": "+447911123456", "channel": "sms"})
	expectStatus(t, rec, http.StatusOK)
	challenge := decode[codeChallenge](t, rec)

	// Someone else can't use Sam's code.
	alex := api.signIn("g-2", "Alex", "alex@example.com")
	rec = api.do("POST", "/v1/me/phone/verify", alex.Token, map[string]string{"challenge_id": challenge.ChallengeID, "code": api.codes.last(t).code})
	expectStatus(t, rec, http.StatusGone)

	rec = api.do("POST", "/v1/me/phone/verify", sam.Token, map[string]string{"challenge_id": challenge.ChallengeID, "code": api.codes.last(t).code})
	expectStatus(t, rec, http.StatusOK)
	if u := decode[store.User](t, rec); u.Phone != "+447911123456" || !slices.Equal(u.SignInMethods, []string{"google", "phone"}) {
		t.Fatalf("user = %+v", u)
	}

	// Now Sam's email gets a code too.
	api.ageCodes(time.Minute)
	if start := api.phoneStart("sam@example.com", ""); start.Next != "code" {
		t.Fatalf("start = %+v", start)
	}

	// The number is Sam's now.
	rec = api.do("POST", "/v1/me/phone/start", alex.Token, map[string]string{"phone": "+447911123456", "channel": "sms"})
	expectStatus(t, rec, http.StatusConflict)

	rec = api.do("DELETE", "/v1/me/phone", sam.Token, nil)
	expectStatus(t, rec, http.StatusOK)
	if u := decode[store.User](t, rec); u.Phone != "" {
		t.Fatalf("user = %+v", u)
	}

	// A phone-only account can't remove its only way in.
	api.ageCodes(time.Hour)
	phoneOnly := api.phoneSignUp("pat@example.com", "+447911123457")
	rec = api.do("DELETE", "/v1/me/phone", phoneOnly.Token, nil)
	expectStatus(t, rec, http.StatusConflict)
}
