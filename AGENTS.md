# Kinzo: notes for agents

Kinzo is a free training and progression app for Android and iOS: log workouts, follow
routines and mesocycles, track progress, and (phase 2) train with friends and an AI coach. These
notes are for anyone, human or agent, picking up work in this repo. Keep them current: when you
finish something on the roadmap below, update "Where things stand" in the same commit.

## Where the plans live

- **Product spec:** [Kinzo Product Spec](https://claude.ai/code/artifact/9f224c53-837f-4f3b-94e7-79f157c1f88f)
  (a Claude Doc). Phases, data model, privacy rules and open questions.
- **Feature specs:** [`docs/specs/`](docs/specs). One file per feature that's agreed but not built.
- **What runs where:** [`docs/server-vs-device.md`](docs/server-vs-device.md). Which features run on
  the phone and which on the server, and the performance habits for each.
- **API contract:** [`openapi/kinzo.yaml`](openapi/kinzo.yaml). Paths tagged `planned` are specced
  but not built yet.

## Where things stand (October 2026)

**Built**

| Area | What works |
| --- | --- |
| App: logging | Exercise library (about 915 exercises with pictograms, variations folded into groups such as Chest press, and 52 named machines), scan a machine with the camera to pick its exercise, live workout screen with sets, rest timer, reorder, swipe to delete, history with workout detail; edit any past workout, or resume one finished in the last three hours |
| App: planning | Saved workouts (routines) with ready-made ones, workout types, splits, mesocycles |
| App: progress | Per-exercise progress charts (Vico), stats and PRs from the local database; a cached trend sparkline per exercise in the exercise list ([plan for muscles and regions](docs/specs/strength-trends.md)) |
| App: body | Progress photos from Profile: take one by pose (front, side, back), browse by day, delete. Private to the phone |
| App: accounts | Optional sign-in with Google or Facebook, or email and a phone code (waiting on the API); username and name picker; account screen (add a phone, sign out, delete) |
| App: friends | Search by name or username, requests, remove, block, invite links (`kinzo://invite/CODE`, `https://<api>/i/CODE`) |
| API | Health check; Google and Facebook sign-in (browser with PKCE, plus Android ID tokens); sessions; profile; friends, blocks and invites; machine scan through Claude (needs `ANTHROPIC_API_KEY`, 30 scans a day each) |
| CI | Android APK (published at a fixed link), desktop build, shared tests, iOS simulator build; API vet, race tests and a Postgres smoke test |

Everything the app logs lives only on the phone. **There is no sync yet**, so friends can't see
each other's workouts.

**Next, in order**

1. **Email and phone code sign-in: the API.** Spec:
   [`docs/specs/email-and-phone-sign-in.md`](docs/specs/email-and-phone-sign-in.md). Tom is taking
   it. The app side is built and stays hidden until `/v1/auth/providers` lists `phone`.
2. **Hosting.** The API on Tom's VPS with docker compose behind HTTPS, then the Google, Facebook
   and Twilio setup in [`docs/sign-in.md`](docs/sign-in.md). Nobody can sign in until this exists.
3. **Sync.** Push and pull endpoints for routines, splits, mesocycles, workouts, sets and body
   measurements, following "Sync design" in the product spec. The schema already has
   `updated_at` and `deleted_at` on every synced table.
4. **Friends feed and friend profiles,** which read synced workouts through `connections`.
5. **Before the App Store:** Sign in with Apple (Apple requires it alongside Google/Facebook).
6. **AI coach:** sign-in required; AI billing is still an open question in the product spec.

Decisions already made:
- Sign-in is optional. It unlocks friends, sync and the AI coach; nothing else needs it.
- Accepting an invite link makes two people friends at once, with no separate request.
- Workouts and PRs are shared with friends by default; body stats and photos never, unless the
  user turns that on.

## Working in this repo

- **Git:** commit and push straight to `main`. No branches or pull requests for routine work
  (Tom's rule, until he says otherwise).
- **Contract first:** change `openapi/kinzo.yaml`, then the API, then the app.
- **Migrations** in `api/migrations/` are numbered goose files. Add a new one for every schema
  change; never edit one that has shipped.
- **Units:** weights in kilograms (three decimals), distances in metres, durations in seconds,
  timestamps UTC. The app converts for display.
- **IDs:** UUIDs; the app makes UUIDv7 for rows it creates so they exist before reaching the server.
- **Words people see:** plain, short and specific. No error codes or internals in the UI.
- **Comments:** explain why, in full sentences. Match the surrounding code's density.
- **Secrets** never go in the repo. API settings come from the environment (`api/README.md`); the
  app's build settings come from Gradle properties (`docs/sign-in.md`).

## Commands

```sh
# API (Go 1.26, Postgres)
docker compose up db                                   # from the repo root
cd api && go vet ./... && go test ./...
KINZO_TEST_DATABASE_URL="postgres://kinzo:kinzo@localhost:5432/kinzo?sslmode=disable" go test ./...
                                                       # adds the account and friends tests (wipes that database)

# App (JDK 21)
cd app
./gradlew :shared:jvmTest                              # shared tests
./gradlew :androidApp:assembleDebug :desktopApp:assemble
./gradlew :desktopApp:run                              # phone-sized desktop window
```

Claude's cloud sessions can't build the Android app by default, because Google's Maven host is
blocked (see `docs/testing.md` part 3). Push to `main` and let the **App** workflow build it;
read failures from the job logs. Go and plain Kotlin checks run locally.

## Layout

| Path | What's there |
| --- | --- |
| `app/shared/src/commonMain/kotlin/com/tomhartill/kinzo/` | Almost all app code, shared by Android, iOS and desktop |
| `…/ui/screens`, `…/ui/components`, `…/ui/theme` | Screens (one package per area), reusable components, design tokens |
| `…/ui/navigation/Navigator.kt` | The back stack; every `Destination` has a route that survives process death |
| `…/data` | Repositories over SQLDelight; `data/api` is the Ktor client for the API |
| `…/account` | Sign-in plumbing: PKCE, deep links, platform interfaces |
| `…/photos` | Camera and private photo storage interfaces; each platform implements them |
| `…/domain` | Plain Kotlin logic (units, progression, rest timer), tested in `commonTest` |
| `…/di/Koin.kt` | Dependency injection; each platform adds its own module (`initKoinAndroid`, `initKoinIos`, `initKoinDesktop`) |
| `app/shared/src/commonMain/sqldelight` | The phone's database schema and queries |
| `app/androidApp`, `app/iosApp`, `app/desktopApp` | Thin platform shells |
| `api/cmd/kinzo-api` | API entry point |
| `api/internal/server` | Routes and handlers |
| `api/internal/store` | Database queries |
| `api/internal/auth` | Google and Facebook sign-in, tokens |
| `api/internal/scan` | Machine recognition: the machine list (`machines.json`, built by `tools/catalogue`) and the Claude call |
| `api/migrations` | Postgres schema |
| `tools/` | Python scripts that build the exercise catalogue and the pictograms |
| `docs/` | Setup guides and feature specs |
