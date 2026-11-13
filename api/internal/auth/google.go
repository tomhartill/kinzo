package auth

import (
	"context"
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"math/big"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	googleAuthURL  = "https://accounts.google.com/o/oauth2/v2/auth"
	googleTokenURL = "https://oauth2.googleapis.com/token"
	googleCertsURL = "https://www.googleapis.com/oauth2/v3/certs"
)

// Google signs people in with their Google account, either through the browser (AuthURL and
// Exchange) or with an ID token the Android app got from Credential Manager (VerifyIDToken).
type Google struct {
	ClientID     string // the "Web application" OAuth client
	ClientSecret string
	// Audiences are the client IDs an ID token may be issued to. The Android app asks for
	// tokens for the web client, so this is usually just ClientID.
	Audiences []string
	HTTP      *http.Client
	Now       func() time.Time

	// The URLs, swappable in tests.
	TokenURL string
	CertsURL string

	mu        sync.Mutex
	keys      map[string]*rsa.PublicKey
	keysUntil time.Time
	lastFetch time.Time
}

func (g *Google) Name() string { return "google" }

func (g *Google) AuthURL(state, redirectURI string) string {
	q := url.Values{
		"client_id":     {g.ClientID},
		"redirect_uri":  {redirectURI},
		"response_type": {"code"},
		"scope":         {"openid email profile"},
		"state":         {state},
		// Show the account chooser, so someone with two Google accounts picks the right one.
		"prompt": {"select_account"},
	}
	return googleAuthURL + "?" + q.Encode()
}

func (g *Google) Exchange(ctx context.Context, code, redirectURI string) (Identity, error) {
	form := url.Values{
		"code":          {code},
		"client_id":     {g.ClientID},
		"client_secret": {g.ClientSecret},
		"redirect_uri":  {redirectURI},
		"grant_type":    {"authorization_code"},
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, g.tokenURL(), strings.NewReader(form.Encode()))
	if err != nil {
		return Identity{}, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	var body struct {
		IDToken string `json:"id_token"`
	}
	if err := doJSON(g.client(), req, &body); err != nil {
		return Identity{}, fmt.Errorf("google token exchange: %w", err)
	}
	return g.VerifyIDToken(ctx, body.IDToken)
}

// VerifyIDToken checks a Google ID token's signature, issuer, audience and expiry.
func (g *Google) VerifyIDToken(ctx context.Context, token string) (Identity, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return Identity{}, ErrInvalidToken
	}
	var header struct {
		Alg string `json:"alg"`
		Kid string `json:"kid"`
	}
	if err := decodeSegment(parts[0], &header); err != nil || header.Alg != "RS256" {
		return Identity{}, ErrInvalidToken
	}
	key, err := g.key(ctx, header.Kid)
	if err != nil {
		return Identity{}, err
	}
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return Identity{}, ErrInvalidToken
	}
	digest := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	if rsa.VerifyPKCS1v15(key, crypto.SHA256, digest[:], sig) != nil {
		return Identity{}, ErrInvalidToken
	}

	var claims struct {
		Iss           string          `json:"iss"`
		Aud           string          `json:"aud"`
		Sub           string          `json:"sub"`
		Exp           int64           `json:"exp"`
		Email         string          `json:"email"`
		EmailVerified json.RawMessage `json:"email_verified"`
		Name          string          `json:"name"`
		Picture       string          `json:"picture"`
	}
	if err := decodeSegment(parts[1], &claims); err != nil {
		return Identity{}, ErrInvalidToken
	}
	if claims.Iss != "accounts.google.com" && claims.Iss != "https://accounts.google.com" {
		return Identity{}, ErrInvalidToken
	}
	if !g.audienceAllowed(claims.Aud) || claims.Sub == "" {
		return Identity{}, ErrInvalidToken
	}
	// A minute of leeway for clock drift.
	if g.now().After(time.Unix(claims.Exp, 0).Add(time.Minute)) {
		return Identity{}, ErrInvalidToken
	}
	// email_verified is a boolean, but has been sent as the string "true" in the past.
	verified := strings.Trim(string(claims.EmailVerified), `"`) == "true"
	return Identity{
		Provider:      "google",
		Subject:       claims.Sub,
		Email:         claims.Email,
		EmailVerified: verified,
		Name:          claims.Name,
		AvatarURL:     claims.Picture,
	}, nil
}

func (g *Google) audienceAllowed(aud string) bool {
	if aud == "" {
		return false
	}
	if aud == g.ClientID {
		return true
	}
	for _, a := range g.Audiences {
		if a == aud {
			return true
		}
	}
	return false
}

// key returns Google's signing key with this ID, fetching the current set when it's unknown.
// Google rotates keys every few weeks; refetches are limited to one a minute.
func (g *Google) key(ctx context.Context, kid string) (*rsa.PublicKey, error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	now := g.now()
	if k, ok := g.keys[kid]; ok && now.Before(g.keysUntil) {
		return k, nil
	}
	if now.Sub(g.lastFetch) < time.Minute {
		if k, ok := g.keys[kid]; ok {
			return k, nil
		}
		return nil, ErrInvalidToken
	}
	g.lastFetch = now
	keys, maxAge, err := fetchJWKS(ctx, g.client(), g.certsURL())
	if err != nil {
		return nil, fmt.Errorf("google signing keys: %w", err)
	}
	g.keys = keys
	g.keysUntil = now.Add(maxAge)
	if k, ok := keys[kid]; ok {
		return k, nil
	}
	return nil, ErrInvalidToken
}

func fetchJWKS(ctx context.Context, client *http.Client, certsURL string) (map[string]*rsa.PublicKey, time.Duration, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, certsURL, nil)
	if err != nil {
		return nil, 0, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, 0, fmt.Errorf("status %d", resp.StatusCode)
	}
	var set struct {
		Keys []struct {
			Kid string `json:"kid"`
			Kty string `json:"kty"`
			N   string `json:"n"`
			E   string `json:"e"`
		} `json:"keys"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&set); err != nil {
		return nil, 0, err
	}
	keys := make(map[string]*rsa.PublicKey, len(set.Keys))
	for _, k := range set.Keys {
		if k.Kty != "RSA" {
			continue
		}
		n, errN := base64.RawURLEncoding.DecodeString(k.N)
		e, errE := base64.RawURLEncoding.DecodeString(k.E)
		if errN != nil || errE != nil {
			continue
		}
		keys[k.Kid] = &rsa.PublicKey{N: new(big.Int).SetBytes(n), E: int(new(big.Int).SetBytes(e).Int64())}
	}
	return keys, cacheMaxAge(resp.Header.Get("Cache-Control")), nil
}

// cacheMaxAge reads max-age from a Cache-Control header, defaulting to an hour.
func cacheMaxAge(header string) time.Duration {
	for _, part := range strings.Split(header, ",") {
		part = strings.TrimSpace(part)
		if v, ok := strings.CutPrefix(part, "max-age="); ok {
			if secs, err := strconv.Atoi(v); err == nil && secs > 0 {
				return time.Duration(secs) * time.Second
			}
		}
	}
	return time.Hour
}

func (g *Google) client() *http.Client {
	if g.HTTP != nil {
		return g.HTTP
	}
	return defaultHTTP
}

func (g *Google) now() time.Time {
	if g.Now != nil {
		return g.Now()
	}
	return time.Now()
}

func (g *Google) tokenURL() string {
	if g.TokenURL != "" {
		return g.TokenURL
	}
	return googleTokenURL
}

func (g *Google) certsURL() string {
	if g.CertsURL != "" {
		return g.CertsURL
	}
	return googleCertsURL
}

func decodeSegment(seg string, v any) error {
	b, err := base64.RawURLEncoding.DecodeString(seg)
	if err != nil {
		return err
	}
	return json.Unmarshal(b, v)
}

var defaultHTTP = &http.Client{Timeout: 10 * time.Second}

// doJSON sends req and decodes a 200 JSON response into v.
func doJSON(client *http.Client, req *http.Request, v any) error {
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("%w: status %d", ErrInvalidToken, resp.StatusCode)
	}
	return json.NewDecoder(resp.Body).Decode(v)
}
