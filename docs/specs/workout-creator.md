# Spec: workout creator ("Create a programme")

Status: **planned**, agreed with Tom in October 2026. Nothing is built yet.

The research behind the exercise ratings has its own spec:
[exercise-ratings-research.md](exercise-ratings-research.md). The muscle tables here are the same
ones [strength-trends.md](strength-trends.md) needs for its muscle and region trends; build them once.

## What it is

A guided flow that asks a few questions and builds a three-month programme: a week of saved
workouts (routines) grouped into a mesocycle. It replaces the "Generate with AI · SOON" card's spot in the Library as
**Create a programme**, and works offline with no account, like the rest of the app.

The questions are a reusable building block, not a one-off screen. The same step components
will later serve onboarding, the AI coach and "rebuild my plan" without being rewritten.

## The questions

| # | Question | Answers | Notes |
| --- | --- | --- | --- |
| 1 | How many days a week can you realistically train? | 1 to 7, chips | Drives the split. |
| 2 | Are you male or female? | Male, Female, Prefer not to say | Skipped when the profile already has it. The answer is saved to the profile. |
| 3 | Is there a body part you want to prioritise? | **No** (default), Upper body, Lower body, or search for a specific muscle or group | Search autocompletes; picks show as removable chips. Up to 3 picks. |
| 4 | How long can each session be? | 30, 45, 60 (default), 75, 90 minutes | Sets the number of working sets per session (roughly 3 minutes per set including rest). |

Decided (October 2026):

- **No experience question.** Tom doesn't think it changes the plan.
- **Equipment and training environment are left out for now.** The creator assumes a full gym.
  A "big gym or home" question, or something using the camera module, comes later.

### How sex is used

Research finds men and women gain muscle at similar *relative* rates, and women often gain
upper-body strength faster in relative terms ([Roberts et al. 2020](https://pubmed.ncbi.nlm.nih.gov/32218059/)).
So sex should nudge defaults, not produce different programmes: for example suggesting glute or
upper-body emphasis as a pre-filled option, and slightly shorter rests for women, who tend to
recover faster between sets. "Prefer not to say" uses neutral defaults. Worth stating this
honestly in the app and on the website; it is a credibility point, not a weakness.

### Saving sex to the profile

- The server already has `users.sex` (`male`, `female`, `other`), but `/v1/me` doesn't expose it
  and the app has no local profile field.
- Plan: store it on the phone (the `setting` table, key `profile.sex`), show and edit it on the
  Profile screen, and when signed in, send it with `PATCH /v1/me`. Contract first: add `sex` to
  the `Me` schema in `openapi/kinzo.yaml`, then the API, then the app.
- "Prefer not to say" is saved like any other answer, so the question is never asked again; the
  Profile screen shows it and lets the user change it. It is the only question with that option.
  The server's `users.sex` check allows `male`, `female` and `other`, so a new migration adds
  `prefer_not_to_say` (not the same thing as `other`).

## Data model: muscles, groups and regions

Today each exercise lists free-text muscles from free-exercise-db (17 values such as `chest`,
`shoulders`, `middle_back`) split into primary and secondary. That is too coarse for "upper pec"
or "side delts", and has no measure of how well an exercise trains a muscle.

New catalogue tables, bundled in `catalogue.json` and imported on the phone like exercises
(see `docs/server-vs-device.md`: the creator runs entirely on the phone). The server gets
matching tables later, so sync and the website can read the same data.

```
body_region      id, name                      upper, lower, core
muscle_group     id, name, region_id           chest, back, shoulders, arms, quads, hamstrings, glutes, calves, abs ...
muscle           id, name, group_id,           upper chest (clavicular pec), mid/lower chest (sternal pec),
                 latin_name, description       front / side / rear delts, triceps long head, ...
muscle_alias     muscle_id or group_id, alias  "pecs", "upper pecs", "lats", "quads", "abs", "six pack", "booty"
exercise_muscle  exercise_id, muscle_id,       rating 1 to 5, evidence grade A to D,
                 rating, evidence, note        a short plain-language note
rating_source    exercise_id, muscle_id,       which studies back this rating
                 source_id
source           id, citation, url, year,      one row per paper, shared across ratings
                 design
```

- Search (question 3) matches `muscle.name`, `muscle_group.name` and `muscle_alias`, reusing the
  ranking in `domain/ExerciseSearch.kt`. Picking a group selects all its muscles.
- The current primary/secondary flags become derived: rating 4 or 5 shows as primary, 2 or 3 as
  secondary, so the exercise browser and filters keep working unchanged.
- About 30 muscles in 12 groups is the right grain: fine enough for "upper chest" and "side
  delts", coarse enough that ratings can be researched. Muscle heads beyond that (three triceps
  heads, two biceps heads) only where research shows exercises train them differently, such as
  the triceps long head and overhead work.
- Variations: ratings attach to the exercise group (`exercise_group`, the variations the library
  already folds together, such as every bench press) and each exercise inherits them through its
  `group_id`. An exercise only gets its own `exercise_muscle` rows where it trains muscles
  differently (incline vs flat press).
- Custom exercises: the user picks muscles and can leave ratings at a default 3, and stability
  at 3.

### Stability score

Each exercise also gets a **stability score from 1 to 5**: how much the exercise holds you in
place, so that effort goes into the target muscle rather than into balancing.

| Score | Meaning | Examples |
| --- | --- | --- |
| 5 | Fully guided, braced and supported | Smith machine bench, chest press machine, leg press |
| 4 | Fixed path or well braced | Barbell bench press on a bench, seated cable row with chest pad |
| 3 | Free weight, two limbs, supported | Barbell squat, dumbbell bench press |
| 2 | Free weight with real balance demand | Standing dumbbell press, walking lunges |
| 1 | Unstable or single-limb and unsupported | Single-leg RDL, push-ups on rings, Bulgarian split squat |

Direction: **5 is the most stable**, so a higher number means more raw mechanical tension on the
target muscle, the same "higher is more" reading as the effectiveness rating. "Stability demand"
is the opposite end of the same scale (a 1 demands the most).

- Unlike muscle ratings, stability belongs to each **exercise**, not its group: dumbbell bench and
  Smith machine bench share a group and muscle ratings but score 3 and 5. So it is a column on
  `exercise` (`stability`, 1 to 5), not inherited.
- It stands in for a mechanical tension score for now. It matters most for advanced lifters, and
  is a research item in the brief like the ratings.

## Ratings

Every catalogue exercise gets a 1 to 5 rating for each muscle it meaningfully trains. The scale,
evidence grades and research method are in the research brief. Short version for bench press,
answering Tom's question: **not 5 across the board.** Flat bench is about a 5 for the mid and
lower chest, 4 for the upper chest (incline is the 5 there), 3 to 4 for the front delts and 3
for the triceps. The reasoning and sources are in the brief.

## How the generator works

All on the phone, as plain Kotlin in `domain/`, deterministic and unit tested.

1. **Pick the split** from days per week: 1 to 2 full body; 3 full body or push/pull/legs;
   4 upper/lower ×2; 5 upper/lower + push/pull/legs; 6 push/pull/legs ×2; 7 is offered as 6
   plus an optional mobility day, since a rest day helps recovery.
2. **Set targets per muscle group per session.** Tom's rule: at most **4 working sets per muscle
   group in any session**, as **two different variations of 2 sets each**. Prioritised groups get
   the full 4 sets, appear in more sessions where the split allows, and go first in the session.
   Other groups get 2 to 4 sets depending on how much session time is left. Only direct sets
   (rating 4 or 5) count toward the cap, so bench press doesn't use up the triceps' 4.
3. **Count sets fractionally by rating.** A set of an exercise counts toward each muscle by its
   rating: 5 = 1.0, 4 = 0.75, 3 = 0.5, 2 = 0.25, 1 = 0. This mirrors how recent meta-analyses
   count "indirect" sets as half a set ([Pelland et al. 2024](https://sportrxiv.org/index.php/server/preprint/download/537/1148/1071)),
   and is why the ratings matter: they drive the volume maths, not just a label.
4. **Choose the two variations per muscle group** for each day: the pair that best fills the
   target, with rules. Compound lifts come first. The two variations should differ: different
   exercise groups, and a **spread in stability**, one less stable lift (such as barbell bench
   for 2 sets) followed by a more stable one (such as a Smith or machine press for 2 sets). Total
   sets per session are capped by the session length answer.
5. **Write the result** as routines with a split and a new mesocycle of **3 months** (13 weeks),
   using the existing `Routine`, `Split` and `Mesocycle` tables. Routines get
   a new source `creator` so the Library can label them "Made for you" and offer "Rebuild".
6. **Show a summary before saving:** days, exercises, and a weekly sets-per-muscle bar so the
   emphasis is visible. Users can swap any exercise for the next-best alternative.

## The reusable question flow

- `ui/components/questions/`: `QuestionFlow` (pager, progress, back, skip-when-known) and step
  components (`ChipChoice`, `NumberChips`, `MuscleSearch`).
- Each step declares where its answer lives: profile (sex), creator input (days, emphasis), or
  both. A step whose answer is already known is skipped, with an "Edit" link on the summary.
- The flow is a `Destination` in `Navigator.kt` so it survives process death mid-questionnaire.

## Delivery, in order

1. **Research foundation.** Muscle taxonomy, aliases and the rating scale agreed; ratings for
   the top ~150 exercises by expected use (see the brief), plus a stability score for every
   exercise. Ratings live in a reviewed CSV under
   `tools/catalogue/`, which `build_catalogue.py` merges into `catalogue.json`.
2. **Schema.** SQLDelight tables and importer; derived primary/secondary; migration on the server
   to keep parity. Exercise detail shows "Trains" with the 1 to 5 ratings.
3. **Profile sex.** OpenAPI, API, Profile screen.
4. **Question flow component** and the creator questions.
5. **Generator** in `domain/`, with tests for each split and emphasis.
6. **Summary, save and rebuild.**
7. **Remaining ratings** for the long tail of the ~915 exercises, and the public methodology page.

## Decided

- Bench press ratings as proposed (Tom agreed).
- Questions: days, sex, emphasis, session length. No experience question; equipment later.
- "Prefer not to say" is stored and never asked again.
- Programmes are 3 months by default, with no deload (easier) weeks built in.
- The Library card is called **Create a programme** (can be renamed any time).

## Open

- **Goal.** Tom's note that the stability mix depends on goals (more unstable work to build
  stabiliser strength or a better bench) suggests a later goal question. Default for now: the
  less-stable-then-more-stable pairing for everyone.

## On hold

- **General fatigue score** per exercise. Collected as an open research question in the brief.
