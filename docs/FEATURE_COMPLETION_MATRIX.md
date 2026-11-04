# Feature Completion Matrix

Status meanings:
- IMPLEMENTED — source code exists in this branch.
- TESTS-WRITTEN — automated test exists but has not yet been confirmed passing.
- PLATFORM-LIMITED — Android does not expose the requested global capability to ordinary apps.
- BLOCKED-ASSET — exact user-supplied media was not available in this coding session.
- DEVICE-VERIFY — must be exercised on physical hardware and OEM builds.

| Requirement | Status | Evidence / limitation |
|---|---|---|
| Automatic arm on plug-in | IMPLEMENTED | PowerReceiver and MainActivity reconciliation |
| 10-second grace period | IMPLEMENTED | ProtectionEngine and AlarmScheduler |
| Replug cancels pre-trigger countdown | IMPLEMENTED | chargerConnected transition |
| Replug cannot clear triggered alarm | IMPLEMENTED + TESTS-WRITTEN | triggered state is immutable to charger events |
| Persist state across process death | IMPLEMENTED | private SharedPreferences |
| Countdown recovery after reboot | IMPLEMENTED, DEVICE-VERIFY | BootReceiver reconciles wall-clock deadline |
| Start alarm after deadline | IMPLEMENTED, PLATFORM-LIMITED | receiver attempts mediaPlayback foreground service; Android may reject background starts |
| Continue alarm after swipe from Recents | IMPLEMENTED, DEVICE-VERIFY | foreground service is ongoing; OS/user can still stop it |
| Exact original alarm audio | BLOCKED-ASSET | original file not supplied to this session; system tone fallback is used |
| Loop fallback alarm | IMPLEMENTED | ToneGenerator is restarted by Handler |
| Max available alarm stream | IMPLEMENTED, DEVICE-VERIFY | requests stream maximum; OEM/user policy can limit it |
| 105% volume | PLATFORM-LIMITED | not exposed by standard Android volume API |
| Block physical volume keys globally | PLATFORM-LIMITED | requires privileged/system or device-owner control |
| Bypass DND/silent on every device | PLATFORM-LIMITED | no universal bypass; notification policy access and OEM behavior vary |
| Block power menu / prevent shutdown | PLATFORM-LIMITED | ordinary third-party apps cannot intercept global power menu |
| Lock-screen information | PARTIAL | activity is configured showWhenLocked; app cannot create an un-dismissible system overlay |
| Device PIN/pattern/password | IMPLEMENTED | DEVICE_CREDENTIAL via BiometricPrompt |
| Fingerprint / face | IMPLEMENTED, DEVICE-VERIFY | BIOMETRIC_STRONG only; supported modalities vary by device |
| Never collect device password | IMPLEMENTED | OS owns credential UI; app receives success callback only |
| No backend / no network permission | IMPLEMENTED | manifest has no INTERNET permission |
| Keystore encryption | NOT-REQUIRED-CURRENTLY | no secrets or credentials are stored; preferences contain only state/deadlines |
| Exact-alarm permission onboarding | IMPLEMENTED | button opens Android special-access screen on Android 12+ |
| Notification permission onboarding | IMPLEMENTED | runtime request on Android 13+ |
| Original circular app icon | BLOCKED-ASSET | temporary vector icon used; exact user asset not supplied |
| Unit tests | TESTS-WRITTEN | state transition tests; CI execution still pending |
| Physical-device test matrix | DEVICE-VERIFY | not possible from GitHub connector environment |

## Release gates
1. Supply the original icon and alarm audio, then replace provisional assets.
2. Obtain a passing GitHub Actions result for unit tests and debug APK assembly.
3. Install and test on the target HONOR device and at least one other Android OEM.
4. Verify exact-alarm special access and notification permission behavior.
5. Verify unplug/replug at 0–10 seconds, alarm trigger, app swipe-away, process kill, reboot, DND, silent mode, and audio route changes.
6. Validate authentication for device credential and each enrolled biometric modality.
7. Verify Android 15+ boot restrictions and document the exact behavior observed.
8. Do not merge or publish until all applicable gates are passed.

## Security invariants
- Charger reconnection never transitions ALARM_TRIGGERED to ARMED or DISARMED.
- Only the successful Android device-authentication callback clears ALARM_TRIGGERED.
- App code never reads, stores, logs, or transmits the device credential.
- The app contains no network permission and no backend integration.
- No claim of unstoppable operation, physical shutdown prevention, or absolute privacy is made beyond the code and platform evidence.
