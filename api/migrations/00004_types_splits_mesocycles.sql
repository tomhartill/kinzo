-- +goose Up
-- Saved workouts get a type (resistance training, swimming and so on, a fixed list in the app),
-- optionally a split such as Push or Upper, and a mesocycle: the block of training they belong
-- to. Exercises get the key of their pictogram. See the data model section of the product spec.

-- The user's own splits. The app's built-in splits aren't stored here, so routines.split_id has
-- no foreign key: it names one of these or one built into the app.
CREATE TABLE splits (
    id                  uuid PRIMARY KEY,
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                text NOT NULL,
    workout_type        text NOT NULL,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX splits_user ON splits (user_id);

-- Both dates are included. The current mesocycle is the one that started last.
CREATE TABLE mesocycles (
    id                  uuid PRIMARY KEY,
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                text NOT NULL,
    start_date          date NOT NULL,
    end_date            date NOT NULL CHECK (end_date >= start_date),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX mesocycles_user ON mesocycles (user_id);

ALTER TABLE routines
    ADD COLUMN workout_type text NOT NULL DEFAULT 'resistance',
    ADD COLUMN split_id uuid,
    ADD COLUMN mesocycle_id uuid REFERENCES mesocycles (id) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX routines_mesocycle ON routines (mesocycle_id);

ALTER TABLE exercises ADD COLUMN icon text;

-- +goose Down
ALTER TABLE exercises DROP COLUMN icon;
ALTER TABLE routines DROP COLUMN mesocycle_id, DROP COLUMN split_id, DROP COLUMN workout_type;
DROP TABLE mesocycles;
DROP TABLE splits;
