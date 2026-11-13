-- +goose Up
-- Accounts and friends. Sign-in is with Google or Facebook (Apple comes before the App Store
-- release); the API checks the provider's answer and then issues its own session tokens. See
-- the Accounts and "Phase 2: friends and feed" sections of the product spec.

-- Facebook doesn't always share an email address, so email becomes optional. Usernames are how
-- friends find each other: lowercase letters, digits, dots and underscores, 3 to 30 long.
ALTER TABLE users
    ALTER COLUMN email DROP NOT NULL,
    ADD COLUMN username text UNIQUE CHECK (username ~ '^[a-z0-9._]{3,30}$'),
    ADD COLUMN avatar_url text,
    ADD COLUMN invite_code text UNIQUE;

ALTER TABLE auth_identities DROP CONSTRAINT auth_identities_provider_check;
ALTER TABLE auth_identities ADD CONSTRAINT auth_identities_provider_check
    CHECK (provider IN ('google', 'apple', 'facebook'));
CREATE INDEX auth_identities_user ON auth_identities (user_id);

-- A signed-in device. The token itself is never stored, only its SHA-256, so a database leak
-- can't be used to sign in. Sessions slide forward while they're in use.
CREATE TABLE sessions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash          bytea NOT NULL UNIQUE,
    created_at          timestamptz NOT NULL DEFAULT now(),
    last_used_at        timestamptz NOT NULL DEFAULT now(),
    expires_at          timestamptz NOT NULL
);
CREATE INDEX sessions_user ON sessions (user_id);

-- A sign-in in progress in the browser, from the redirect to the provider until it comes back.
-- code_challenge is the app's PKCE challenge, so only the app that started the sign-in can
-- finish it.
CREATE TABLE auth_flows (
    state               text PRIMARY KEY,
    provider            text NOT NULL,
    app_redirect        text NOT NULL,
    code_challenge      text NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now()
);

-- A one-time code handed back to the app after a browser sign-in, swapped for a session.
CREATE TABLE auth_codes (
    code_hash           bytea PRIMARY KEY,
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_challenge      text NOT NULL,
    new_user            boolean NOT NULL,
    expires_at          timestamptz NOT NULL
);

-- Friends and, in phase 3, coaches share one access model. A row lets viewer_id see owner_id's
-- data within scope. A friendship is always two rows, one each way, created pending together,
-- accepted together and deleted together. A coach connection will be a single row.
CREATE TABLE connections (
    owner_id            uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    viewer_id           uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role                text NOT NULL CHECK (role IN ('friend', 'coach')),
    status              text NOT NULL CHECK (status IN ('pending', 'accepted')),
    requested_by        uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope               text[] NOT NULL DEFAULT '{workouts,prs}',
    can_edit_routines   boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT now(),
    accepted_at         timestamptz,
    PRIMARY KEY (owner_id, viewer_id, role),
    CHECK (owner_id <> viewer_id),
    CHECK (scope <@ '{workouts,prs,body_stats,pain_scores,photos}')
);
CREATE INDEX connections_viewer ON connections (viewer_id, role, status);

CREATE TABLE blocks (
    blocker_id          uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id          uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX blocks_blocked ON blocks (blocked_id);

-- +goose Down
DROP TABLE blocks;
DROP TABLE connections;
DROP TABLE auth_codes;
DROP TABLE auth_flows;
DROP TABLE sessions;
DROP INDEX auth_identities_user;
ALTER TABLE auth_identities DROP CONSTRAINT auth_identities_provider_check;
ALTER TABLE auth_identities ADD CONSTRAINT auth_identities_provider_check
    CHECK (provider IN ('google', 'apple'));
ALTER TABLE users
    DROP COLUMN invite_code,
    DROP COLUMN avatar_url,
    DROP COLUMN username;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;
