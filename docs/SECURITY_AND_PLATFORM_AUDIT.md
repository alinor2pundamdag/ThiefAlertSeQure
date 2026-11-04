# Security and Android Platform Audit

## Threat model
Protect against casual charger removal and ordinary user-level interference. This app is not a device-owner, kiosk, system, or privileged application. It cannot defend against a person who can force shutdown, uninstall/disable the app, revoke permissions, boot recovery, remove the battery, or use privileged tools.

## State integrity
- All transitions are centralized in ProtectionEngine.
- Triggered state is monotonic against charger events: only successful OS device authentication clears it.
- The state is persisted synchronously to private app preferences.
- Countdown uses an epoch deadline to permit reboot recovery. Wall-clock changes can affect remaining time; a future hardening pass should combine elapsedRealtime while booted with a persisted boot identifier and wall-clock fallback.
- AlarmDeadlineReceiver rechecks the deadline before triggering; early delivery reschedules.

## Authentication
- No PIN/password is read or stored by this app.
- BiometricPrompt delegates verification to Android.
- Allowed authenticators are BIOMETRIC_STRONG and DEVICE_CREDENTIAL. Weak face unlock may not qualify; availability is device-dependent.
- A successful prompt is the only app-level transition that clears an active alarm.

## Audio and system controls
- Requests maximum STREAM_ALARM volume, but user/OEM policies may prevent changes.
- Uses a ToneGenerator fallback because the supplied custom audio asset is absent.
- Does not claim volume-key interception, power-menu blocking, universal DND bypass, or 105% volume.
- The notification is ongoing but users/system can still stop or restrict the app.

## Permissions and privacy
- No INTERNET permission; no backend or network SDK.
- Notifications are requested at runtime on Android 13+.
- Exact-alarm special access is not automatically granted. Inexact fallback can delay alarm deadline delivery.
- WAKE_LOCK is declared but a permanent wake lock is intentionally not held because it drains battery and does not guarantee process survival.

## Release blockers
1. Upload exact icon and alarm recording, then add them under mipmap/drawable and res/raw.
2. Implement explicit in-app exact-alarm and notification permission onboarding.
3. Add a proper Android 12+ exact-alarm access flow and reschedule on permission grant.
4. Test power broadcasts and FGS launch policy on Android 14–16 and OEM devices.
5. Verify behavior under DND and audio routing on physical devices.
6. Add instrumentation tests and CI execution evidence.
7. Replace epoch-only countdown with boot-aware monotonic deadline persistence.
8. Review notification interaction and owner authentication while device is locked.
9. Verify package ID and branding with owner before publishing.
