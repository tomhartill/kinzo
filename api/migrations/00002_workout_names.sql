-- +goose Up
-- Workouts have a name, as on the phone: "Morning workout" by default, or whatever the user
-- renames it to. Existing rows (there are none in production yet) get an empty name.
ALTER TABLE workouts ADD COLUMN name text NOT NULL DEFAULT '';

-- +goose Down
ALTER TABLE workouts DROP COLUMN name;
