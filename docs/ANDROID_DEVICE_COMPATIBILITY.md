<div align="right">
  <a href="../README.md"><img src="../ThiefAlertSeQureIcon.jpg" alt="ThiefAlertSeQure icon" width="64" /></a>
</div>

[← Back to ThiefAlertSeQure overview](../README.md)

---

# Android device and manufacturer compatibility

**Scope:** Android 8.0 (API 26) through Android 16 (API 36), including Samsung One UI, HONOR MagicOS, Huawei EMUI, Xiaomi / Redmi / POCO MIUI and HyperOS, OPPO ColorOS, realme UI, OnePlus OxygenOS, vivo Funtouch OS / OriginOS, Google Pixel, Motorola, and other Android-derived builds.

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
| Android 8.0 / 8.1 | 26–27 | Background execution limits; notification channels; runtime restrictions on implicit broadcasts | Manifest power-connected/disconnected receivers; foreground monitoring service; notification channels |
| Android 9 | 28 | Additional background and standby restrictions | Keep monitoring user-visible through foreground service; show battery guidance |
| Android 10 | 29 | Background activity-launch restrictions and evolving full-screen behavior | Use alarm notification and public Settings routes; do not rely on arbitrary background Activity launches |
| Android 11 | 30 | Package visibility filtering | Do not use `resolveActivity()` as the sole gate for Settings routes; attempt launch and catch failure |
| Android 12 / 12L | 31–32 | Background foreground-service start restrictions; exact-alarm special access | Check `canScheduleExactAlarms()`; use permitted alarm/recovery routes; catch start restrictions |
| Android 13 | 33 | Runtime notification permission; exact-alarm permission behavior | Request `POST_NOTIFICATIONS`; check notifications and exact-alarm access at runtime |
| Android 14 | 34 | Full-screen intent special access and foreground-service type prerequisites | Check `canUseFullScreenIntent()`; expose the official app-specific Settings page when available |
| Android 15 | 35 | Edge-to-edge enforcement for target-35 apps; BOOT_COMPLETED cannot directly start mediaPlayback foreground service | Apply system-bar/cutout insets; preserve exact-alarm recovery route for a latched alarm after boot |
| Android 16 | 36 | Additional foreground-service/background-job policy changes | Continue to use narrowly scoped foreground services; do not rely on background jobs launched from a foreground service to bypass quotas |

The app's current build configuration is **minSdk 26, compileSdk 35, targetSdk 35**. Running on Android 16 is not the same as compiling against API 36; API 36-specific APIs should not be used until compileSdk is raised and verified.

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

### Motorola and other Android devices
- Review App battery usage and allow background activity or Unrestricted where available.
- Search Settings for Auto-start, Startup manager, Background activity, or App launch if the device provides those features.

**There is no public Android API that universally opens or changes every OEM's private auto-start and background-management controls.** The app therefore opens standard App info / Settings and supplies manufacturer-specific manual instructions. Menu names change across releases, regions, and individual models.

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

vivo's support site lists a dedicated “Background app power consumption management” FAQ, but exact instructions depend on the device and Funtouch OS / OriginOS version. The app therefore directs users to Settings search rather than claiming a single universal path.

## Core protection architecture

- Charger events use Android's protected `ACTION_POWER_CONNECTED` and `ACTION_POWER_DISCONNECTED` broadcasts declared in the manifest. Android documents these as system broadcasts that wake interested apps.
- Live charging state is revalidated before a state transition; an unknown battery state is not treated as proof of unplugging.
- A foreground service maintains user-visible monitoring while enabled. OEM battery controls may still stop or restrict it.
- Exact alarm scheduling is checked at runtime; inexact fallback may be delayed by Android.
- Android 15+ boot recovery must not start a mediaPlayback foreground service directly from `BOOT_COMPLETED` for target API 35. The app uses its alarm recovery route and must retain a clear fallback if exact-alarm access is unavailable.
- Alarm volume is best-effort. Android, hardware, DND, Bluetooth, wired audio, and OEM policies can limit audible output.
- System keyguard authentication is owned by Android. The app can react to a verified keyguard-dismissal signal but cannot determine whether fingerprint, face, PIN, pattern, or password was used.

## Official Android platform references

- [Settings API reference](https://developer.android.com/reference/android/provider/Settings) — public Settings actions and API-level availability.
- [Monitor battery level and charging state](https://developer.android.com/training/monitoring-device-state/battery-monitoring) — manifest receivers for power-connected and power-disconnected broadcasts.
- [Intent API reference](https://developer.android.com/reference/android/content/Intent) — protected charger broadcasts and system-only battery broadcasts.
- [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms) — exact-alarm access and fallback behavior.
- [Foreground-service launch restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) — Android 12+ background-start limitations.
- [Foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types) — media playback and special-use service declarations.
- [Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15) — boot receiver restrictions for mediaPlayback foreground services.
- [Android 16 foreground-service changes](https://developer.android.com/develop/background-work/services/fgs/changes) — current background-job and foreground-service policy changes.
- [Notification permission and time-sensitive notifications](https://developer.android.com/develop/ui/views/notifications/time-sensitive) — Android 13 notification permission and alarm notification guidance.
- [Package visibility](https://developer.android.com/training/package-visibility/declaring) — Android 11+ visibility filtering and intent queries.

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
