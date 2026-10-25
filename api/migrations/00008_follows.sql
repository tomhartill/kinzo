-- +goose Up
-- Friends become follows, as on Instagram: following is one-way, and a private account approves
-- each new follower. A follow is one connections row: viewer_id follows owner_id, so it lets
-- the viewer see the owner's data within scope, exactly what the table was built for.

-- Private accounts approve new followers. Public is the default.
ALTER TABLE users ADD COLUMN private boolean NOT NULL DEFAULT false;

ALTER TABLE connections DROP CONSTRAINT connections_role_check;

-- An accepted friendship was a row each way, so it becomes two people following each other.
-- A pending request was also two rows; only the requester's half (the one where they are the
-- viewer) means anything now, and every account starts public, so it becomes a follow.
DELETE FROM connections WHERE role = 'friend' AND status = 'pending' AND viewer_id <> requested_by;
UPDATE connections SET role = 'follow', status = 'accepted', accepted_at = coalesce(accepted_at, now())
WHERE role = 'friend';

ALTER TABLE connections ADD CONSTRAINT connections_role_check CHECK (role IN ('follow', 'coach'));

-- +goose Down
ALTER TABLE connections DROP CONSTRAINT connections_role_check;
DELETE FROM connections WHERE role = 'follow';
ALTER TABLE connections ADD CONSTRAINT connections_role_check CHECK (role IN ('friend', 'coach'));
ALTER TABLE users DROP COLUMN private;
