# ThiefAlertSeQure

A local-only Android charging anti-theft alarm. Installed application name: **ThiefAlertSeQure**.

## Core protection rules

- Launch the app once after installation and complete the system-access checklist.
- Connecting a charger automatically changes protection to ARMED.
- The independent motion alarm is OFF by default because it can trigger while the charger remains connected; enable it explicitly only if that behavior is wanted.
- Disconnecting while ARMED starts a 10-second countdown.
- Reconnecting before the deadline returns the state to ARMED.
- Rebooting during COUNTDOWN requires a fresh live charger-state check. If still disconnected, a new full 10-second grace period starts; if connected, protection returns to ARMED. An unknown charger or boot state never expires the old deadline.
- At the deadline, ALARM_TRIGGERED is latched. Reconnecting the charger cannot clear it.
- Only successful Android Keyguard device-credential verification or AndroidX BIOMETRIC_STRONG authentication clears the triggered state.
- Cancelled and failed authentication never clear the alarm. Explicitly disabling background monitoring cancels a pending countdown and disarms protection, but cannot clear an active alarm.
- Five consecutive biometric failures start a 30-second cooldown. After the cooldown, a new group of five failures can start another cooldown.
- Android controls incorrect PIN, pattern and password lockout. The app never reads or stores device credentials.

## Premium UI and device compatibility

- Material 3 dark interface with a responsive scroll layout, high-contrast protection states and large touch targets.
- Minimum supported Android: API 26 / Android 8.0.
- Target Android API: 35.
- Fingerprint and face are available only when Android reports an enrolled BIOMETRIC_STRONG authenticator. Hardware, OEM and Android-version differences are expected.
- Exact alarm access is user-controlled on Android 12+. Without it, the fallback alarm can be delayed.
- Boot recovery runs after Android delivers BOOT_COMPLETED (normally after the first device unlock). The app is not Direct-Boot-aware, so it cannot guarantee charger monitoring or alarm playback before that first unlock. Android 15+ recovery uses an exact alarm when the user has granted exact-alarm access; without it, immediate background audio is not guaranteed.
- Open the app after installation; grant notifications and Alarms & reminders access where requested/needed. Review app battery/background settings and OEM auto-start settings if available.

## Optional Android Enterprise mode

A fully managed Device Owner enrollment can enable Lock Task Mode. While kiosk mode is active on supported Android builds, the app omits the global-actions power dialog and applies selected Device Owner restrictions.

This requires intentional Android Enterprise provisioning, usually on a new or factory-reset test device. The app cannot self-grant Device Owner status. Read [docs/DEVICE_OWNER_MODE.md](docs/DEVICE_OWNER_MODE.md) before enrollment.

Managed mode does not block hardware forced shutdown, battery removal, bootloader/recovery reset, or OEM firmware controls. The in-app exit control removes the managed policies.

## First launch and Settings

- First launch presents an in-app privacy notice with separate Accept & continue and Decline & continue choices. Both continue to the same essential local protection dashboard; the recorded choice is not represented as consent when declined.
- A dedicated Settings screen exposes Android exact-alarm, notification, battery/background, DND and app-information settings, plus a way to review the privacy notice again.

## Privacy

- No INTERNET permission.
- No backend, account, analytics, advertising SDK or cloud storage.
- Device credentials are verified by Android Keyguard and are never collected, stored, logged or transmitted.
- The original icon and alarm audio are stored locally in the APK.

## Build and test

    ./gradlew :app:testDebugUnitTest :app:assembleDebug

CI uploads a debug APK only after unit tests and assembly pass. A passing CI build is not a substitute for physical-device tests.

## Documentation

- [Feature completion matrix](docs/FEATURE_COMPLETION_MATRIX.md)
- [Protection rules and state machine](docs/PROTECTION_RULES.md)
- [Security and Android platform audit](docs/SECURITY_AND_PLATFORM_AUDIT.md)
- [Device Owner mode and rollback](docs/DEVICE_OWNER_MODE.md)
- [Privacy policy](docs/PRIVACY_POLICY.md)
