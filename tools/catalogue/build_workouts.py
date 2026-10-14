#!/usr/bin/env python3
"""Builds the ready-made workouts that ship inside the app (the "Kinzo workouts" in Library),
and the built-in splits (Push, Upper and so on) that workouts are filed under.

Each workout names its exercises as they appear in the catalogue built by build_catalogue.py,
so run that first. This script looks the names up, checks each target suits the exercise
(reps for lifts and bodyweight moves, seconds for timed holds), and gives every row a stable
UUID.

Usage:
    python3 tools/catalogue/build_workouts.py

Re-running gives the same output byte for byte. Bump WORKOUTS_VERSION whenever the output
changes, so installed apps re-import it.
"""

import json
import sys
import uuid
from pathlib import Path

WORKOUTS_VERSION = 2

ROOT = Path(__file__).resolve().parents[2]
CATALOGUE = ROOT / "app/shared/src/commonMain/composeResources/files/catalogue.json"
OUTPUT = ROOT / "app/shared/src/commonMain/composeResources/files/workouts.json"

# Fixed namespace for these IDs. Never change it, or installed apps see all-new workouts.
NAMESPACE = uuid.uuid5(uuid.NAMESPACE_URL, "https://github.com/tomhartill/kinzo/workouts")


# The built-in splits, in the order the app lists them, as (name, workout type). Users can add
# their own in the app. Removing one here hides it, but workouts filed under it keep its name.
SPLITS = [
    ("Full body", "resistance"),
    ("Upper", "resistance"),
    ("Lower", "resistance"),
    ("Push", "resistance"),
    ("Pull", "resistance"),
    ("Legs", "resistance"),
    ("Arms", "resistance"),
    ("Posterior", "resistance"),
    ("Quads", "resistance"),
    ("Front", "resistance"),
    ("Limbs", "resistance"),
    ("Chest", "resistance"),
    ("Back", "resistance"),
    ("Shoulders", "resistance"),
    ("Core", "resistance"),
]


def reps(name, sets, count, rest):
    return {"exercise": name, "sets": sets, "reps": count, "rest": rest}


def held(name, sets, seconds, rest):
    return {"exercise": name, "sets": sets, "seconds": seconds, "rest": rest}


BENCH = "Barbell Bench Press - Medium Grip"

# Rest is in seconds. Heavy compound lifts get the longest rests, accessories the shortest.
WORKOUTS = [
    {
        "key": "full-body-a",
        "split": "Full body",
        "name": "Full body A",
        "notes": "A beginner barbell session. Alternate with Full body B, three days a week, and add a little weight each time.",
        "exercises": [
            reps("Barbell Squat", 3, 5, 180),
            reps(BENCH, 3, 5, 180),
            reps("Bent Over Barbell Row", 3, 5, 150),
            held("Plank", 3, 30, 60),
        ],
    },
    {
        "key": "full-body-b",
        "split": "Full body",
        "name": "Full body B",
        "notes": "The other half of the beginner plan. Alternate with Full body A.",
        "exercises": [
            reps("Barbell Deadlift", 1, 5, 180),
            reps("Standing Military Press", 3, 5, 150),
            reps("Pullups", 3, 8, 120),
            reps("Hanging Leg Raise", 3, 10, 60),
        ],
    },
    {
        "key": "push",
        "split": "Push",
        "name": "Push",
        "notes": "Chest, shoulders and triceps. Pairs with Pull and Legs.",
        "exercises": [
            reps(BENCH, 4, 8, 150),
            reps("Incline Dumbbell Press", 3, 10, 120),
            reps("Standing Military Press", 3, 8, 120),
            reps("Side Lateral Raise", 3, 12, 60),
            reps("Triceps Pushdown", 3, 12, 60),
        ],
    },
    {
        "key": "pull",
        "split": "Pull",
        "name": "Pull",
        "notes": "Back and biceps. Pairs with Push and Legs.",
        "exercises": [
            reps("Barbell Deadlift", 3, 5, 180),
            reps("Wide-Grip Lat Pulldown", 3, 10, 90),
            reps("Seated Cable Rows", 3, 10, 90),
            reps("Face Pull", 3, 15, 60),
            reps("Barbell Curl", 3, 10, 60),
            reps("Hammer Curls", 2, 12, 60),
        ],
    },
    {
        "key": "legs",
        "split": "Legs",
        "name": "Legs",
        "notes": "Quads, hamstrings, glutes and calves. Pairs with Push and Pull.",
        "exercises": [
            reps("Barbell Squat", 4, 8, 180),
            reps("Romanian Deadlift", 3, 8, 150),
            reps("Leg Press", 3, 12, 120),
            reps("Lying Leg Curls", 3, 12, 60),
            reps("Standing Calf Raises", 4, 15, 60),
        ],
    },
    {
        "key": "upper",
        "split": "Upper",
        "name": "Upper body",
        "notes": "Everything above the waist. Alternate with Lower body, four days a week.",
        "exercises": [
            reps(BENCH, 4, 6, 150),
            reps("Bent Over Barbell Row", 4, 8, 120),
            reps("Dumbbell Shoulder Press", 3, 10, 90),
            reps("Wide-Grip Lat Pulldown", 3, 10, 90),
            reps("Barbell Curl", 2, 12, 60),
            reps("Triceps Pushdown", 2, 12, 60),
        ],
    },
    {
        "key": "lower",
        "split": "Lower",
        "name": "Lower body",
        "notes": "Legs and core. Alternate with Upper body.",
        "exercises": [
            reps("Barbell Squat", 4, 6, 180),
            reps("Romanian Deadlift", 3, 8, 150),
            reps("Dumbbell Lunges", 3, 10, 90),
            reps("Leg Extensions", 3, 12, 60),
            reps("Standing Calf Raises", 3, 15, 60),
            held("Plank", 3, 45, 60),
        ],
    },
    {
        "key": "dumbbell-full-body",
        "split": "Full body",
        "name": "Dumbbell full body",
        "notes": "One pair of dumbbells and a bench, for home or a quiet gym.",
        "exercises": [
            reps("Goblet Squat", 3, 10, 90),
            reps("Dumbbell Bench Press", 3, 10, 90),
            reps("One-Arm Dumbbell Row", 3, 10, 90),
            reps("Stiff-Legged Dumbbell Deadlift", 3, 10, 90),
            reps("Dumbbell Shoulder Press", 3, 10, 90),
        ],
    },
    {
        "key": "bodyweight",
        "split": "Full body",
        "name": "Bodyweight at home",
        "notes": "No equipment needed. Add reps as it gets easier.",
        "exercises": [
            reps("Pushups", 3, 12, 60),
            reps("Bodyweight Squat", 3, 15, 60),
            reps("Bodyweight Walking Lunge", 3, 12, 60),
            reps("Butt Lift (Bridge)", 3, 15, 45),
            held("Superman", 3, 30, 45),
            held("Plank", 3, 30, 45),
        ],
    },
    {
        "key": "core",
        "split": "Core",
        "name": "Core",
        "notes": "A short abs and core finisher, on its own or after another workout.",
        "exercises": [
            reps("Crunches", 3, 15, 45),
            reps("Russian Twist", 3, 20, 45),
            reps("Flat Bench Lying Leg Raise", 3, 12, 45),
            held("Side Bridge", 2, 30, 30),
            held("Plank", 3, 45, 45),
            reps("Mountain Climbers", 3, 20, 45),
        ],
    },
]


def split_id(name, kind):
    return str(uuid.uuid5(NAMESPACE, f"split:{kind}:{name.lower()}"))


def build():
    catalogue = json.loads(CATALOGUE.read_text(encoding="utf-8"))
    by_name = {exercise["name"]: exercise for exercise in catalogue["exercises"]}
    problems = []
    splits = [{"id": split_id(name, kind), "name": name, "type": kind} for name, kind in SPLITS]
    split_types = {name: kind for name, kind in SPLITS}
    if len(split_types) != len(SPLITS):
        problems.append("two built-in splits share a name")
    workouts = []
    for workout in WORKOUTS:
        workout_type = workout.get("type", "resistance")
        if workout["split"] not in split_types:
            problems.append(f"{workout['name']}: no built-in split called {workout['split']!r}")
        elif split_types[workout["split"]] != workout_type:
            problems.append(f"{workout['name']}: the {workout['split']} split is for {split_types[workout['split']]}, not {workout_type}")
        workout_id = str(uuid.uuid5(NAMESPACE, f"workout:{workout['key']}"))
        exercises = []
        for position, planned in enumerate(workout["exercises"]):
            exercise = by_name.get(planned["exercise"])
            if exercise is None:
                problems.append(f"{workout['name']}: no exercise called {planned['exercise']!r} in the catalogue")
                continue
            kind = exercise["measurementType"]
            if "reps" in planned and kind not in ("weight_reps", "reps"):
                problems.append(f"{workout['name']}: {planned['exercise']} is logged as {kind}, not reps")
            if "seconds" in planned and kind not in ("duration", "distance_duration"):
                problems.append(f"{workout['name']}: {planned['exercise']} is logged as {kind}, not time")
            exercises.append({
                "id": str(uuid.uuid5(NAMESPACE, f"workout:{workout['key']}:{position}")),
                "exerciseId": exercise["id"],
                "sets": planned["sets"],
                "reps": planned.get("reps"),
                "durationS": planned.get("seconds"),
                "restS": planned["rest"],
            })
        workouts.append({
            "id": workout_id,
            "name": workout["name"],
            "notes": workout["notes"],
            "type": workout_type,
            "splitId": split_id(workout["split"], workout_type),
            "exercises": exercises,
        })
    if problems:
        sys.exit("\n".join(problems))
    return {"version": WORKOUTS_VERSION, "splits": splits, "workouts": workouts}


def main():
    workouts = build()
    OUTPUT.write_text(json.dumps(workouts, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"wrote {len(workouts['workouts'])} workouts and {len(workouts['splits'])} splits to {OUTPUT}")


if __name__ == "__main__":
    main()
