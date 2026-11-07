# APK installation and update safety

## Fixed application identity

Every official build must keep this application identity unchanged:

- Application ID: `com.thiefalertsequre.app`
- Current version: `1.1.1`
- Current version code: `3`
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
adb install -r ThiefAlertSeQure-v1.1.1-release.apk
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
