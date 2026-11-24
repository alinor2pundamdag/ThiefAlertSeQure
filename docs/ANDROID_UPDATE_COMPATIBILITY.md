# Android update compatibility contract

**Current candidate snapshot (24 November 2026):** clean branch `release/v1.1.5-candidate`, PR #9 open/draft/unmerged, versionName `1.1.5_Developer_Alinor`, versionCode `7`. Clean head `56418b68e422ded696705fd4d07d76605151d19a` passed Android CI #45307869027 and CodeQL #45307868989. This is not a production release. The last published baseline remains v1.1.3 / code 5; v1.1.4 / code 6 was an intermediate candidate.

## Immutable production identity

- Application ID / package: `com.thiefalertsequre.app`
- Official signing certificate SHA-256: `9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64`
- Preserve the original owner-controlled production keystore, private key, alias and credentials. Never regenerate or replace them to make CI pass.
- v1.1.3 baseline: versionCode 5, versionName 1.1.3, minSdk 26.
- Historical v1.1.4 candidate: versionCode 6, versionName `1.1.4_Developer_Alinor`, minSdk 26. It was not confirmed as a published official release in this repository snapshot.
- Current v1.1.5 candidate: versionCode 7, versionName `1.1.5_Developer_Alinor`, minSdk 26. The debug test variant is isolated as package suffix `.v114test` and versionName `1.1.5-test`; it is never accepted by the production updater.
- Preserve the owner-approved minSdk 26 invariant; do not lower it or raise it without an explicit compatibility decision.
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



**Current verification boundary (18 November 2026):** The `adb install -r` marker scenario remains a separate release-install acceptance check. No emulator, instrumentation compilation/execution, or physical-device test is claimed for the current Batch Four candidate. CI/build results must be recorded against the exact latest commit; they do not establish physical-device acceptance.

## Data preservation

Android's in-place package update preserves app-private data when package identity, signing identity and version ordering are accepted by Package Installer. The app must not clear or migrate user data destructively during an ordinary update. `android:allowBackup="false"` does not disable normal in-place update data preservation.

The automated marker test proves that app-private SharedPreferences and files survive an actual `adb install -r` upgrade on its emulator API. It does not replace physical HONOR 200 Pro acceptance or prove every individual feature's data migration.

## Safe development builds

Development APKs use `com.thiefalertsequre.app.v114test` and are intentionally separate. They must never be presented as production update APKs or used to replace `com.thiefalertsequre.app`.

## Key custody

Never commit, paste into chat, upload as ordinary artifacts, or print the private keystore or passwords in logs. Keep an encrypted offline backup. GitHub Actions receives signing material only through repository secrets. If the original key is lost, stop release work and plan a deliberate migration; do not silently create a new key.
