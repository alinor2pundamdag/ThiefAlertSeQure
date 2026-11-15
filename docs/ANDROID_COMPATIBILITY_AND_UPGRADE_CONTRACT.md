# Android compatibility and release-upgrade contract

This document is the persistent acceptance contract for ThiefAlertSeQure Android releases. Keep it current for every future version; do not treat a passing build as permission to weaken these requirements.

## Immutable production identity

- Production application ID / namespace: `com.thiefalertsequre.app`.
- Last official installed baseline: v1.1.3, versionCode 5.
- Candidate v1.1.4: versionCode 6, versionName `1.1.4`.
- Official release signing-certificate SHA-256:
  `9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64`.
- Debug/instrumentation APKs use a separate `com.thiefalertsequre.app.v114test` application ID. They must never replace the production installation or be published as an update.
- Current compatibility candidate: minSdk 23, targetSdk 36, compileSdk 36. Lowering minSdk from the v1.1.3 baseline of 26 does not change the production application ID or block an update on devices already running API 26+.

Every official update must:
1. Keep the exact production application ID and compatible signing identity.
2. Increase versionCode strictly above the previous official release.
3. Use the permanent release keystore; never sign a distributable release with a debug key.
4. Verify the downloaded APK's package, versionName, versionCode, signer certificate and SHA-256 before opening Android Package Installer.
5. Use Android's in-place update path. Never uninstall the existing app, clear app data, or change the package identity to work around an installer error.
6. Run the previous-official-release-to-candidate instrumentation test with `adb install -r`, never `adb uninstall`, and verify SharedPreferences, app-private files, monitor preference and monitor restart.
7. Keep the same checks in every later release workflow; update this document only when a deliberate, reviewed migration is approved.

## v1.1.3 → v1.1.4 upgrade behavior

- The v1.1.3 updater requires the owner to disarm protection and turn its background monitor off before downloading/installing. After this first update, the owner must re-enable monitoring if it was disabled for the update; v1.1.3 does not persist a separate “temporarily paused for update” intent, so v1.1.4 cannot safely infer whether `monitor_enabled=false` was temporary or an intentional opt-out.
- Starting with v1.1.4, an update is allowed when protection is DISARMED and there is no countdown or active alarm. A running monitor may remain enabled; Android stops the old process during package replacement and the package-replaced receiver attempts to restart it. Explicit owner opt-out remains respected.
- Android's package installer normally preserves the app's private data on an in-place update when package identity, signer and version rules are satisfied. The release workflow proves this using the previous official APK, private preference/file markers and `adb install -r`. This is a CI emulator test, not a physical HONOR 200 Pro test.

## HONOR 200 Pro background acceptance

HONOR's published guidance confirms that its App launch manager, battery optimization, Recents lock and power-saving controls can stop or restrict background apps. See [HONOR background app guidance](https://www.honor.com/global/support/content/en-us00406916/) and [HONOR app launch guidance](https://www.honor.com/uk/support/content/en-us00406923/). The app must guide the owner; ordinary Android APIs cannot read or change these private OEM switches.

For the HONOR 200 Pro, detect and display the actual installed model, Android release and API level at runtime. The owner should manually verify:

1. Settings search → **App launch** → ThiefAlertSeQure. On some MagicOS versions, use **Optimizer / Phone Manager → Battery** (or **% remaining**) → **App launch**.
2. Turn **Manage automatically** OFF.
3. Enable **Auto-launch**, **Secondary launch**, and **Run in background**, then confirm.
4. Settings search → **Battery optimization** → **All apps** → ThiefAlertSeQure → **Don't allow**, if this list is present.
5. Lock the app card in **Recents** if MagicOS offers the lock gesture/icon.
6. Turn off **Power saving** and **Ultra power saving** during acceptance testing.
7. Keep ThiefAlertSeQure's ongoing protection notification enabled; do not Force stop the app.
8. Verify exact-alarm access and notification permission in Android setup status.

The app must report HONOR/Huawei OEM launch controls as UNKNOWN until the owner manually verifies them. Do not label these controls VERIFIED based only on manufacturer detection. The local charger monitor does not require a network connection; HONOR's “Stay connected when device sleeps” is only relevant to network-dependent notification features.

HONOR menus vary by region and MagicOS version. The device's actual Settings UI takes precedence over screenshots or a generic menu path.

## Android lifecycle and security invariants

- Charger unplug is accepted only after a previously confirmed connected baseline and stable live-state sampling. The protected OS event, original timestamps, boot identity and transition generation are persisted before debounce. If the monitor process dies, recovery revalidates live power and same-boot identity before resuming; stale or cross-boot candidates are discarded. Unknown or stale power snapshots must never create a countdown.
- Optional device-admin screen locking is best-effort. Failure to lock the screen must never stop alarm audio or vibration.
- Countdown state is persisted with wall-clock and monotonic deadline data plus boot identity. Reboot recovery must revalidate live charger state and never compare an old boot's elapsedRealtime value with a new boot.
- The monitor foreground service remains private and uses the declared `specialUse` type. The alarm foreground service remains private and uses `mediaPlayback`; boot recovery must not start a prohibited mediaPlayback foreground service directly from BOOT_COMPLETED.
- Alarm playback, vibration and flash remain independent of optional device-admin screen locking. Failure of the optional lock request must not stop alarm audio.
- All PendingIntents must use immutable flags unless mutability is explicitly required and justified. System-only power receivers must accept only protected system broadcasts.
- Boot, deadline and device-admin receivers must retain their intended exported state and permissions. The APK FileProvider must remain non-exported and grant access only through scoped content URIs.
- Do not add unrelated permissions, background data collection, broad storage access, microphone, location or camera capture for charger protection.
- OEM controls, force-stop behavior, battery-saver behavior and real hardware cannot be proven by an emulator. Report emulator results separately from physical HONOR 200 Pro acceptance.

## Required release verification

- Unit tests and Android lint pass.
- Internal navigation, manifest permissions, component exposure and foreground-service declarations pass instrumentation.
- Emulator instrumentation matrix covers API 23–37; distinguish emulator-image/infrastructure failures from app failures. The CI emulator-readiness shim must remain before each emulator action: Android can report `sys.boot_completed=1` before InputManager and SettingsProvider are ready.
- Release APK is minified, package/version metadata is verified, official certificate matches, and APK ZIP integrity/checksum pass.
- The previous official APK installs first, test markers are seeded, candidate installs with `adb install -r`, and markers/versionCode/monitor restart are verified.
- Signed preflight artifact is not an official release until the release is deliberately merged/tagged through the approved production process.
- No merge to `main`, release publication, or changes to protected PRs without explicit authorization.

## Physical-device acceptance still required

On the HONOR 200 Pro, record the actual MagicOS version, Android version/API and build number, then test: app launch after reboot, charger connect/disconnect while screen on/off, 10-second countdown timing, reconnect cancellation, screen locked, Recents cleared, Battery Saver/Ultra power saving, service process death, exact-alarm access denied/granted, notification permission denied/granted, app update from v1.1.3 to v1.1.4, and post-update monitor restart. Do not claim physical acceptance until these are run on the actual phone.
