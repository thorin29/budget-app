# Budget for Android

The phone app for [Budget](https://github.com/thorin29/budget), the self-hosted
budget planner. It talks to your own Budget server and nothing else (apart from
checking this repo for updates).

> **Status:** v0.1.0. Needs Budget server 0.15.0 or newer.

## What it does

- **This month:** bills by half of the month, income, anything unpaid from
  last month, and the item paid from what's left over.
- **Four figures:**
  - the bills balance (tap to update);
  - what's free after the first half;
  - what's free after the month;
  - what's safe to send today.
- **Recording a payment:** tap a bill, check the amount and the date, then press
  Confirm. There is no one-tap tick, the same as on the web.
- **Works offline:** changes made without a connection wait on the phone and
  send when it's back.
  - Each change is applied once, even if it has to be sent twice.
  - The server refuses a change if the same entry was changed elsewhere after
    you entered it. The app then lists it under "Not saved".
- **Updates itself:** it checks this repo's releases and installs a new version
  from inside the app.

## Adding a phone

1. On the web, open **Settings → Sign-in & people → Add a phone**. It shows a
   one-time code and a QR code, valid for 15 minutes.
2. In the app, tap **Scan QR code** and point it at the web page. It fills in
   the server address and code. Or type both. (The phone's own camera app
   won't open the link; scan from inside Budget.)
3. Enter your Budget username and password.

The password is used once to get this phone its own token and is not kept.

## Security

- The token is encrypted with a key held in the Android Keystore, and is
  excluded from backups and device transfer (`allowBackup=false` plus
  data-extraction rules).
- The server address is typed in or scanned. It is never built into the app,
  this repo or the APK.
- The token is only sent to the configured server.
- A refused request is checked with the server before the phone gives up its
  sign-in, so one odd answer can't sign you out.
- Signing out removes the token, the cache and anything still waiting to send.

## Building

CI builds and signs the APK. See `RELEASING.md` for the signing secrets and how
to publish an update.

Locally, open the project in Android Studio. A debug build installs as
"Budget (debug)" beside the release app.

## Toolchain

AGP 9.4, Gradle 9.6, Kotlin 2.2, Compose BOM 2026.09, compileSdk/targetSdk 37,
minSdk 26, JDK 17. These are the same versions the Kairos app builds with. Pins
are in `gradle/libs.versions.toml`.

## License

MIT
