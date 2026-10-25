package store

import (
	"context"
	"errors"
	"slices"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
)

// ErrSelf means someone tried to follow or block themselves.
var ErrSelf = errors.New("that's you")

// What Person.Following can be.
const (
	FollowingNone      = "none"
	FollowingRequested = "requested" // asked to follow a private account; waiting for them
	FollowingYes       = "following"
)

// Person is someone else, with how the signed-in person and they follow each other.
type Person struct {
	PublicUser
	Private bool `json:"private"`
	// Following is whether the signed-in person follows them, or has asked to.
	Following string `json:"following"`
	// FollowsYou is true when they follow the signed-in person.
	FollowsYou bool `json:"follows_you"`
	// RequestedYou is true when they've asked to follow the signed-in person's private account.
	RequestedYou bool `json:"requested_you"`
	// Since is when the follow or request started, in lists.
	Since *time.Time `json:"since,omitempty"`
}

// Profile is a person with their follower counts, for their profile page.
type Profile struct {
	Person
	FollowerCount  int `json:"follower_count"`
	FollowingCount int `json:"following_count"`
}

// FollowLists is everything the Follows screen shows.
type FollowLists struct {
	Followers []Person `json:"followers"`
	Following []Person `json:"following"`
	// Requests are people waiting to follow a private account.
	Requests []Person `json:"requests"`
}

// lockPair serialises changes between two people, so two requests sent at the same moment
// can't both create rows.
func lockPair(ctx context.Context, tx pgx.Tx, a, b string) error {
	if a > b {
		a, b = b, a
	}
	_, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock(hashtextextended($1, 0))`, a+b)
	return err
}

// blocked reports whether either person has blocked the other.
func blocked(ctx context.Context, q pgx.Tx, a, b string) (bool, error) {
	var yes bool
	err := q.QueryRow(ctx, `
		SELECT EXISTS (SELECT 1 FROM blocks
			WHERE (blocker_id = $1::uuid AND blocked_id = $2::uuid) OR (blocker_id = $2::uuid AND blocked_id = $1::uuid))`,
		a, b).Scan(&yes)
	return yes, err
}

// personColumns lists the columns scanPerson reads, for users aliased t (such as "u."), as seen
// by the signed-in person, who must be query argument $1.
func personColumns(t string) string {
	return publicColumns(t) + `, ` + t + `private,
		coalesce((SELECT CASE status WHEN 'accepted' THEN 'following' ELSE 'requested' END FROM connections
			WHERE owner_id = ` + t + `id AND viewer_id = $1::uuid AND role = 'follow'), 'none'),
		coalesce((SELECT status FROM connections
			WHERE owner_id = $1::uuid AND viewer_id = ` + t + `id AND role = 'follow'), '')`
}

// scanPerson reads a row of personColumns, then any extra columns into more.
func scanPerson(row pgx.Row, more ...any) (Person, error) {
	var p Person
	var theirs string
	err := row.Scan(append([]any{&p.ID, &p.Username, &p.DisplayName, &p.AvatarURL, &p.Private, &p.Following, &theirs}, more...)...)
	p.FollowsYou = theirs == "accepted"
	p.RequestedYou = theirs == "pending"
	if errors.Is(err, pgx.ErrNoRows) {
		return Person{}, ErrNotFound
	}
	return p, err
}

// Follow follows other, or asks to if their account is private. Following someone again is
// fine. Returns them afterwards. A follow is a connections row with role 'follow' in which
// viewer_id follows owner_id.
func (s *Store) Follow(ctx context.Context, me, other string) (Person, error) {
	if !isUUID(other) {
		return Person{}, ErrNotFound
	}
	var p Person
	err := pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockPair(ctx, tx, me, other); err != nil {
			return err
		}
		if err := liveUser(ctx, tx, me, other); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `
			INSERT INTO connections (owner_id, viewer_id, role, status, requested_by, accepted_at)
			SELECT id, $1::uuid, 'follow', CASE WHEN private THEN 'pending' ELSE 'accepted' END, $1::uuid,
				CASE WHEN private THEN NULL ELSE now() END
			FROM users WHERE id = $2::uuid
			ON CONFLICT DO NOTHING`, me, other); err != nil {
			return err
		}
		var err error
		p, err = scanPerson(tx.QueryRow(ctx, `SELECT `+personColumns("users.")+` FROM users WHERE id = $2::uuid`, me, other))
		return err
	})
	return p, err
}

// Unfollow stops following other, or withdraws a request to.
func (s *Store) Unfollow(ctx context.Context, me, other string) error {
	if !isUUID(other) {
		return ErrNotFound
	}
	_, err := s.pool.Exec(ctx,
		`DELETE FROM connections WHERE owner_id = $2::uuid AND viewer_id = $1::uuid AND role = 'follow'`, me, other)
	return err
}

// AcceptFollower approves other's request to follow me. ErrNotFound if there isn't one.
func (s *Store) AcceptFollower(ctx context.Context, me, other string) error {
	if !isUUID(other) {
		return ErrNotFound
	}
	var status string
	err := s.pool.QueryRow(ctx, `
		UPDATE connections SET status = 'accepted', accepted_at = coalesce(accepted_at, now())
		WHERE owner_id = $1::uuid AND viewer_id = $2::uuid AND role = 'follow'
		RETURNING status`, me, other).Scan(&status)
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	return err
}

// RemoveFollower stops other following me, or declines their request.
func (s *Store) RemoveFollower(ctx context.Context, me, other string) error {
	if !isUUID(other) {
		return ErrNotFound
	}
	_, err := s.pool.Exec(ctx,
		`DELETE FROM connections WHERE owner_id = $1::uuid AND viewer_id = $2::uuid AND role = 'follow'`, me, other)
	return err
}

// liveUser checks the other person exists and isn't blocked either way. Both look the same
// from outside, so a blocked person can't tell they've been blocked.
func liveUser(ctx context.Context, tx pgx.Tx, me, other string) error {
	if me == other {
		return ErrSelf
	}
	var exists bool
	if err := tx.QueryRow(ctx,
		`SELECT EXISTS (SELECT 1 FROM users WHERE id = $1::uuid AND deleted_at IS NULL)`, other).Scan(&exists); err != nil {
		return err
	}
	if !exists {
		return ErrNotFound
	}
	isBlocked, err := blocked(ctx, tx, me, other)
	if err != nil {
		return err
	}
	if isBlocked {
		return ErrNotFound
	}
	return nil
}

// Block cuts other off: follows and requests between you go, both ways, and neither of you can
// find or follow the other until it's lifted.
func (s *Store) Block(ctx context.Context, me, other string) error {
	if me == other {
		return ErrSelf
	}
	if !isUUID(other) {
		return ErrNotFound
	}
	return pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockPair(ctx, tx, me, other); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `
			DELETE FROM connections
			WHERE (owner_id = $1::uuid AND viewer_id = $2::uuid) OR (owner_id = $2::uuid AND viewer_id = $1::uuid)`,
			me, other); err != nil {
			return err
		}
		tag, err := tx.Exec(ctx, `
			INSERT INTO blocks (blocker_id, blocked_id)
			SELECT $1::uuid, id FROM users WHERE id = $2::uuid
			ON CONFLICT DO NOTHING`, me, other)
		if err == nil && tag.RowsAffected() == 0 {
			// Already blocked is fine; no such person isn't.
			var exists bool
			if err := tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM users WHERE id = $1::uuid)`, other).Scan(&exists); err != nil {
				return err
			}
			if !exists {
				return ErrNotFound
			}
		}
		return err
	})
}

func (s *Store) Unblock(ctx context.Context, me, other string) error {
	if !isUUID(other) {
		return ErrNotFound
	}
	_, err := s.pool.Exec(ctx, `DELETE FROM blocks WHERE blocker_id = $1::uuid AND blocked_id = $2::uuid`, me, other)
	return err
}

// Blocked lists the people me has blocked, most recent first.
func (s *Store) Blocked(ctx context.Context, me string) ([]PublicUser, error) {
	rows, err := s.pool.Query(ctx, `
		SELECT `+publicColumns("u.")+` FROM blocks b JOIN users u ON u.id = b.blocked_id
		WHERE b.blocker_id = $1::uuid AND u.deleted_at IS NULL
		ORDER BY b.created_at DESC`, me)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(row pgx.CollectableRow) (PublicUser, error) {
		var p PublicUser
		err := row.Scan(&p.ID, &p.Username, &p.DisplayName, &p.AvatarURL)
		return p, err
	})
}

// Follows lists my followers and who I follow (by name), and requests to follow me (newest
// first).
func (s *Store) Follows(ctx context.Context, me string) (FollowLists, error) {
	var lists FollowLists
	var err error
	// The other person is u; since is when the follow or request started.
	list := func(join, where string) ([]Person, error) {
		rows, err := s.pool.Query(ctx, `
			SELECT `+personColumns("u.")+`, coalesce(c.accepted_at, c.created_at)
			FROM connections c JOIN users u ON u.id = `+join+`
			WHERE c.role = 'follow' AND u.deleted_at IS NULL AND `+where+`
			ORDER BY c.created_at DESC`, me)
		if err != nil {
			return nil, err
		}
		return pgx.CollectRows(rows, func(row pgx.CollectableRow) (Person, error) {
			var since time.Time
			p, err := scanPerson(row, &since)
			p.Since = &since
			return p, err
		})
	}
	if lists.Followers, err = list("c.viewer_id", "c.owner_id = $1::uuid AND c.status = 'accepted'"); err != nil {
		return FollowLists{}, err
	}
	if lists.Requests, err = list("c.viewer_id", "c.owner_id = $1::uuid AND c.status = 'pending'"); err != nil {
		return FollowLists{}, err
	}
	if lists.Following, err = list("c.owner_id", "c.viewer_id = $1::uuid"); err != nil {
		return FollowLists{}, err
	}
	sortByName(lists.Followers)
	sortByName(lists.Following)
	return lists, nil
}

// SearchUsers finds people by username or name, leaving out me and anyone blocked either way.
// An exact username comes first, then usernames starting with the query, then names.
func (s *Store) SearchUsers(ctx context.Context, me, query string) ([]Person, error) {
	q := NormalizeUsername(query)
	if len(q) < 2 {
		return []Person{}, nil
	}
	rows, err := s.pool.Query(ctx, `
		SELECT `+personColumns("u.")+`
		FROM users u
		WHERE u.id <> $1::uuid AND u.deleted_at IS NULL AND u.username IS NOT NULL
		  AND (u.username LIKE $3 || '%' OR lower(u.display_name) LIKE '%' || $3 || '%')
		  AND NOT EXISTS (SELECT 1 FROM blocks b
			WHERE (b.blocker_id = $1::uuid AND b.blocked_id = u.id) OR (b.blocker_id = u.id AND b.blocked_id = $1::uuid))
		ORDER BY (u.username = $2) DESC, (u.username LIKE $3 || '%') DESC, u.username
		LIMIT 20`, me, q, escapeLike(q))
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(row pgx.CollectableRow) (Person, error) { return scanPerson(row) })
}

// ProfileByID returns someone with their follower counts. ErrNotFound if blocked either way.
func (s *Store) ProfileByID(ctx context.Context, me, other string) (Profile, error) {
	if !isUUID(other) {
		return Profile{}, ErrNotFound
	}
	var p Profile
	err := pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		if err := liveUser(ctx, tx, me, other); err != nil && !errors.Is(err, ErrSelf) {
			return err
		}
		var err error
		p.Person, err = scanPerson(tx.QueryRow(ctx, `
			SELECT `+personColumns("users.")+`,
				(SELECT count(*) FROM connections c JOIN users f ON f.id = c.viewer_id
					WHERE c.owner_id = users.id AND c.role = 'follow' AND c.status = 'accepted' AND f.deleted_at IS NULL),
				(SELECT count(*) FROM connections c JOIN users f ON f.id = c.owner_id
					WHERE c.viewer_id = users.id AND c.role = 'follow' AND c.status = 'accepted' AND f.deleted_at IS NULL)
			FROM users WHERE id = $2::uuid`, me, other), &p.FollowerCount, &p.FollowingCount)
		return err
	})
	return p, err
}

// InviteCode returns me's invite code, making one if they don't have one yet.
func (s *Store) InviteCode(ctx context.Context, me string) (string, error) {
	var code string
	err := s.pool.QueryRow(ctx, `
		UPDATE users SET invite_code = coalesce(invite_code, $2) WHERE id = $1::uuid
		RETURNING invite_code`, me, newInviteCode()).Scan(&code)
	return code, err
}

// ResetInviteCode replaces me's invite code, so links already shared stop working.
func (s *Store) ResetInviteCode(ctx context.Context, me string) (string, error) {
	var code string
	err := s.pool.QueryRow(ctx,
		`UPDATE users SET invite_code = $2 WHERE id = $1::uuid RETURNING invite_code`, me, newInviteCode()).Scan(&code)
	return code, err
}

// Inviter returns whose invite code this is. ErrNotFound for an unknown or replaced code.
func (s *Store) Inviter(ctx context.Context, code string) (PublicUser, error) {
	var p PublicUser
	err := s.pool.QueryRow(ctx,
		`SELECT `+publicColumns("")+` FROM users WHERE invite_code = $1 AND deleted_at IS NULL`, code).
		Scan(&p.ID, &p.Username, &p.DisplayName, &p.AvatarURL)
	if errors.Is(err, pgx.ErrNoRows) {
		return PublicUser{}, ErrNotFound
	}
	return p, err
}

// AcceptInvite makes me and the inviter follow each other straight away, even if either
// account is private: sharing the link was the inviter's approval and opening it is mine.
// Returns the inviter afterwards.
func (s *Store) AcceptInvite(ctx context.Context, me, code string) (Person, error) {
	inviter, err := s.Inviter(ctx, code)
	if err != nil {
		return Person{}, err
	}
	var p Person
	err = pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockPair(ctx, tx, me, inviter.ID); err != nil {
			return err
		}
		if err := liveUser(ctx, tx, me, inviter.ID); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `
			INSERT INTO connections (owner_id, viewer_id, role, status, requested_by, accepted_at)
			VALUES ($1::uuid, $2::uuid, 'follow', 'accepted', $2::uuid, now()), ($2::uuid, $1::uuid, 'follow', 'accepted', $2::uuid, now())
			ON CONFLICT (owner_id, viewer_id, role) DO UPDATE
				SET status = 'accepted', accepted_at = coalesce(connections.accepted_at, now())`,
			me, inviter.ID); err != nil {
			return err
		}
		var err error
		p, err = scanPerson(tx.QueryRow(ctx, `SELECT `+personColumns("users.")+` FROM users WHERE id = $2::uuid`, me, inviter.ID))
		return err
	})
	return p, err
}

func publicColumns(t string) string {
	return t + "id::text, coalesce(" + t + "username, ''), coalesce(" + t + "display_name, ''), coalesce(" + t + "avatar_url, '')"
}

func escapeLike(s string) string {
	return strings.NewReplacer(`\`, `\\`, `%`, `\%`, `_`, `\_`).Replace(s)
}

func sortByName(people []Person) {
	key := func(p Person) string {
		if p.DisplayName != "" {
			return strings.ToLower(p.DisplayName)
		}
		return p.Username
	}
	slices.SortStableFunc(people, func(a, b Person) int { return strings.Compare(key(a), key(b)) })
}

func isUUID(s string) bool {
	if len(s) != 36 {
		return false
	}
	for i, r := range s {
		switch i {
		case 8, 13, 18, 23:
			if r != '-' {
				return false
			}
		default:
			if !strings.ContainsRune("0123456789abcdefABCDEF", r) {
				return false
			}
		}
	}
	return true
}
