package server

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/url"
	"strings"

	"github.com/tomhartill/kinzo/api/internal/auth"
	"github.com/tomhartill/kinzo/api/internal/store"
)

// IDTokenVerifier checks an ID token a provider issued straight to the app, such as the one
// Android's Credential Manager returns for Google.
type IDTokenVerifier interface {
	VerifyIDToken(ctx context.Context, token string) (auth.Identity, error)
}

type accounts struct {
	store     *store.Store
	publicURL string
	// Browser sign-in providers by name ("google", "facebook").
	providers map[string]auth.Provider
	// Verifies Google ID tokens from the Android app; nil when Google is off.
	googleIDTokens IDTokenVerifier
}

// WithAccounts turns on sign-in, accounts, follows and blocks.
func WithAccounts(st *store.Store, publicURL string, providers []auth.Provider, googleIDTokens IDTokenVerifier) Option {
	return func(s *Server) {
		byName := make(map[string]auth.Provider, len(providers))
		for _, p := range providers {
			byName[p.Name()] = p
		}
		s.accounts = &accounts{
			store:          st,
			publicURL:      strings.TrimSuffix(publicURL, "/"),
			providers:      byName,
			googleIDTokens: googleIDTokens,
		}
	}
}

func (s *Server) accountRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /v1/auth/providers", s.authProviders)
	mux.HandleFunc("GET /v1/auth/{provider}/start", s.authStart)
	mux.HandleFunc("GET /v1/auth/{provider}/callback", s.authCallback)
	mux.HandleFunc("POST /v1/auth/exchange", s.authExchange)
	mux.HandleFunc("POST /v1/auth/google/id-token", s.authGoogleIDToken)
	mux.HandleFunc("POST /v1/auth/sign-out", s.signedIn(s.signOut))

	mux.HandleFunc("GET /v1/me", s.signedIn(s.getMe))
	mux.HandleFunc("PATCH /v1/me", s.signedIn(s.updateMe))
	mux.HandleFunc("DELETE /v1/me", s.signedIn(s.deleteMe))
	mux.HandleFunc("GET /v1/usernames/{username}", s.signedIn(s.usernameAvailability))

	mux.HandleFunc("GET /v1/users/search", s.signedIn(s.searchUsers))
	mux.HandleFunc("GET /v1/users/{id}", s.signedIn(s.getUser))
	mux.HandleFunc("GET /v1/follows", s.signedIn(s.listFollows))
	mux.HandleFunc("POST /v1/follows", s.signedIn(s.follow))
	mux.HandleFunc("DELETE /v1/follows/{id}", s.signedIn(s.unfollow))
	mux.HandleFunc("POST /v1/followers/{id}/accept", s.signedIn(s.acceptFollower))
	mux.HandleFunc("DELETE /v1/followers/{id}", s.signedIn(s.removeFollower))
	mux.HandleFunc("GET /v1/blocks", s.signedIn(s.listBlocks))
	mux.HandleFunc("POST /v1/blocks", s.signedIn(s.block))
	mux.HandleFunc("DELETE /v1/blocks/{id}", s.signedIn(s.unblock))

	mux.HandleFunc("GET /v1/invites/mine", s.signedIn(s.myInvite))
	mux.HandleFunc("POST /v1/invites/mine/reset", s.signedIn(s.resetInvite))
	mux.HandleFunc("GET /v1/invites/{code}", s.signedIn(s.getInvite))
	mux.HandleFunc("POST /v1/invites/{code}/accept", s.signedIn(s.acceptInvite))
	mux.HandleFunc("GET /i/{code}", s.invitePage)

	mux.HandleFunc("POST /v1/scan/machine", s.signedIn(s.scanMachine))
}

// session is the signed-in caller of a request.
type session struct {
	id   string
	user store.User
}

// signedIn wraps a handler that needs a signed-in caller, from an "Authorization: Bearer"
// session token.
func (s *Server) signedIn(next func(http.ResponseWriter, *http.Request, session)) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token, ok := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
		if !ok || token == "" {
			writeError(w, http.StatusUnauthorized, "signed_out", "Sign in to do that.")
			return
		}
		id, user, err := s.accounts.store.SessionUser(r.Context(), token)
		if errors.Is(err, store.ErrNotFound) {
			writeError(w, http.StatusUnauthorized, "signed_out", "Your session has ended. Sign in again.")
			return
		}
		if err != nil {
			s.internalError(w, "load session", err)
			return
		}
		next(w, r, session{id: id, user: user})
	}
}

func (s *Server) internalError(w http.ResponseWriter, what string, err error) {
	s.logger.Error(what, "err", err)
	writeError(w, http.StatusInternalServerError, "internal", "Something went wrong on our side. Try again in a moment.")
}

// readJSON decodes a small JSON request body, answering 400 itself when it can't.
func readJSON(w http.ResponseWriter, r *http.Request, v any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, 64<<10)
	if err := json.NewDecoder(r.Body).Decode(v); err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "The request body isn't valid JSON.")
		return false
	}
	return true
}

// signInResponse is what the app gets when someone signs in.
type signInResponse struct {
	Token string     `json:"token"`
	User  store.User `json:"user"`
	// NewUser is true on a first sign-in, so the app can offer to pick a username.
	NewUser bool `json:"new_user"`
}

func (s *Server) authProviders(w http.ResponseWriter, _ *http.Request) {
	names := []string{}
	for _, name := range []string{"google", "facebook"} {
		if _, ok := s.accounts.providers[name]; ok {
			names = append(names, name)
		}
	}
	channels := []string{}
	if s.phone != nil {
		names = append(names, "phone")
		channels = s.phone.channels
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"providers":        names,
		"google_id_tokens": s.accounts.googleIDTokens != nil,
		"phone_channels":   channels,
	})
}

// authStart begins a browser sign-in. The app opens this URL with where to come back to and
// a PKCE challenge; the API remembers both and sends the person on to the provider.
func (s *Server) authStart(w http.ResponseWriter, r *http.Request) {
	provider, ok := s.accounts.providers[r.PathValue("provider")]
	if !ok {
		writeError(w, http.StatusNotFound, "unknown_provider", "That sign-in option isn't available.")
		return
	}
	q := r.URL.Query()
	redirect := q.Get("redirect_uri")
	challenge := q.Get("code_challenge")
	if !allowedAppRedirect(redirect) {
		writeError(w, http.StatusBadRequest, "bad_redirect", "redirect_uri must be kinzo://auth/callback or a loopback address.")
		return
	}
	if q.Get("code_challenge_method") != "S256" || len(challenge) != 43 {
		writeError(w, http.StatusBadRequest, "bad_challenge", "An S256 code_challenge is required.")
		return
	}
	state := auth.NewToken("", 24)
	if err := s.accounts.store.StartAuthFlow(r.Context(), state, provider.Name(), redirect, challenge); err != nil {
		s.internalError(w, "start auth flow", err)
		return
	}
	http.Redirect(w, r, provider.AuthURL(state, s.callbackURL(provider.Name())), http.StatusFound)
}

func (s *Server) callbackURL(provider string) string {
	return s.accounts.publicURL + "/v1/auth/" + provider + "/callback"
}

// authCallback is where the provider sends the person back. The API swaps the provider's code
// for who they are, then hands the app a one-time code of its own.
func (s *Server) authCallback(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	flow, err := s.accounts.store.TakeAuthFlow(r.Context(), q.Get("state"))
	if err != nil || flow.Provider != r.PathValue("provider") {
		if err != nil && !errors.Is(err, store.ErrNotFound) {
			s.logger.Error("take auth flow", "err", err)
		}
		writeMessagePage(w, http.StatusBadRequest, "That sign-in link has expired",
			"Go back to Kinzo and try signing in again.")
		return
	}
	back := func(params url.Values) {
		http.Redirect(w, r, withQuery(flow.AppRedirect, params), http.StatusFound)
	}
	if e := q.Get("error"); e != "" || q.Get("code") == "" {
		// The person backed out on the provider's page, or said no to sharing.
		back(url.Values{"error": {"cancelled"}})
		return
	}
	provider := s.accounts.providers[flow.Provider]
	identity, err := provider.Exchange(r.Context(), q.Get("code"), s.callbackURL(flow.Provider))
	if err != nil {
		s.logger.Warn("provider exchange failed", "provider", flow.Provider, "err", err)
		back(url.Values{"error": {"failed"}})
		return
	}
	user, newUser, err := s.accounts.store.SignIn(r.Context(), identity)
	if err != nil {
		s.logger.Error("sign in", "err", err)
		back(url.Values{"error": {"failed"}})
		return
	}
	code, err := s.accounts.store.CreateAuthCode(r.Context(), user.ID, flow.CodeChallenge, newUser)
	if err != nil {
		s.logger.Error("create auth code", "err", err)
		back(url.Values{"error": {"failed"}})
		return
	}
	back(url.Values{"code": {code}})
}

// authExchange swaps the one-time code from a browser sign-in for a session token.
func (s *Server) authExchange(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Code         string `json:"code"`
		CodeVerifier string `json:"code_verifier"`
	}
	if !readJSON(w, r, &body) {
		return
	}
	userID, newUser, err := s.accounts.store.TakeAuthCode(r.Context(), body.Code, body.CodeVerifier)
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusUnauthorized, "invalid_code", "That sign-in has expired. Try again.")
		return
	}
	if err != nil {
		s.internalError(w, "take auth code", err)
		return
	}
	s.finishSignIn(w, r, userID, newUser)
}

// authGoogleIDToken signs in with a Google ID token the app got itself (Android's
// one-tap Google sign-in), without going through the browser.
func (s *Server) authGoogleIDToken(w http.ResponseWriter, r *http.Request) {
	if s.accounts.googleIDTokens == nil {
		writeError(w, http.StatusNotFound, "unknown_provider", "Google sign-in isn't set up on this server.")
		return
	}
	var body struct {
		IDToken string `json:"id_token"`
	}
	if !readJSON(w, r, &body) {
		return
	}
	identity, err := s.accounts.googleIDTokens.VerifyIDToken(r.Context(), body.IDToken)
	if err != nil {
		if !errors.Is(err, auth.ErrInvalidToken) {
			s.logger.Error("verify google id token", "err", err)
		}
		writeError(w, http.StatusUnauthorized, "invalid_token", "Google didn't confirm that sign-in. Try again.")
		return
	}
	user, newUser, err := s.accounts.store.SignIn(r.Context(), identity)
	if err != nil {
		s.internalError(w, "sign in", err)
		return
	}
	s.finishSignIn(w, r, user.ID, newUser)
}

func (s *Server) finishSignIn(w http.ResponseWriter, r *http.Request, userID string, newUser bool) {
	user, err := s.accounts.store.UserByID(r.Context(), userID)
	if err != nil {
		s.internalError(w, "load user", err)
		return
	}
	token, err := s.accounts.store.CreateSession(r.Context(), userID)
	if err != nil {
		s.internalError(w, "create session", err)
		return
	}
	writeJSON(w, http.StatusOK, signInResponse{Token: token, User: user, NewUser: newUser})
}

func (s *Server) signOut(w http.ResponseWriter, r *http.Request, sess session) {
	if err := s.accounts.store.DeleteSession(r.Context(), sess.id); err != nil {
		s.internalError(w, "delete session", err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) getMe(w http.ResponseWriter, _ *http.Request, sess session) {
	writeJSON(w, http.StatusOK, sess.user)
}

func (s *Server) updateMe(w http.ResponseWriter, r *http.Request, sess session) {
	var body struct {
		Username    *string `json:"username"`
		DisplayName *string `json:"display_name"`
		Private     *bool   `json:"private"`
	}
	if !readJSON(w, r, &body) {
		return
	}
	user, err := s.accounts.store.UpdateProfile(r.Context(), sess.user.ID, body.Username, body.DisplayName, body.Private)
	switch {
	case errors.Is(err, store.ErrInvalidUsername):
		writeError(w, http.StatusBadRequest, "invalid_username", "Use 3 to 30 letters, numbers, dots or underscores.")
	case errors.Is(err, store.ErrUsernameTaken):
		writeError(w, http.StatusConflict, "username_taken", "Someone already has that username.")
	case errors.Is(err, store.ErrInvalidDisplayName):
		writeError(w, http.StatusBadRequest, "invalid_display_name", "Names can't be blank or longer than 50 characters.")
	case err != nil:
		s.internalError(w, "update profile", err)
	default:
		writeJSON(w, http.StatusOK, user)
	}
}

func (s *Server) deleteMe(w http.ResponseWriter, r *http.Request, sess session) {
	if err := s.accounts.store.DeleteAccount(r.Context(), sess.user.ID); err != nil {
		s.internalError(w, "delete account", err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) usernameAvailability(w http.ResponseWriter, r *http.Request, sess session) {
	username := store.NormalizeUsername(r.PathValue("username"))
	if !store.ValidUsername(username) {
		writeJSON(w, http.StatusOK, map[string]any{"username": username, "available": false, "valid": false})
		return
	}
	available, err := s.accounts.store.UsernameAvailable(r.Context(), sess.user.ID, username)
	if err != nil {
		s.internalError(w, "username availability", err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"username": username, "available": available, "valid": true})
}

// withQuery adds params to a URL that may already have a query.
func withQuery(raw string, params url.Values) string {
	u, err := url.Parse(raw)
	if err != nil {
		return raw
	}
	q := u.Query()
	for k, vs := range params {
		q[k] = vs
	}
	u.RawQuery = q.Encode()
	return u.String()
}

// allowedAppRedirect accepts only the app's own scheme, or a loopback address for the
// desktop build, so a sign-in can't be sent to someone else's site.
func allowedAppRedirect(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil {
		return false
	}
	switch u.Scheme {
	case "kinzo":
		return u.Host == "auth"
	case "http":
		host := u.Hostname()
		return (host == "127.0.0.1" || host == "localhost") && u.Port() != ""
	}
	return false
}
