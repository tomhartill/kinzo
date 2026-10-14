package store

import (
	"context"
	"errors"
	"slices"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
)

// How the signed-in person relates to someone else.
const (
	RelationNone     = "none"
	RelationFriends  = "friends"
	RelationSent     = "request_sent"     // they asked; waiting for the other person
	RelationReceived = "request_received" // the other person asked; waiting for them
)

// ErrSelf means someone tried to befriend or block themselves.
var ErrSelf = errors.New("that's you")

// Person is someone else, with how the signed-in person relates to them.
type Person struct {
	PublicUser
	Relationship string `json:"relationship"`
	// Since is when the friendship started or the request was sent.
	Since *time.Time `json:"since,omitempty"`
}

// FriendLists is everything the Friends screen shows.
type FriendLists struct {
	Friends  []Person `json:"friends"`
	Incoming []Person `json:"incoming"`
	Outgoing []Person `json:"outgoing"`
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

// relationship reads the friend rows between me and other.
func relationship(ctx context.Context, q pgx.Tx, me, other string) (string, error) {
	var status, requestedBy string
	err := q.QueryRow(ctx, `
		SELECT status, requested_by::text FROM connections
		WHERE owner_id = $1::uuid AND viewer_id = $2::uuid AND role = 'friend'`, me, other).Scan(&status, &requestedBy)
	switch {
	case errors.Is(err, pgx.ErrNoRows):
		return RelationNone, nil
	case err != nil:
		return "", err
	case status == "accepted":
		return RelationFriends, nil
	case requestedBy == me:
		return RelationSent, nil
	default:
		return RelationReceived, nil
	}
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

// SendFriendRequest asks other to be friends. If they had already asked, the two of you
// become friends straight away. Returns the relationship afterwards.
func (s *Store) SendFriendRequest(ctx context.Context, me, other string) (rel string, err error) {
	if !isUUID(other) {
		return "", ErrNotFound
	}
	err = pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockPair(ctx, tx, me, other); err != nil {
			return err
		}
		if err := liveUser(ctx, tx, me, other); err != nil {
			return err
		}
		rel, err = relationship(ctx, tx, me, other)
		if err != nil {
			return err
		}
		switch rel {
		case RelationFriends, RelationSent:
			return nil
		case RelationReceived:
			rel = RelationFriends
			return acceptPair(ctx, tx, me, other)
		}
		rel = RelationSent
		_, err = tx.Exec(ctx, `
			INSERT INTO connections (owner_id, viewer_id, role, status, requested_by)
			VALUES ($1::uuid, $2::uuid, 'friend', 'pending', $1::uuid), ($2::uuid, $1::uuid, 'friend', 'pending', $1::uuid)`,
			me, other)
		return err
	})
	return rel, err
}

func acceptPair(ctx context.Context, tx pgx.Tx, a, b string) error {
	_, err := tx.Exec(ctx, `
		UPDATE connections SET status = 'accepted', accepted_at = now()
		WHERE role = 'friend' AND ((owner_id = $1::uuid AND viewer_id = $2::uuid) OR (owner_id = $2::uuid AND viewer_id = $1::uuid))`,
		a, b)
	return err
}

// AcceptFriendRequest accepts other's request. ErrNotFound if there isn't one.
func (s *Store) AcceptFriendRequest(ctx context.Context, me, other string) error {
	if !isUUID(other) {
		return ErrNotFound
	}
	return pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockPair(ctx, tx, me, other); err != nil {
			return err
		}
		rel, err := relationship(ctx, tx, me, other)
		if err != nil {
			return err
		}
		if rel == RelationFriends {
			return nil
		}
		if rel != RelationReceived {
			return ErrNotFound
		}
		return acceptPair(ctx, tx, me, other)
	})
}

// DeleteFriendRequest declines a request from other or cancels one sent to them.
func (s *Store) DeleteFriendRequest(ctx context.Context, me, other string) error {
	return s.deletePair(ctx, me, other, "pending")
}

// RemoveFriend ends a friendship for both people at once.
func (s *Store) RemoveFriend(ctx context.Context, me, other string) error {
	return s.deletePair(ctx, me, other, "accepted")
}

func (s *Store) deletePair(ctx context.Context, me, other, status string) error {
	if !isUUID(other) {
		return ErrNotFound
	}
	_, err := s.pool.Exec(ctx, `
		DELETE FROM connections
		WHERE role = 'friend' AND status = $3
		  AND ((owner_id = $1::uuid AND viewer_id = $2::uuid) OR (owner_id = $2::uuid AND viewer_id = $1::uuid))`,
		me, other, status)
	return err
}

// Block cuts other off: any friendship or request between you goes, and neither of you can
// find or add the other until it's lifted.
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

// Friends lists friends (by name), requests waiting for me and requests I've sent (newest
// first).
func (s *Store) Friends(ctx context.Context, me string) (FriendLists, error) {
	lists := FriendLists{Friends: []Person{}, Incoming: []Person{}, Outgoing: []Person{}}
	rows, err := s.pool.Query(ctx, `
		SELECT `+publicColumns("u.")+`, c.status, c.requested_by::text, coalesce(c.accepted_at, c.created_at)
		FROM connections c JOIN users u ON u.id = c.viewer_id
		WHERE c.owner_id = $1::uuid AND c.role = 'friend' AND u.deleted_at IS NULL
		ORDER BY c.created_at DESC`, me)
	if err != nil {
		return FriendLists{}, err
	}
	defer rows.Close()
	for rows.Next() {
		var p Person
		var status, requestedBy string
		var since time.Time
		if err := rows.Scan(&p.ID, &p.Username, &p.DisplayName, &p.AvatarURL, &status, &requestedBy, &since); err != nil {
			return FriendLists{}, err
		}
		p.Since = &since
		switch {
		case status == "accepted":
			p.Relationship = RelationFriends
			lists.Friends = append(lists.Friends, p)
		case requestedBy == me:
			p.Relationship = RelationSent
			lists.Outgoing = append(lists.Outgoing, p)
		default:
			p.Relationship = RelationReceived
			lists.Incoming = append(lists.Incoming, p)
		}
	}
	if err := rows.Err(); err != nil {
		return FriendLists{}, err
	}
	sortByName(lists.Friends)
	return lists, nil
}

// SearchUsers finds people by username or name, leaving out me and anyone blocked either way.
// An exact username comes first, then usernames starting with the query, then names.
func (s *Store) SearchUsers(ctx context.Context, me, query string) ([]Person, error) {
	q := NormalizeUsername(query)
	if len(q) < 2 {
		return []Person{}, nil
	}
	like := escapeLike(q)
	rows, err := s.pool.Query(ctx, `
		SELECT `+publicColumns("u.")+`,
			CASE
				WHEN c.status = 'accepted' THEN 'friends'
				WHEN c.requested_by = $1::uuid THEN 'request_sent'
				WHEN c.status = 'pending' THEN 'request_received'
				ELSE 'none'
			END
		FROM users u
		LEFT JOIN connections c ON c.owner_id = $1::uuid AND c.viewer_id = u.id AND c.role = 'friend'
		WHERE u.id <> $1::uuid AND u.deleted_at IS NULL AND u.username IS NOT NULL
		  AND (u.username LIKE $3 || '%' OR lower(u.display_name) LIKE '%' || $3 || '%')
		  AND NOT EXISTS (SELECT 1 FROM blocks b
			WHERE (b.blocker_id = $1::uuid AND b.blocked_id = u.id) OR (b.blocker_id = u.id AND b.blocked_id = $1::uuid))
		ORDER BY (u.username = $2) DESC, (u.username LIKE $3 || '%') DESC, u.username
		LIMIT 20`, me, q, like)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(row pgx.CollectableRow) (Person, error) {
		var p Person
		err := row.Scan(&p.ID, &p.Username, &p.DisplayName, &p.AvatarURL, &p.Relationship)
		return p, err
	})
}

// PersonByID returns someone, with how me relates to them. ErrNotFound if blocked.
func (s *Store) PersonByID(ctx context.Context, me, other string) (Person, error) {
	if !isUUID(other) {
		return Person{}, ErrNotFound
	}
	var p Person
	err := pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		if err := liveUser(ctx, tx, me, other); err != nil && !errors.Is(err, ErrSelf) {
			return err
		}
		if err := tx.QueryRow(ctx, `SELECT `+publicColumns("")+` FROM users WHERE id = $1::uuid`, other).
			Scan(&p.ID, &p.Username, &p.DisplayName, &p.AvatarURL); err != nil {
			return err
		}
		var err error
		p.Relationship, err = relationship(ctx, tx, me, other)
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

// AcceptInvite makes me and the inviter friends straight away: sharing the link was the
// inviter's half of the agreement, so there's no request to wait on.
func (s *Store) AcceptInvite(ctx context.Context, me, code string) (PublicUser, error) {
	inviter, err := s.Inviter(ctx, code)
	if err != nil {
		return PublicUser{}, err
	}
	err = pgx.BeginFunc(ctx, s.pool, func(tx pgx.Tx) error {
		if err := lockPair(ctx, tx, me, inviter.ID); err != nil {
			return err
		}
		if err := liveUser(ctx, tx, me, inviter.ID); err != nil {
			return err
		}
		rel, err := relationship(ctx, tx, me, inviter.ID)
		if err != nil {
			return err
		}
		switch rel {
		case RelationFriends:
			return nil
		case RelationNone:
			_, err = tx.Exec(ctx, `
				INSERT INTO connections (owner_id, viewer_id, role, status, requested_by, accepted_at)
				VALUES ($1::uuid, $2::uuid, 'friend', 'accepted', $2::uuid, now()), ($2::uuid, $1::uuid, 'friend', 'accepted', $2::uuid, now())`,
				me, inviter.ID)
			return err
		default:
			return acceptPair(ctx, tx, me, inviter.ID)
		}
	})
	return inviter, err
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
