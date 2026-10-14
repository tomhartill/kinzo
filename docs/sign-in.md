# Setting up sign-in

Kinzo signs people in with Google or Facebook. The app never sees a password: it opens the
provider's page, the provider sends the person back to the Kinzo API, and the API hands the
app its own session. On Android, Google sign-in uses the phone's own account sheet instead of a
web page when it can.

None of this is needed to use the app. Until it's set up, the app works as before and the
sign-in buttons say sign-in isn't set up yet.

You need four things, done once:

1. The API running somewhere phones can reach over HTTPS.
2. A Google Cloud project with two OAuth clients.
3. A Facebook app.
4. The app built with the API's address.

Throughout, `PUBLIC_URL` means the API's public address, for example `https://api.kinzo.app`.

## 1. Host the API

Google and Facebook only send people back to HTTPS addresses (apart from `localhost` while
testing on your own machine), so the API needs a domain with a certificate. On the VPS, run
`docker compose up -d` from the repo root behind a reverse proxy that handles certificates,
such as Caddy (`api.kinzo.app { reverse_proxy localhost:8080 }` is the whole config).

The API's settings go in a `.env` file next to `docker-compose.yml` (it's git-ignored):

```sh
KINZO_PUBLIC_URL=https://api.kinzo.app
GOOGLE_CLIENT_ID=...apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=...
FACEBOOK_APP_ID=...
FACEBOOK_APP_SECRET=...
```

Check it with `curl https://api.kinzo.app/v1/auth/providers`: it lists the providers that are
switched on.

## 2. Google

In the [Google Cloud console](https://console.cloud.google.com/):

1. Create a project called Kinzo.
2. **APIs & Services > OAuth consent screen**: user type External, app name Kinzo, your email
   for support and developer contact. Scopes: `openid`, `email`, `profile` (no review needed
   for these). While the app is in "Testing", add your own Google accounts as test users;
   press **Publish app** when you want anyone to sign in.
3. **APIs & Services > Credentials > Create credentials > OAuth client ID**:
   - **Web application**, named "Kinzo API". Authorised redirect URI:
     `PUBLIC_URL/v1/auth/google/callback`. Its client ID and secret are `GOOGLE_CLIENT_ID` and
     `GOOGLE_CLIENT_SECRET` for the API, and the client ID is also the app's
     `kinzo.googleWebClientId` (step 4).
   - **Android**, named "Kinzo Android". Package name `com.tomhartill.kinzo`. SHA-1 certificate
     fingerprint of the shared debug key in `app/androidApp/debug.keystore`:
     `AA:AD:62:61:B1:70:BC:BB:36:1A:2E:00:AB:73:91:35:88:AD:6D:FD`.
     Nothing from this client goes anywhere; it just has to exist for the Android account sheet
     to work. Add another one with the release key's SHA-1 before publishing to the Play Store.

## 3. Facebook

At [developers.facebook.com](https://developers.facebook.com/apps/):

1. **Create app**, use case "Authenticate and request data from users with Facebook Login".
2. **Use cases > Facebook Login > Customise > Settings**: add
   `PUBLIC_URL/v1/auth/facebook/callback` to **Valid OAuth Redirect URIs**.
3. **Use cases > Customise > Permissions**: make sure `email` and `public_profile` are added.
4. **App settings > Basic**: the App ID and App secret are `FACEBOOK_APP_ID` and
   `FACEBOOK_APP_SECRET`. Fill in a privacy policy URL; Facebook asks for one before the app
   can go live.
5. While the app is in development, only people with a role on it (you, and testers you add
   under **App roles**) can sign in. Switch it to Live when you're ready.

## 4. Point the app at the API

The app reads two values at build time:

| Gradle property | Environment variable | Value |
| --- | --- | --- |
| `kinzo.apiUrl` | `KINZO_API_URL` | `PUBLIC_URL` |
| `kinzo.googleWebClientId` | `KINZO_GOOGLE_WEB_CLIENT_ID` | The web client's ID |

- **Your own builds**: add them to `~/.gradle/gradle.properties`, for example
  `kinzo.apiUrl=https://api.kinzo.app`. For an API on your Mac, use `http://10.0.2.2:8080` in
  the Android emulator or `http://localhost:8080` for desktop and the iOS simulator.
- **The CI build** (the APK at the android-latest link): add them as repository variables on
  GitHub under **Settings > Secrets and variables > Actions > Variables**, named
  `KINZO_API_URL` and `KINZO_GOOGLE_WEB_CLIENT_ID`. The next build picks them up.

## How it fits together

- The app opens `PUBLIC_URL/v1/auth/{google|facebook}/start` in Custom Tabs (Android),
  an authentication sheet (iOS) or the browser (desktop), with a PKCE challenge.
- After the provider signs the person in, the API sends the browser to `kinzo://auth/callback`
  with a one-time code, and the app swaps the code for a session at `/v1/auth/exchange`.
- On Android, Google's account sheet gives the app an ID token instead, which it sends to
  `/v1/auth/google/id-token`. If the sheet isn't available it falls back to the browser.
- An email that both Google and Facebook have verified signs in to the same account.
- Invite links are `PUBLIC_URL/i/CODE`. Opened on a phone, the page offers to open Kinzo
  (`kinzo://invite/CODE`).

## Before the App Store

Apple requires Sign in with Apple in any iOS app that offers Google or Facebook sign-in. The
spec already plans for it; it needs adding before the first App Store submission.
