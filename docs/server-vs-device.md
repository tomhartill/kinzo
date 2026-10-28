# What runs on the phone and what runs on the server

Kinzo is offline-first, so the default answer is **the phone**. The server earns a job only when
the phone can't do it well. This guide sets the rule, applies it to every feature we have or have
planned, and lists the performance habits that keep both sides fast.

## The rule

Run it on the phone unless one of these is true:

1. **It needs other people's data.** Friends, the feed, search for users, invites, a coach's
   view of a client. The phone only holds its owner's data.
2. **It must be trusted.** Anything a user could fake by editing their own phone: account
   identity, who is allowed to see what, rate limits, anything that costs money (AI, SMS).
3. **It needs a secret.** API keys for AI providers, Twilio, Google and Facebook client secrets.
   Nothing secret ships inside the app, because anyone can unpack an APK.
4. **It's too heavy or too big for a phone.** Large models, aggregating many users' data, or
   reference data that changes daily and is too large to bundle.
5. **It has to outlive the phone.** Backups and moving to a new device, which sync covers.

If none apply, the phone does it: it's instant, works with no signal, costs nothing to run and
keeps health data private. Mid-workout, the network is never on the critical path.

## Feature by feature

| Feature | Where | Why |
| --- | --- | --- |
| Logging sets, rest timer, reorder, undo | Phone | Must be instant with no signal. Writes go to SQLite first; sync later. |
| Workout history and detail | Phone | It's the user's own data, already local. |
| Progress charts, estimated 1RM, best sets | Phone | Pure maths over local rows (`domain/Progression.kt`). |
| Trend sparklines in lists (exercises now; muscles, groups, upper/lower later) | Phone, cached | A small `trend` table, refreshed when a workout finishes or is deleted, so a long list never reads the whole history. See [`specs/strength-trends.md`](specs/strength-trends.md). |
| PR detection | Phone, then synced | Shown the moment a set is completed, so it can't wait for a round trip. The server stores the synced PR rows so friends can see them. |
| Exercise library, search, filters, variation groups | Phone | About 915 exercises and 52 named machines fit easily in SQLite; search is a local filter. |
| Catalogue updates (new exercises, machine notes, muscle scores) | Built offline, bundled with the app; later also served as a versioned download | Versioned so the phone only imports when it changes (`CatalogueImporter`). |
| Routines, splits, mesocycles | Phone, synced | The user's own data, edited offline. |
| Rule-based progression ("next time try 82.5 kg x 5") | Phone | The spec calls for it to be cheap, instant and offline. |
| Workout creator (rule-based programme from days, sex, priorities) | Phone | Deterministic rules over the local catalogue and profile. See below. |
| Profile (sex, date of birth, height, units) | Phone, synced | Edited locally like everything else; the server keeps the copy that other devices pull. |
| Body measurements | Phone, synced | Private by default, so it never needs the server unless shared. |
| Sign-in, sessions, tokens | Server | Needs secrets and must be trusted. |
| Usernames, user search, follows, blocks, invites | Server | Needs other people's data. |
| Feed and someone's profile | Server queries, phone renders and caches | The server filters by the connection's scope on every request; the phone caches the last result so the screen opens instantly. |
| Sync (push, then pull changes since a cursor) | Both | The phone decides what changed; the server is the store of record and resolves conflicts (last write wins per row). |
| AI coach chat, AI programme generation | Server proxy to the model | Needs a secret key, costs money and must be rate-limited. The phone sends a compact summary, not the raw history. |
| Scan a machine (first version, built) | Server proxy to a vision model (`POST /v1/scan/machine`) | Same reasons as the coach. The server sends the photo and the machine list to Claude Haiku and answers with catalogue IDs, so the phone needs no extra data. Capped per user per day. |
| Scan a machine (later, on-device model) | Phone | The spec's plan once a small model exists: instant and free. |
| Progress photos | Phone (built: taking and browsing); encrypted upload to object storage later | Most sensitive data in the app; only leaves the phone when the user wants backup or sharing. Files sit in the app's private storage, never the gallery. |
| Push notifications | Server | Only the server knows a friend just finished a workout. |
| Analytics and model training data | Server, consented only | Aggregates many users; runs as a batch job, never on a request path. |

## The workout creator

The new creator (days per week, sex, body-part priority, muscle search) belongs on the phone:

- **Inputs** are local: the profile (sex is read from it, and saved to it if missing), the
  user's answers, and the catalogue.
- **The muscle and exercise-effectiveness data** (muscle groups, muscles, and a 1 to 5 score per
  exercise and muscle) is reference data. It's built offline from the research, versioned, and
  shipped inside `catalogue.json` like the rest of the catalogue. The phone already has an
  `exercise_muscle` table that can carry the score. No network call is needed to build a plan.
- **The muscle search box** is a local filter over a few hundred muscle and group names, the
  same way `ExerciseSearch` works today. It will feel instant; a server round trip per keystroke
  would not.
- **The plan it produces** is ordinary routines and a mesocycle, so it syncs like anything the
  user made by hand.
- **The AI coach can sit on top later**: the same rules produce a draft on the phone, and the
  coach (on the server) explains or adjusts it. The phone never waits for the AI to show a plan.

The server's only role is to hold the canonical copy of the scores so the catalogue build and a
future "download the latest catalogue" endpoint read from one source.

## Performance habits

**On the phone**

- Database work runs off the main thread. The repositories already wrap queries in
  `withContext(io)`; keep that pattern.
- Do heavy maths in SQL or in `domain/` on a background dispatcher, never in a composable.
- Watch the screens that read all history. `WorkoutRepository.history()` loads every finished workout
  and the progress charts load every session of an exercise, which is fine for a year of training but will slow down with
  thousands of sessions. When it does, add paging to the history list and a small cached summary
  table per exercise (best set, latest 1RM), updated when a workout finishes, rather than moving
  the work to the server.
- Indexes follow queries. Every new query that filters or sorts by a column gets an index in the
  same change, as `workout_exercise_exercise` does.
- Import the catalogue only when its version changes (already the case) and in one transaction.

**On the network**

- Sync in batches: one push and one pull per sync, not one request per row.
- Pull with a cursor (`updated_at`), so a sync after a workout moves a few rows, not the account.
- Send gzipped JSON. Leave out fields the other side can derive.
- Sync after a workout finishes, when the app opens, and on a schedule in the background. Never
  block a screen on sync.
- Cache server-only screens (feed, friend profiles) on the phone and refresh in the background.

**On the server**

- Keep request handlers to indexed queries; anything slow (training data exports, aggregates,
  notifications fan-out) goes to a background job.
- Check the connection and its scope on every request for someone else's data, in SQL rather
  than by loading rows and filtering in Go.
- Rate-limit everything that costs money or can be abused: code sign-in, AI calls, user search.
- Precompute the feed only if live queries get slow. With following lists in the tens, a query
  over their recent workouts is fine.

## Quick test for a new feature

Ask in order, and stop at the first yes:

1. Does it read or change another person's data? **Server.**
2. Does it need a secret, cost money, or need to be trusted? **Server.**
3. Is it too heavy for a phone, or does its data change faster than app releases? **Server**, or a
   versioned download the phone caches.
4. Otherwise: **phone**, synced if it's the user's own data.
