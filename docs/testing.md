# Testing Kinzo

Three ways to try the app, from least to most setup.

## 1. Install the CI build on an Android phone (no setup on your Mac)

Every push to `main` that touches `app/` builds a debug APK on GitHub Actions.

1. On your phone, sign in to GitHub in the browser and open
   [Actions > App](https://github.com/tomhartill/kinzo/actions/workflows/app.yml).
2. Open the newest green run and scroll to **Artifacts**. Download `kinzo-debug-apk`.
   GitHub wraps it in a zip; open the zip in the Files app to get `androidApp-debug.apk`.
3. Tap the APK. Android asks to allow installs from your browser or Files app; allow it, then
   tap **Install**. Play Protect may warn about an unknown developer; choose **Install anyway**.

Artifacts expire after 14 days. To get a fresh build without changing code, open the same
Actions page and press **Run workflow** on `main`.

A new APK installs over the old one and keeps its data. Debug builds are signed with a shared
key committed at `app/androidApp/debug.keystore`, so CI builds and builds from your own Android
Studio also install over each other.

## 2. Run everything on your Mac

One-time installs:

- [Android Studio](https://developer.android.com/studio). Open it once and let it download the
  Android SDK. Then install the **Kotlin Multiplatform** plugin (Settings > Plugins).
- JDK 21: Android Studio bundles one. Or `brew install --cask zulu@21`.
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) for the API and Postgres.
- Xcode from the App Store, for iOS. Open it once to accept the licence and install the iOS
  simulator.

### The app

Open the `app/` folder in Android Studio (not the repo root). The first sync takes a while.

| Goal | How |
| --- | --- |
| Fastest UI loop, no phone | `cd app && ./gradlew :desktopApp:run` opens a phone-sized window |
| Your Android phone over USB | See below, then press Run with `androidApp` selected |
| Android emulator | Device Manager in Android Studio > create a Pixel device > Run |
| iOS simulator | Open `app/iosApp/iosApp.xcodeproj` in Xcode, pick an iPhone simulator, press Run |
| Shared unit tests | `cd app && ./gradlew :shared:jvmTest` |

To run on your phone from Android Studio, turn on developer mode once: Settings > About phone >
tap **Build number** seven times. Then Settings > System > Developer options > turn on
**USB debugging**, plug the phone in, and accept the prompt on the phone. It appears in Android
Studio's device list. Wireless debugging (same Developer options screen) works too.

### The API

```sh
docker compose up          # from the repo root: Postgres + API on http://localhost:8080
curl localhost:8080/healthz
```

`docker compose down` stops it; `docker compose down -v` also wipes the database.

The app does not call the API yet. When it does, the address differs by device: the desktop
build and iOS simulator use `http://localhost:8080`, the Android emulator uses
`http://10.0.2.2:8080`, and a real phone needs your Mac's Wi-Fi address
(System Settings > Wi-Fi > Details), with phone and Mac on the same network.

## 3. Building Android inside Claude's cloud sessions

Claude's cloud sessions can build the API but not the Android app, because the default network
policy blocks Google's Android download server (`dl.google.com`), which Gradle needs for the
Android plugin and SDK. GitHub Actions is unaffected, so CI builds still work.

To let Claude build and test Android itself:

1. Open the project's settings > **Cloud environment** > **Add cloud environment**.
2. Under **Network access**, keep **Allow package managers** ticked and add these allowed domains:
   `dl.google.com`, `maven.google.com`.
3. Save. New Claude sessions in the project use it.

Steps and options: <https://code.claude.com/docs/en/cloud-environments#network-access>.
