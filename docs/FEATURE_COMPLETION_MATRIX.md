# ThiefAlertSeQure — Feature Completion Matrix

## Implemented in this branch

| Feature | Implementation |
|---|---|
| Installed app name | ThiefAlertSeQure in AndroidManifest |
| Exact launcher icon | Original ThiefAlertSeQureIcon.jpg copied byte-for-byte to mipmap-nodpi |
| Exact alarm sound | Original uploaded Soundfizz .opus copied to res/raw and looped by MediaPlayer |
| Unsupported Opus fallback | ToneGenerator fallback if platform decoder cannot load the supplied file |
| Charger auto-arm | PowerReceiver |
| 10-second unplug countdown | ProtectionEngine + AlarmScheduler |
| Reconnect before deadline | Returns to ARMED |
| Reconnect after trigger | Cannot clear ALARM_TRIGGERED |
| Wall-clock manipulation resistance | elapsedRealtime deadline + boot count while same boot |
| Reboot recovery | Persisted state + wall-clock fallback |
| Local-only operation | No INTERNET permission or backend |
| Alarm foreground notification | Ongoing, lockscreen-visible notification opens authentication Activity |
| Device PIN / pattern / password | Android Keyguard credential confirmation |
| Fingerprint / face | BIOMETRIC_STRONG only, when enrolled and supported by device |
| Five failed biometric attempts | Persisted attempt counter; fifth failure starts 30-second cooldown |
| Repeated cooldown | Counter resets after cooldown; another five failures start another 30-second cooldown |
| Cancel authentication | Does not count as failure and never clears alarm |
| Failed authentication | Never clears alarm |
| Successful authentication | Clears triggered state and stops alarm |
| State transition serialization | Shared process lock + synchronous preference commits |
| Unit tests | State machine, monotonic clock, authentication throttle |
| App icon and sound asset integration | Source assets preserved without editing |

## Important authentication boundary

The five-failure / 30-second app cooldown applies to biometric failures reported through AndroidX BiometricPrompt. Android does not disclose incorrect device PIN, pattern, or password attempts to ordinary apps. Device credentials are verified by Android Keyguard, which applies its own lockout policy. The app never reads, stores, logs, or transmits device credentials. Cancellation never clears the alarm and is not counted as a biometric failure.

Strong face authentication is available only if Android reports the enrolled face modality as BIOMETRIC_STRONG. Weak face unlock is intentionally not accepted.

## Android platform limitations

- An ordinary app cannot block the global power menu, guarantee survival against force-stop/uninstall, globally disable hardware volume keys, or force 105% volume.
- Android 15+ restricts starting a mediaPlayback foreground service from BOOT_COMPLETED. The triggered state remains latched, but immediate background audio after reboot cannot be guaranteed by an ordinary app.
- Exact alarm delivery depends on special access. Without it, Android may delay the inexact fallback.
- DND behavior depends on Android alarm exceptions and device settings.
- The app uses a show-when-locked Activity and a public ongoing notification, not an un-dismissible system-owned overlay.

## Release gates

1. Latest GitHub Actions run must pass unit tests and debug APK assembly.
2. Install and test on the target HONOR device.
3. Verify the supplied Opus file decodes and loops on the target Android version.
4. Verify the original icon and exact app label before and after installation.
5. Verify charger events, deadline, reconnect, process death and reboot.
6. Verify PIN, pattern, password, fingerprint and supported strong face authentication.
7. Verify five biometric failures, 30-second cooldown, reset, and second cooldown cycle.
8. Verify cancellation and unsuccessful authentication never stop the alarm.
9. Verify lockscreen, notification, DND, volume and OEM battery behavior.
10. Keep the PR unmerged until all gates are passed.
