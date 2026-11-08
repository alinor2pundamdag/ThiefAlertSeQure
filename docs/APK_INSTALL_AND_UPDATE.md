# APK installation and update safety

## Fixed application identity

Every official build must keep this application identity unchanged:

- Application ID: `com.thiefalertsequre.app`
- Current development version: `1.1.3`
- Current version code: `5`
- Current CI output: debug-only APK unless all four permanent release-signing secrets are configured
- Minimum Android: API 26

Do not change `applicationId`, add an `applicationIdSuffix`, or reuse a lower version code for an update. The display label and launcher icon can change without changing the package identity.

## Signing is the update key

Android accepts an in-place APK update only when the new APK is signed by the same signing certificate as the installed app (or by a valid, platform-supported signing-key rotation lineage).

The project now has an explicit release signing configuration. Configure these GitHub Actions repository secrets before distributing a production APK:

1. `ANDROID_KEYSTORE_BASE64` — base64-encoded contents of the permanent release keystore.
2. `ANDROID_KEYSTORE_PASSWORD` — keystore password.
3. `ANDROID_KEY_ALIAS` — release key alias.
4. `ANDROID_KEY_PASSWORD` — key password.

Keep one permanent keystore and securely back it up offline. Never commit the keystore or passwords to Git. Do not regenerate the key for a later release. If the key is lost, future APKs cannot normally update installs signed by it.

When all four secrets exist, CI creates a release-signed APK. Without them, CI creates a **debug-only development APK**; its cached debug key is not a durable production signing guarantee. Do not alternate between debug and release APKs on the same installed package.

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
adb install -r ThiefAlertSeQure-v1.1.2-debug.apk
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


## Verified 1.1.2 development APK

- GitHub Actions run: [42479548944](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/42479548944)
- Artifact: `ThiefAlertSeQure-debug-apk` (artifact ID `13617306483`, expires 2026-11-22)
- Package / version: `com.thiefalertsequre.app`, versionCode `4`, versionName `1.1.2`
- APK SHA-256: `5dc6467e90364aa7f4f730d1caeb99777ac29d69058e2981b3021a6c966dc287`
- V2 signer certificate SHA-256: `c115de2ef097e5eab7243874ad7bf212adf94f419d8fc2d963d78c8fdaebf00c`

The earlier 1.1.1 debug artifacts from the push and pull-request workflows had different signer certificates (`bb6f5da1a5b4b795383af711ae8f4bbcbc77c203ef31f0180b514234a84a2a9d` and `148b01dd2f4b2a5912abd6d8ed43da44687caf2ef02d97a565ee10d171129555`). Therefore, do not assume this 1.1.2 APK can update an older debug installation. Compare the installed certificate first; if it differs, do not uninstall or clear data without a backup and explicit acceptance of data loss.

## HONOR 200 Pro acceptance test (after installing the verified 1.1.2 APK)

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
10. With the camera flash alert enabled, trigger the alarm and confirm the rear LED pulses slowly with the audio. Authenticate successfully and confirm both audio and LED stop. Repeat with the flash alert disabled; audio must still work. Confirm no camera permission prompt appears.
11. Reboot during a countdown. After first unlock, confirm connected power returns to ARMED; confirmed disconnected power receives a fresh 10-second grace period; unknown power waits for a reliable sample.
12. Reboot while ALARM_TRIGGERED. After first unlock, confirm the latched alarm recovery notification/service resumes where Android permits it. Direct-Boot protection before first unlock is not supported.
13. Review Settings status after returning from Android settings. Confirm the app reports actual notification, exact-alarm, battery-optimization, Device Admin and full-screen-intent access rather than assuming a setting was granted.

Record each result as PASS / FAIL / NOT TESTED, including the exact MagicOS and Android build. Device Owner enrollment/rollback has been user-reported PASS on a separate test device; do not provision it on an everyday phone because enrollment normally requires a prepared or factory-reset test device.

**Important:** The 1.1.3 CI build is a development/debug APK unless the release keystore secrets are configured. Verify its signer SHA-256 against the previously installed development build before updating. If the signer differs, do not uninstall or clear app data; stop and inspect the signing identity first. CI success does not replace this physical-device test.
