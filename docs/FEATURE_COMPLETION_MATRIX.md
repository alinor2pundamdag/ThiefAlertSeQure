# ThiefAlertSeQure — Feature Completion Matrix

## Implemented in this branch

| Feature | Implementation |
|---|---|
| Installed app name | Exact label ThiefAlertSeQure |
| Original launcher icon | Original JPG copied without image edits |
| Original alarm sound | Original Soundfizz Ogg Opus asset in res/raw, looping MediaPlayer |
| Audio fallback | ToneGenerator if platform decoder cannot load Opus |
| Alarm volume | Saves prior alarm-stream volume, raises to device maximum during alarm, restores after successful disarm |
| Charger auto-arm | PowerReceiver |
| 10-second unplug countdown | ProtectionEngine + AlarmScheduler |
| Reconnect before deadline | Returns to ARMED |
| Reconnect after trigger | Cannot clear ALARM_TRIGGERED |
| Same-boot clock integrity | elapsedRealtime + boot count |
| Reboot recovery | Persisted state + wall-clock fallback |
| Android 15 boot recovery | Schedules exact alarm when user granted exact-alarm access; exact-alarm delivery is an allowed FGS-start path |
| Local-only operation | No INTERNET permission, backend, analytics or advertising SDK |
| Alarm foreground notification | Ongoing notification opens lockscreen-capable authentication Activity |
| Premium adaptive UI | Material 3 dark theme, responsive scroll layout, high-contrast status states, large touch targets and explicit setup controls |
| Device PIN / pattern / password | Android Keyguard credential confirmation; credentials are never read or stored |
| Fingerprint / face | BIOMETRIC_STRONG only, if enrolled and supported |
| Five failed biometric attempts | Persisted counter; fifth consecutive failure starts 30-second cooldown |
| Repeated cooldown | Counter resets after cooldown; each subsequent five failures starts another cooldown |
| Cancel authentication | Does not count as failure and never clears alarm |
| Successful authentication | Clears triggered state and restores prior alarm volume |
| Optional Device Owner mode | DevicePolicyManager + Lock Task Mode; requires explicit Android Enterprise enrollment and opt-in |
| Managed power dialog restriction | Omits LOCK_TASK_FEATURE_GLOBAL_ACTIONS while app is in Lock Task Mode on supported builds |
| Managed device restrictions | Settings factory reset, safe boot, USB file transfer, add user, debugging |
| Managed rollback | In-app exit control stops Lock Task and clears applied restrictions |
| Unit tests | Protection state machine and authentication throttle |

## Authentication boundary
The five-failure / 30-second app cooldown applies to biometric failures reported through AndroidX BiometricPrompt. Android does not disclose incorrect device PIN/pattern/password attempts to ordinary apps. Android Keyguard enforces its own credential lockout. The app never collects, stores, logs or transmits device credentials.

Strong face authentication is available only if Android reports the enrolled face modality as BIOMETRIC_STRONG. Weak face unlock is intentionally not accepted.

## Permission and reliability rules
- Notification permission is requested on Android 13+; denial degrades gracefully.
- Exact-alarm special access is user-controlled on Android 12+. Without it, the app uses an inexact fallback and the ten-second deadline may be delayed.
- Android 15+ blocks a mediaPlayback foreground service launched directly from BOOT_COMPLETED. The app now schedules a user-authorized exact alarm for recovery where exact-alarm access is granted. Without that access, immediate playback after reboot is not guaranteed.
- DND is not changed by the app. Alarm audio uses USAGE_ALARM; users configure alarm exceptions in Android sound/DND settings.
- Maximum alarm volume means the device's reported maximum alarm-stream index, not 105% output. Prior volume is restored after successful disarm.
- Standard mode cannot block the power menu, hardware forced shutdown, global volume keys, force-stop, uninstall, or recovery reset.
- Managed mode can hide the normal global-actions dialog while Lock Task Mode is active and restrict selected Settings actions. It cannot defeat hardware shutdown, bootloader/recovery, battery removal, or firmware controls.
- Device Owner enrollment is a deliberate enterprise provisioning operation, not a runtime permission and not something the app can self-grant.

## Release gates
1. Latest GitHub Actions unit tests pass.
2. Latest debug APK assembles.
3. Install on physical Android 14/15/16 devices.
4. Verify exact Opus asset decodes and loops on each test device.
5. Verify icon and label before and after installation.
6. Verify charger events, exact ten-second timing, reconnect and reboot.
7. Verify PIN, pattern, password, fingerprint and supported strong face.
8. Verify repeated five-failure / 30-second cooldown and cancellation.
9. Verify volume restoration, DND, silent mode, notification denial and battery settings.
10. On a factory-reset test device only, verify Device Owner enrollment, lock-task restrictions and in-app rollback.
11. Keep the PR unmerged until CI and physical-device gates pass.
