# APK installation and update safety

> **Official development baseline: v1.1.3 (versionCode 5).** v1.1.3 carries forward the completed v1.1.2 protection, alarm, authentication, privacy, settings, event-history, and optional Device Owner functionality, with the synchronized rear-LED flashlight alert added. The flashlight is the intended functional addition; it does not replace or remove the established v1.1.2 features.

> **Signing migration:** the original v1.1.2 debug private key was not found in repository branches or available CI artifacts. A new owner-controlled release keystore has now been generated and pinned as the v1.1.3 signing baseline. It cannot update an already-installed v1.1.2 APK signed by the old certificate. Keep v1.1.2 installed until the new release-signed v1.1.3 APK is ready and an explicitly approved, backed-up migration is performed.


## Fixed application identity

Every official build must keep this application identity unchanged:

- Application ID: `com.thiefalertsequre.app`
- Current development version: `1.1.3`
- Current version code: `5`
- Branch and pull-request CI output: debug-only APK. Official release APKs are built only by the tag-triggered release workflow after all four signing secrets are verified.
- Minimum Android: API 26

Do not change `applicationId`, add an `applicationIdSuffix`, or reuse a lower version code for an update. The display label and launcher icon can change without changing the package identity.

## Signing is the update key

Android accepts an in-place APK update only when the new APK is signed by the same signing certificate as the installed app (or by a valid, platform-supported signing-key rotation lineage).

The project now has an explicit release signing configuration. The four required GitHub Actions repository secrets were verified present on 11 November 2026. Their values remain encrypted and are never printed in logs or committed to Git. Do not distribute a production APK until the tag-triggered release workflow has successfully built, signed, verified, and published it:

1. `ANDROID_KEYSTORE_BASE64` — base64-encoded contents of the permanent release keystore.
2. `ANDROID_KEYSTORE_PASSWORD` — keystore password.
3. `ANDROID_KEY_ALIAS` — release key alias.
4. `ANDROID_KEY_PASSWORD` — key password.

Keep one permanent keystore and securely back it up offline. Never commit the keystore or passwords to Git. Do not regenerate the key for a later release. If the key is lost, future APKs cannot normally update installs signed by it. The newly generated v1.1.3 release key has certificate SHA-256 `9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64` and alias `thiefalertsequre`. CI pins this fingerprint and rejects a different key.

Branch and pull-request CI always creates a **debug-only development APK** signed by the cached key at `$HOME/.config/.android/debug.keystore`. It never reads or uses the permanent release secrets. The workflow caches the exact path Gradle resolves, under cache key `android-debug-keystore-v2`, and verifies the APK signer against that debug keystore. The CI debug cache is branch-scoped and is not a production signing identity. The dedicated tag-triggered `.github/workflows/release.yml` workflow requires all four permanent secrets, builds only a release APK, and verifies its signer against the pinned certificate before publishing. Do not alternate between debug and release APKs on the same installed package.

## Why Android may reject an update

| Installer result / symptom | Meaning | Correct action |
|---|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Existing package is signed by a different certificate | Build with the original signing key. Uninstalling removes local app data and should be a last resort. |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | Incoming `versionCode` is lower than installed | Increase `versionCode`; do not reset it. |
| `INSTALL_FAILED_CONFLICTING_PROVIDER` | Another installed package claims the same provider authority | Inspect merged manifest/provider authorities; do not randomly rename the app package. |
| `INSTALL_FAILED_DUPLICATE_PERMISSION` | Package declares a permission name already owned by a different package | Inspect custom permission declarations and package ownership. |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | APK native ABI does not match the device | Build/package supported ABIs; this project currently has no app-native ABI requirement. |

A generic “App not installed” message does not identify which failure occurred. Capture the exact installer message or use ADB to identify the package-manager error before changing package identity.

## Safe update verification

On a connected test device:

```bash
adb shell dumpsys package com.thiefalertsequre.app
adb install -r ThiefAlertSeQure-v1.1.3-debug.apk
```

For an APK file, inspect its signer before installing:

```bash
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs app.apk
```

Compare the SHA-256 certificate digest of the installed baseline APK and the candidate APK. They must match for a normal update.

To capture a detailed install failure:

```bash
adb install -r app.apk
adb logcat -d -s PackageManager PackageInstaller
```

Do not uninstall the existing app just to bypass a signature error unless its local data is backed up and the user explicitly accepts data loss.

## What this fix cannot do

No Android app can override Package Manager's signature verification, silently replace a different signing key, or guarantee that a device will accept an APK signed by a different developer identity. The app code cannot repair a mismatch after the APK has been signed. The permanent release keystore must be configured before the first production distribution and preserved for every future release.

## CI checks

The Android workflow now verifies the APK's application ID, version code, version name, and signing certificate output before uploading the artifact. Release and debug APK artifacts have distinct names to prevent accidental mixing.


## Historical 1.1.2 development APK (previous build)

- GitHub Actions run: [42479548944](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/42479548944)
- Artifact: `ThiefAlertSeQure-debug-apk` (artifact ID `13617306483`, expires 2026-11-22)
- Package / version: `com.thiefalertsequre.app`, versionCode `4`, versionName `1.1.2`
- APK SHA-256: `5dc6467e90364aa7f4f730d1caeb99777ac29d69058e2981b3021a6c966dc287`
- V2 signer certificate SHA-256: `c115de2ef097e5eab7243874ad7bf212adf94f419d8fc2d963d78c8fdaebf00c`

The earlier 1.1.1 debug artifacts from the push and pull-request workflows had different signer certificates (`bb6f5da1a5b4b795383af711ae8f4bbcbc77c203ef31f0180b514234a84a2a9d` and `148b01dd2f4b2a5912abd6d8ed43da44687caf2ef02d97a565ee10d171129555`). Therefore, do not assume this 1.1.2 APK can update an older debug installation. Compare the installed certificate first; if it differs, do not uninstall or clear data without a backup and explicit acceptance of data loss.

## HONOR 200 Pro acceptance test (v1.1.3 — after its CI APK is available)

Keep the app's protection monitor enabled. In HONOR Settings, retain the manually configured App Launch settings: Manage automatically OFF, Auto-launch ON, Secondary launch ON, and Run in background ON. The app cannot toggle these private OEM controls itself.

1. Open ThiefAlertSeQure, grant notifications, and grant Alarms & reminders / exact-alarm access where Android offers it. Enable Device Administrator only if you want automatic screen locking.
2. Connect the charger. Confirm the dashboard reports ARMED.
3. With the screen on, unplug the charger. Confirm the 10-second countdown begins promptly.
4. Reconnect before the deadline. Confirm the countdown cancels and the state returns to ARMED.
5. Repeat unplugging and leave it disconnected beyond 10 seconds. Confirm ALARM_TRIGGERED, audible alarm/vibration fallback, and the ongoing notification.
6. Reconnect after the alarm has triggered. Confirm the alarm remains active.
7. Unlock Android normally. Confirm this alone does not stop the alarm. Open the app/notification and complete its explicit Android device-credential or BIOMETRIC_STRONG prompt. Confirm the alarm stops only after that successful prompt.
8. Confirm the previous alarm volume is restored after successful authentication.
9. Repeat the countdown while the app is backgrounded and the screen is off. Repeat with another app in the foreground.
10. With the camera flash alert enabled (default), confirm the LED stays OFF while ARMED and during the countdown. After ALARM_TRIGGERED, confirm the rear LED pulses slowly with the audio. Reconnect after trigger and unlock Android normally; neither action may stop the alarm or LED. Complete the app's correct PIN/biometric prompt and confirm audio and LED stop together. Incorrect or cancelled authentication must leave both active. Repeat with the flash alert disabled; audio must still work. Confirm no camera permission prompt appears. If another app takes camera/torch control, confirm the audio alarm remains active even if the LED is interrupted.
11. Reboot during a countdown. After first unlock, confirm connected power returns to ARMED; confirmed disconnected power receives a fresh 10-second grace period; unknown power waits for a reliable sample.
12. Reboot while ALARM_TRIGGERED. After first unlock, confirm the latched alarm recovery notification/service resumes where Android permits it. Direct-Boot protection before first unlock is not supported.
13. Review Settings status after returning from Android settings. Confirm the app reports actual notification, exact-alarm, battery-optimization, Device Admin and full-screen-intent access rather than assuming a setting was granted.

Record each result as PASS / FAIL / NOT TESTED, including the exact MagicOS and Android build. Device Owner enrollment/rollback has been user-reported PASS on a separate test device; do not provision it on an everyday phone because enrollment normally requires a prepared or factory-reset test device.

**Important:** The v1.1.3 source now includes the synchronized alarm flashlight and the acknowledgement-only first-launch notice. Its CI artifact is pending verification; do not install a guessed or previous APK as v1.1.3. When CI completes, use the artifact recorded for the exact branch head. The resulting APK is a development/debug APK unless the release keystore secrets are configured. Verify its signer SHA-256 against the previously installed development build before updating. If the signer differs, do not uninstall or clear app data; stop and inspect the signing identity first. CI success does not replace this physical-device test.


## v1.1.3 CI artifact and signing incident — verified 2026-11-09

The exact feature-branch head `a064a410b225b556eb57d1194b779e3bd9344678` completed GitHub Actions run [42499601985](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/42499601985) successfully. Unit tests, lint, asset validation, Gradle-script uniqueness, APK assembly, package/version metadata checks, signer verification and artifact upload all passed.

- Production application ID: `com.thiefalertsequre.app`
- Version: `1.1.3` / versionCode `5`
- Artifact ID: `13624629302` (debug APK; expires 2026-11-22)
- APK SHA-256: `6d705b93da06c462bd767bcbf52510e4a948360cfe6d33501ce291ff8b6a4650`
- V2 signer certificate SHA-256: `bb901aca0f9128ce1d5dd30c309bdb85fb41d5f2a4cff4a36d9f8bb4897af0bb`
- The v1.1.2 installed baseline signer was `c115de2ef097e5eab7243874ad7bf212adf94f419d8fc2d963d78c8fdaebf00c`.

**This production-package v1.1.3 debug APK is not a safe in-place update for v1.1.2.** The signing certificate differs. Do not install it over the existing app, uninstall the existing app, or clear its data to bypass the mismatch.

A separate diagnostic build completed successfully in run [42499226556](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/42499226556): package `com.thiefalertsequre.app.v113test`, version 1.1.3 / code 5, signer SHA-256 `f1d21709532795482efd44b43d94251308e6e5300c61b83857ce2d9d60e88483`, APK SHA-256 `44e6127d6e9c3d6a243d801d7817bec55d35ba8d65efd96b9b8e05194f85db9a`. This package is deliberately separate and can be installed beside v1.1.2 for physical feature testing; it does not replace or migrate the production package's data.

The cache diagnostic established that the cache action reported a hit, but the resulting signer did not match the v1.1.2 baseline. A cache-hit message is therefore not proof that the original private key was restored. The original private key cannot be reconstructed from the APK, public certificate, or SHA-256 fingerprint. The current production-package build is technically valid but its debug signing identity is not update-compatible with the existing installation.

**Release gate remains blocked:** configure a persistent owner-controlled release keystore and four GitHub Actions secrets before production distribution. If continuity with the existing v1.1.2 installation is required, first recover the original private debug keystore from a trusted backup. If it cannot be recovered, keep v1.1.2 installed and use the separate `.v113test` package for testing; any eventual migration to a new production signing identity requires an explicit, backed-up reinstall decision. Do not describe the debug APK as a production release.

The earlier paragraph above saying the v1.1.3 artifact was pending is historical and superseded by this section. The CI build is verified; physical flashlight testing and Android 17 background-audio testing are still not verified.


## Signing recovery attempt and CI release gate — 2026-11-09

A fresh pull-request build was deliberately run against the PR's `main` base cache to test whether it could restore the v1.1.2 signing identity. The cache reported a hit, but the resulting v1.1.3 APK signer was still different:

- Run: https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/42554665364
- Expected v1.1.2 certificate: `c115de2ef097e5eab7243874ad7bf212adf94f419d8fc2d963d78c8fdaebf00c`
- Actual PR-build certificate: `dba6541743563346ff32c8daa36bfc39b75d6a46d03a1f7024ec128e256f9da2`
- Unit tests, lint, asset validation, Gradle script uniqueness, APK assembly and APK metadata verification passed.
- The signer gate failed as intended; artifact upload was skipped. No incompatible APK was published by this run.

The workflow now checks the signing certificate against the known v1.1.2 baseline before uploading an APK. It also limits automatic push builds to `main`; feature work is verified through the PR-to-main workflow, avoiding misleading branch-push artifacts. The signer check applies to every APK variant, including release builds, because a newly generated release key would also be unable to update the installed v1.1.2 app.

**Recovery conclusion:** The cached key available to the PR build is not the original private key. The v1.1.2 APK and its public certificate expose no private-key material, so the original signing key cannot be reconstructed from them. GitHub Actions cache listing/deletion and repository-secret write access are not available through the connected GitHub actions in this session. No signing secrets were configured or changed.

**Only valid path to an in-place update:** locate the original `debug.keystore` backup that produced certificate `c115de2...`, then configure the workflow's four signing secrets with that exact keystore, alias and passwords. CI will then build and verify v1.1.3 with the matching identity. If no backup exists, an in-place update over v1.1.2 is cryptographically impossible; keep v1.1.2 installed and use the separate `.v113test` package for testing until an explicitly backed-up migration plan is approved. Never uninstall v1.1.2 just to bypass this check.


## v1.1.3 official-baseline decision — 2026-11-09

The project direction is to make v1.1.3 the official successor and future development baseline. The feature branch contains the complete v1.1.2 feature set plus the synchronized alarm flashlight. The flashlight is optional, slow-pulsed, default-enabled with disclosure, owner-disableable, and never allowed to gate alarm audio. The first-launch privacy notice retains its disclosure and acknowledgement action without a Decline button.

The CI signing workflow was corrected to cache the Gradle-resolved debug keystore path (`~/.config/.android/debug.keystore`) with a new cache key (`android-debug-keystore-v2`). APK signer verification now compares the produced APK against the actual keystore selected for that build: the cached debug keystore for development builds or the configured permanent release keystore for release builds. It no longer incorrectly compares a newly migrated v1.1.3 build against the lost v1.1.2 debug certificate.

This does **not** recover the v1.1.2 private key, and it does not make the new debug APK update-compatible with an installed v1.1.2 copy. A future production APK must be signed with the permanent owner-controlled release keystore. Migration of an installed v1.1.2 copy remains a separate user-approved operation after data-preservation planning.


## Permanent v1.1.3 release keystore — generated 2026-11-09

A new owner-controlled Android release keystore was generated and verified. It is the signing identity to use for v1.1.3 and every subsequent official APK update.

- Keystore file: `ThiefAlertSeQure-upload-key.jks` (keep a private backup; never commit it)
- Alias: `thiefalertsequre`
- Certificate SHA-256: `9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64`
- Key type: RSA 2048
- Validity: 10,000 days
- GitHub secret names: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`

**Configuration status (verified 11 November 2026):** all four required repository Actions secrets are now present in GitHub and stored using GitHub's encrypted secrets mechanism. Their values are not displayed in repository files or logs. The owner-controlled keystore and its secure backup remain separate; none of the private key material or passwords is committed to this repository. Branch and pull-request CI remains debug-only. The dedicated tag-triggered release workflow will use the secrets and fail closed if any value or certificate check is invalid.

The workflow now:
1. Rejects a partial signing-secret configuration instead of silently falling back to debug.
2. Checks the release keystore's certificate against the pinned SHA-256 above before building.
3. Builds the release variant only when all four secrets are present.
4. Verifies the resulting APK signer against the same configured keystore before artifact upload.

**Important installation consequence:** v1.1.2 was signed with a different, unrecovered certificate. This new key cannot make the existing v1.1.2 install update in place. It establishes a stable identity for the new v1.1.3 baseline and future updates. Do not install the current debug APK; wait for the release-signed artifact after the four GitHub secrets are configured.

## Owner-initiated in-app updates

The Settings screen now includes **Check for official updates**. This is an explicit action; no background polling or automatic download runs. For safety, both checking and installation are blocked while background protection is enabled or the protection state is ARMED, COUNTDOWN, or ALARM_TRIGGERED. Disable monitoring and ensure the state is DISARMED before updating, because Android may restart the app process during package replacement.

1. The app requests public release metadata from the official GitHub Releases endpoint over HTTPS.
2. It accepts only a published, non-draft, non-prerelease release with the expected versioned `-release.apk` and matching `.sha256` asset.
3. It downloads the APK only after the owner taps the update action.
4. Before installation, it verifies the downloaded file's SHA-256 checksum, application ID, newer version code, release tag/version name, and the pinned official signing certificate.
5. It opens Android's Package Installer. The owner must approve the installation. Android may also require the owner to allow installs from ThiefAlertSeQure in Android settings.

When the package ID and signing certificate match, Android installs the new APK **over the existing app**. A normal in-place package update retains the app's private data; uninstalling is not part of the update flow. The updater never attempts to bypass Android's package installer or install silently.

The updater intentionally rejects debug APKs and any APK signed with a certificate other than the pinned official release key. The app's installed certificate is checked too, so a debug build cannot accidentally present a release APK as an eligible update.

## Official release pipeline

Create an annotated or lightweight version tag only after the intended commit has been merged into `main`, for example `v1.1.3`. The dedicated `.github/workflows/release.yml` workflow:

- refuses tags that do not point to a commit on `main`;
- requires all four permanent signing secrets and verifies the pinned certificate;
- checks that the tag matches `appVersionName` and that `versionCode` is greater than the previous official release;
- runs unit tests and release lint;
- builds only the release variant (never falls back to debug);
- verifies application ID, version code, version name and signing certificate;
- publishes `ThiefAlertSeQure-v<VERSION>-release.apk` and its `.sha256` checksum as GitHub Release assets.

A missing secret, wrong key, version mismatch, failed test, or failed signature check stops publication. Do not publish a manually uploaded debug APK as an official release.

## Permanent update compatibility rule

For every future version, keep all of the following stable:

- application ID: `com.thiefalertsequre.app`;
- release signing certificate SHA-256: `9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64`;
- the same owner-controlled private keystore and alias;
- monotonically increasing `versionCode`.

Only `versionName` and the increasing `versionCode` should change for an ordinary update. Never create a new release keystore for v1.1.4, v1.1.5, or later.

**One-time legacy migration:** an installed v1.1.2 APK signed with the unrecovered old debug key cannot be updated in place by the new permanent release key. This is an Android cryptographic restriction, not an application bug. Do not uninstall the legacy installation until its data has been reviewed and a migration/backup decision has been made. Once the app is installed from the permanent release key, future signed releases can update in place.

