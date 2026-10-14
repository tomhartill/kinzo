#!/usr/bin/env python3
"""Builds the exercise catalogue that ships inside the app.

The source is free-exercise-db (https://github.com/yuhonas/free-exercise-db), which is
released into the public domain under the Unlicense. This script maps it onto Kinzo's data
model and gives every row a stable UUID, so the app, and later the API, agree on IDs without
talking to each other.

Usage:
    python3 tools/catalogue/build_catalogue.py path/to/exercises.json

The input is free-exercise-db's dist/exercises.json. Re-running on the same input gives the
same output byte for byte. Bump CATALOGUE_VERSION whenever the output changes, so installed
apps re-import it.
"""

import hashlib
import json
import sys
import uuid
from pathlib import Path

CATALOGUE_VERSION = 1

OUTPUT = Path(__file__).resolve().parents[2] / "app/shared/src/commonMain/composeResources/files/catalogue.json"

# Fixed namespace for catalogue IDs. Never change it: every installed app and the server
# would see a whole new catalogue.
NAMESPACE = uuid.uuid5(uuid.NAMESPACE_URL, "https://github.com/tomhartill/kinzo/catalogue")

# free-exercise-db equipment value -> (key, display name, Kinzo equipment type).
EQUIPMENT = {
    "barbell": ("barbell", "Barbell", "free_weight"),
    "dumbbell": ("dumbbell", "Dumbbell", "free_weight"),
    "e-z curl bar": ("ez_curl_bar", "EZ curl bar", "free_weight"),
    "kettlebells": ("kettlebell", "Kettlebell", "free_weight"),
    "cable": ("cable", "Cable", "cable"),
    "machine": ("machine", "Machine", "machine"),
    "body only": ("bodyweight", "Bodyweight", "bodyweight"),
    None: ("bodyweight", "Bodyweight", "bodyweight"),
    "bands": ("band", "Band", "band"),
    "medicine ball": ("medicine_ball", "Medicine ball", "other"),
    "exercise ball": ("exercise_ball", "Exercise ball", "other"),
    "foam roll": ("foam_roller", "Foam roller", "other"),
    "other": ("other", "Other", "other"),
}

MUSCLES = {
    "abdominals", "abductors", "adductors", "biceps", "calves", "chest", "forearms", "glutes",
    "hamstrings", "lats", "lower back", "middle back", "neck", "quadriceps", "shoulders",
    "traps", "triceps",
}

# Exercises the general rules below get wrong.
MEASUREMENT_OVERRIDES = {
    "Rope_Jumping": "duration",
    "Battling_Ropes": "duration",
    "Balance_Board": "duration",
    # Bodyweight moves whose "equipment" is a bar, rings, straps, a ball or a wheel.
    **{key: "reps" for key in (
        "Ab_Roller", "Band_Assisted_Pull-Up", "Ball_Leg_Curl", "Bodyweight_Mid_Row",
        "Dips_-_Chest_Version", "Exercise_Ball_Crunch", "Exercise_Ball_Pull-In",
        "Gironda_Sternum_Chins", "Hyperextensions_Back_Extensions", "Inverted_Row_with_Straps",
        "Kipping_Muscle_Up", "Knee_Hip_Raise_On_Parallel_Bars", "London_Bridges", "Mixed_Grip_Chin",
        "Muscle_Up", "One_Arm_Chin-Up", "Parallel_Bar_Dip", "Physioball_Hip_Bridge",
        "Platform_Hamstring_Slides", "Push-Ups_With_Feet_On_An_Exercise_Ball", "Ring_Dips",
        "Rocky_Pull-Ups_Pulldowns", "Rope_Climb", "Side_To_Side_Chins", "Single-Leg_High_Box_Squat",
        "Suspended_Fallout", "Suspended_Push-Up", "Suspended_Reverse_Crunch", "Suspended_Row",
        "Suspended_Split_Squat",
    )},
}

# Barbell lifts with bands added for extra resistance: the bar's weight is still logged.
BARBELL_WITH_BANDS = {"Bench_Press_-_With_Bands", "Squats_-_With_Bands"}


def measurement_type(ex):
    if ex["id"] in MEASUREMENT_OVERRIDES:
        return MEASUREMENT_OVERRIDES[ex["id"]]
    if ex["category"] == "stretching":
        return "duration"
    if ex["category"] == "cardio":
        return "distance_duration"
    if ex.get("force") == "static":
        # Holds such as the plank and the side bridge are timed.
        return "duration"
    if ex.get("equipment") in ("body only", None):
        return "reps"
    if ex.get("equipment") == "bands" and ex["id"] not in BARBELL_WITH_BANDS:
        # A band's resistance has no number to log, so band moves count reps.
        return "reps"
    if ex["category"] == "plyometrics" and ex.get("equipment") == "other":
        # Box jumps and bounds: the "equipment" is a box or a hurdle, not a load.
        return "reps"
    return "weight_reps"


def muscle_key(name):
    if name not in MUSCLES:
        raise ValueError(f"unknown muscle {name!r}")
    return name.replace(" ", "_")


def stable_id(kind, key):
    return str(uuid.uuid5(NAMESPACE, f"{kind}:{key}"))


def build(source_bytes):
    source = json.loads(source_bytes)
    equipment = {}
    exercises = []
    for ex in source:
        key, name, kind = EQUIPMENT[ex.get("equipment")]
        equipment[key] = {"id": stable_id("equipment", key), "name": name, "type": kind}
        primary = [muscle_key(m) for m in ex["primaryMuscles"]]
        secondary = [muscle_key(m) for m in ex["secondaryMuscles"] if muscle_key(m) not in primary]
        exercises.append({
            "id": stable_id("free-exercise-db", ex["id"]),
            "name": " ".join(ex["name"].split()),
            "equipmentId": equipment[key]["id"],
            "measurementType": measurement_type(ex),
            "primaryMuscles": primary,
            "secondaryMuscles": secondary,
            "instructions": [" ".join(step.split()) for step in ex["instructions"] if step.strip()],
            "sourceRef": ex["id"],
        })
    exercises.sort(key=lambda e: (e["name"].lower(), e["id"]))
    ids = [e["id"] for e in exercises]
    if len(ids) != len(set(ids)):
        raise ValueError("duplicate exercise IDs")
    return {
        "version": CATALOGUE_VERSION,
        "source": {
            "name": "free-exercise-db",
            "url": "https://github.com/yuhonas/free-exercise-db",
            "licence": "Unlicense",
            "sha256": hashlib.sha256(source_bytes).hexdigest(),
        },
        "equipment": sorted(equipment.values(), key=lambda e: e["name"]),
        "exercises": exercises,
    }


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    catalogue = build(Path(sys.argv[1]).read_bytes())
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(catalogue, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"wrote {len(catalogue['exercises'])} exercises and {len(catalogue['equipment'])} equipment rows to {OUTPUT}")


if __name__ == "__main__":
    main()
