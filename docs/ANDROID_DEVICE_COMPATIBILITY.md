<div align="right">
  <a href="../README.md"><img src="../ThiefAlertSeQureIcon.jpg" alt="ThiefAlertSeQure icon" width="64" /></a>
</div>

[← Back to ThiefAlertSeQure overview](../README.md)

---

# Android device and manufacturer compatibility

**Scope:** Android 8.0 (API 26) through Android 17 (API 37), including Samsung One UI, HONOR MagicOS, Huawei EMUI, Xiaomi / Redmi / POCO MIUI and HyperOS, OPPO ColorOS, realme UI, OnePlus OxygenOS, vivo Funtouch OS / OriginOS, Google Pixel, Motorola, ASUS, Sony Xperia, Nothing OS, Lenovo, TECNO, Infinix, ZTE / nubia, and other Android-derived builds.

**Important:** This is a compatibility strategy and source-level audit, not a certification of every handset. Actual behavior must be tested on physical devices and OS builds.

## Compatibility principles

1. Prefer public Android SDK APIs and system Settings actions.
2. Check Android API level before using version-specific APIs.
3. Treat settings activities as optional: attempt the public action, then fall back to app info, then general Settings.
4. Detect the manufacturer and show relevant user instructions; never assume an OEM menu exists.
5. Do not depend on private OEM activity class names, undocumented intents, hidden APIs, root, or Accessibility-based workarounds.
6. Check the actual permission or capability after returning from Settings. Opening a settings page is not proof that the user enabled the capability.
7. Preserve safe behavior when a capability is unavailable: show a clear warning and never claim that the feature is active.

## Android API compatibility matrix

| Android release | API | Relevant platform behavior | App handling |
|---|---:|---|---|
| Android 8.0 / 8.1 | 26–27 | Background execution limits; notification channels; runtime restrictions on implicit broadcasts | Runtime-registered power-connected/disconnected receiver while the user-enabled foreground monitoring service is active; notification channels |
| Android 9 | 28 | Additional background and standby restrictions | Keep monitoring user-visible through foreground service; show battery guidance |
| Android 10 | 29 | Background activity-launch restrictions and evolving full-screen behavior | Use alarm notification and public Settings routes; do not rely on arbitrary background Activity launches |
| Android 11 | 30 | Package visibility filtering | Do not use `resolveActivity()` as the sole gate for Settings routes; attempt launch and catch failure |
| Android 12 / 12L | 31–32 | Background foreground-service start restrictions; exact-alarm special access | Check `canScheduleExactAlarms()`; use permitted alarm/recovery routes; catch start restrictions |
| Android 13 | 33 | Runtime notification permission; exact-alarm permission behavior | Request `POST_NOTIFICATIONS`; check notifications and exact-alarm access at runtime |
| Android 14 | 34 | Full-screen intent special access and foreground-service type prerequisites | Check `canUseFullScreenIntent()`; expose the official app-specific Settings page when available |
| Android 15 | 35 | Edge-to-edge enforcement for target-35 apps; BOOT_COMPLETED cannot directly start mediaPlayback foreground service | Apply system-bar/cutout insets; preserve exact-alarm recovery route for a latched alarm after boot |
| Android 16 | 36 | Edge-to-edge enforcement and large-screen resizability/orientation changes for apps targeting API 36 | Apply insets; keep layouts adaptive and verify landscape, split-screen, foldables, and tablets |
| Android 17 | 37 | Background audio hardening for all apps; target-37 apps have additional while-in-use FGS rules, with an exact-alarm + USAGE_ALARM exception; target-37 large-screen resizability restrictions cannot be opted out | Alarm audio and volume operations must remain inside a qualifying non-short foreground service; the project uses a mediaPlayback alarm FGS and a specialUse monitor FGS. Test Android 17 audio hardening with the platform ADB command |

The app's updated build configuration is **minSdk 26, compileSdk 36, targetSdk 36**, using Android Gradle Plugin 8.9.1 and Gradle 8.11.1. This meets the current Google Play target-API requirement for new apps and updates (API 36). Android 17 (API 37) is supported as a runtime OS target, but the app does not yet target or compile against API 37. Android 17 target-37-only behavior changes must be re-audited before raising targetSdk to 37.

## Bluetooth compatibility and setup (Android 8–17)

The current A2 implementation uses an already-paired trusted device and Bluetooth ACL connection/disconnection events. It does not scan for nearby devices, advertise the phone, connect to a remote GATT service, or read location. Therefore it requests only Bluetooth Connect on Android 12+; it must not add Bluetooth Scan, Bluetooth Advertise, or location permission unless a separately approved feature genuinely needs them.

| Android release | API | Bluetooth permission / behavior | ThiefAlertSeQure handling |
|---|---:|---|---|
| Android 8.0 | 26 | Legacy BLUETOOTH and BLUETOOTH_ADMIN install-time permissions | Runtime event receiver while the user-enabled foreground monitor is active; no nearby-device scan |
| Android 8.1 | 27 | Same legacy permission model | Same as API 26 |
| Android 9 | 28 | Legacy permissions; stronger battery/background management varies by OEM | Keep monitor user-visible; show OEM battery guidance |
| Android 10 | 29 | Legacy permissions; background Activity launches restricted | Do not open UI from background Bluetooth events |
| Android 11 | 30 | Legacy permissions; package-visibility restrictions affect some Settings discovery | Use public Bluetooth Settings action with fallback |
| Android 12 | 31 | BLUETOOTH_CONNECT is a runtime Nearby devices permission; legacy permissions capped at API 30 | Request Connect only when owner selects/uses a paired trusted device |
| Android 12L | 32 | Same Android 12 permission model | Same as API 31 |
| Android 13 | 33 | Nearby devices permission remains separate from notification permission | Check Connect and POST_NOTIFICATIONS independently |
| Android 14 | 34 | Connect remains runtime; foreground-service types and background-start rules also apply | Keep Bluetooth event monitoring inside the already-running, user-visible monitor service |
| Android 15 | 35 | Same Connect permission model; target-35 system behavior changes apply independently | Do not assume an OEM battery or boot setting can be changed by the app |
| Android 16 | 36 | Android Ranging API introduced; BLE Channel Sounding and RSSI ranging are capability-dependent | Detect Ranging API availability, then query real runtime capabilities before offering ranging |
| Android 17 | 37 | Ranging API continues; target-37 RFCOMM socket read behavior changes | Current A2 does not use RFCOMM sockets; if future code does, treat read() == -1 as stream closure |

### Runtime checks shown in the app

The Bluetooth compatibility panel reports:
- Android release and API level.
- Bluetooth Classic hardware availability.
- Bluetooth Low Energy hardware availability.
- Whether Bluetooth Connect permission is granted (API 31+).
- Adapter state: ON, OFF, permission required, unsupported, or unknown.
- Whether the Android Ranging API exists at API 36+; this is not a claim that any particular ranging technology is supported.

A status of **available** means the OS exposes the adapter or hardware feature. It does not prove that a particular accessory is connected, that ACL broadcasts will always be delivered, or that the OEM will keep the process alive. A fresh trusted-device connection baseline is still required after enabling or restarting A2.

### Bluetooth settings the owner should review

1. Turn Bluetooth ON in Android Quick Settings or Settings → Connected devices / Bluetooth.
2. Pair the trusted watch, earbuds, or accessory using Android's own pairing screen first.
3. Open ThiefAlertSeQure → Settings → Trusted Bluetooth Separation Alert → choose the paired device.
4. On Android 12+, grant **Nearby devices / Bluetooth Connect** when the app requests it. Do not grant unrelated permissions.
5. Enable the A2 separation alert and reconnect the selected accessory once to establish a fresh baseline.
6. Keep ThiefAlertSeQure's foreground protection notification enabled.
7. Review Settings → Apps → ThiefAlertSeQure → Battery and allow background use / Unrestricted only if the device offers it and the owner accepts the battery trade-off.
8. Follow the manufacturer-specific background guidance above. OEM auto-start controls are manual and are not universally exposed to third-party apps.
9. If Bluetooth is turned off, the accessory battery dies, the accessory sleeps, or Android/OEM suppresses an event, the disconnect alert may not be delivered. The app must show this limitation rather than claiming guaranteed detection.

### Distance and direction are separate optional capabilities

- **ACL connect/disconnect:** indicates connection state only. It does not provide compass direction or exact distance.
- **RSSI:** signal strength can support a rough near/far trend when the OS and accessory expose suitable measurements. It is affected by walls, body position, antenna design, transmit power, interference, and accessory sleep. Never present RSSI as an accurate meter measurement.
- **Bluetooth Channel Sounding:** Android 16+ exposes a Ranging API, but the actual technology must be supported by the local phone, remote accessory, Bluetooth stack, and OS. Do not enable it merely because API 36 exists.
- **UWB:** can provide precise ranging only when the phone and remote device both support the required UWB capabilities and the platform exposes them.
- **Direction finding (AoA/AoD):** requires compatible Bluetooth Direction Finding hardware, including suitable antenna-array support. A phone compass reports the phone's own heading; it cannot locate a generic Bluetooth accessory to the north/south/east/west.
- If no supported precise-ranging or direction hardware exists, display **Not supported** or **Direction unavailable**. Never fabricate a distance, bearing, map pin, or accuracy percentage.

## Recommended Bluetooth improvements (future work; not release-assigned)

These are proposals, not commitments. The owner decides which release, if any, receives each item.

1. **Capability-aware Bluetooth diagnostics:** show permissions, adapter state, Classic/BLE availability, monitor receiver state, and baseline status.
2. **Reconnect / baseline recovery:** clearly guide the owner to reconnect the trusted accessory after permission changes, service restart, reboot, or Bluetooth toggle.
3. **Bluetooth adapter-state monitoring:** detect adapter OFF/ON transitions while the monitor is active, but do not treat an OFF event alone as proof of theft without an explicitly approved policy.
4. **RSSI proximity trend:** optional foreground-only near/far trend with smoothing and a clear estimate label; no exact meters or compass direction.
5. **Android Ranging integration:** explore BLE Channel Sounding / UWB through API 36+ only after verifying both endpoint capabilities and a real supported accessory.
6. **Directional finding:** consider only if the phone/accessory hardware supports AoA/AoD; otherwise keep it unavailable.
7. **Accessory battery / link health:** show battery only if the paired accessory exposes a standard, accessible battery service; do not guess battery percentage.
8. **Per-device trust management:** allow owner-controlled rename, replace, disable, and explicit re-pairing of the trusted device without clearing other protection preferences.
9. **False-disconnect resilience:** record event time, reconnect cancellation, and bounded debounce; never delay or silence the existing charger alarm.
10. **Local diagnostic export:** owner-initiated, redacted Bluetooth event report with Android API, adapter state, permission state, and timestamps; no automatic upload or telemetry.

Every improvement must preserve the existing charger alarm, authentication path, local-only privacy model, app ID, signing certificate, stored settings, and upgrade-over-install compatibility. Bluetooth enhancements must remain optional and must never interfere with an active theft alarm.

## Manufacturer guidance

The app detects the device's reported manufacturer and brand and selects matching instructions. These settings are user-controlled and are not automatically toggled by the app.

### Samsung One UI
- Open Settings → Battery (or Battery and device care → Battery) → Background usage limits.
- Add ThiefAlertSeQure to Never sleeping apps; do not put it in Sleeping or Deep sleeping apps.
- Review App info → Battery for an Unrestricted option if the installed One UI version offers it.
- Keep app notifications and the alarm channel enabled.

### HONOR MagicOS / Huawei EMUI
- Search Settings for App launch or Launch management.
- Select ThiefAlertSeQure.
- If Manage automatically exists, disable it and enable Auto-launch, Secondary launch, and Run in background.
- Also review battery optimization.

### Xiaomi / Redmi / POCO (MIUI / HyperOS)
- App info → Battery saver / Power → No restrictions, where offered.
- Settings → Apps → Permissions → Background autostart (or Autostart) → allow ThiefAlertSeQure.
- Lock the app in Recents if that option exists on the installed build.

### OPPO / realme / OnePlus
- OPPO / realme: review App battery management and allow foreground activity, background activity, and auto-launch where offered. Some releases put auto-launch under Phone Manager → Privacy Permissions → Auto-launch apps.
- OnePlus / OxygenOS: search Settings → Apps → Auto launch.
- Lock the app in Recents if the option exists. ColorOS, realme UI, and OxygenOS labels vary.

### vivo / iQOO
- Review Background power consumption management.
- Search Settings for Autostart or Background start and allow the app where offered.

### Google Pixel
- App info → App battery usage → Allow background usage.
- Choose Unrestricted if available. Pixel devices generally use Android's standard battery controls rather than a separate OEM auto-start switch.

### Motorola, ASUS, Sony, Nothing, Lenovo, TECNO, Infinix, ZTE, and other Android devices
- Review App battery usage and allow background activity or Unrestricted where available.
- Search Settings for Auto-start, Startup manager, Background activity, or App launch if the device provides those features.
- The app recognizes ASUS, Sony Xperia, Nothing OS, Lenovo, TECNO / Infinix / itel, and ZTE / nubia as separate manufacturer labels, but uses safe generic Settings guidance unless a stable public route is verified for that exact firmware.

**There is no public Android API that universally opens or changes every OEM's private auto-start and background-management controls.** The app therefore opens standard App info / Settings and supplies manufacturer-specific manual instructions. Menu names change across releases, regions, and individual models.

## In-app App Launch management guide

The Settings screen shows one device-detected background setup card. It displays the detected manufacturer, brand, model, Android release, and API level, then selects guidance for that device family. It no longer presents a fake **Manage automatically / Manage manually** selector because an ordinary app cannot read or change the OEM's private switch state.

The card contains three separate **Auto-launch**, **Secondary launch**, and **Run in background** explanations. On supported HONOR/Huawei builds, the guide instructs the user to search Settings for **App launch**, select ThiefAlertSeQure, turn **Manage automatically** off, enable all three controls, and confirm. The three cards do not pretend that Samsung, Pixel, Xiaomi, or other families have HONOR's exact controls.

For Samsung, Xiaomi/Redmi/POCO, OPPO/realme/OnePlus, vivo/iQOO, Pixel, Motorola, and other Android devices, the guide describes the closest known equivalent and explicitly identifies controls that may not exist. Actions open only public Android Settings destinations; on HONOR/Huawei the App Launch page is not reliably deep-linkable by ordinary apps, so Settings search is the supported fallback. The guide includes the detected model and Android release to make firmware-specific variation visible.

The app does not claim to read or change OEM-only switch states. There is no public Android API for universally reading or toggling these vendor controls, and undocumented component intents are intentionally avoided. A successful Settings launch is not proof that the user changed a setting.

## System Settings navigation strategy

All system-setting actions should use this fallback order:

1. The most specific public Android Settings action for the capability.
2. The relevant app-level Settings page (notifications or App info).
3. General Android Settings.

A Settings action may be absent on a particular Android release or OEM build. The app must catch launch failures and move to the next fallback. It must not assume that a successful page launch means permission was granted.

Version-specific examples:
- Exact alarms: API 31+ only; check `AlarmManager.canScheduleExactAlarms()` before scheduling.
- Notifications: API 33+ runtime permission plus app/channel enabled checks.
- Full-screen intents: API 34+ special access; check `NotificationManager.canUseFullScreenIntent()`.
- Battery optimization exemption: API 23+; check `PowerManager.isIgnoringBatteryOptimizations()`. Android advises most apps not to request exemption unless the use case genuinely requires it.
- Do Not Disturb: use the public priority-settings action, then Sound settings, then general Settings. The app does not silently change DND policy.
- Device credential: use Android Keyguard; never collect the PIN, pattern, password, or biometric template.

## Official manufacturer references

These vendor support pages confirm the kinds of background controls described above. They are examples, not a guarantee that every model exposes the same labels:

- [Samsung: Galaxy Battery optimization](https://www.samsung.com/us/support/galaxy-battery/optimization/) — Background usage limits and Never sleeping apps.
- [Samsung Developer: Application Management](https://developer.samsung.com/mobile/app-management.html) — Samsung documents sleeping/deep-sleeping restrictions and the Never sleeping apps exception.
- [HONOR: Apps launch slowly / allow background running](https://www.honor.com/sa-en/support/content/en-us00406923/) — App launch, Manage automatically, Auto-launch, Secondary launch, and Run in background.
- [Xiaomi: Background autostart on Xiaomi 14T Pro](https://www.mi.com/ph/support/faq/details/KA-507611/) — Settings → Apps → Permissions → Background autostart.
- [Xiaomi: Background autostart on Redmi 13C](https://www.mi.com/global/support/faq/details/KA-497677/) — confirms the same control on another product family.
- [Xiaomi: Background permissions and no restrictions](https://www.mi.com/global/support/faq/details/KA-694585/) — no restrictions, app autostart, and locking in Recents.
- [OPPO: Battery usage by app](https://support.oppo.com/en/answer/?aid=2171043) — ColorOS app battery usage and background activity.
- [realme: Troubleshooting and notification/background permissions](https://www.realme.com/global/support/troubleshooting) — foreground/background activity, auto-launch, and Startup Manager.
- [OnePlus: OxygenOS 14 User Manual](https://service.oneplus.com/content/dam/support/user-manuals/common/OxygenOS_14.0_User_Manual.pdf) — Settings → Apps → Auto launch.

vivo's official support material documents **Background power consumption management**, where users can allow selected apps to continue running in the background. Some Funtouch OS releases also expose Autostart under Settings → More settings → Applications → Autostart; OriginOS/Funtouch menu paths differ, so the app uses Settings search when a precise route is not verified for that build. [vivo Philippines support](https://www.vivo.com/ph/support/questionList?categoryId=54461) · [vivo device guide (PDF)](https://eu-exstatic-vivofs.vivo.com/8Xa6evfY85lu15Pb/1658104689682/c8570d4b762027b070c0f05851682905.pdf)

## Core protection architecture

- Charger events use Android's protected `ACTION_POWER_CONNECTED` and `ACTION_POWER_DISCONNECTED` broadcasts registered dynamically while the user-enabled foreground monitoring service is active. The implementation does not depend on manifest-declared implicit power receivers, which are restricted by modern Android background-broadcast rules.
- Live charging state is revalidated before a state transition; an unknown battery state is not treated as proof of unplugging.
- A foreground service maintains user-visible monitoring while enabled. OEM battery controls may still stop or restrict it.
- Exact alarm scheduling is checked at runtime; inexact fallback may be delayed by Android.
- Android 15+ boot recovery must not start a mediaPlayback foreground service directly from `BOOT_COMPLETED` for target API 35. The app uses its alarm recovery route and must retain a clear fallback if exact-alarm access is unavailable.
- Alarm volume is best-effort. Android, hardware, DND, Bluetooth, wired audio, and OEM policies can limit audible output.
- System keyguard authentication is owned by Android. The app can react to a verified keyguard-dismissal signal but cannot determine whether fingerprint, face, PIN, pattern, or password was used.

## Official Android platform references

- [Settings API reference](https://developer.android.com/reference/android/provider/Settings) — public Settings actions and API-level availability.
- [Monitor battery level and charging state](https://developer.android.com/training/monitoring-device-state/battery-monitoring) — Android's public charging-state and battery-monitoring APIs; this project registers charger events from its active foreground monitor.
- [Intent API reference](https://developer.android.com/reference/android/content/Intent) — protected charger broadcasts and system-only battery broadcasts.
- [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms) — exact-alarm access and fallback behavior.
- [Foreground-service launch restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) — Android 12+ background-start limitations.
- [Foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types) — media playback and special-use service declarations.
- [Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15) — boot receiver restrictions for mediaPlayback foreground services.
- [Android 16 foreground-service changes](https://developer.android.com/develop/background-work/services/fgs/changes) — current background-job and foreground-service policy changes.
- [Google Pixel: app battery usage and background usage](https://support.google.com/pixelphone/answer/6090599) — Pixel app-level Allow background usage controls.
- [vivo Philippines: Background app power consumption management](https://www.vivo.com/ph/support/questionList?categoryId=54461) — official vivo support topic for background power management.
- [Notification permission and time-sensitive notifications](https://developer.android.com/develop/ui/views/notifications/time-sensitive) — Android 13 notification permission and alarm notification guidance.
- [Package visibility](https://developer.android.com/training/package-visibility/declaring) — Android 11+ visibility filtering and intent queries.

## Android 17 background-audio verification

Android 17 applies audio restrictions even to apps that still target API 36. Any background playback, audio-focus request, or volume change must occur while an Activity is visible or while a foreground service other than `SHORT_SERVICE` is active. The alarm implementation must keep its `mediaPlayback` foreground service active before it starts the player, requests audio focus, or changes alarm volume. Do not move audio playback or volume enforcement into a receiver or ordinary background worker.

For Android 17 device/emulator testing, run:

```bash
adb shell cmd audio set-enable-hardening throw
```

Trigger an alarm with the screen off and verify audio playback, audio focus, and repeated alarm-volume enforcement. Inspect `adb logcat` or `adb shell dumpsys audio` for `AudioHardening` entries. Restore the normal setting after testing:

```bash
adb shell cmd audio set-enable-hardening disable
```

This test has not yet been performed on physical hardware.

## Known hard limits

No ordinary third-party app can guarantee:
- Survival after the user force-stops the app, disables it, uninstalls it, or powers the phone off.
- Background execution when Android or an OEM explicitly blocks it.
- That every vendor offers an auto-start setting or a direct link to it.
- Full-screen alarm presentation when Android denies eligibility or the user disables access.
- Exact countdown delivery when exact-alarm access is denied or the OS defers background work.
- Maximum audible output beyond the device's own supported volume and interruption policy.
- Identical behavior across all biometric sensors, keyguard customizations, firmware builds, or external audio routes.

## Physical-device verification matrix

Do not mark a manufacturer or Android version as verified until tests are run on real hardware.

| Test | Expected result |
|---|---|
| Fresh install and first launch | App opens; no unsupported Settings action crashes |
| App notification permission denied / granted | Readiness status is accurate; settings fallback works |
| Exact alarm access denied / granted | App reports actual state; countdown fallback is fail-safe |
| Full-screen intent unavailable / granted | Notification fallback remains available |
| Battery optimization restricted / unrestricted | Status reflects actual capability; instructions match OEM |
| Plug / unplug with screen on and off | Only a confirmed unplug starts the 10-second countdown |
| App in background and screen locked | Foreground monitor and alarm path operate where OS policy permits |
| Reboot with charger connected / disconnected | Persisted state is reconciled without inventing a transition |
| Alarm unlock with fingerprint / PIN / pattern | One successful system unlock stops alarm; no duplicate prompt or re-lock |
| DND, silent, Bluetooth and wired audio | Output limitations are recorded; no false guarantee of max volume |
| Unsupported OEM settings action | Fallback to App info / general Settings works |

Recommended representative test devices: one Pixel/AOSP device, Samsung One UI, HONOR MagicOS, Xiaomi/Redmi HyperOS, OPPO or realme, vivo, and Motorola. Test at least the minimum API (26), API 31/32, API 33, API 34, API 35, and API 36 where hardware is available.

## Verification status

Source-level OEM detection, manufacturer-specific guidance, public Settings fallback navigation, and edge-to-edge inset handling have been added to the active feature branch. These changes still require a fresh CI build and physical-device testing. Do not describe every Android model as verified until the matrix above has been completed.

---
