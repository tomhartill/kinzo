package server

import (
	"errors"
	"net"
	"net/http"
	"net/mail"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"time"

	"github.com/tomhartill/kinzo/api/internal/auth"
	"github.com/tomhartill/kinzo/api/internal/store"
)

// Sending limits. Every text is billed, and SMS-pumping fraud sends thousands of codes to
// premium numbers to collect a share, so the limits are tight.
var (
	phoneLimits = []sendLimit{{1, 30 * time.Second}, {5, time.Hour}, {10, 24 * time.Hour}}
	ipLimits    = []sendLimit{{20, time.Hour}}
)

type sendLimit struct {
	max    int
	window time.Duration
}

// retryIn is how long until another send fits every limit, given when earlier sends happened
// (oldest first). Zero means one can go now.
func retryIn(sent []time.Time, limits []sendLimit, now time.Time) time.Duration {
	var wait time.Duration
	for _, l := range limits {
		var inWindow []time.Time
		for _, t := range sent {
			if now.Sub(t) < l.window {
				inWindow = append(inWindow, t)
			}
		}
		if len(inWindow) >= l.max {
			// The send that has to age out is the one max places from the newest.
			free := inWindow[len(inWindow)-l.max].Add(l.window).Sub(now)
			wait = max(wait, free)
		}
	}
	return wait
}

type phoneCodes struct {
	sender   auth.CodeSender
	rules    auth.PhoneRules
	channels []string
	// behindProxy means the API runs behind a reverse proxy, so the caller's address comes
	// from X-Forwarded-For.
	behindProxy bool
}

// WithPhoneCodes turns on sign-in with an email and a texted or WhatsApped code, and adding a
// phone to an account. channels are how codes can go ("sms", "whatsapp"). It needs
// WithAccounts too.
func WithPhoneCodes(sender auth.CodeSender, rules auth.PhoneRules, channels []string, behindProxy bool) Option {
	return func(s *Server) {
		s.phone = &phoneCodes{sender: sender, rules: rules, channels: channels, behindProxy: behindProxy}
	}
}

func (s *Server) phoneRoutes(mux *http.ServeMux) {
	mux.HandleFunc("POST /v1/auth/phone/start", s.startPhoneSignIn)
	mux.HandleFunc("POST /v1/auth/phone/verify", s.verifyPhoneSignIn)
	mux.HandleFunc("POST /v1/me/phone/start", s.signedIn(s.startAddPhone))
	mux.HandleFunc("POST /v1/me/phone/verify", s.signedIn(s.verifyAddPhone))
	mux.HandleFunc("DELETE /v1/me/phone", s.signedIn(s.removePhone))
}

// codeChallenge is what the app gets when a code is on its way.
type codeChallenge struct {
	ChallengeID string `json:"challenge_id"`
	PhoneHint   string `json:"phone_hint"`
	Channel     string `json:"channel"`
	ExpiresIn   int    `json:"expires_in"`
	ResendIn    int    `json:"resend_in"`
}

type phoneStartResponse struct {
	Next      string         `json:"next"`
	Challenge *codeChallenge `json:"challenge,omitempty"`
	Providers []string       `json:"providers,omitempty"`
}

// startPhoneSignIn takes an email, and a number when the email is new, and sends a code.
// Calling it again resends, on either channel.
func (s *Server) startPhoneSignIn(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Email   string `json:"email"`
		Phone   string `json:"phone"`
		Channel string `json:"channel"`
	}
	if !readJSON(w, r, &body) || !s.validChannel(w, body.Channel) {
		return
	}
	email, ok := normalizeEmail(body.Email)
	if !ok {
		writeError(w, http.StatusBadRequest, "invalid_email", "That doesn't look like an email address.")
		return
	}

	challenge := store.Challenge{Purpose: "sign_in", Channel: body.Channel}
	account, err := s.accounts.store.AccountByEmail(r.Context(), email)
	switch {
	case err == nil && account.Phone != "":
		// A known email: the code goes to the account's own number, whatever was typed, so
		// knowing someone's email gets you nowhere without their phone.
		challenge.UserID = account.UserID
		challenge.Phone = account.Phone
	case err == nil:
		// The account signs in only with Google or Facebook. Sending a code here would let
		// anyone who knows the email attach their own phone to it.
		writeJSON(w, http.StatusOK, phoneStartResponse{Next: "provider", Providers: account.Providers})
		return
	case errors.Is(err, store.ErrNotFound):
		if strings.TrimSpace(body.Phone) == "" {
			writeJSON(w, http.StatusOK, phoneStartResponse{Next: "phone"})
			return
		}
		phone, ok := s.parsePhone(w, body.Phone)
		if !ok {
			return
		}
		challenge.Phone = phone
		challenge.Email = email
	default:
		s.internalError(w, "account by email", err)
		return
	}
	if c, ok := s.sendCode(w, r, challenge); ok {
		writeJSON(w, http.StatusOK, phoneStartResponse{Next: "code", Challenge: c})
	}
}

// verifyPhoneSignIn checks a code and signs in.
func (s *Server) verifyPhoneSignIn(w http.ResponseWriter, r *http.Request) {
	c, ok := s.checkCode(w, r, "sign_in", "")
	if !ok {
		return
	}
	user, newUser, err := s.accounts.store.PhoneSignIn(r.Context(), c.Phone, c.Email)
	if err != nil {
		s.internalError(w, "phone sign in", err)
		return
	}
	s.finishSignIn(w, r, user.ID, newUser)
}

// startAddPhone sends a code to a number the signed-in person wants to sign in with.
func (s *Server) startAddPhone(w http.ResponseWriter, r *http.Request, sess session) {
	var body struct {
		Phone   string `json:"phone"`
		Channel string `json:"channel"`
	}
	if !readJSON(w, r, &body) || !s.validChannel(w, body.Channel) {
		return
	}
	phone, ok := s.parsePhone(w, body.Phone)
	if !ok {
		return
	}
	// Checked before sending, so nobody pays for a text that can't be used.
	owner, err := s.accounts.store.PhoneOwner(r.Context(), phone)
	if err != nil && !errors.Is(err, store.ErrNotFound) {
		s.internalError(w, "phone owner", err)
		return
	}
	if owner != "" && owner != sess.user.ID {
		writeError(w, http.StatusConflict, "phone_taken", "That number already signs in to another account.")
		return
	}
	c, ok := s.sendCode(w, r, store.Challenge{
		Purpose: "add_phone", UserID: sess.user.ID, Phone: phone, Channel: body.Channel,
	})
	if ok {
		writeJSON(w, http.StatusOK, c)
	}
}

// verifyAddPhone checks the code and saves the number on the account.
func (s *Server) verifyAddPhone(w http.ResponseWriter, r *http.Request, sess session) {
	c, ok := s.checkCode(w, r, "add_phone", sess.user.ID)
	if !ok {
		return
	}
	user, err := s.accounts.store.SetPhone(r.Context(), sess.user.ID, c.Phone)
	switch {
	case errors.Is(err, store.ErrPhoneTaken):
		writeError(w, http.StatusConflict, "phone_taken", "That number already signs in to another account.")
	case err != nil:
		s.internalError(w, "set phone", err)
	default:
		writeJSON(w, http.StatusOK, user)
	}
}

func (s *Server) removePhone(w http.ResponseWriter, r *http.Request, sess session) {
	user, err := s.accounts.store.RemovePhone(r.Context(), sess.user.ID)
	switch {
	case errors.Is(err, store.ErrLastSignInMethod):
		writeError(w, http.StatusConflict, "last_sign_in_method",
			"Your phone is the only way into this account. Add Google or Facebook first.")
	case err != nil:
		s.internalError(w, "remove phone", err)
	default:
		writeJSON(w, http.StatusOK, user)
	}
}

func (s *Server) validChannel(w http.ResponseWriter, channel string) bool {
	if !slices.Contains(s.phone.channels, channel) {
		writeError(w, http.StatusBadRequest, "unsupported_channel", "Codes can't be sent that way yet.")
		return false
	}
	return true
}

func (s *Server) parsePhone(w http.ResponseWriter, typed string) (string, bool) {
	phone, err := s.phone.rules.Parse(typed)
	switch {
	case errors.Is(err, auth.ErrUnsupportedCountry):
		writeError(w, http.StatusBadRequest, "unsupported_country", "Codes can't be sent to that country yet.")
		return "", false
	case err != nil:
		writeError(w, http.StatusBadRequest, "invalid_phone", "That doesn't look like a mobile number. Check the country and try again.")
		return "", false
	}
	return phone, true
}

// sendCode sends a code within the limits and records it. It answers the request itself when
// it can't.
func (s *Server) sendCode(w http.ResponseWriter, r *http.Request, c store.Challenge) (*codeChallenge, bool) {
	c.IP = s.clientIP(r)
	toPhone, fromIP, err := s.accounts.store.RecentCodes(r.Context(), c.Phone, c.IP)
	if err != nil {
		s.internalError(w, "recent codes", err)
		return nil, false
	}
	now := time.Now()
	if wait := max(retryIn(toPhone, phoneLimits, now), retryIn(fromIP, ipLimits, now)); wait > 0 {
		writeRetry(w, wait)
		return nil, false
	}
	ref, err := s.phone.sender.Send(r.Context(), c.Phone, c.Channel)
	if err != nil {
		if !errors.Is(err, auth.ErrSendRefused) {
			s.logger.Error("send code", "channel", c.Channel, "err", err)
		}
		other := "WhatsApp"
		if c.Channel == "whatsapp" {
			other = "a text"
		}
		writeError(w, http.StatusBadGateway, "send_failed", "We couldn't send a code to that number. Try "+other+" instead.")
		return nil, false
	}
	c.ProviderRef = ref
	id, err := s.accounts.store.CreateChallenge(r.Context(), c)
	if err != nil {
		s.internalError(w, "create challenge", err)
		return nil, false
	}
	return &codeChallenge{
		ChallengeID: id,
		PhoneHint:   auth.PhoneHint(c.Phone),
		Channel:     c.Channel,
		ExpiresIn:   int(store.CodeLifetime.Seconds()),
		ResendIn:    int(phoneLimits[0].window.Seconds()),
	}, true
}

var sixDigits = regexp.MustCompile(`^[0-9]{6}$`)

// checkCode reads a code attempt and checks it against an open challenge of the given purpose
// (and account, for adding a phone). It answers the request itself unless the code is right.
func (s *Server) checkCode(w http.ResponseWriter, r *http.Request, purpose, userID string) (store.Challenge, bool) {
	var body struct {
		ChallengeID string `json:"challenge_id"`
		Code        string `json:"code"`
	}
	if !readJSON(w, r, &body) {
		return store.Challenge{}, false
	}
	expired := func() {
		writeError(w, http.StatusGone, "code_expired", "That code has expired. Send a new one.")
	}
	c, err := s.accounts.store.OpenChallenge(r.Context(), body.ChallengeID)
	if errors.Is(err, store.ErrNotFound) || (err == nil && (c.Purpose != purpose || (userID != "" && c.UserID != userID))) {
		expired()
		return store.Challenge{}, false
	}
	if err != nil {
		s.internalError(w, "open challenge", err)
		return store.Challenge{}, false
	}
	right := false
	if sixDigits.MatchString(body.Code) {
		right, err = s.phone.sender.Check(r.Context(), c.ProviderRef, c.Phone, body.Code)
		if errors.Is(err, auth.ErrCodeExpired) {
			expired()
			return store.Challenge{}, false
		}
		if err != nil {
			s.internalError(w, "check code", err)
			return store.Challenge{}, false
		}
	}
	if !right {
		left, err := s.accounts.store.WrongCode(r.Context(), c.ID)
		if err != nil {
			s.internalError(w, "wrong code", err)
			return store.Challenge{}, false
		}
		if left == 0 {
			expired()
			return store.Challenge{}, false
		}
		writeJSON(w, http.StatusBadRequest, codeError{
			Error:        errorDetail{Code: "wrong_code", Message: "That code isn't right."},
			AttemptsLeft: left,
		})
		return store.Challenge{}, false
	}
	if err := s.accounts.store.UseChallenge(r.Context(), c.ID); err != nil {
		if errors.Is(err, store.ErrNotFound) {
			expired()
		} else {
			s.internalError(w, "use challenge", err)
		}
		return store.Challenge{}, false
	}
	return c, true
}

type errorDetail struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type codeError struct {
	Error        errorDetail `json:"error"`
	AttemptsLeft int         `json:"attempts_left,omitempty"`
	RetryIn      int         `json:"retry_in,omitempty"`
}

func writeRetry(w http.ResponseWriter, wait time.Duration) {
	seconds := int((wait + time.Second - 1) / time.Second) // rounded up
	w.Header().Set("Retry-After", strconv.Itoa(seconds))
	writeJSON(w, http.StatusTooManyRequests, codeError{
		Error:   errorDetail{Code: "too_many_codes", Message: "That's a lot of codes. Wait a little, then try again."},
		RetryIn: seconds,
	})
}

// clientIP is the caller's address, for the per-address sending limit. Behind a reverse
// proxy it's the last X-Forwarded-For entry, the one the proxy itself added; earlier entries
// come from the caller and can't be trusted.
func (s *Server) clientIP(r *http.Request) string {
	if s.phone.behindProxy {
		if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
			parts := strings.Split(xff, ",")
			if ip := net.ParseIP(strings.TrimSpace(parts[len(parts)-1])); ip != nil {
				return ip.String()
			}
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	if ip := net.ParseIP(host); ip != nil {
		return ip.String()
	}
	return ""
}

// normalizeEmail trims and lowercases an email, and reports whether it looks like one.
func normalizeEmail(raw string) (string, bool) {
	email := strings.ToLower(strings.TrimSpace(raw))
	addr, err := mail.ParseAddress(email)
	if err != nil || addr.Address != email || !strings.Contains(email[strings.LastIndex(email, "@")+1:], ".") {
		return "", false
	}
	return email, len(email) <= 254
}
