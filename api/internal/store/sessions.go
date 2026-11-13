package store

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/tomhartill/kinzo/api/internal/auth"
)

const (
	// SessionLifetime is how long a device stays signed in without opening the app.
	SessionLifetime = 180 * 24 * time.Hour
	// AuthFlowLifetime is how long someone has to finish signing in with the provider.
	AuthFlowLifetime = 15 * time.Minute
	// AuthCodeLifetime is how long the app has to swap a one-time code for a session.
	AuthCodeLifetime = 2 * time.Minute
)

// CreateSession signs a device in and returns its token. Only the token's hash is stored.
func (s *Store) CreateSession(ctx context.Context, userID string) (string, error) {
	token := auth.NewToken("kz_", 32)
	_, err := s.pool.Exec(ctx,
		`INSERT INTO sessions (user_id, token_hash, expires_at) VALUES ($1::uuid, $2, $3)`,
		userID, auth.Hash(token), time.Now().Add(SessionLifetime))
	return token, err
}

// SessionUser returns the user a session token belongs to, and slides the session forward
// at most once a day.
func (s *Store) SessionUser(ctx context.Context, token string) (sessionID string, user User, err error) {
	var lastUsed time.Time
	row := s.pool.QueryRow(ctx, `
		SELECT s.id::text, s.last_used_at, `+userColumnsOf("u.")+`
		FROM sessions s JOIN users u ON u.id = s.user_id
		WHERE s.token_hash = $1 AND s.expires_at > now() AND u.deleted_at IS NULL`,
		auth.Hash(token))
	err = row.Scan(append([]any{&sessionID, &lastUsed}, user.scanTargets()...)...)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", User{}, ErrNotFound
	}
	if err != nil {
		return "", User{}, err
	}
	if time.Since(lastUsed) > 24*time.Hour {
		_, err = s.pool.Exec(ctx,
			`UPDATE sessions SET last_used_at = now(), expires_at = $2 WHERE id = $1::uuid`,
			sessionID, time.Now().Add(SessionLifetime))
	}
	return sessionID, user, err
}

func (s *Store) DeleteSession(ctx context.Context, sessionID string) error {
	_, err := s.pool.Exec(ctx, `DELETE FROM sessions WHERE id = $1::uuid`, sessionID)
	return err
}

// StartAuthFlow remembers a browser sign-in until the provider sends the person back.
func (s *Store) StartAuthFlow(ctx context.Context, state, provider, appRedirect, codeChallenge string) error {
	// Old unfinished sign-ins are cleared out as new ones start.
	if _, err := s.pool.Exec(ctx, `DELETE FROM auth_flows WHERE created_at < $1`, time.Now().Add(-AuthFlowLifetime)); err != nil {
		return err
	}
	_, err := s.pool.Exec(ctx,
		`INSERT INTO auth_flows (state, provider, app_redirect, code_challenge) VALUES ($1, $2, $3, $4)`,
		state, provider, appRedirect, codeChallenge)
	return err
}

// AuthFlow is a browser sign-in waiting for the provider's callback.
type AuthFlow struct {
	Provider      string
	AppRedirect   string
	CodeChallenge string
}

// TakeAuthFlow returns and forgets a sign-in, so each state works once.
func (s *Store) TakeAuthFlow(ctx context.Context, state string) (AuthFlow, error) {
	var f AuthFlow
	err := s.pool.QueryRow(ctx,
		`DELETE FROM auth_flows WHERE state = $1 AND created_at > $2 RETURNING provider, app_redirect, code_challenge`,
		state, time.Now().Add(-AuthFlowLifetime)).Scan(&f.Provider, &f.AppRedirect, &f.CodeChallenge)
	if errors.Is(err, pgx.ErrNoRows) {
		return AuthFlow{}, ErrNotFound
	}
	return f, err
}

// CreateAuthCode makes the one-time code the app swaps for a session after a browser sign-in.
func (s *Store) CreateAuthCode(ctx context.Context, userID, codeChallenge string, newUser bool) (string, error) {
	if _, err := s.pool.Exec(ctx, `DELETE FROM auth_codes WHERE expires_at < now()`); err != nil {
		return "", err
	}
	code := auth.NewToken("", 24)
	_, err := s.pool.Exec(ctx,
		`INSERT INTO auth_codes (code_hash, user_id, code_challenge, new_user, expires_at) VALUES ($1, $2::uuid, $3, $4, $5)`,
		auth.Hash(code), userID, codeChallenge, newUser, time.Now().Add(AuthCodeLifetime))
	return code, err
}

// TakeAuthCode uses up a one-time code. It only works with the PKCE verifier that matches the
// challenge the app sent when it started, so a code intercepted on its way back is useless.
func (s *Store) TakeAuthCode(ctx context.Context, code, verifier string) (userID string, newUser bool, err error) {
	var challenge string
	err = s.pool.QueryRow(ctx,
		`DELETE FROM auth_codes WHERE code_hash = $1 AND expires_at > now() RETURNING user_id::text, code_challenge, new_user`,
		auth.Hash(code)).Scan(&userID, &challenge, &newUser)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", false, ErrNotFound
	}
	if err != nil {
		return "", false, err
	}
	if !auth.VerifyPKCE(verifier, challenge) {
		return "", false, ErrNotFound
	}
	return userID, newUser, nil
}
