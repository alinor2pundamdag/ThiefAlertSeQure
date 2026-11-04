# ThiefAlertSeQure

ThiefAlertSeQure is a local-only Android charging anti-theft alarm. The installed application name is **ThiefAlertSeQure**.

## Local protection behavior
- Automatically arms when charging is connected.
- Charger removal starts a 10-second countdown.
- Reconnecting before the deadline cancels only that countdown.
- Once triggered, the alarm remains latched until Android confirms owner authentication.
- State transitions are serialized and persisted locally.
- A monotonic elapsed-realtime deadline protects the countdown against wall-clock changes during the same boot; reboot recovery uses the persisted wall-clock deadline and boot count.

## Authentication
- Device PIN, pattern, and password are verified by Android Keyguard.
- Fingerprint and face are offered only when Android reports an enrolled BIOMETRIC_STRONG modality.
- Five consecutive biometric failures start a persisted 30-second cooldown. After cooldown, the count resets and another group of five failures can trigger another cooldown.
- Cancelled authentication and failed authentication never clear the alarm.
- Android does not disclose failed PIN/pattern/password attempts to ordinary apps. Keyguard applies its own credential lockout; the app never reads or stores the device credential.

## Exact user assets
- Launcher icon: original ThiefAlertSeQureIcon.jpg, copied without image edits to app/src/main/res/mipmap-nodpi/thiefalertsequreicon.jpg.
- Alarm: original Soundfizz .opus recording, copied to app/src/main/res/raw/thiefalertsequre_alarm.opus.
- App label: ThiefAlertSeQure.

## Build and test
gradle :app:testDebugUnitTest :app:assembleDebug

CI must pass and physical-device verification is still required before release. Android does not allow an ordinary app to block the power menu, guarantee unkillable background operation, globally disable volume keys, force 105% volume, or guarantee immediate alarm playback from a BOOT_COMPLETED receiver on Android 15+.
