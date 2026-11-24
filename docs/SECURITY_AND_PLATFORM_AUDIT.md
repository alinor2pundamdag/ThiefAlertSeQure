> **Current verified Batch Four candidate (24 November 2026):** `work/batch-4-implementation`, v1.1.5 code 7, PR #8 open/draft/unmerged, latest code-bearing commit `5e850fc4a444a1e36541890ca022a745f3be021a`. Android CI #45304517153 and CodeQL #45304517314 passed. The development artifact is debug-signed only; the minified release APK metadata passed but the APK is unsigned in PR CI. Production signing availability and CodeQL alert #8 closure remain unverified. Phase 12 physical acceptance remains 0/25 PASS / 25/25 NOT TESTED; no instrumentation/emulator/physical-device test is claimed. The 11/11 owner-reported handset scenarios elsewhere in this document apply to v1.1.3 only.

> Cross-feature corrections include A2 deadline invalidation after authenticated disarm/package replacement, emergency protection-state snapshots, non-blocking alarm recovery, dedicated flash timing workers, SafePreferences-only legacy Gallery index reads, explicit OS backup/device-transfer exclusions, API guards, and redacted diagnostics. Filament 1.80.1/1.81.0 require compileSdk 37; the approved compileSdk 36 / AGP 8.10.1 baseline therefore remains on Filament 1.75.1. Revisit the newer upstream swap-chain fix only with an approved API-37 toolchain migration.

<div align="right">
  <a href="../README.md"><img src="../ThiefAlertSeQureIcon.jpg" alt="ThiefAlertSeQure icon" width="64" /></a>
</div>

[← Back to ThiefAlertSeQure overview](../README.md)

---

# Security and Android Platform Audit

## Android 17 background audio compatibility

Android 17 (API 37) applies background-audio hardening to all apps running on that OS, even if they target API 36. Audio playback, audio-focus requests, and volume changes must occur while an Activity is visible or while a foreground service other than SHORT_SERVICE is active. ThiefAlertSeQure performs alarm playback and alarm-volume enforcement inside AlarmService, declared as a mediaPlayback foreground service. The separate charger monitor is specialUse and does not own alarm playback.

For apps targeting API 37, Android adds while-in-use foreground-service capability requirements, with an exception when the app has exact-alarm access and interacts with USAGE_ALARM streams. The current app targets API 36. Exact-alarm access denial can delay the countdown callback and may prevent a background-started alarm service from starting; the inexact fallback is best-effort and is not an audible-alarm guarantee.

Android 17 test command: `adb shell cmd audio set-enable-hardening throw`. Trigger the alarm with the app backgrounded and screen off, then inspect logcat / dumpsys audio for AudioHardening failures. Disable the test override afterward with `adb shell cmd audio set-enable-hardening disable`. This test remains pending on a real Android 17 physical device. Emulator testing is excluded by the current owner direction.

## GitHub repository security automation

Checked on 11 November 2026:
- Secret scanning: enabled.
- Secret scanning push protection: enabled.
- Open secret-scanning alerts: none returned.
- Dependabot vulnerability alerts: enabled; no open alerts returned.
- Dependabot automated security updates: enabled. GitHub may open dependency update pull requests; these are not auto-merged.
- Code scanning: the advanced CodeQL workflow analyzes Java/Kotlin application code and GitHub Actions workflow code on pushes to `main`, `feat/**`, `feature/**`, `fix/**`, `security/**`, and `work/**`, pull requests to `main`, and a weekly schedule. The FIXALL coding branch is included. CodeQL run [#14](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43872054576) completed successfully on head `1141b10db19afc9ffa085b549adfd61ae0c39629`; this records successful analysis execution, not a guarantee that no findings exist.

The signed release workflow's manual preflight is restricted to `refs/heads/main`. Tag-triggered publication separately verifies that the tagged commit is already an ancestor of `main`. The preflight builds and verifies but does not publish a GitHub Release.

## Threat model
Protect against casual charger removal and ordinary user-level interference. Standard mode is a normal Android app, not a system, privileged or device-owner app. Optional managed mode uses legitimate Android Enterprise Device Owner provisioning and Lock Task APIs. Neither mode can promise resistance to hardware forced shutdown, battery removal, bootloader/recovery, firmware changes or physical tampering.

## Local privacy and data
- No app backend, analytics, ad SDK or account. INTERNET permission is limited to owner-initiated GitHub release checks; the local protection core remains offline.
- App state and biometric cooldown counters remain in private app storage.
- Device PIN, pattern and password are verified only by Android Keyguard; the app never reads, stores, logs or transmits credentials.
- Backup is disabled in the manifest.
- No Accessibility service, overlay-based key interception, root exploit or hidden persistence is used.

## State integrity
- State transitions are serialized through ProtectionStore.
- Triggered state is monotonic against charger events and owner monitoring opt-out; only a successful app-launched Android device-credential confirmation or BIOMETRIC_STRONG prompt clears it. Merely unlocking the phone never clears the alarm. The separate motion alarm is opt-in and can trigger while charging. Explicitly disabling monitoring can disarm non-triggered states and cancels their pending deadline.
- State and throttle are synchronously persisted.
- Countdown uses elapsedRealtime plus a verified boot count. BOOT_COMPLETED explicitly invalidates the previous charger baseline even if Settings.Global.BOOT_COUNT is unavailable. If the first post-boot charger sample is unknown, a dedicated pending marker retries until power is known; the first confirmed disconnected sample starts a fresh 10-second grace period. If connected, protection returns to ARMED. An old-boot monotonic deadline is never compared to wall-clock time.
- AlarmDeadlineReceiver rechecks the deadline and reschedules early delivery.
- Exact alarm scheduling handles permission revocation races and falls back to inexact delivery when exact access is unavailable.

## Authentication
- Device PIN, pattern and password are verified only by Android Keyguard.
- Fingerprint and face are accepted only through BIOMETRIC_STRONG.
- Five consecutive biometric failure callbacks start a persisted 30-second cooldown. After it expires, another five failures can start another cooldown.
- Cancellation is not a failed attempt and never clears the alarm.
- Android does not disclose incorrect device-credential attempts to third-party apps; Keyguard's own lockout applies.
- Only the result of the app-launched Android device-credential confirmation activity or a successful BIOMETRIC_STRONG prompt clears ALARM_TRIGGERED through one shared coordinator. `ACTION_USER_PRESENT` and keyguard locked-to-unlocked polling are not proof of credential entry: trusted-unlock flows can dismiss the keyguard without a fresh PIN or biometric.

## Audio and lockscreen
- The packaged Ogg Opus default and owner-selected audio use MediaPlayer; five generated built-in presets use ToneGenerator. The complete built-in choice set is six entries (packaged default plus five generated presets). If selected and packaged audio both fail, repeating vibration is the fallback.
- Audio uses USAGE_ALARM and requests maximum available alarm-stream volume.
- Prior alarm volume is saved and restored after successful disarm. If service is destroyed while alarm remains latched, it does not restore volume prematurely. Corrupt saved-volume metadata is removed and reported; the original value cannot be reconstructed from a malformed preference.
- While AlarmService is active, the alarm stream maximum is reasserted every 500 ms; this is best-effort volume control, not hardware-key interception.
- Notification is ongoing and opens the authentication Activity. The full-screen dashboard is optional, requires explicit owner opt-in, and on Android 14+ is attached only when `canUseFullScreenIntent()` confirms system access. If denied, the ordinary high-priority notification remains.
- DND is not silently changed. Users configure alarm exceptions in Android settings.
- Android/OEM policies can constrain sound output and background execution.

## Visual alarm flashlight
- AlarmService starts the optional LED output only after ProtectionStore confirms ALARM_TRIGGERED; countdown and ARMED states never pulse the LED.
- The alarm flashlight uses CameraManager.setTorchMode without CAMERA permission or a camera capture session. Separately, optional front-camera photo evidence requests CAMERA permission only after explicit owner opt-in; it captures one still only after successful owner authentication while the authentication Activity is visible. New photos are unencrypted Gallery JPEGs by default in the app-owned Pictures/ThiefAlertSeQure folder; Settings can enable Android Keystore AES-GCM encryption for future captures. Existing photos are not converted when the setting changes. Both stores are bounded to 20 images / 30 days / 2 MiB each; encrypted-vault photos are individually viewable/exportable/deletable, while Gallery photos are managed through Gallery and the app's scoped delete action. No camera foreground-service type, background camera capture, video, microphone capture or cloud upload is used.
- Flash hardware is declared optional in the manifest so Play does not require a flash unit for installation. Audio alarm remains the primary output and is not gated by flash availability.
- The default pulse is 700 ms on / 700 ms off (about 0.71 cycles per second), not a rapid strobe. Photosensitive users can disable it in Settings.
- Torch access is best-effort: camera contention, an unavailable camera service, thermal management, OEM policy or process termination can stop light output. stopAlarm() explicitly turns the torch off on authenticated disarm and service teardown. Android also may turn it off when the client exits.
- Physical verification of pulse timing, screen-off behavior, torch shutdown on authentication, camera contention and thermal behavior is required on each target device. CI cannot prove LED behavior.

## Android Enterprise / Device Owner mode
- Dedicated DeviceAdminReceiver is declared.
- App never self-assigns Device Owner.
- Only an already provisioned Device Owner can explicitly enable managed policies.
- Lock Task allowlist includes only this app.
- Lock Task features keep Keyguard available but omit GLOBAL_ACTIONS, hiding the normal power dialog while kiosk mode is active on supported Android builds.
- New managed policies restrict Settings factory reset, adding users and USB file transfer only. Safe Boot and USB debugging deliberately remain available as recovery paths. The controller snapshots admin-scoped local/global restriction values on Android 14+. Lock Task getters on Android 14+ expose resolved policy, not per-admin settings; rollback restores the captured DPM-visible baseline, but another management agent's separate contribution cannot be reconstructed exactly.
- In-app rollback marks recovery pending before attempting to exit Lock Task, restores each saved restriction and allowlist independently, and retains the snapshot for retry if any DevicePolicyManager operation fails. Corrupt/legacy snapshots use documented emergency navigation defaults.
- This does not block physical long-press shutdown, hardware key combinations, bootloader/recovery reset or OEM firmware controls.

## Android boot and Direct Boot behavior
The app listens for BOOT_COMPLETED and is not Direct-Boot-aware. Android normally delivers BOOT_COMPLETED after the first user unlock, so this app cannot promise protection before that unlock following a reboot.

Android 15+ prohibits a BOOT_COMPLETED receiver from directly starting a mediaPlayback foreground service for apps targeting API 35 and higher. The monitor service uses specialUse, which is not in Android 15's prohibited BOOT_COMPLETED FGS-type list. For a latched alarm, BootReceiver schedules a recovery alarm rather than directly starting mediaPlayback from BOOT_COMPLETED. Exact-alarm access is user-controlled; without it, an inexact fallback is attempted and playback timing / background FGS launch cannot be guaranteed.

## Compatibility
- Minimum SDK 26 (Android 8.0).
- Target SDK 36 (compileSdk 36; minSdk 26).
- Material Components UI adapts through a scrollable layout and SP text sizing.
- Biometric availability depends on hardware, enrollment and Android strength classification.
- Device Owner APIs and restrictions depend on Android version, provisioning state and OEM build.
- HONOR/MagicOS, Xiaomi/HyperOS, Samsung/One UI and other OEM battery managers may require user-side background/autostart configuration. No universal public API disables all OEM power management.
- Optional flash and front-camera hardware are declared non-required. Torch control does not request CAMERA permission. The separate photo-evidence feature requires explicit owner opt-in and runtime CAMERA permission, and runs only after successful owner authentication while the app is visible. Gallery mode additionally requests READ/WRITE external-storage permission only on API 26–28; Android 10+ uses app-owned MediaStore without broad media permissions. No camera foreground-service type is declared.

## Source-review correction — 11 November 2026

A final alarm recovery-path review found that `ProtectionMonitorService.startTheftAlarm()` scheduled an immediate recovery callback after a foreground-service launch failure but ignored the scheduler's `false` result. It now displays the existing `AlarmRecoveryNotice` when Android cannot provide exact-alarm recovery, so the owner is directed to reopen the app rather than relying only on potentially delayed background delivery. The change is in commit `807f26548cfa27059356dbe9a132c1e554739254` and awaits its current-head CI run.

## Physical verification record — 11 November 2026

**Device:** HONOR 200 Pro (device model from the project test record). **Android/MagicOS build:** not supplied; record it before claiming OS-version-specific compatibility. **Build under test:** v1.1.3 development/test build, versionCode 5, package `com.thiefalertsequre.app.v113test` (branch CI test identity). **Result source:** owner-reported physical test results; ChatGPT did not operate the handset or independently observe the tests.

| # | Physical test | Reported result |
|---:|---|---|
| 1 | Automatic arming | PASS |
| 2 | Charger-removal countdown | PASS |
| 3 | Reconnect cancellation | PASS |
| 4 | Alarm persistence | PASS |
| 5 | Authentication protection | PASS |
| 6 | Volume restoration | PASS |
| 7 | Flashlight independence | PASS |
| 8 | Camera conflict | PASS |
| 9 | Background operation | PASS |
| 10 | Reboot recovery | PASS |
| 11 | Lock-screen presentation | PASS |

No failure details were supplied. These results clear the reported HONOR 200 Pro functional test gate for the listed scenarios. They do not establish Android 17 compatibility, all Android/OEM versions, Play policy approval, Device Owner behavior on every build, or production signing/update migration.

## Remaining verification
1. Last fully verified non-instrumentation baseline: **PASS** (Android run [#1421](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43938339649) and CodeQL run [#25](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43938339708) on `9168d1d`). Later Phase 10–12 commits require fresh exact-head CI. Instrumentation compilation/execution and emulator matrices are prohibited by owner direction.
2. Android 8 through Android 17 compatibility paths: still pending where devices are unavailable; Android 17 background-audio hardening remains specifically unverified.
3. Target HONOR 200 Pro: the 11 scenarios above are owner-reported **PASS**; capture exact Android/MagicOS build and date-stamped evidence if available.
4. Opus decoding/loop and volume restoration: volume restoration is reported **PASS**; separate decoder-failure/vibration fallback test remains pending.
5. Screen locked/lock-screen presentation: reported **PASS**. Notification permission denied, DND, silent mode, Bluetooth and headphones remain separate cases unless explicitly included in the reported run.
6. Exact-alarm access granted/revoked and Android 15+ reboot recovery: reboot recovery is reported **PASS**; permission-revocation and no-exact-alarm fallback tests remain pending.
7. Process death, reboot and app update: reboot recovery is reported **PASS**; process-death and signed in-place update/migration tests remain pending. Treat force-stop as a platform boundary, not as a recoverable guarantee: Android can stop the package and suppress its scheduled work until the owner manually launches it again.
8. Device Owner enrollment and in-app rollback are user-reported PASS on a separate test device; record its Android build and repeat after managed-policy changes.
9. Flashlight independence and camera-conflict/audio continuity are reported **PASS** on the target device. Thermal behavior and extended-duration torch operation remain unverified.
10. Do not describe the app as unstoppable or production-certified; platform boundaries and the remaining release gates still apply.

## Lock-screen authentication regression safeguards

- `DevicePolicyManager.lockNow()` is idempotent per alarm episode, keyed to the persisted alarm transition timestamp. Repeated service starts must not relock an already-unlocked phone.
- `MainActivity` does not launch an app-owned biometric prompt while Android reports the device keyguard as locked; this avoids competing authentication surfaces.
- `AlarmService` does not treat `ACTION_USER_PRESENT`, `isDeviceSecure`, or an unlocked keyguard as authentication. After the phone is unlocked, the owner must open the app/notification and complete the explicit Android credential or strong-biometric prompt.
- The system credential and biometric remain entirely Android-managed. The app receives only the successful result from its explicit authentication activity/prompt and never accesses credential material.
- These changes were included in the owner-reported 11 November 2026 HONOR 200 Pro physical test set; lock-screen presentation and authentication protection were reported PASS. CI still cannot independently verify OEM keyguard behavior.


## 17 November 2026 — continuation security checks

- The `java/android/arbitrary-apk-installation` CodeQL finding was traced to the explicit Android Package Installer handoff. The updater verifies the app-private cached APK's SHA-256, production package ID, increasing versionCode and pinned release signer before the system installer is opened. The in-source suppression is placed at the exact `setDataAndType` sink, not at the Intent constructor. The PR CodeQL gate passed after this correction; the full Java/Kotlin and Actions analysis jobs also completed successfully.
- Manual signed release preflight is restricted to `main` only, consistent with the release policy. Manual preflight never publishes a release.
- Current task excludes emulator, instrumentation and physical-device testing. Non-instrumentation JVM/static/lint/build/APK checks are the required software verification.


## Current software verification snapshot — 2026-11-17

- Exact branch head `a1a567bf53f97189be2547ccf872ae665c30a54b`: Android workflow [#44005105554](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44005105554) and CodeQL workflow [#44005105442](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44005105442) both completed successfully.
- The Android workflow passed its instrumentation-disabled guard, JVM unit tests, source-contract checks, lint, APK assembly and package/version/signer metadata checks.
- The CodeQL Java/Kotlin source build and Actions analysis completed successfully. This verifies scan execution, not the absence or closure of every alert.
- The PR's single inline review thread referring to APK installation alert #8 is resolved. The underlying repository Code Scanning alert state could not be read through the available connector and remains **NOT VERIFIED**.
- The updater still revalidates its private-cache APK against SHA-256, production package ID, higher versionCode and pinned signer before Android installer handoff. The two sink suppressions are located immediately before the `setDataAndType` calls.
- Current branch comparison: 530 ahead / 0 behind; 131 changed paths (90 added, 41 modified, 0 deleted). PR #7 remains draft/open/unmerged.
- Permanent production signing secret availability and a production-signed APK are **NOT VERIFIED**. No secret values were accessed or exposed.
- No instrumentation compilation/execution, emulator testing or physical-device testing was performed.


## Exact-head software verification closeout — 2026-11-17

- Android run [#44010766603](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010766603) and CodeQL run [#44010774991](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010774991) passed on `e97817903976689f02cba8bb485fc160a7122fd3`.
- CodeQL completed Java/Kotlin and Actions analysis and uploaded SARIF successfully. This is not evidence that alert #8 is closed; the alert's active/closed state remains not verified through the available connector.
- Android CI passed the 30-file JVM unit suite, instrumentation-disabled guard, source checks, lint, asset checks, APK assembly and package/version/signer metadata checks.
- Branch comparison: 530 ahead / 0 behind, 131 changed paths (90 added, 41 modified, 0 deleted). PR #7 is draft/open/unmerged and mergeable.
- Permanent production signing-secret availability and production-signed APK availability remain not verified.
- No instrumentation compilation/execution, emulator or physical-device testing was performed.


## Final verified candidate snapshot — 2026-11-17

- Android [#44010766603 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010766603) and CodeQL [#44010774991 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010774991) passed on `e97817903976689f02cba8bb485fc160a7122fd3`.
- CodeQL Java/Kotlin and Actions analysis completed; SARIF upload succeeded. This does not prove alert #8 is closed; alert state remains not verified.
- Android workflow passed 30 JVM unit tests, instrumentation-disabled guard, source checks, lint, assets, APK build, metadata/package/version/signer checks and artifact upload.
- Branch comparison before this one docs-only refresh: 530 ahead / 0 behind; after it: 531 ahead / 0 behind; 131 changed paths and no deletions.
- Production signing-secret presence and production-signed APK availability remain not verified.
- No instrumentation compilation/execution, emulator or physical-device testing was performed.


## Authoritative latest security/build verification — 2026-11-17

- Exact candidate `524754f683d43450c844bca05147e66c15baf3d6`: Android [#44011669247 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44011669247) and CodeQL [#44011663424 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44011663424).
- CodeQL Java/Kotlin and Actions analysis completed successfully. This does not prove CodeQL alert #8 is closed; the alert record remains **NOT VERIFIED** because the available GitHub connection cannot read that alert record.
- Android CI passed 30 JVM unit-test source files, instrumentation-disabled guard, source-contract checks, lint, assets, APK build/metadata and package/version/signer checks.
- The uploaded debug artifact is not production-signed. Permanent signing-secret availability and a production-signed APK remain **NOT VERIFIED**.
- Branch comparison: 531 ahead / 0 behind, 131 changed paths (90 added, 41 modified, 0 deleted). PR #7 remains draft/open/unmerged.
- No instrumentation, emulator or physical-device test was performed.


## Distribution-channel policy review — 24 November 2026

- The current updater checks GitHub Releases and launches the Android package installer using `REQUEST_INSTALL_PACKAGES`. This is a direct APK/sideload update path.
- Google Play policy does not permit an ordinary app to use `REQUEST_INSTALL_PACKAGES` for self-updating; the stated exception is qualifying enterprise device-management use. If Google Play distribution is intended, replace the GitHub APK self-update path with Play-managed updates or a separate Play-compatible build before submission: https://support.google.com/googleplay/android-developer/answer/16559646
- The app declares a `specialUse` foreground service for persistent user-enabled protection monitoring. A Play release requires the appropriate foreground-service declaration and justification; full-screen-intent and battery-optimization exemptions also require policy review and must remain user-controlled: https://support.google.com/googleplay/android-developer/answer/16559646
- The distribution channel has not been selected in this audit. No Play-compliance claim is made.
