package auth

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"math/big"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestVerifyPKCE(t *testing.T) {
	verifier := strings.Repeat("a", 43)
	sum := sha256.Sum256([]byte(verifier))
	challenge := base64.RawURLEncoding.EncodeToString(sum[:])
	if !VerifyPKCE(verifier, challenge) {
		t.Fatal("matching verifier rejected")
	}
	if VerifyPKCE(strings.Repeat("b", 43), challenge) {
		t.Fatal("wrong verifier accepted")
	}
	if VerifyPKCE("short", challenge) {
		t.Fatal("short verifier accepted")
	}
}

// googleFixture serves a signing key the way Google does and signs ID tokens with it.
type googleFixture struct {
	key    *rsa.PrivateKey
	server *httptest.Server
	google *Google
	now    time.Time
}

func newGoogleFixture(t *testing.T) *googleFixture {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	f := &googleFixture{key: key, now: time.Unix(1_800_000_000, 0)}
	f.server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "public, max-age=3600")
		_ = json.NewEncoder(w).Encode(map[string]any{"keys": []map[string]string{{
			"kid": "key-1",
			"kty": "RSA",
			"alg": "RS256",
			"n":   base64.RawURLEncoding.EncodeToString(key.N.Bytes()),
			"e":   base64.RawURLEncoding.EncodeToString(big.NewInt(int64(key.E)).Bytes()),
		}}})
	}))
	t.Cleanup(f.server.Close)
	f.google = &Google{ClientID: "web-client", CertsURL: f.server.URL, Now: func() time.Time { return f.now }}
	return f
}

func (f *googleFixture) sign(t *testing.T, kid string, claims map[string]any) string {
	t.Helper()
	enc := func(v any) string {
		b, _ := json.Marshal(v)
		return base64.RawURLEncoding.EncodeToString(b)
	}
	signed := enc(map[string]string{"alg": "RS256", "kid": kid, "typ": "JWT"}) + "." + enc(claims)
	digest := sha256.Sum256([]byte(signed))
	sig, err := rsa.SignPKCS1v15(rand.Reader, f.key, crypto.SHA256, digest[:])
	if err != nil {
		t.Fatal(err)
	}
	return signed + "." + base64.RawURLEncoding.EncodeToString(sig)
}

func (f *googleFixture) claims() map[string]any {
	return map[string]any{
		"iss":            "https://accounts.google.com",
		"aud":            "web-client",
		"sub":            "1234567890",
		"exp":            f.now.Add(time.Hour).Unix(),
		"email":          "tom@example.com",
		"email_verified": true,
		"name":           "Tom",
		"picture":        "https://example.com/tom.png",
	}
}

func TestGoogleVerifyIDToken(t *testing.T) {
	f := newGoogleFixture(t)
	id, err := f.google.VerifyIDToken(context.Background(), f.sign(t, "key-1", f.claims()))
	if err != nil {
		t.Fatalf("valid token rejected: %v", err)
	}
	want := Identity{Provider: "google", Subject: "1234567890", Email: "tom@example.com", EmailVerified: true, Name: "Tom", AvatarURL: "https://example.com/tom.png"}
	if id != want {
		t.Fatalf("identity = %+v, want %+v", id, want)
	}
}

func TestGoogleRejectsBadTokens(t *testing.T) {
	f := newGoogleFixture(t)
	with := func(change func(map[string]any)) map[string]any {
		c := f.claims()
		change(c)
		return c
	}
	other, _ := rsa.GenerateKey(rand.Reader, 2048)
	tests := []struct {
		name  string
		token func() string
	}{
		{"wrong audience", func() string { return f.sign(t, "key-1", with(func(c map[string]any) { c["aud"] = "someone-else" })) }},
		{"wrong issuer", func() string {
			return f.sign(t, "key-1", with(func(c map[string]any) { c["iss"] = "https://evil.example" }))
		}},
		{"expired", func() string {
			return f.sign(t, "key-1", with(func(c map[string]any) { c["exp"] = f.now.Add(-2 * time.Minute).Unix() }))
		}},
		{"unknown key", func() string { return f.sign(t, "key-2", f.claims()) }},
		{"signed by someone else", func() string {
			real := f.key
			f.key = other
			defer func() { f.key = real }()
			return f.sign(t, "key-1", f.claims())
		}},
		{"tampered", func() string {
			parts := strings.Split(f.sign(t, "key-1", f.claims()), ".")
			b, _ := json.Marshal(with(func(c map[string]any) { c["sub"] = "victim" }))
			return parts[0] + "." + base64.RawURLEncoding.EncodeToString(b) + "." + parts[2]
		}},
		{"not a JWT", func() string { return "nonsense" }},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Each case gets a fresh refetch allowance.
			f.google.lastFetch = time.Time{}
			if _, err := f.google.VerifyIDToken(context.Background(), tt.token()); !errors.Is(err, ErrInvalidToken) {
				t.Fatalf("err = %v, want ErrInvalidToken", err)
			}
		})
	}
}

func TestGoogleEmailVerifiedAsString(t *testing.T) {
	f := newGoogleFixture(t)
	c := f.claims()
	c["email_verified"] = "true"
	id, err := f.google.VerifyIDToken(context.Background(), f.sign(t, "key-1", c))
	if err != nil || !id.EmailVerified {
		t.Fatalf("EmailVerified = %v, err = %v; want true", id.EmailVerified, err)
	}
}

func TestFacebookExchange(t *testing.T) {
	graph := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/v24.0/oauth/access_token":
			if r.URL.Query().Get("code") != "good-code" || r.URL.Query().Get("client_secret") != "secret" {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			_, _ = w.Write([]byte(`{"access_token":"fb-token"}`))
		case "/v24.0/me":
			if r.URL.Query().Get("access_token") != "fb-token" || r.URL.Query().Get("appsecret_proof") == "" {
				w.WriteHeader(http.StatusUnauthorized)
				return
			}
			_, _ = w.Write([]byte(`{"id":"fb-1","name":"Sam","email":"sam@example.com","picture":{"data":{"url":"https://x/sam.jpg","is_silhouette":false}}}`))
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer graph.Close()
	fb := &Facebook{AppID: "app", AppSecret: "secret", GraphURL: graph.URL}

	id, err := fb.Exchange(context.Background(), "good-code", "https://api/cb")
	if err != nil {
		t.Fatal(err)
	}
	want := Identity{Provider: "facebook", Subject: "fb-1", Email: "sam@example.com", EmailVerified: true, Name: "Sam", AvatarURL: "https://x/sam.jpg"}
	if id != want {
		t.Fatalf("identity = %+v, want %+v", id, want)
	}
	if _, err := fb.Exchange(context.Background(), "bad-code", "https://api/cb"); err == nil {
		t.Fatal("bad code accepted")
	}
}

func TestAuthURLs(t *testing.T) {
	g := &Google{ClientID: "web-client"}
	if u := g.AuthURL("st", "https://api/cb"); !strings.Contains(u, "state=st") || !strings.Contains(u, "client_id=web-client") {
		t.Fatalf("google auth url = %s", u)
	}
	f := &Facebook{AppID: "app"}
	if u := f.AuthURL("st", "https://api/cb"); !strings.HasPrefix(u, "https://www.facebook.com/v24.0/dialog/oauth?") || !strings.Contains(u, "scope=public_profile%2Cemail") {
		t.Fatalf("facebook auth url = %s", u)
	}
}
