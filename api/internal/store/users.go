// Package store holds the API's database queries.
package store

import (
	"context"
	"errors"
	"fmt"
	"math/rand/v2"
	"regexp"
	"strings"
	"time"
	"unicode"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/text/runes"
	"golang.org/x/text/transform"
	"golang.org/x/text/unicode/norm"

	"github.com/tomhartill/kinzo/api/internal/auth"
)

type Store struct {
	pool *pgxpool.Pool
}

func New(pool *pgxpool.Pool) *Store {
	return &Store{pool: pool}
}

var (
	ErrNotFound        = errors.New("not found")
	ErrUsernameTaken   = errors.New("username taken")
	ErrInvalidUsername = errors.New("invalid username")
)

// User is the signed-in person's own account.
type User struct {
	ID          string `json:"id"`
	Username    string `json:"username"`
	DisplayName string `json:"display_name"`
	AvatarURL   string `json:"avatar_url,omitempty"`
	Email       string `json:"email,omitempty"`
	// EmailVerified is false for an email typed at a phone sign-up, which nobody has confirmed.
	EmailVerified bool `json:"email_verified"`
	// Phone is the person's own number in E.164, when they sign in with phone codes.
	Phone string `json:"phone,omitempty"`
	// SignInMethods are the ways into the account, such as "google" and "phone".
	SignInMethods []string `json:"sign_in_methods"`
	// Private accounts approve each new follower.
	Private   bool      `json:"private"`
	CreatedAt time.Time `json:"created_at"`
}

// PublicUser is what other people see of someone: never their email.
type PublicUser struct {
	ID          string `json:"id"`
	Username    string `json:"username"`
	DisplayName string `json:"display_name"`
	AvatarURL   string `json:"avatar_url,omitempty"`
}

var userColumns = userColumnsOf("users.")

// userColumnsOf lists the columns scanUser reads, qualified with a table name or alias such
// as "u.".
func userColumnsOf(t string) string {
	return fmt.Sprintf(`%[1]sid::text, coalesce(%[1]susername, ''), coalesce(%[1]sdisplay_name, ''),
		coalesce(%[1]savatar_url, ''), coalesce(%[1]semail, ''), %[1]semail_verified_at IS NOT NULL,
		coalesce((SELECT subject FROM auth_identities WHERE user_id = %[1]sid AND provider = 'phone'), ''),
		array(SELECT provider FROM auth_identities WHERE user_id = %[1]sid ORDER BY provider),
		%[1]sprivate, %[1]screated_at`, t)
}

// scanTargets are where a row of userColumns goes, in order.
func (u *User) scanTargets() []any {
	return []any{&u.ID, &u.Username, &u.DisplayName, &u.AvatarURL, &u.Email, &u.EmailVerified,
		&u.Phone, &u.SignInMethods, &u.Private, &u.CreatedAt}
}

func scanUser(row pgx.Row) (User, error) {
	var u User
	err := row.Scan(u.scanTargets()...)
	if errors.Is(err, pgx.ErrNoRows) {
		return User{}, ErrNotFound
	}
	return u, err
}

// UserByID returns a live (not deleted) user.
func (s *Store) UserByID(ctx context.Context, id string) (User, error) {
	return scanUser(s.pool.QueryRow(ctx,
		`SELECT `+userColumns+` FROM users WHERE id = $1::uuid AND deleted_at IS NULL`, id))
}

// SignIn finds or creates the account for a provider identity. A first sign-in with a new
// provider joins an existing account when both have verified the same email. An unverified
// email (typed at a phone sign-up) never joins: the provider sign-in gets its own account and
// the unverified copy is taken off the phone account, so nobody can capture someone else's
// Google sign-in by typing their email. newUser is true when the account was created just now.
func (s *Store) SignIn(ctx context.Context, id auth.Identity) (user User, newUser bool, err error) {
	err = pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		var userID string
		err := tx.QueryRow(ctx,
			`SELECT user_id::text FROM auth_identities WHERE provider = $1 AND subject = $2`,
			id.Provider, id.Subject).Scan(&userID)
		switch {
		case err == nil:
			// Returning user. Fill in a picture if they had none.
			if id.AvatarURL != "" {
				if _, err := tx.Exec(ctx,
					`UPDATE users SET avatar_url = $2, updated_at = now() WHERE id = $1::uuid AND avatar_url IS NULL`,
					userID, id.AvatarURL); err != nil {
					return err
				}
			}
		case errors.Is(err, pgx.ErrNoRows):
			email := strings.ToLower(strings.TrimSpace(id.Email))
			if email != "" && id.EmailVerified {
				err := tx.QueryRow(ctx, `
					SELECT id::text FROM users
					WHERE lower(email) = $1 AND email_verified_at IS NOT NULL AND deleted_at IS NULL`,
					email).Scan(&userID)
				if err != nil && !errors.Is(err, pgx.ErrNoRows) {
					return err
				}
				if userID == "" {
					if _, err := tx.Exec(ctx,
						`UPDATE users SET email = NULL, updated_at = now() WHERE lower(email) = $1 AND email_verified_at IS NULL`,
						email); err != nil {
						return err
					}
				}
			}
			if userID == "" {
				if !id.EmailVerified {
					email = "" // not kept: it would let someone claim an address that isn't theirs
				}
				userID, err = createUser(ctx, tx, id.Name, id.AvatarURL, email, id.EmailVerified)
				if err != nil {
					return err
				}
				newUser = true
			}
			if _, err := tx.Exec(ctx,
				`INSERT INTO auth_identities (provider, subject, user_id) VALUES ($1, $2, $3::uuid)`,
				id.Provider, id.Subject, userID); err != nil {
				return err
			}
		default:
			return err
		}
		user, err = scanUser(tx.QueryRow(ctx, `SELECT `+userColumns+` FROM users WHERE id = $1::uuid`, userID))
		return err
	})
	return user, newUser, err
}

// createUser makes an account. emailVerified says whether email has been proved to be the
// person's. An email another account already has isn't kept.
func createUser(ctx context.Context, tx pgx.Tx, name, avatarURL, email string, emailVerified bool) (string, error) {
	var emailArg any
	if email != "" {
		var taken bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM users WHERE lower(email) = $1)`, email).Scan(&taken); err != nil {
			return "", err
		}
		if !taken {
			emailArg = email
		}
	}
	base := UsernameFromName(name)
	for attempt := 0; attempt < 20; attempt++ {
		username := base
		if attempt > 0 {
			// tom, then tom42, then tom7731: short suffixes first, longer if they keep clashing.
			digits := 2
			if attempt > 5 {
				digits = 4
			}
			username = fmt.Sprintf("%s%0*d", trimTo(base, 30-digits), digits, rand.IntN(pow10(digits)))
		}
		var userID string
		err := tx.QueryRow(ctx, `
			INSERT INTO users (email, email_verified_at, display_name, avatar_url, username, invite_code)
			VALUES ($1, CASE WHEN $1::text IS NOT NULL AND $2 THEN now() END, $3, nullif($4, ''), $5, $6)
			ON CONFLICT (username) DO NOTHING
			RETURNING id::text`,
			emailArg, emailVerified, strings.TrimSpace(name), avatarURL, username, newInviteCode()).Scan(&userID)
		if errors.Is(err, pgx.ErrNoRows) {
			continue // username taken, try another
		}
		return userID, err
	}
	return "", errors.New("couldn't find a free username")
}

// UpdateProfile changes the username, display name and whether the account is private. Nil
// leaves a field as it is. Going public approves everyone waiting to follow.
func (s *Store) UpdateProfile(ctx context.Context, userID string, username, displayName *string, private *bool) (User, error) {
	if username != nil {
		u := NormalizeUsername(*username)
		if !ValidUsername(u) {
			return User{}, ErrInvalidUsername
		}
		username = &u
	}
	if displayName != nil {
		d := strings.TrimSpace(*displayName)
		if d == "" || len([]rune(d)) > 50 {
			return User{}, ErrInvalidDisplayName
		}
		displayName = &d
	}
	var user User
	err := pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		var err error
		user, err = scanUser(tx.QueryRow(ctx, `
			UPDATE users SET
				username = coalesce($2, username),
				display_name = coalesce($3, display_name),
				private = coalesce($4, private),
				updated_at = now()
			WHERE id = $1::uuid AND deleted_at IS NULL
			RETURNING `+userColumns, userID, username, displayName, private))
		if err != nil || user.Private {
			return err
		}
		_, err = tx.Exec(ctx, `
			UPDATE connections SET status = 'accepted', accepted_at = now()
			WHERE owner_id = $1::uuid AND role = 'follow' AND status = 'pending'`, userID)
		return err
	})
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) && pgErr.Code == "23505" {
		return User{}, ErrUsernameTaken
	}
	return user, err
}

// ErrInvalidDisplayName means a display name was blank or longer than 50 characters.
var ErrInvalidDisplayName = errors.New("invalid display name")

// UsernameAvailable reports whether someone other than userID could take this username.
func (s *Store) UsernameAvailable(ctx context.Context, userID, username string) (bool, error) {
	var taken bool
	err := s.pool.QueryRow(ctx,
		`SELECT EXISTS (SELECT 1 FROM users WHERE username = $1 AND id <> $2::uuid)`,
		username, userID).Scan(&taken)
	return !taken, err
}

// DeleteAccount removes the user and everything of theirs: sessions, follows, workouts.
func (s *Store) DeleteAccount(ctx context.Context, userID string) error {
	_, err := s.pool.Exec(ctx, `DELETE FROM users WHERE id = $1::uuid`, userID)
	return err
}

var usernamePattern = regexp.MustCompile(`^[a-z0-9._]{3,30}$`)

// NormalizeUsername lowercases a typed username and drops a leading @.
func NormalizeUsername(s string) string {
	return strings.ToLower(strings.TrimPrefix(strings.TrimSpace(s), "@"))
}

// ValidUsername: 3 to 30 lowercase letters, digits, dots and underscores.
func ValidUsername(s string) bool {
	return usernamePattern.MatchString(s)
}

// UsernameFromName suggests a username from a display name: "Zoë O'Brien" becomes "zoeobrien".
func UsernameFromName(name string) string {
	// Split accents off their letters (é becomes e plus a combining mark), then drop the marks.
	t := transform.Chain(norm.NFD, runes.Remove(runes.In(unicode.Mn)), norm.NFC)
	plain, _, _ := transform.String(t, name)
	var b strings.Builder
	for _, r := range strings.ToLower(plain) {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') {
			b.WriteRune(r)
		}
	}
	u := trimTo(b.String(), 20)
	if len(u) < 3 {
		return "kinzo" + u
	}
	return u
}

func trimTo(s string, n int) string {
	if len(s) > n {
		return s[:n]
	}
	return s
}

func pow10(n int) int {
	p := 1
	for range n {
		p *= 10
	}
	return p
}

func newInviteCode() string {
	return auth.NewToken("", 9) // 12 URL-safe characters
}
