# Android update compatibility contract

**Permanent release invariant:** v1.1.3 → v1.1.4 and every later official update.

## Immutable production identity

- Application ID / package: `com.thiefalertsequre.app`
- Official signing certificate SHA-256: `9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64`
- Preserve the original owner-controlled production keystore, private key, alias and credentials. Never regenerate or replace them to make CI pass.
- v1.1.3 baseline: versionCode 5, versionName 1.1.3.
- Every subsequent official release must have a strictly higher versionCode.

## Mandatory release gates

1. Verify installed and candidate APK package IDs against the immutable production package.
2. Verify the previous official APK signer against the pinned certificate.
3. Verify the candidate APK signer against the same certificate.
4. Verify versionName matches the release tag and versionCode is strictly greater than the installed and previous official version.
5. Verify APK checksum, package metadata, ZIP integrity and R8 mapping.
6. Install the previous official APK, seed an app-private SharedPreferences marker, run `adb install -r` with the candidate, and verify the marker survives without uninstalling.
7. Keep the updater explicit and owner-confirmed; never bypass Android Package Installer.
8. Never publish an unsigned or debug-signed APK as an official update.

## Data preservation

Android's in-place package update preserves app-private data when package identity, signing identity and version ordering are accepted by Package Installer. The app must not clear or migrate user data destructively during an ordinary update. `android:allowBackup="false"` does not disable normal in-place update data preservation.

The automated marker test proves that app-private preferences survive an actual `adb install -r` upgrade on its emulator API. It does not replace physical HONOR 200 Pro acceptance or prove every individual feature's data migration.

## Safe development builds

Development APKs use `com.thiefalertsequre.app.v114test` and are intentionally separate. They must never be presented as production update APKs or used to replace `com.thiefalertsequre.app`.

## Key custody

Never commit, paste into chat, upload as ordinary artifacts, or print the private keystore or passwords in logs. Keep an encrypted offline backup. GitHub Actions receives signing material only through repository secrets. If the original key is lost, stop release work and plan a deliberate migration; do not silently create a new key.
