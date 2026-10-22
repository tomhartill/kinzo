# Spec: strength trends (sparklines) beyond exercises

Status: **exercises built; muscles, muscle groups and upper/lower body planned.** The exercise
list shows a small line of each exercise's recent sessions with an up, down or flat mark. This
spec reuses the same pieces for every other strength trend.

## What's built

| Piece | Where | What it does |
| --- | --- | --- |
| `trend` table | `sqldelight/.../Trend.sq`, migration `4.sqm` | One row per subject: `subject_kind`, `subject_id`, the metric, and up to 12 recent values. Derived data, never synced. |
| Trend maths | `domain/Trends.kt` | Picks the values, decides the direction (slope of a fitted line; under 2% of the average is flat), encodes the cache. |
| Cache writer | `data/TrendRepository.kt` (`TrendCache`) | Refreshes a subject's row inside the transaction that finishes or deletes a workout, reading only that exercise's last 12 sessions. |
| Rebuild | `TrendRepository.rebuildIfNeeded()` | Builds every row from history the first time a version runs, and again whenever `Trends.VERSION` goes up. |
| Sparkline | `ui/components/Sparkline.kt` | A 48×20 dp canvas line plus a direction mark. Path built once per row with `drawWithCache`; no chart library. |

**Why it's fast:** the list reads one small table (one row per exercise the user has done), not
their set history. A heavy user with 200 exercises used reads 200 short rows. Finishing a
workout recomputes only the 5 to 10 exercises in it, each from a bounded query. The lazy list
draws only the rows on screen. Nothing goes to the server, per
[`server-vs-device.md`](../server-vs-device.md): it's the user's own data and pure maths.

**Per exercise, the line follows** the first metric its progress chart offers: estimated 1RM
for weight and reps, best reps, longest hold, or furthest distance.

## Next: other subjects

Each new subject is a new `subject_kind` and a function that turns history into one value per
session (or per week). The table, direction rule, encoding, rebuild and sparkline don't change.

| Kind | `subject_id` | Value per point | Source |
| --- | --- | --- | --- |
| `muscle` | `Muscle.key` (`chest`, `lats`, ...) | Weekly strength index (below) | `exercise_muscle` (primary, later the 1 to 5 score) |
| `muscle_group` | group key from the workout creator's muscle data | Average of its muscles' indexes | Workout creator catalogue data |
| `region` | `upper`, `lower` (later `push`, `pull`, `core`) | Average of its groups' indexes | A fixed muscle-to-region map in `domain/` |
| `overall` | `all` | Average of every trained muscle | Same |

**Strength index.** Exercises aren't comparable in kilograms (a 100 kg squat and 20 kg curl), so
a muscle's trend uses each exercise's estimated 1RM **relative to that exercise's own first
value in the window**, averaged across the exercises that train the muscle, weighted by how
much the exercise works it (primary 1.0, secondary 0.5; the 1 to 5 effectiveness score once the
workout creator ships it). It's plotted **weekly**, not per session, because a muscle is
trained by different exercises on different days, and a week smooths that out. Volume (hard
sets per muscle per week) is a good second trend on the same table with a different metric.

**When it updates.** Finishing or deleting a workout refreshes the exercises in it (as now),
then the muscles those exercises train, then their groups and regions. That's a few dozen
small rows per workout, all in the same transaction. Rebuild-on-version covers everything else.

**Where it shows.** Muscle filter menu and a future muscles screen (one row per muscle with a
sparkline), the Profile or Progress tab for upper/lower and overall, and the workout
creator's muscle search results.

## Decisions to make

- **Muscle maths:** relative 1RM index (proposed above) vs. weekly volume vs. both. Index answers
  "am I getting stronger", volume answers "am I training it enough".
- **Window:** 12 sessions per exercise; 12 weeks for muscles and regions is proposed.
- **Colour:** the marks are monochrome because the palette keeps red for destructive actions.
  Green up and red down would read faster but would need a palette change.
- **Server:** stays out of it unless friends get to see each other's trends; then the phone
  syncs the computed rows rather than the server recomputing them.
