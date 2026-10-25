package server

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"testing"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/tomhartill/kinzo/api/internal/auth"
	"github.com/tomhartill/kinzo/api/internal/database"
	"github.com/tomhartill/kinzo/api/internal/store"
)

// These tests run against a real Postgres when KINZO_TEST_DATABASE_URL is set (CI sets it).
// They wipe the users table, so never point it at a database you care about.

// fakeProvider stands in for Google or Facebook: each code maps to an identity.
type fakeProvider struct {
	name       string
	identities map[string]auth.Identity
}

func (f *fakeProvider) Name() string { return f.name }

func (f *fakeProvider) AuthURL(state, redirectURI string) string {
	return "https://provider.example/auth?" + url.Values{"state": {state}, "redirect_uri": {redirectURI}}.Encode()
}

func (f *fakeProvider) Exchange(_ context.Context, code, _ string) (auth.Identity, error) {
	id, ok := f.identities[code]
	if !ok {
		return auth.Identity{}, auth.ErrInvalidToken
	}
	return id, nil
}

type fakeIDTokens map[string]auth.Identity

func (f fakeIDTokens) VerifyIDToken(_ context.Context, token string) (auth.Identity, error) {
	id, ok := f[token]
	if !ok {
		return auth.Identity{}, auth.ErrInvalidToken
	}
	return id, nil
}

type testAPI struct {
	t       *testing.T
	handler http.Handler
	google  *fakeProvider
	idToken fakeIDTokens
	scans   *fakeRecogniser
	codes   *fakeCodes
	pool    *pgxpool.Pool
}

func newTestAPI(t *testing.T) *testAPI {
	t.Helper()
	dbURL := os.Getenv("KINZO_TEST_DATABASE_URL")
	if dbURL == "" {
		t.Skip("KINZO_TEST_DATABASE_URL not set")
	}
	ctx := context.Background()
	pool, err := pgxpool.New(ctx, dbURL)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(pool.Close)
	if err := database.Migrate(ctx, pool); err != nil {
		t.Fatal(err)
	}
	if _, err := pool.Exec(ctx, `TRUNCATE users, auth_flows CASCADE`); err != nil {
		t.Fatal(err)
	}
	api := &testAPI{
		t:       t,
		google:  &fakeProvider{name: "google", identities: map[string]auth.Identity{}},
		idToken: fakeIDTokens{},
		scans:   &fakeRecogniser{},
		codes:   &fakeCodes{},
		pool:    pool,
	}
	facebook := &fakeProvider{name: "facebook", identities: map[string]auth.Identity{}}
	srv := New(pool, slog.New(slog.NewTextHandler(io.Discard, nil)),
		WithAccounts(store.New(pool), "https://api.example", []auth.Provider{api.google, facebook}, api.idToken),
		WithMachineScan(api.scans, 2),
		WithPhoneCodes(auth.WithTestNumbers(api.codes, testNumbers),
			auth.PhoneRules{Countries: []string{"GB"}, TestNumbers: testNumbers}, []string{"sms", "whatsapp"}, false))
	api.handler = srv.Handler()
	return api
}

func (a *testAPI) do(method, path, token string, body any) *httptest.ResponseRecorder {
	a.t.Helper()
	var r io.Reader
	if body != nil {
		b, _ := json.Marshal(body)
		r = bytes.NewReader(b)
	}
	req := httptest.NewRequest(method, path, r)
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	a.handler.ServeHTTP(rec, req)
	return rec
}

func decode[T any](t *testing.T, rec *httptest.ResponseRecorder) T {
	t.Helper()
	var v T
	if err := json.Unmarshal(rec.Body.Bytes(), &v); err != nil {
		t.Fatalf("decode %q: %v", rec.Body.String(), err)
	}
	return v
}

func expectStatus(t *testing.T, rec *httptest.ResponseRecorder, want int) {
	t.Helper()
	if rec.Code != want {
		t.Fatalf("status = %d, want %d; body %s", rec.Code, want, rec.Body.String())
	}
}

// signInWithGoogleToken signs someone in the way the Android app does.
func (a *testAPI) signIn(subject, name, email string) signInResponse {
	a.t.Helper()
	token := "token-" + subject
	a.idToken[token] = auth.Identity{Provider: "google", Subject: subject, Name: name, Email: email, EmailVerified: email != ""}
	rec := a.do("POST", "/v1/auth/google/id-token", "", map[string]string{"id_token": token})
	expectStatus(a.t, rec, http.StatusOK)
	return decode[signInResponse](a.t, rec)
}

func TestBrowserSignInFlow(t *testing.T) {
	api := newTestAPI(t)
	api.google.identities["provider-code"] = auth.Identity{Provider: "google", Subject: "g-1", Name: "Tom Hartill", Email: "tom@example.com", EmailVerified: true}

	verifier := strings.Repeat("v", 50)
	sum := sha256.Sum256([]byte(verifier))
	challenge := base64.RawURLEncoding.EncodeToString(sum[:])

	// 1. The app opens the start URL; the API sends the browser to the provider.
	start := api.do("GET", "/v1/auth/google/start?"+url.Values{
		"redirect_uri":          {"kinzo://auth/callback"},
		"code_challenge":        {challenge},
		"code_challenge_method": {"S256"},
	}.Encode(), "", nil)
	expectStatus(t, start, http.StatusFound)
	providerURL, _ := url.Parse(start.Header().Get("Location"))
	state := providerURL.Query().Get("state")
	if providerURL.Query().Get("redirect_uri") != "https://api.example/v1/auth/google/callback" {
		t.Fatalf("provider redirect_uri = %q", providerURL.Query().Get("redirect_uri"))
	}

	// 2. The provider sends the browser back; the API sends it on to the app with a code.
	cb := api.do("GET", "/v1/auth/google/callback?"+url.Values{"state": {state}, "code": {"provider-code"}}.Encode(), "", nil)
	expectStatus(t, cb, http.StatusFound)
	appURL, _ := url.Parse(cb.Header().Get("Location"))
	if appURL.Scheme != "kinzo" || appURL.Query().Get("code") == "" {
		t.Fatalf("app redirect = %s", appURL)
	}
	code := appURL.Query().Get("code")

	// The state only works once.
	again := api.do("GET", "/v1/auth/google/callback?"+url.Values{"state": {state}, "code": {"provider-code"}}.Encode(), "", nil)
	expectStatus(t, again, http.StatusBadRequest)

	// 3. The app swaps the code for a session, but only with the right verifier.
	bad := api.do("POST", "/v1/auth/exchange", "", map[string]string{"code": code, "code_verifier": strings.Repeat("x", 50)})
	expectStatus(t, bad, http.StatusUnauthorized)
	// A wrong verifier burns the code too, so get a fresh one.
	start = api.do("GET", "/v1/auth/google/start?"+url.Values{
		"redirect_uri": {"kinzo://auth/callback"}, "code_challenge": {challenge}, "code_challenge_method": {"S256"},
	}.Encode(), "", nil)
	providerURL, _ = url.Parse(start.Header().Get("Location"))
	cb = api.do("GET", "/v1/auth/google/callback?"+url.Values{"state": {providerURL.Query().Get("state")}, "code": {"provider-code"}}.Encode(), "", nil)
	appURL, _ = url.Parse(cb.Header().Get("Location"))

	ok := api.do("POST", "/v1/auth/exchange", "", map[string]string{"code": appURL.Query().Get("code"), "code_verifier": verifier})
	expectStatus(t, ok, http.StatusOK)
	signedIn := decode[signInResponse](t, ok)
	// Not NewUser: the account was made on the first, burned attempt.
	if signedIn.NewUser || signedIn.User.Username != "tomhartill" || signedIn.User.DisplayName != "Tom Hartill" {
		t.Fatalf("sign in = %+v", signedIn)
	}

	me := api.do("GET", "/v1/me", signedIn.Token, nil)
	expectStatus(t, me, http.StatusOK)

	// Signing out ends the session.
	expectStatus(t, api.do("POST", "/v1/auth/sign-out", signedIn.Token, nil), http.StatusNoContent)
	expectStatus(t, api.do("GET", "/v1/me", signedIn.Token, nil), http.StatusUnauthorized)
}

func TestBrowserSignInCancelled(t *testing.T) {
	api := newTestAPI(t)
	start := api.do("GET", "/v1/auth/facebook/start?"+url.Values{
		"redirect_uri":          {"http://127.0.0.1:53682/callback"},
		"code_challenge":        {strings.Repeat("c", 43)},
		"code_challenge_method": {"S256"},
	}.Encode(), "", nil)
	expectStatus(t, start, http.StatusFound)
	providerURL, _ := url.Parse(start.Header().Get("Location"))
	cb := api.do("GET", "/v1/auth/facebook/callback?"+url.Values{"state": {providerURL.Query().Get("state")}, "error": {"access_denied"}}.Encode(), "", nil)
	expectStatus(t, cb, http.StatusFound)
	if got := cb.Header().Get("Location"); got != "http://127.0.0.1:53682/callback?error=cancelled" {
		t.Fatalf("redirect = %s", got)
	}
}

func TestSignInRejectsOtherRedirects(t *testing.T) {
	api := newTestAPI(t)
	for _, redirect := range []string{"https://evil.example/cb", "kinzo://elsewhere", "http://evil.example:80/cb", ""} {
		rec := api.do("GET", "/v1/auth/google/start?"+url.Values{
			"redirect_uri": {redirect}, "code_challenge": {strings.Repeat("c", 43)}, "code_challenge_method": {"S256"},
		}.Encode(), "", nil)
		expectStatus(t, rec, http.StatusBadRequest)
	}
}

func TestSameEmailJoinsOneAccount(t *testing.T) {
	api := newTestAPI(t)
	first := api.signIn("g-1", "Tom", "tom@example.com")
	again := api.signIn("g-1", "Tom", "tom@example.com")
	if again.NewUser || again.User.ID != first.User.ID {
		t.Fatal("returning user got a new account")
	}
	// A different provider with the same verified email lands in the same account.
	api.idToken["fb"] = auth.Identity{Provider: "facebook", Subject: "fb-1", Name: "Tom", Email: "TOM@example.com", EmailVerified: true}
	rec := api.do("POST", "/v1/auth/google/id-token", "", map[string]string{"id_token": "fb"})
	expectStatus(t, rec, http.StatusOK)
	if got := decode[signInResponse](t, rec); got.User.ID != first.User.ID {
		t.Fatal("same verified email made a second account")
	}
	// Same name, no email: a separate account with its own username.
	other := api.signIn("g-2", "Tom", "")
	if other.User.ID == first.User.ID || other.User.Username == first.User.Username {
		t.Fatalf("second Tom = %+v", other.User)
	}
}

func TestUsernames(t *testing.T) {
	api := newTestAPI(t)
	tom := api.signIn("g-1", "Tom", "")
	sam := api.signIn("g-2", "Sam", "")

	rec := api.do("PATCH", "/v1/me", tom.Token, map[string]string{"username": "@Tom.Lifts"})
	expectStatus(t, rec, http.StatusOK)
	if u := decode[store.User](t, rec); u.Username != "tom.lifts" {
		t.Fatalf("username = %q", u.Username)
	}
	expectStatus(t, api.do("PATCH", "/v1/me", sam.Token, map[string]string{"username": "tom.lifts"}), http.StatusConflict)
	expectStatus(t, api.do("PATCH", "/v1/me", sam.Token, map[string]string{"username": "no spaces"}), http.StatusBadRequest)

	avail := decode[map[string]any](t, api.do("GET", "/v1/usernames/tom.lifts", sam.Token, nil))
	if avail["available"] != false {
		t.Fatalf("tom.lifts available to Sam: %v", avail)
	}
	avail = decode[map[string]any](t, api.do("GET", "/v1/usernames/tom.lifts", tom.Token, nil))
	if avail["available"] != true {
		t.Fatalf("tom.lifts not available to its owner: %v", avail)
	}
}

func (a *testAPI) follows(token string) store.FollowLists {
	a.t.Helper()
	rec := a.do("GET", "/v1/follows", token, nil)
	expectStatus(a.t, rec, http.StatusOK)
	return decode[store.FollowLists](a.t, rec)
}

func (a *testAPI) follow(token, userID string) store.Person {
	a.t.Helper()
	rec := a.do("POST", "/v1/follows", token, map[string]string{"user_id": userID})
	expectStatus(a.t, rec, http.StatusOK)
	return decode[store.Person](a.t, rec)
}

func TestFollowing(t *testing.T) {
	api := newTestAPI(t)
	tom := api.signIn("g-1", "Tom", "")
	sam := api.signIn("g-2", "Sam", "")

	search := decode[map[string][]store.Person](t, api.do("GET", "/v1/users/search?q=@sa", tom.Token, nil))
	if len(search["users"]) != 1 || search["users"][0].ID != sam.User.ID || search["users"][0].Following != store.FollowingNone {
		t.Fatalf("search = %+v", search)
	}

	// Following is one-way and needs no approval from a public account.
	if p := api.follow(tom.Token, sam.User.ID); p.Following != store.FollowingYes || p.FollowsYou {
		t.Fatalf("after following = %+v", p)
	}
	samLists := api.follows(sam.Token)
	if len(samLists.Followers) != 1 || !samLists.Followers[0].FollowsYou || samLists.Followers[0].Following != store.FollowingNone ||
		len(samLists.Following) != 0 {
		t.Fatalf("sam's lists = %+v", samLists)
	}
	if lists := api.follows(tom.Token); len(lists.Following) != 1 || len(lists.Followers) != 0 {
		t.Fatalf("tom's lists = %+v", lists)
	}

	// Following back makes it both ways; following again changes nothing.
	api.follow(sam.Token, tom.User.ID)
	if p := api.follow(sam.Token, tom.User.ID); p.Following != store.FollowingYes || !p.FollowsYou {
		t.Fatalf("follow back = %+v", p)
	}
	profile := decode[store.Profile](t, api.do("GET", "/v1/users/"+sam.User.ID, tom.Token, nil))
	if profile.FollowerCount != 1 || profile.FollowingCount != 1 {
		t.Fatalf("profile = %+v", profile)
	}

	// Unfollowing only ends Tom's side.
	expectStatus(t, api.do("DELETE", "/v1/follows/"+sam.User.ID, tom.Token, nil), http.StatusNoContent)
	if lists := api.follows(tom.Token); len(lists.Following) != 0 || len(lists.Followers) != 1 {
		t.Fatalf("tom's lists after unfollowing = %+v", lists)
	}
	// Sam can remove Tom as a follower too.
	expectStatus(t, api.do("DELETE", "/v1/followers/"+sam.User.ID, tom.Token, nil), http.StatusNoContent)
	if lists := api.follows(sam.Token); len(lists.Following) != 0 {
		t.Fatalf("sam still follows tom: %+v", lists)
	}

	expectStatus(t, api.do("POST", "/v1/follows", tom.Token, map[string]string{"user_id": tom.User.ID}), http.StatusBadRequest)
}

func TestPrivateAccounts(t *testing.T) {
	api := newTestAPI(t)
	tom := api.signIn("g-1", "Tom", "")
	sam := api.signIn("g-2", "Sam", "")
	pat := api.signIn("g-3", "Pat", "")

	rec := api.do("PATCH", "/v1/me", sam.Token, map[string]bool{"private": true})
	expectStatus(t, rec, http.StatusOK)
	if u := decode[store.User](t, rec); !u.Private {
		t.Fatalf("sam = %+v", u)
	}

	// Following a private account asks first.
	if p := api.follow(tom.Token, sam.User.ID); p.Following != store.FollowingRequested || !p.Private {
		t.Fatalf("follow private = %+v", p)
	}
	api.follow(pat.Token, sam.User.ID)
	lists := api.follows(sam.Token)
	if len(lists.Requests) != 2 || len(lists.Followers) != 0 || !lists.Requests[0].RequestedYou {
		t.Fatalf("sam's lists = %+v", lists)
	}

	// Tom can't approve his own request; Sam can.
	expectStatus(t, api.do("POST", "/v1/followers/"+sam.User.ID+"/accept", tom.Token, nil), http.StatusNotFound)
	expectStatus(t, api.do("POST", "/v1/followers/"+tom.User.ID+"/accept", sam.Token, nil), http.StatusNoContent)
	if lists := api.follows(tom.Token); len(lists.Following) != 1 || lists.Following[0].Following != store.FollowingYes {
		t.Fatalf("tom's lists = %+v", lists)
	}

	// Going public lets everyone waiting in.
	api.do("PATCH", "/v1/me", sam.Token, map[string]bool{"private": false})
	if lists := api.follows(sam.Token); len(lists.Followers) != 2 || len(lists.Requests) != 0 {
		t.Fatalf("sam's lists after going public = %+v", lists)
	}
}

func TestDeclineAndCancelRequests(t *testing.T) {
	api := newTestAPI(t)
	tom := api.signIn("g-1", "Tom", "")
	sam := api.signIn("g-2", "Sam", "")
	api.do("PATCH", "/v1/me", sam.Token, map[string]bool{"private": true})

	api.follow(tom.Token, sam.User.ID)
	expectStatus(t, api.do("DELETE", "/v1/followers/"+tom.User.ID, sam.Token, nil), http.StatusNoContent)
	if lists := api.follows(tom.Token); len(lists.Following) != 0 {
		t.Fatal("declined request still pending")
	}
	api.follow(tom.Token, sam.User.ID)
	expectStatus(t, api.do("DELETE", "/v1/follows/"+sam.User.ID, tom.Token, nil), http.StatusNoContent)
	if lists := api.follows(sam.Token); len(lists.Requests) != 0 {
		t.Fatal("cancelled request still pending")
	}
}

func TestBlocking(t *testing.T) {
	api := newTestAPI(t)
	tom := api.signIn("g-1", "Tom", "")
	sam := api.signIn("g-2", "Sam", "")
	api.follow(tom.Token, sam.User.ID)
	api.follow(sam.Token, tom.User.ID)

	expectStatus(t, api.do("POST", "/v1/blocks", sam.Token, map[string]string{"user_id": tom.User.ID}), http.StatusNoContent)
	if lists := api.follows(tom.Token); len(lists.Following)+len(lists.Followers) != 0 {
		t.Fatal("blocking didn't end the follows")
	}
	// Tom can't find or follow Sam, and gets the same answer as for someone who doesn't exist.
	if found := decode[map[string][]store.Person](t, api.do("GET", "/v1/users/search?q=sam", tom.Token, nil)); len(found["users"]) != 0 {
		t.Fatal("blocked user shows in search")
	}
	expectStatus(t, api.do("POST", "/v1/follows", tom.Token, map[string]string{"user_id": sam.User.ID}), http.StatusNotFound)

	blocked := decode[map[string][]store.PublicUser](t, api.do("GET", "/v1/blocks", sam.Token, nil))
	if len(blocked["users"]) != 1 {
		t.Fatalf("blocks = %+v", blocked)
	}
	expectStatus(t, api.do("DELETE", "/v1/blocks/"+tom.User.ID, sam.Token, nil), http.StatusNoContent)
	expectStatus(t, api.do("POST", "/v1/follows", tom.Token, map[string]string{"user_id": sam.User.ID}), http.StatusOK)
}

func TestInvites(t *testing.T) {
	api := newTestAPI(t)
	tom := api.signIn("g-1", "Tom", "")
	sam := api.signIn("g-2", "Sam", "")

	inv := decode[invite](t, api.do("GET", "/v1/invites/mine", tom.Token, nil))
	if inv.URL != "https://api.example/i/"+inv.Code {
		t.Fatalf("invite = %+v", inv)
	}
	page := api.do("GET", "/i/"+inv.Code, "", nil)
	expectStatus(t, page, http.StatusOK)
	if !strings.Contains(page.Body.String(), "kinzo://invite/"+inv.Code) {
		t.Fatal("invite page has no app link")
	}

	preview := decode[map[string]any](t, api.do("GET", "/v1/invites/"+inv.Code, sam.Token, nil))
	if preview["is_you"] != false {
		t.Fatalf("preview = %v", preview)
	}
	// An invite makes two people follow each other, even when the inviter is private.
	api.do("PATCH", "/v1/me", tom.Token, map[string]bool{"private": true})
	rec := api.do("POST", "/v1/invites/"+inv.Code+"/accept", sam.Token, nil)
	expectStatus(t, rec, http.StatusOK)
	if p := decode[store.Person](t, rec); p.Following != store.FollowingYes || !p.FollowsYou {
		t.Fatalf("accepted invite = %+v", p)
	}
	if lists := api.follows(tom.Token); len(lists.Followers) != 1 || len(lists.Following) != 1 {
		t.Fatalf("invite didn't make follows: %+v", lists)
	}

	// A reset link stops the old one working.
	fresh := decode[invite](t, api.do("POST", "/v1/invites/mine/reset", tom.Token, nil))
	if fresh.Code == inv.Code {
		t.Fatal("reset kept the same code")
	}
	expectStatus(t, api.do("GET", "/v1/invites/"+inv.Code, sam.Token, nil), http.StatusNotFound)
	expectStatus(t, api.do("GET", "/i/"+inv.Code, "", nil), http.StatusNotFound)
}

func TestDeleteAccount(t *testing.T) {
	api := newTestAPI(t)
	tom := api.signIn("g-1", "Tom", "")
	sam := api.signIn("g-2", "Sam", "")
	api.follow(tom.Token, sam.User.ID)

	expectStatus(t, api.do("DELETE", "/v1/me", tom.Token, nil), http.StatusNoContent)
	expectStatus(t, api.do("GET", "/v1/me", tom.Token, nil), http.StatusUnauthorized)
	if lists := api.follows(sam.Token); len(lists.Followers) != 0 {
		t.Fatal("deleted account still follows")
	}
	// Signing in again starts a fresh account.
	if again := api.signIn("g-1", "Tom", ""); !again.NewUser {
		t.Fatal("deleted account came back")
	}
}

func TestSignedOutRequestsAreRefused(t *testing.T) {
	api := newTestAPI(t)
	for _, path := range []string{"/v1/me", "/v1/follows", "/v1/users/search?q=tom"} {
		rec := api.do("GET", path, "", nil)
		expectStatus(t, rec, http.StatusUnauthorized)
		if code := decode[apiError](t, rec).Error.Code; code != "signed_out" {
			t.Fatalf("%s error code = %q", path, code)
		}
		expectStatus(t, api.do("GET", path, "kz_not-a-real-token", nil), http.StatusUnauthorized)
	}
}
