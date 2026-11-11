<div align="right">
  <a href="../README.md"><img src="../ThiefAlertSeQureIcon.jpg" alt="ThiefAlertSeQure icon" width="64" /></a>
</div>

[← Back to ThiefAlertSeQure overview](../README.md)

---

# ThiefAlertSeQure — Feature Completion Matrix

> **Official successor baseline: v1.1.3 (versionCode 5).** This release line preserves the completed v1.1.2 feature set; its intended functional addition is the synchronized rear-LED flashlight during a latched alarm. No v1.1.2 protection, alarm, authentication, privacy, settings, history, or managed-device feature is intentionally removed. Flashlight availability is optional and cannot block audio.


## Implemented in this branch

| Feature | Implementation |
|---|---|
| Installed app name | Exact label ThiefAlertSeQure |
| Original launcher icon | Original JPG copied without image edits |
| Original alarm sound | Packaged Ogg Opus asset in res/raw, looping MediaPlayer (resource filename must use `.ogg`) |
| Audio fallback | Repeating vibration if selected and packaged audio both fail |
| Alarm volume | Saves prior alarm-stream volume, raises to device maximum during alarm, restores after successful disarm; malformed saved-volume metadata is detected and removed rather than crashing recovery (the original value cannot be reconstructed if corrupted) |
| Charger auto-arm | Runtime-registered PowerTransitionReceiver from the user-enabled foreground monitor plus persisted connected-baseline validation |
| 10-second unplug countdown | ProtectionEngine + AlarmScheduler |
| Reconnect before deadline | Returns to ARMED |
| Reconnect after trigger | Cannot clear ALARM_TRIGGERED |
| Same-boot clock integrity | elapsedRealtime + boot count |
| Reboot recovery | Revalidate charger state; if connected, return to ARMED; if disconnected, start a fresh 10-second grace period; if the first post-boot power sample is unknown, retain a short retry marker and start the full grace period only after a live disconnected sample; this also works when BOOT_COUNT is unavailable |
| Android 15 boot recovery | After BOOT_COMPLETED/first user unlock, schedules exact alarm when granted; no Direct Boot protection before first unlock |
| Local-first protection | Alarm and protection logic operate locally/offline; INTERNET is used only for owner-initiated GitHub release checks. No backend, analytics or advertising SDK |
| Background protection monitor | User-enabled, stoppable foreground service with persistent notification; disabling cancels pending countdown and disarms but cannot clear a latched alarm |
| Independent motion alarm | Explicit opt-in, OFF by default; can trigger while charger remains connected; only monitored while ARMED |
| Local event export | CSV export through Android's system document picker |
| Alarm foreground notification | Ongoing high-priority alarm notification; optional full-screen dashboard only when owner-enabled and Android full-screen access is granted; otherwise normal notification fallback |
| Synchronized camera LED alert | Optional slow rear-LED pulses only while ALARM_TRIGGERED; default ON, owner can disable it; stops on authenticated disarm; no CAMERA permission or image/video capture; unavailable hardware never blocks audio |
| First-launch privacy notice | Notice is shown until the owner acknowledges it; one “I understand & continue” action proceeds to the dashboard; no Decline button |
| Dedicated Settings screen | Android alarm, notification, battery/background, DND, app-info and privacy-notice controls |
| Premium adaptive UI | Material 3 dark theme, responsive scroll layout, high-contrast status states, large touch targets and explicit setup controls |
| Device PIN / pattern / password | Explicit app-launched Android device-credential confirmation; credentials are never read or stored; ordinary phone unlock alone does not stop the alarm |
| Fingerprint / face | BIOMETRIC_STRONG only, if enrolled and supported |
| Five failed biometric attempts | Persisted counter; fifth consecutive failure starts 30-second cooldown |
| Repeated cooldown | Counter resets after cooldown; each subsequent five failures starts another cooldown |
| Cancel authentication | Does not count as failure and never clears alarm |
| Successful authentication | Clears triggered state and restores prior alarm volume |
| Optional Device Owner mode | DevicePolicyManager + Lock Task Mode; requires explicit Android Enterprise enrollment and opt-in |
| Managed power dialog restriction | Omits LOCK_TASK_FEATURE_GLOBAL_ACTIONS while app is in Lock Task Mode on supported builds |
| Managed device restrictions | Settings factory reset, add user and USB file transfer; Safe Boot and USB debugging remain available as recovery paths |
| Managed rollback | In-app recovery restores the saved pre-enable policy; damaged/legacy snapshots trigger emergency navigation defaults and retain a retry path if any DPM call fails |
| Unit tests | Protection state machine, authentication throttle, and recognized OEM-family guidance including safe generic fallbacks |

## Authentication boundary
The five-failure / 30-second app cooldown applies to biometric failures reported through AndroidX BiometricPrompt. Device PIN/pattern/password remains available during that app cooldown; Android Keyguard independently enforces credential lockout. Android does not disclose incorrect device PIN/pattern/password attempts to ordinary apps. Android Keyguard enforces its own credential lockout. The app never collects, stores, logs or transmits device credentials. `ACTION_USER_PRESENT`, `isDeviceSecure`, and keyguard dismissal are not accepted as proof of a fresh credential; the owner must complete the explicit app-launched credential or strong-biometric prompt.

Strong face authentication is available only if Android reports the enrolled face modality as BIOMETRIC_STRONG. Weak face unlock is intentionally not accepted.

## Permission and reliability rules
- Notification permission is requested on Android 13+; denial degrades gracefully.
- Exact-alarm special access is user-controlled on Android 12+. Without it, the app uses an inexact fallback and the ten-second deadline may be delayed.
- Android 15+ blocks a mediaPlayback foreground service launched directly from BOOT_COMPLETED. The app now schedules a user-authorized exact alarm for recovery where exact-alarm access is granted. Without that access, immediate playback after reboot is not guaranteed.
- DND is not changed by the app. Alarm audio uses USAGE_ALARM; users configure alarm exceptions in Android sound/DND settings.
- Maximum alarm volume means the device's reported maximum alarm-stream index, not 105% output. Prior volume is restored after successful disarm.
- The visual alarm uses CameraManager.setTorchMode without CAMERA permission or camera capture. The flash feature is optional hardware, slow-pulsed (700 ms on / 700 ms off), user-disableable, and independent of audio. Camera contention, thermal/OEM policy or missing flash can prevent light output; no universal torch guarantee is possible.
- Flashing light may affect photosensitive users. The owner can disable the visual alert in Settings; the feature does not strobe at high frequency.
- Standard mode cannot block the power menu, hardware forced shutdown, global volume keys, force-stop, uninstall, or recovery reset.
- Managed mode can hide the normal global-actions dialog while Lock Task Mode is active and restrict selected Settings actions. It cannot defeat hardware shutdown, bootloader/recovery, battery removal, or firmware controls.
- Device Owner enrollment is a deliberate enterprise provisioning operation, not a runtime permission and not something the app can self-grant.

## Release gates
1. Latest GitHub Actions unit tests pass.
2. Latest debug APK assembles.
3. Install on physical Android 8–17 devices where available; prioritize API 26, 31–36, and Android 17 audio hardening.
4. Verify selected audio URI survives reboot, decodes, previews and loops; verify vibration fallback on missing/corrupt audio.
5. Verify foreground monitor, motion sensitivity, battery threshold crossing and stop action on physical devices.
6. Verify icon and label before and after installation.
7. Verify charger events, ten-second timing with monitor enabled, AlarmManager backup, reconnect and reboot.
8. Verify PIN, pattern, password, fingerprint and supported strong face.
9. Verify repeated five-failure / 30-second cooldown and cancellation.
10. Verify volume restoration, DND, silent mode, notification denial, battery settings, and full-screen dashboard with Android 14+ access both granted and denied.
11. Device Owner enrollment, lock-task restrictions and in-app rollback are user-reported PASS on a separate test device; record its Android build and repeat after managed-policy changes.
12. Complete Google Play foreground-service declaration for specialUse; approval is not guaranteed. The four permanent release-signing secrets are configured, but no signed production APK or Play approval exists yet. Branch CI intentionally uploads only the isolated debug test APK. Keep the PR unmerged until source review and fresh CI pass; the 11 HONOR 200 Pro physical scenarios are owner-reported PASS, while Android 17 and the remaining platform edge cases stay pending.
