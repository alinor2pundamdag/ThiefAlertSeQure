# Security and Android Platform Audit

## Threat model
Protect against casual charger removal and ordinary user-level interference. Standard mode is a normal Android app, not a system, privileged or device-owner app. Optional managed mode uses legitimate Android Enterprise Device Owner provisioning and Lock Task APIs. Neither mode can promise resistance to hardware forced shutdown, battery removal, bootloader/recovery, firmware changes or physical tampering.

## Local privacy and data
- No INTERNET permission, backend, analytics, ad SDK or account.
- App state and biometric cooldown counters remain in private app storage.
- Device PIN, pattern and password are verified only by Android Keyguard; the app never reads, stores, logs or transmits credentials.
- Backup is disabled in the manifest.
- No Accessibility service, overlay-based key interception, root exploit or hidden persistence is used.

## State integrity
- State transitions are serialized through ProtectionStore.
- Triggered state is monotonic against charger events; only successful Keyguard or BiometricPrompt authentication clears it.
- State and throttle are synchronously persisted.
- Countdown uses elapsedRealtime plus a verified boot count. If a countdown was pending at reboot and the charger remains absent, restart a full 10-second grace period; if charging, protection returns to ARMED. If boot identity is unavailable, never use wall-clock time to expire a monotonic deadline.
- AlarmDeadlineReceiver rechecks the deadline and reschedules early delivery.
- Exact alarm scheduling handles permission revocation races and falls back to inexact delivery when exact access is unavailable.

## Authentication
- Device PIN, pattern and password are verified only by Android Keyguard.
- Fingerprint and face are accepted only through BIOMETRIC_STRONG.
- Five consecutive biometric failure callbacks start a persisted 30-second cooldown. After it expires, another five failures can start another cooldown.
- Cancellation is not a failed attempt and never clears the alarm.
- Android does not disclose incorrect device-credential attempts to third-party apps; Keyguard's own lockout applies.
- Only successful Keyguard or BiometricPrompt results clear ALARM_TRIGGERED.

## Audio and lockscreen
- Packaged Ogg Opus alarm asset is played in a loop through MediaPlayer. If selected and packaged audio both fail, repeating vibration is the fallback; there is no ToneGenerator implementation.
- Audio uses USAGE_ALARM and requests maximum available alarm-stream volume.
- Prior alarm volume is saved and restored after successful disarm. If service is destroyed while alarm remains latched, it does not restore volume prematurely.
- Notification is ongoing and opens a show-when-locked authentication Activity.
- DND is not silently changed. Users configure alarm exceptions in Android settings.
- Android/OEM policies can constrain sound output and background execution.

## Android Enterprise / Device Owner mode
- Dedicated DeviceAdminReceiver is declared.
- App never self-assigns Device Owner.
- Only an already provisioned Device Owner can explicitly enable managed policies.
- Lock Task allowlist includes only this app.
- Lock Task features keep Keyguard available but omit GLOBAL_ACTIONS, hiding the normal power dialog while kiosk mode is active on supported Android builds.
- Selected restrictions block Settings factory reset, safe boot, debugging, adding users and USB file transfer.
- In-app rollback stops Lock Task and clears each restriction and allowlist.
- This does not block physical long-press shutdown, hardware key combinations, bootloader/recovery reset or OEM firmware controls.

## Android 15+ boot behavior
Android 15+ prohibits a BOOT_COMPLETED receiver from directly starting a mediaPlayback foreground service for target API 35. For a latched alarm, BootReceiver now schedules an immediate exact alarm. Android documents exact-alarm delivery as exempt from foreground-service background-start restrictions. This route requires user-granted SCHEDULE_EXACT_ALARM access; without it, an inexact fallback is attempted and playback timing cannot be guaranteed.

## Compatibility
- Minimum SDK 26 (Android 8.0).
- Target SDK 35.
- Material Components UI adapts through a scrollable layout and SP text sizing.
- Biometric availability depends on hardware, enrollment and Android strength classification.
- Device Owner APIs and restrictions depend on Android version, provisioning state and OEM build.
- HONOR/MagicOS, Xiaomi/HyperOS, Samsung/One UI and other OEM battery managers may require user-side background/autostart configuration. No universal public API disables all OEM power management.

## Remaining verification
1. Latest GitHub Actions must pass on current branch head.
2. Test Android 8 through Android 16 compatibility paths where devices are available.
3. Test target HONOR device specifically, including battery optimization and autostart.
4. Test Opus decoding, loop, fallback and volume restoration.
5. Test screen locked, notification permission denied, DND, silent mode, Bluetooth and headphones.
6. Test exact alarm access granted/revoked and Android 15+ reboot recovery.
7. Test process death, force-stop limitations, reboot and app update.
8. Test Device Owner only on a backed-up, factory-reset test device, including successful in-app rollback.
9. Do not describe app as unstoppable or production-certified until physical-device tests pass.
