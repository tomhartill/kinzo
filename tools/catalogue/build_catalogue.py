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
import re
import sys
import uuid
from pathlib import Path

CATALOGUE_VERSION = 2

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


# Movement pictograms. Each key names an SVG the app draws next to the exercise; "generic" is
# a plain dumbbell for anything that fits no other picture.
ICONS = (
    "squat", "leg_press", "lunge", "deadlift", "hip_thrust", "kettlebell_swing", "olympic_lift",
    "bench_press", "overhead_press", "push_up", "dip", "chest_fly", "lateral_raise", "row",
    "pull_up", "pulldown", "shrug", "curl", "triceps_extension", "leg_extension", "leg_curl",
    "calf_raise", "crunch", "leg_raise", "plank", "side_plank", "twist", "back_extension", "carry",
    "throw", "jump", "run", "bike", "rowing_machine", "jump_rope", "stretch_standing",
    "stretch_floor", "foam_roll", "generic",
)

# Stands in for "stretch_standing" or "stretch_floor", picked from the start position the
# instructions describe.
STRETCH = "stretch"

# Exercises the rules below get wrong, by free-exercise-db id.
ICON_OVERRIDES = {
    # Named after one movement but look like another.
    "Air_Bike": "crunch",  # a bicycle crunch, not a bike
    "Handstand_Push-Ups": "overhead_press",
    "Lying_Machine_Squat": "leg_press",
    "One_Leg_Barbell_Squat": "lunge",  # rear foot on a bench
    "Bench_Sprint": "jump",  # explosive alternating step-ups onto a box
    "Dumbbell_Raise": "shrug",  # an upright row
    "Middle_Back_Shrug": "row",  # chest on an incline bench, squeezing the shoulder blades
    "Iron_Cross": "lateral_raise",
    "Kettlebell_Halo": "overhead_press",
    "London_Bridges": "row",  # leaning back hand over hand on a rope
    "Cable_Incline_Pushdown": "pulldown",  # a straight-arm lat pushdown, not triceps
    "Leg_Lift": "leg_curl",  # standing, leg raised behind
    "Downward_Facing_Balance": "plank",
    "Exercise_Ball_Pull-In": "plank",
    "Isometric_Wipers": "push_up",
    "Drop_Push": "push_up",
    "Plate_Pinch": "carry",  # plates held at the sides for time
    "Power_Stairs": "deadlift",  # implements lifted from the floor onto each step
    # Stretches and mobility drills that a name rule or the position check gets wrong.
    "Front_Leg_Raises": "stretch_standing",
    "Side_Leg_Raises": "stretch_standing",
    "Rear_Leg_Raises": "stretch_floor",
    "Lying_Crossover": "stretch_floor",
    "Overhead_Triceps": "stretch_floor",
    "Elbow_Circles": "stretch_standing",  # "sit or stand"
    "Shoulder_Circles": "stretch_standing",
    "Pyramid": "stretch_floor",  # draped over a ball, hands and feet on the floor
    # Hip abduction and adduction, and balance work: no picture fits.
    "Monster_Walk": "generic",
    "Cable_Hip_Adduction": "generic",
    "Balance_Board": "generic",
}

# (pattern, icon) pairs tried in order against the exercise name, lower-cased with punctuation
# turned into spaces; the first match wins. Order matters: a specific name comes before a word
# it contains ("leg curl" before "curl", "jump squat" before "squat").
ICON_RULES = [(re.compile(pattern), icon) for pattern, icon in (
    # Recovery and stretching come first: their names often contain a movement word.
    (r"\bsmr\b", "foam_roll"),
    (r"\bstretch", STRETCH),
    # Cardio machines.
    (r"\browing\b", "rowing_machine"),
    (r"\bbicycl|\bbike\b", "bike"),
    (r"\brope jump|\bjump rope|\bskipping rope", "jump_rope"),
    # Combined lifts named after their parts.
    (r"\bturkish get up", "overhead_press"),
    (r"\bthruster", "squat"),
    (r"\bskull ?crusher|\bjm press\b|\btate press\b|\btriceps? press\b", "triceps_extension"),
    (r"\bshrugs?\b|\bupright\b.*\brows?\b", "shrug"),
    (r"\bcalf\b|\bcalves\b", "calf_raise"),
    (r"\bleg press\b", "leg_press"),
    (r"\bleg extensions?\b", "leg_extension"),
    (r"\bleg curls?\b|\bhamstring curl|\bglute ham\b|\bhamstring slides\b", "leg_curl"),
    (r"\bpush ?ups?\b", "push_up"),
    (r"\bpull ?ups?\b|(?<!to )\bchins?\b|\bchin ups?\b|\bmuscle ups?\b|\brope climb\b", "pull_up"),
    (r"\bpull ?downs?\b", "pulldown"),
    (r"\blunges?\b|\bsplit squats?\b|\bstep ups?\b", "lunge"),
    (r"\bdeadlifts?\b|\brack pulls?\b|\bgood mornings?\b|\bstiff leg|\bromanian\b|\bpull through\b"
     r"|\batlas stones?\b|\bkeg load\b|\bsandbag load\b|\btire flip\b", "deadlift"),
    (r"\bclean\b(?! grip)|\bsnatch\b|\bjerk\b|\bhigh pull\b|\brack delivery\b", "olympic_lift"),
    (r"\bdips?\b", "dip"),
    (r"\bjump|\bhops?\b|\bbound\b|\bleap\b|\bskip|\bbutt kick\b|\bbox shuffle\b|\bpush off\b", "jump"),
    (r"\bsquats?\b", "squat"),
    (r"\bbench press|\bchest press|\bfloor press|\bboard press|\bpin press|\bchain press"
     r"|\bsmith press|\bdecline press|\bneck press|\bsvend press|\bincline\b.*\bpress\b"
     r"|\bclose grip\b.*\bpress\b|\bdumbbell bench\b|\bincline shoulder raise\b", "bench_press"),
    # Reverse flyes and rear-delt raises lift the arms out to the sides.
    (r"\breverse\b.*\bfly|\brear delt fly|\bback fly|\bpull apart\b", "lateral_raise"),
    (r"\bfly\b|\bflyes?\b|\bcross ?over\b|\bbutterfly\b|\biron cross\b|\baround the worlds\b"
     r"|\bchest squeezes\b|\bpullover\b", "chest_fly"),
    (r"\btriceps?\b|\bpushdown\b|\bchain handle extension\b|\boverhead extension\b",
     "triceps_extension"),
    (r"\bleg raises?\b|\bknee raise\b|\bhip raise\b|\bleg pull in\b|\bleg tucks?\b"
     r"|\breverse crunch\b|\bhanging pike\b|\bflutter kicks?\b|\bscissor kick\b|\bbottoms up\b"
     r"|\bdead bug\b|\bwind sprints\b|\bhip flexion\b", "leg_raise"),
    (r"\bcrunch|\bsit ups?\b|\bjackknife\b|\bheel touchers\b|\btoe touchers\b|\belbow to knee\b"
     r"|\bcocoons\b|\botis up\b", "crunch"),
    (r"\btwists?\b|\bwood chop\b|\bcable lift\b|\bjudo flip\b|\blandmine 180\b|\bspell caster\b"
     r"|\bpirate ships\b|\bwindmill\b|\bpallof press\b|\bside bends?\b|\btorso rotation\b", "twist"),
    (r"\bshoulder press\b|\bmilitary press\b|\bpush press\b|\barnold\b|\bpress behind\b|\bbradford\b"
     r"|\bsee ?saw press\b|\bpara press\b|\bseated press\b|\bbent press\b|\bcuban press\b"
     r"|\blinear jammer\b|\banti gravity press\b|\bdumbbell press\b|\bkettlebell press\b"
     r"|\blog lift\b|\bcircus bell\b", "overhead_press"),
    (r"\blateral\b|\blaterals\b|\bdeltoid raise\b|\bdelt raise\b|\bfront\b.*\braise\b"
     r"|\bplate raise\b|\bscaption\b|\braises on incline\b|\bcrucifix\b|\bcar drivers\b"
     r"|\bpower partials\b|\brotation\b|\bsingle dumbbell raise\b", "lateral_raise"),
    (r"\brows?\b|\bt bar\b|\bbench pull\b|\bface pull\b", "row"),
    (r"\bhyperextensions?\b|\bback extensions?\b|\bsuperman\b|\blower back curl\b", "back_extension"),
    (r"\bcurls?\b|\bwrist roller\b|\bwrist rotations\b|\bpronation\b|\bsupination\b", "curl"),
    (r"\bside bridge\b|\bside plank\b", "side_plank"),
    (r"\bhip thrust\b|\bbridge\b|\bhip lift\b|\bkickback\b|\bhip extension\b", "hip_thrust"),
    (r"\bplank\b|\broll ?out\b|\bab roller\b|\bfallout\b|\bbody up\b|\bbutt ups\b|\bspider crawl\b"
     r"|\bmountain climbers?\b", "plank"),
    (r"\bfarmer\b|\bcarry\b|\bwalk\b|\byoke\b|\bdrags?\b|\bsled push\b|\bprowler\b|\bconan\b",
     "carry"),
    (r"\bthrow\b|\bslam\b|\bchest pass\b|\bchest push\b|\bbag thrust\b|\breturn push\b"
     r"|\bsledgehammer\b|\bbattling ropes\b", "throw"),
    (r"\bswings?\b|\bbetween the legs\b|\bfigure 8\b", "kettlebell_swing"),
    (r"\btreadmill\b|\brunning\b|\bjogging\b|\belliptical\b|\bstairmaster\b|\bstep mill\b"
     r"|\bsprints?\b|\bskating\b|\bcarioca\b|\bclaw series\b|\barm drill\b|\bstart technique\b"
     r"|\bacceleration\b", "run"),
)]

# Used when no name rule matches.
ICON_BY_CATEGORY = {
    "cardio": "run",
    "plyometrics": "jump",
    "olympic weightlifting": "olympic_lift",
    "strongman": "carry",
    "stretching": STRETCH,
}
ICON_BY_MUSCLE = {
    "abdominals": "crunch",
    "biceps": "curl",
    "calves": "calf_raise",
    "forearms": "curl",
    "glutes": "hip_thrust",
    "lats": "pulldown",
    "lower back": "back_extension",
    "middle back": "row",
    "quadriceps": "squat",
    "traps": "shrug",
    "triceps": "triceps_extension",
}

FLOOR_WORDS = re.compile(
    r"\b(lie|lying|lay|laying|sit|sitting|seat|seated|kneel|kneeling|all fours|hands and knees"
    r"|face ?down|face up|on your back|on your stomach|on your side|prone|supine|push ?up position)\b"
)
STANDING_WORDS = re.compile(r"\b(stand|standing)\b")


def stretch_icon(ex):
    """Standing or floor stretch, whichever start position the instructions mention first."""
    text = " ".join(ex["instructions"]).lower()
    floor = FLOOR_WORDS.search(text)
    standing = STANDING_WORDS.search(text)
    if floor and (not standing or floor.start() < standing.start()):
        return "stretch_floor"
    return "stretch_standing"


def icon_by_fallback(ex):
    if ex.get("equipment") == "foam roll":
        return "foam_roll"
    if ex["category"] in ICON_BY_CATEGORY:
        return ICON_BY_CATEGORY[ex["category"]]
    muscle = ex["primaryMuscles"][0] if ex["primaryMuscles"] else None
    compound = ex.get("mechanic") == "compound"
    if muscle == "chest":
        return "bench_press" if compound else "chest_fly"
    if muscle == "shoulders":
        return "overhead_press" if compound and ex.get("force") == "push" else "lateral_raise"
    if muscle == "hamstrings":
        return "deadlift" if compound else "leg_curl"
    return ICON_BY_MUSCLE.get(muscle, "generic")


def icon_for(ex):
    """The movement pictogram for a free-exercise-db exercise."""
    if ex["id"] in ICON_OVERRIDES:
        icon = ICON_OVERRIDES[ex["id"]]
    else:
        name = re.sub(r"[^a-z0-9]+", " ", ex["name"].lower()).strip()
        icon = next((icon for pattern, icon in ICON_RULES if pattern.search(name)), None)
        if icon is None:
            icon = icon_by_fallback(ex)
    if icon == STRETCH:
        icon = stretch_icon(ex)
    if icon not in ICONS:
        raise ValueError(f"unknown icon {icon!r} for {ex['id']}")
    return icon


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
            "icon": icon_for(ex),
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
