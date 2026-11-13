-- +goose Up
-- Each exercise in a logged workout can carry a note, as on the phone: a seat height, a form
-- cue, how it felt.
ALTER TABLE workout_exercises ADD COLUMN notes text;

-- +goose Down
ALTER TABLE workout_exercises DROP COLUMN notes;
