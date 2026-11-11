<div align="right">
  <a href="../README.md"><img src="../ThiefAlertSeQureIcon.jpg" alt="ThiefAlertSeQure icon" width="64" /></a>
</div>

[← Back to ThiefAlertSeQure overview](../README.md)

---

# Security and Android Platform Audit

## Android 17 background audio compatibility

Android 17 (API 37) applies background-audio hardening to all apps running on that OS, even if they target API 36. Audio playback, audio-focus requests, and volume changes must occur while an Activity is visible or while a foreground service other than SHORT_SERVICE is active. ThiefAlertSeQure performs alarm playback and alarm-volume enforcement inside AlarmService, declared as a mediaPlayback foreground service. The separate charger monitor is specialUse and does not own alarm playback.

For apps targeting API 37, Android adds while-in-use foreground-service capability requirements, with an exception when the app has exact-alarm access and interacts with USAGE_ALARM streams. The current app targets API 36. Exact-alarm access denial can delay the countdown callback and may prevent a background-started alarm service from starting; the inexact fallback is best-effort and is not an audible-alarm guarantee.

Android 17 test command: `adb shell cmd audio set-enable-hardening throw`. Trigger the alarm with the app backgrounded and screen off, then inspect logcat / dumpsys audio for AudioHardening failures. Disable the test override afterward with `adb shell cmd audio set-enable-hardening disable`. This test remains pending on real Android 17 hardware/emulator.

## GitHub repository security automation

Checked on 11 November 2026:
- Secret scanning: enabled.
- Secret scanning push protection: enabled.
- Open secret-scanning alerts: none returned.
- Dependabot vulnerability alerts: enabled; no open alerts returned.
- Dependabot automated security updates: enabled. GitHub may open dependency update pull requests; these are not auto-merged.
- Code scanning: no completed analysis was available at the time of this check; static code scanning remains a separate follow-up.

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
- Packaged Ogg Opus alarm asset is played in a loop through MediaPlayer. If selected and packaged audio both fail, repeating vibration is the fallback; there is no ToneGenerator implementation.
- Audio uses USAGE_ALARM and requests maximum available alarm-stream volume.
- Prior alarm volume is saved and restored after successful disarm. If service is destroyed while alarm remains latched, it does not restore volume prematurely. Corrupt saved-volume metadata is removed and reported; the original value cannot be reconstructed from a malformed preference.
- While AlarmService is active, the alarm stream maximum is reasserted every 500 ms; this is best-effort volume control, not hardware-key interception.
- Notification is ongoing and opens the authentication Activity. The full-screen dashboard is optional, requires explicit owner opt-in, and on Android 14+ is attached only when `canUseFullScreenIntent()` confirms system access. If denied, the ordinary high-priority notification remains.
- DND is not silently changed. Users configure alarm exceptions in Android settings.
- Android/OEM policies can constrain sound output and background execution.

## Visual alarm flashlight
- AlarmService starts the optional LED output only after ProtectionStore confirms ALARM_TRIGGERED; countdown and ARMED states never pulse the LED.
- The implementation uses CameraManager.setTorchMode, which controls the torch without opening a camera capture session. No CAMERA permission, camera foreground-service type, photo, video, microphone or network access is added.
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
- Optional flash hardware is declared non-required; the torch is controlled without CAMERA permission, camera capture, or a camera foreground-service type.

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
1. Last completed successful CI run: **PASS** (run 43004454206 on head `4dc3a7a553e7da43265cbd2feaa4c3e5ae64d294`). Latest branch head `807f26548cfa27059356dbe9a132c1e554739254` is undergoing fresh CI in run 43009188681; do not treat the latest head as CI-verified until that run completes.
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
