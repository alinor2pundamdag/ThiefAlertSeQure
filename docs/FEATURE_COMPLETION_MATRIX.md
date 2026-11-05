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
| Reboot recovery | If countdown is pending and charger remains absent, trigger immediately; if charging, return to ARMED |
| Android 15 boot recovery | Schedules exact alarm when user granted exact-alarm access; exact-alarm delivery is an allowed FGS-start path |
| Local-only operation | No INTERNET permission, backend, analytics or advertising SDK |\n| Background protection monitor | User-enabled, stoppable foreground service with persistent notification; uses supported specialUse FGS declaration |\n| Local event export | CSV export through Android's system document picker |
| Alarm foreground notification | Ongoing notification opens lockscreen-capable authentication Activity |
| First-launch privacy notice | Accept / decline choices are recorded distinctly; both continue to the same essential local protection functions |
| Dedicated Settings screen | Android alarm, notification, battery/background, DND, app-info and privacy-notice controls |
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
The five-failure / 30-second app cooldown applies to biometric failures reported through AndroidX BiometricPrompt. Device PIN/pattern/password remains available during that app cooldown; Android Keyguard independently enforces credential lockout. Android does not disclose incorrect device PIN/pattern/password attempts to ordinary apps. Android Keyguard enforces its own credential lockout. The app never collects, stores, logs or transmits device credentials.

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
4. Verify selected audio URI survives reboot, decodes, previews and loops; verify vibration fallback on missing/corrupt audio.\n5. Verify foreground monitor, motion sensitivity, battery threshold crossing and stop action on physical devices.
6. Verify icon and label before and after installation.
7. Verify charger events, ten-second timing with monitor enabled, AlarmManager backup, reconnect and reboot.
8. Verify PIN, pattern, password, fingerprint and supported strong face.
9. Verify repeated five-failure / 30-second cooldown and cancellation.
10. Verify volume restoration, DND, silent mode, notification denial and battery settings.
11. On a factory-reset test device only, verify Device Owner enrollment, lock-task restrictions and in-app rollback.
12. Complete Google Play foreground-service declaration for specialUse; approval is not guaranteed. Keep the PR unmerged until CI and physical-device gates pass.
