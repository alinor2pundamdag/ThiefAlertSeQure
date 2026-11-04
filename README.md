# ThiefAlertSeQure

Local-first Android charging anti-theft alarm prototype. No backend, analytics, advertising, or network permission is declared.

## Implemented in this branch
- Persisted protection state machine: DISARMED → ARMED → COUNTDOWN (10 seconds) → ALARM_TRIGGERED.
- Reconnection cancels only a pre-trigger countdown. Reconnection cannot clear ALARM_TRIGGERED.
- Countdown deadline and state survive process recreation; boot receiver reconciles state against charging status.
- Device-owner verification uses AndroidX BiometricPrompt with BIOMETRIC_STRONG or DEVICE_CREDENTIAL.
- Alarm foreground service, ongoing notification, and maximum available alarm-stream volume request.
- Unit tests cover core transitions and reboot persistence semantics.
- GitHub Actions builds the APK and runs unit tests.

## Android platform limits (not bypassable by a normal app)
- Android 15+ blocks a BOOT_COMPLETED receiver from launching a mediaPlayback foreground service. Boot recovery therefore persists the alarm and countdown state but cannot promise immediate background audio playback after reboot. The user must reopen the app or interact with its notification to resume playback.
- A normal app cannot intercept the global power menu, prevent hardware power-off, globally disable volume keys, or force 105% hardware volume.
- DND/silent bypass requires user-granted notification policy access and still cannot guarantee audio on every OEM/device configuration.
- A normal app cannot place an un-dismissible system-owned overlay over every lockscreen state. The app uses a visible activity and notification entry instead.
- Exact alarm scheduling depends on the user's special access on Android 12+; when unavailable, an inexact fallback is used and the ten-second trigger may be delayed.
- Android/OEM battery management can terminate background work. No third-party app can guarantee process immortality.

## Asset status
The exact user-provided alarm recording and circular icon were not included in the files available to this coding session. This branch uses an Android system tone fallback and a platform default launcher icon. Replace with the exact supplied assets when they are uploaded; no claim is made that these are the requested originals.

## Build
Requires JDK 17 and Android Gradle Plugin-compatible SDK 35:
`gradle :app:testDebugUnitTest :app:assembleDebug`

This is an initial implementation branch, not a claim of device-verified production readiness. Test on physical devices across Android 8–16 and OEM power-management variants before release.
