# Security and Android Platform Audit

## Threat model
Protect against casual charger removal and ordinary user-level interference. This app is not a device-owner, kiosk, system, or privileged application. It cannot defend against force shutdown, uninstall/disable, permission revocation, recovery boot, battery removal, or privileged tools.

## State integrity
- State transitions are serialized through a process-wide lock in ProtectionStore.
- Triggered state is monotonic against charger events; only successful Android owner authentication clears it.
- State and authentication cooldown are synchronously persisted in private app preferences.
- Countdown uses elapsedRealtime and Android boot count while the device remains in the same boot, so wall-clock changes cannot accelerate or extend the active countdown.
- After reboot, the persisted wall-clock deadline is used because elapsedRealtime resets at boot.
- AlarmDeadlineReceiver rechecks the deadline before triggering and reschedules early deliveries.

## Authentication
- Device PIN, pattern, and password are verified only by Android Keyguard.
- Fingerprint and face are accepted only through BIOMETRIC_STRONG.
- Five consecutive biometric failure callbacks start a persisted 30-second cooldown. After it expires, the next five failures can start another cooldown.
- Authentication cancellation is not counted as a biometric failure.
- Android does not disclose incorrect device-credential attempts to third-party apps. Keyguard's own anti-brute-force lockout applies to PIN/pattern/password. The app does not collect credentials.
- Only a successful Keyguard or BiometricPrompt result can clear ALARM_TRIGGERED.

## Audio and lockscreen
- Uses the exact user-uploaded Ogg Opus audio asset in res/raw and loops it with MediaPlayer.
- Uses ToneGenerator only if the platform decoder cannot load the supplied Opus file.
- Requests the maximum available alarm stream volume; Android/OEM policy can still constrain output.
- Alarm notification is ongoing and its content action opens the lockscreen-capable authentication Activity.
- The Activity is configured to show when locked and turn the screen on.
- Audio playback uses USAGE_ALARM; DND alarm exceptions remain controlled by Android settings.
- No claim is made for 105% volume, global volume-key interception, or global power-menu blocking.

## Permissions and privacy
- No INTERNET permission, backend, analytics or advertising SDK.
- Notifications are requested at runtime on Android 13+.
- Exact-alarm special access is user-controlled on Android 12+; inexact fallback may delay the nominal deadline.
- A permanent wake lock is not held while merely armed. MediaPlayer uses a playback wake mode while the alarm is playing.

## Remaining verification
1. Confirm GitHub Actions passes on the latest branch head.
2. Install and exercise on physical Android 14/15/16 devices.
3. Verify exact Opus decode, loop and fallback behavior.
4. Test all device credential and strong biometric paths.
5. Test the five-failure cooldown repeatedly and cancellation semantics.
6. Test alarm behavior with screen locked, DND, silent mode, Bluetooth and headphones.
7. Test app process death, force-stop limitations, reboot, exact-alarm access revocation and OEM battery management.
8. Do not describe the application as unstoppable or production-certified until device tests pass.
