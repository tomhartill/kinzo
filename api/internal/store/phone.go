package store

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
)

const (
	// CodeLifetime is how long a texted code works.
	CodeLifetime = 10 * time.Minute
	// CodeAttempts is how many wrong tries a code gets before a new one is needed.
	CodeAttempts = 5
)

var (
	// ErrPhoneTaken means the number already signs in to another account.
	ErrPhoneTaken = errors.New("phone taken")
	// ErrLastSignInMethod means removing the phone would leave no way into the account.
	ErrLastSignInMethod = errors.New("last sign-in method")
)

// EmailAccount is what an email tells phone sign-in about the account it belongs to.
type EmailAccount struct {
	UserID string
	// Phone is the account's number, empty when it signs in only with Google or Facebook.
	Phone string
	// Providers are its other ways in, such as "google".
	Providers []string
}

// AccountByEmail looks up a live account by email, verified or not.
func (s *Store) AccountByEmail(ctx context.Context, email string) (EmailAccount, error) {
	var a EmailAccount
	err := s.pool.QueryRow(ctx, `
		SELECT u.id::text,
			coalesce((SELECT subject FROM auth_identities WHERE user_id = u.id AND provider = 'phone'), ''),
			array(SELECT provider FROM auth_identities WHERE user_id = u.id AND provider <> 'phone' ORDER BY provider)
		FROM users u WHERE lower(u.email) = $1 AND u.deleted_at IS NULL`,
		strings.ToLower(email)).Scan(&a.UserID, &a.Phone, &a.Providers)
	if errors.Is(err, pgx.ErrNoRows) {
		return EmailAccount{}, ErrNotFound
	}
	return a, err
}

// PhoneOwner returns the account a number signs in to.
func (s *Store) PhoneOwner(ctx context.Context, phone string) (string, error) {
	var userID string
	err := s.pool.QueryRow(ctx,
		`SELECT user_id::text FROM auth_identities WHERE provider = 'phone' AND subject = $1`, phone).Scan(&userID)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", ErrNotFound
	}
	return userID, err
}

// RecentCodes returns when codes were sent to a number, and from an IP address, over the
// last day, oldest first. The sending limits are worked out from them.
func (s *Store) RecentCodes(ctx context.Context, phone, ip string) (toPhone, fromIP []time.Time, err error) {
	since := time.Now().Add(-24 * time.Hour)
	toPhone, err = s.times(ctx,
		`SELECT created_at FROM phone_challenges WHERE phone_e164 = $1 AND created_at > $2 ORDER BY created_at`, phone, since)
	if err != nil || ip == "" {
		return toPhone, nil, err
	}
	fromIP, err = s.times(ctx,
		`SELECT created_at FROM phone_challenges WHERE ip = $1::inet AND created_at > $2 ORDER BY created_at`, ip, since)
	return toPhone, fromIP, err
}

func (s *Store) times(ctx context.Context, query string, args ...any) ([]time.Time, error) {
	rows, err := s.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, pgx.RowTo[time.Time])
}

// Challenge is one code sent to a phone.
type Challenge struct {
	ID      string
	Purpose string // "sign_in" or "add_phone"
	// UserID is the account the code is for, when it's already known.
	UserID string
	// Email is the email typed at a sign-up, kept for the new account.
	Email       string
	Phone       string
	Channel     string
	ProviderRef string
	Attempts    int
	IP          string
	ExpiresAt   time.Time
}

// CreateChallenge records a code that has just been sent and returns its ID.
func (s *Store) CreateChallenge(ctx context.Context, c Challenge) (string, error) {
	// Day-old rows no longer count towards any limit, so they're cleared out as new ones come.
	if _, err := s.pool.Exec(ctx,
		`DELETE FROM phone_challenges WHERE created_at < $1`, time.Now().Add(-24*time.Hour)); err != nil {
		return "", err
	}
	var id string
	err := s.pool.QueryRow(ctx, `
		INSERT INTO phone_challenges (purpose, user_id, email, phone_e164, channel, provider_ref, ip, expires_at)
		VALUES ($1, nullif($2, '')::uuid, nullif($3, ''), $4, $5, $6, nullif($7, '')::inet, $8)
		RETURNING id::text`,
		c.Purpose, c.UserID, c.Email, c.Phone, c.Channel, c.ProviderRef, c.IP, time.Now().Add(CodeLifetime)).Scan(&id)
	return id, err
}

// OpenChallenge returns a code that can still be tried: not used, not expired and not out of
// tries. Anything else is ErrNotFound.
func (s *Store) OpenChallenge(ctx context.Context, id string) (Challenge, error) {
	if !isUUID(id) {
		return Challenge{}, ErrNotFound
	}
	c := Challenge{ID: id}
	err := s.pool.QueryRow(ctx, `
		SELECT purpose, coalesce(user_id::text, ''), coalesce(email, ''), phone_e164, channel, provider_ref, attempts, expires_at
		FROM phone_challenges
		WHERE id = $1::uuid AND used_at IS NULL AND expires_at > now() AND attempts < $2`,
		id, CodeAttempts).Scan(&c.Purpose, &c.UserID, &c.Email, &c.Phone, &c.Channel, &c.ProviderRef, &c.Attempts, &c.ExpiresAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return Challenge{}, ErrNotFound
	}
	return c, err
}

// WrongCode counts a wrong try and returns how many are left.
func (s *Store) WrongCode(ctx context.Context, id string) (int, error) {
	var attempts int
	err := s.pool.QueryRow(ctx,
		`UPDATE phone_challenges SET attempts = attempts + 1 WHERE id = $1::uuid RETURNING attempts`, id).Scan(&attempts)
	return max(CodeAttempts-attempts, 0), err
}

// UseChallenge marks a code as used, so it works once. It is ErrNotFound if something else
// used it first.
func (s *Store) UseChallenge(ctx context.Context, id string) error {
	tag, err := s.pool.Exec(ctx,
		`UPDATE phone_challenges SET used_at = now() WHERE id = $1::uuid AND used_at IS NULL`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return err
}

// PhoneSignIn signs in with a number whose code was right: the account it belongs to, or a new
// one. A new account keeps email, unverified, if no other account has it.
func (s *Store) PhoneSignIn(ctx context.Context, phone, email string) (user User, newUser bool, err error) {
	err = pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		var userID string
		err := tx.QueryRow(ctx,
			`SELECT user_id::text FROM auth_identities WHERE provider = 'phone' AND subject = $1`, phone).Scan(&userID)
		switch {
		case errors.Is(err, pgx.ErrNoRows):
			userID, err = createUser(ctx, tx, "", "", strings.ToLower(strings.TrimSpace(email)), false)
			if err != nil {
				return err
			}
			newUser = true
			if _, err := tx.Exec(ctx,
				`INSERT INTO auth_identities (provider, subject, user_id) VALUES ('phone', $1, $2::uuid)`,
				phone, userID); err != nil {
				return err
			}
		case err != nil:
			return err
		}
		user, err = scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM users WHERE id = $1::uuid`, userID))
		return err
	})
	return user, newUser, err
}

// SetPhone adds a number to an account, or replaces the one it has.
func (s *Store) SetPhone(ctx context.Context, userID, phone string) (User, error) {
	var user User
	err := pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		var owner string
		err := tx.QueryRow(ctx,
			`SELECT user_id::text FROM auth_identities WHERE provider = 'phone' AND subject = $1`, phone).Scan(&owner)
		switch {
		case err == nil && owner != userID:
			return ErrPhoneTaken
		case err != nil && !errors.Is(err, pgx.ErrNoRows):
			return err
		}
		if _, err := tx.Exec(ctx,
			`DELETE FROM auth_identities WHERE user_id = $1::uuid AND provider = 'phone'`, userID); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx,
			`INSERT INTO auth_identities (provider, subject, user_id) VALUES ('phone', $1, $2::uuid)`,
			phone, userID); err != nil {
			return err
		}
		user, err = scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM users WHERE id = $1::uuid`, userID))
		return err
	})
	return user, err
}

// RemovePhone takes the number off an account, unless it's the only way in.
func (s *Store) RemovePhone(ctx context.Context, userID string) (User, error) {
	var user User
	err := pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		var others int
		if err := tx.QueryRow(ctx,
			`SELECT count(*) FROM auth_identities WHERE user_id = $1::uuid AND provider <> 'phone'`,
			userID).Scan(&others); err != nil {
			return err
		}
		if others == 0 {
			return ErrLastSignInMethod
		}
		if _, err := tx.Exec(ctx,
			`DELETE FROM auth_identities WHERE user_id = $1::uuid AND provider = 'phone'`, userID); err != nil {
			return err
		}
		var err error
		user, err = scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM users WHERE id = $1::uuid`, userID))
		return err
	})
	return user, err
}
