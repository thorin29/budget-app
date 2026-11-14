# Decisions

The server repo's DECISIONS.md holds the reasoning for the API and its
security. This file covers choices specific to the phone.

## Same toolchain as the Kairos app

AGP 9.4 with built-in Kotlin (so no `org.jetbrains.kotlin.android` plugin),
Gradle 9.6, Compose BOM 2026.09, compileSdk 37, JDK 17. These versions already
build in CI for Kairos, so the two apps move together.

## Fewer libraries

- OkHttp with kotlinx.serialization, no Retrofit: there are a dozen calls.
- No navigation library: one state decides the screen (setup, month,
  settings).

## Room for what's kept on the phone

Room 3 with a bundled SQLite, the same versions as Kairos. It holds the queue
of unsent changes, the refused ones, and the last copy of each month and of
the projection (kept as the server's JSON, since it is fetched again on every
refresh).

Unlike Kairos's cache, the queue is the only copy of what was entered offline,
so the database never drops tables on a schema change. A new version needs a
migration, built from the schema JSON in `app/schemas` (CI uploads it as the
`room-schemas` artifact; commit it once per database version). 0.1.x kept these
in DataStore; 0.2.0 moves a waiting queue across on first launch.

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

## The 0.3 design

The phone isn't a copy of the web. It opens on what matters on the go (safe
to send, the balance, what's due) and puts recording one tap away.

- **Figtree**, bundled (five weights, Latin), with tabular figures so amounts
  line up.
- **Phosphor Icons**, bundled as vector drawables: Regular, with Fill for the
  active tab and paid items. Bill icons are guessed from the item's name, then
  its category (`BillIcons`), since the server stores no icon.
- **One accent**, chosen per phone: Cobalt, Violet, Graphite, Forest, Plum or
  Teal, each with a light and a dark tone. Warnings stay amber and red, so no
  accent is orange or red.
- **The number pad** starts on the planned amount; the first key typed
  replaces it. Review then Confirm keeps the web's confirmation step.
- **Plan** uses the server's own day-by-day projection. A payment today comes
  off every day ahead, so the slider just subtracts it; nothing is
  recalculated on the phone.

## Update badge

Copied from the Kairos app so both behave the same: the same `latest.json`
release asset, the same blue dot (#4FC3F7) and "New" pill (#0B6FA4), one
notification per version (the last notified versionCode is saved), and a
WorkManager check about every two hours plus one at launch. Budget has a
bottom bar instead of Kairos's top bar and rail, so the dot sits on the More
tab, which is visible on every screen. The last result is saved, so a failed
check changes nothing; the badges clear once the newer build is installed.
