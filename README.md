# Kinzo

A free, fully featured training and progression app for iOS and Android. "Kinzo" is a working name.

The product spec lives in [Kinzo Product Spec](https://claude.ai/code/artifact/9f224c53-837f-4f3b-94e7-79f157c1f88f).
What's built, what's next and how to work in the repo: [`AGENTS.md`](AGENTS.md). Agreed features
that aren't built yet have a spec in [`docs/specs/`](docs/specs).

## Repository layout

| Path | What it is | Stack |
| --- | --- | --- |
| [`app/`](app) | The mobile app: one codebase for Android and iOS | Kotlin Multiplatform, Compose Multiplatform |
| [`api/`](api) | The backend API | Go, Postgres |
| [`openapi/`](openapi) | The contract between the app and the API | OpenAPI 3.1 |
| [`docs/`](docs) | Setup guides and feature specs | Markdown |
| [`tools/`](tools) | Scripts that build the exercise catalogue and pictograms | Python |

The app is offline-first: it reads and writes a local database on the phone. Syncing with the
API is the next big piece; today only accounts and friends go through the API.

## Getting started

Signing in is optional. Google and Facebook sign-in need a one-off setup:
[`docs/sign-in.md`](docs/sign-in.md). Email and phone code sign-in is specced in
[`docs/specs/email-and-phone-sign-in.md`](docs/specs/email-and-phone-sign-in.md).

New to mobile? [`docs/testing.md`](docs/testing.md) walks through installing the CI build on an
Android phone, running everything on a Mac, and letting Claude's cloud sessions build Android.

### App

Requirements: Android Studio (with the Kotlin Multiplatform plugin), JDK 21, and Xcode for iOS.

```sh
cd app
./gradlew :androidApp:installDebug      # Android phone or emulator
./gradlew :desktopApp:run               # phone-sized desktop window for fast UI work
./gradlew :shared:jvmTest               # shared unit tests
```

For iOS, open `app/iosApp/iosApp.xcodeproj` in Xcode and run on a simulator. Xcode builds the
shared Kotlin code automatically. To run on a real iPhone, set `TEAM_ID` in
`app/iosApp/Configuration/Config.xcconfig`.

Code layout inside `app/shared/src/commonMain/kotlin/com/tomhartill/kinzo/`:

| Package | Contents |
| --- | --- |
| `ui/theme` | Design tokens: colour, type, spacing, shapes. Rebranding happens here. |
| `ui/components` | Reusable components: buttons, cards, stat tiles, set rows, progress charts (Vico) |
| `ui/screens` | Screens grouped by area, plus a design system gallery (Profile > Design system) |
| `ui/navigation` | The back stack and its saved routes |
| `data` | Repositories over the local SQLDelight database; `data/api` is the API client |
| `account` | Sign-in plumbing: PKCE, deep links, platform sign-in interfaces |
| `domain` | Plain Kotlin logic such as unit conversion, tested in `commonTest` |
| `di` | Koin dependency injection setup |

### API

```sh
docker compose up                       # Postgres + API on http://localhost:8080
curl localhost:8080/healthz
```

See [`api/README.md`](api/README.md) for running from source.

## Design

Neutral and monochrome until there is a brand: black, white and greys, with red kept for
destructive actions and errors. No shadows; flat surfaces with hairline borders. Light and dark
themes follow the system setting. Every tappable thing mid-workout is at least 48dp tall.

## CI

GitHub Actions run on every pull request, only for the parts that changed:

- **API**: gofmt, `go vet`, `go test -race`, then a smoke test that migrates a real Postgres and calls `/healthz`.
- **App**: Android debug APK (downloadable from the run), desktop build, shared tests, and an iOS simulator build.
