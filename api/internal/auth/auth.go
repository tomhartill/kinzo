// Package auth talks to the sign-in providers (Google and Facebook) and makes Kinzo's own
// session tokens. The API never sees a password: a provider vouches for who someone is, and
// the API then issues its own token for the app to keep.
package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
)

// Identity is who a provider says someone is.
type Identity struct {
	Provider string
	// Subject is the provider's stable ID for the person. Emails can change; this can't.
	Subject string
	Email   string
	// EmailVerified is true when the provider has checked the person owns Email. Only then is
	// it safe to join this sign-in to an existing account with the same email.
	EmailVerified bool
	Name          string
	AvatarURL     string
}

// Provider is a sign-in provider reached through the browser: the API sends the person to
// AuthURL and the provider sends them back to the API with a code.
type Provider interface {
	Name() string
	// AuthURL is the provider's sign-in page. redirectURI is the API's own callback URL.
	AuthURL(state, redirectURI string) string
	// Exchange swaps the code from the callback for the person's identity.
	Exchange(ctx context.Context, code, redirectURI string) (Identity, error)
}

// ErrInvalidToken means a provider token or code didn't check out.
var ErrInvalidToken = errors.New("invalid token")

// NewToken returns a random URL-safe token with the given prefix, for sessions, one-time
// codes and invite codes.
func NewToken(prefix string, bytes int) string {
	b := make([]byte, bytes)
	if _, err := rand.Read(b); err != nil {
		panic(err) // crypto/rand never fails on supported platforms
	}
	return prefix + base64.RawURLEncoding.EncodeToString(b)
}

// Hash is how tokens are stored: SHA-256, so the database never holds a usable token.
func Hash(token string) []byte {
	sum := sha256.Sum256([]byte(token))
	return sum[:]
}

// VerifyPKCE checks a PKCE code verifier against the S256 challenge sent at the start.
func VerifyPKCE(verifier, challenge string) bool {
	if len(verifier) < 43 || len(verifier) > 128 {
		return false
	}
	sum := sha256.Sum256([]byte(verifier))
	return base64.RawURLEncoding.EncodeToString(sum[:]) == challenge
}
