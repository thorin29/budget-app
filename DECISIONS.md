# Decisions

The server repo's DECISIONS.md holds the reasoning for the API and its
security. This file covers choices specific to the phone.

## Same toolchain as the Kairos app

AGP 9.4 with built-in Kotlin (so no `org.jetbrains.kotlin.android` plugin),
Gradle 9.6, Compose BOM 2026.09, compileSdk 37, JDK 17. These versions already
build in CI for Kairos, so the two apps move together.

## Fewer libraries

- OkHttp with kotlinx.serialization, no Retrofit: there are a dozen calls.
- DataStore for the queue and cache, no Room: the data is small, and all of it
  can be fetched again from the server.
- No navigation library: one state decides the screen (setup, month,
  settings).

## The token never leaves the Keystore's protection

It is AES-256-GCM encrypted with a non-exportable Keystore key, held decrypted
only in memory, and excluded from every backup. If the OS clears the key, the
phone asks you to sign in again rather than crashing or making a new key.

## Every change goes through the queue

Online or offline there is one path: enqueue, then send. Each queued change
carries:

- an id, which is also its Idempotency-Key, so a resend is applied once;
- when Confirm was pressed, so the server can refuse it if the entry changed
  elsewhere afterwards.

Changes are sent oldest first and stop at the first that can't be sent now, so
order is kept. A refused change is removed from the queue and shown under "Not
saved".

## The confirmation step stays

As on the web, nothing is recorded by a single tap. Tapping a bill opens a
sheet; Confirm records it.

## Updates from GitHub Releases

As in the Kairos app: the latest release carries `latest.json` and the signed
APK. The app compares `versionCode`, downloads only from github.com over HTTPS,
and hands the file to the system installer.
