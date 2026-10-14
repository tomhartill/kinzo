-- +goose Up
-- Initial schema for phase 1. See the data model section of the product spec.
--
-- Conventions:
--   * IDs are UUIDs. Rows the app creates use UUIDv7 generated on the phone, so they
--     exist before they ever reach the server. Catalogue rows default to a random UUID.
--   * Every synced table has updated_at and deleted_at (soft delete) so clients can pull
--     "everything changed since my cursor".
--   * Weights are always kilograms, distances metres, durations seconds. Kilograms keep three
--     decimals so pound values survive the round trip (15 lb = 6.804 kg shows as 15 again).
--   * References to exercises, equipment and routines are DEFERRABLE INITIALLY DEFERRED and
--     checked at commit. Deleting a user cascades through their custom exercises and their
--     workouts in no fixed order; deferring the check lets the whole account go in one
--     transaction, while deleting a catalogue row that someone still uses is still rejected.

CREATE TABLE users (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email               text NOT NULL UNIQUE,
    display_name        text,
    sex                 text CHECK (sex IN ('male', 'female', 'other')),
    date_of_birth       date,
    height_cm           numeric(5, 1) CHECK (height_cm > 0),
    weight_unit         text NOT NULL DEFAULT 'kg' CHECK (weight_unit IN ('kg', 'lb')),
    consent_analytics   boolean NOT NULL DEFAULT false,
    consent_training    boolean NOT NULL DEFAULT false,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);

CREATE TABLE auth_identities (
    provider            text NOT NULL CHECK (provider IN ('google', 'apple')),
    subject             text NOT NULL,
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, subject)
);

CREATE TABLE body_measurements (
    id                  uuid PRIMARY KEY,
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    measured_at         timestamptz NOT NULL,
    weight_kg           numeric(7, 3) CHECK (weight_kg > 0),
    body_fat_pct        numeric(4, 1) CHECK (body_fat_pct >= 0 AND body_fat_pct <= 100),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX body_measurements_user_time ON body_measurements (user_id, measured_at);

-- Equipment and exercises: owner_user_id NULL means the shared catalogue; otherwise the
-- row is a user's private custom entry and never appears for anyone else.
CREATE TABLE equipment (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id       uuid REFERENCES users (id) ON DELETE CASCADE,
    name                text NOT NULL,
    type                text NOT NULL CHECK (type IN ('machine', 'free_weight', 'cable', 'bodyweight', 'band', 'cardio', 'other')),
    description         text,
    setup_instructions  text,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX equipment_owner ON equipment (owner_user_id);

CREATE TABLE exercises (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id       uuid REFERENCES users (id) ON DELETE CASCADE,
    name                text NOT NULL,
    description         text,
    instructions        text[] NOT NULL DEFAULT '{}',
    primary_muscles     text[] NOT NULL DEFAULT '{}',
    secondary_muscles   text[] NOT NULL DEFAULT '{}',
    equipment_id        uuid REFERENCES equipment (id) DEFERRABLE INITIALLY DEFERRED,
    measurement_type    text NOT NULL CHECK (measurement_type IN ('weight_reps', 'reps', 'duration', 'distance_duration')),
    source              text NOT NULL DEFAULT 'kinzo',
    source_ref          text,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX exercises_owner ON exercises (owner_user_id);
CREATE INDEX exercises_equipment ON exercises (equipment_id);

CREATE TABLE routines (
    id                  uuid PRIMARY KEY,
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                text NOT NULL,
    notes               text,
    created_by          text NOT NULL DEFAULT 'user' CHECK (created_by IN ('user', 'coach', 'ai')),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX routines_user ON routines (user_id);

CREATE TABLE routine_exercises (
    id                  uuid PRIMARY KEY,
    routine_id          uuid NOT NULL REFERENCES routines (id) ON DELETE CASCADE,
    exercise_id         uuid NOT NULL REFERENCES exercises (id) DEFERRABLE INITIALLY DEFERRED,
    position            integer NOT NULL,
    target_sets         integer CHECK (target_sets > 0),
    target_reps         integer CHECK (target_reps > 0),
    target_weight_kg    numeric(7, 3) CHECK (target_weight_kg >= 0),
    target_duration_s   integer CHECK (target_duration_s > 0),
    rest_s              integer CHECK (rest_s >= 0),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX routine_exercises_routine ON routine_exercises (routine_id, position);
CREATE INDEX routine_exercises_exercise ON routine_exercises (exercise_id);

CREATE TABLE workouts (
    id                  uuid PRIMARY KEY,
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    routine_id          uuid REFERENCES routines (id) DEFERRABLE INITIALLY DEFERRED,
    started_at          timestamptz NOT NULL,
    ended_at            timestamptz CHECK (ended_at IS NULL OR ended_at >= started_at),
    notes               text,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX workouts_user_time ON workouts (user_id, started_at);
CREATE INDEX workouts_routine ON workouts (routine_id);

CREATE TABLE workout_exercises (
    id                  uuid PRIMARY KEY,
    workout_id          uuid NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
    exercise_id         uuid NOT NULL REFERENCES exercises (id) DEFERRABLE INITIALLY DEFERRED,
    position            integer NOT NULL,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX workout_exercises_workout ON workout_exercises (workout_id, position);
CREATE INDEX workout_exercises_exercise ON workout_exercises (exercise_id);

CREATE TABLE sets (
    id                  uuid PRIMARY KEY,
    workout_exercise_id uuid NOT NULL REFERENCES workout_exercises (id) ON DELETE CASCADE,
    position            integer NOT NULL,
    reps                integer CHECK (reps >= 0),
    weight_kg           numeric(7, 3) CHECK (weight_kg >= 0),
    duration_s          integer CHECK (duration_s >= 0),
    distance_m          numeric(9, 1) CHECK (distance_m >= 0),
    rpe                 numeric(3, 1) CHECK (rpe >= 1 AND rpe <= 10),
    pain_score          smallint CHECK (pain_score BETWEEN 0 AND 10),
    is_warmup           boolean NOT NULL DEFAULT false,
    note                text,
    completed_at        timestamptz,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX sets_workout_exercise ON sets (workout_exercise_id, position);

CREATE TABLE personal_records (
    id                  uuid PRIMARY KEY,
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    exercise_id         uuid NOT NULL REFERENCES exercises (id) DEFERRABLE INITIALLY DEFERRED,
    kind                text NOT NULL CHECK (kind IN ('estimated_1rm', 'max_weight', 'max_reps', 'max_volume', 'max_duration', 'max_distance')),
    value               numeric(13, 3) NOT NULL,
    set_id              uuid REFERENCES sets (id) ON DELETE SET NULL,
    achieved_at         timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz
);
CREATE INDEX personal_records_user_exercise ON personal_records (user_id, exercise_id, kind);
CREATE INDEX personal_records_exercise ON personal_records (exercise_id);
CREATE INDEX personal_records_set ON personal_records (set_id);

-- +goose Down
DROP TABLE personal_records;
DROP TABLE sets;
DROP TABLE workout_exercises;
DROP TABLE workouts;
DROP TABLE routine_exercises;
DROP TABLE routines;
DROP TABLE exercises;
DROP TABLE equipment;
DROP TABLE body_measurements;
DROP TABLE auth_identities;
DROP TABLE users;
