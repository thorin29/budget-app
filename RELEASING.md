# Releasing

## One-time: signing secrets

Every release is signed with the same key, so updates install over the
previous version. The key is never committed.

Add four repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The keystore file, base64-encoded |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Keep a copy of the keystore and passwords somewhere safe, such as a password
manager. If they are lost, phones must uninstall and reinstall to take a build
signed with a new key, and they lose their sign-in.

## Each release

1. In `app/build.gradle.kts`, bump `versionCode` (+1) and `versionName`.
2. Add a `## [x.y.z]` entry at the top of `CHANGELOG.md`. The release notes
   are taken from it.
3. Commit, then push a tag (`git tag v0.2.0 && git push origin v0.2.0`), or run
   the **Release** workflow from the Actions tab.

The workflow runs the tests, builds the signed APK, and publishes it with
`latest.json`. Phones see the update the next time they check (on opening,
at most daily, or from Settings).
