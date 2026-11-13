-- +goose Up
-- One row per machine scan. It enforces the daily limit (each scan costs a vision-model
-- call) and shows which machines people scan that the catalogue doesn't know yet, so they can
-- be added by hand. The photo itself is never stored.
CREATE TABLE machine_scans (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at          timestamptz NOT NULL DEFAULT now(),
    -- The best match and the variation picked, or null when nothing matched.
    machine_id          uuid,
    exercise_id         uuid,
    -- What the model saw, in a sentence.
    description         text NOT NULL DEFAULT ''
);
CREATE INDEX machine_scans_user_created ON machine_scans (user_id, created_at);
CREATE INDEX machine_scans_unmatched ON machine_scans (created_at) WHERE machine_id IS NULL;

-- +goose Down
DROP TABLE machine_scans;
