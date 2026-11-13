-- +goose Up
-- Each exercise in a logged workout keeps its own rest time, as on the phone: seconds of rest
-- after each set, 0 for no timer, or NULL for the user's default.
ALTER TABLE workout_exercises ADD COLUMN rest_s integer CHECK (rest_s >= 0);

-- +goose Down
ALTER TABLE workout_exercises DROP COLUMN rest_s;
