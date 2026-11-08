<div align="right">
  <a href="../README.md"><img src="../ThiefAlertSeQureIcon.jpg" alt="ThiefAlertSeQure icon" width="64" /></a>
</div>

[← Back to ThiefAlertSeQure overview](../README.md)

---

# Android settings and alarm hardening

Date: 2026-11-06

## Scope

The app supports Android 8.0 / API 26 and newer. The dashboard and Settings screen detect the running OS using `Build.VERSION.SDK_INT` and show an OS/API-specific navigation hint in each system-setting tutorial dialog.

## System-setting entries

Each system-setting action has a separate `i` tutorial card. The user-facing routes include:

- App notification settings and the anti-theft alarm notification channel.
- Android 13+ notification runtime permission.
- Android 12+ exact-alarm / Alarms & reminders access.
- App info, permissions, battery use, and background restrictions.
- Battery-optimization exemption request.
- Do Not Disturb / alarm interruption settings.
- Android 14+ full-screen intent access.
- Secure screen-lock settings.
- Explicit Device Administrator enrollment for `lockNow()`.
- Alarm volume and sound settings.
- Manufacturer-aware background and auto-start guidance for Samsung, HONOR/Huawei, Xiaomi/Redmi/POCO, OPPO/realme/OnePlus, vivo/iQOO, Pixel, Motorola, and other Android devices.

System setting actions use public Android Settings actions where available. Each action falls back to app-specific settings and then general Settings when the OEM build does not provide the requested page. The `AndroidDeviceCompatibility` helper detects the reported manufacturer/brand and displays manual guidance for Samsung One UI, HONOR/MagicOS, Huawei/EMUI, Xiaomi/Redmi/POCO, OPPO/realme/OnePlus, vivo/iQOO, Pixel, Motorola, and other Android devices. OEM-only pages are not deep-linked through undocumented component names; Android does not provide a universal public API for changing private auto-start controls.

Scrollable screens apply system-bar and display-cutout insets to handle Android 15+ edge-to-edge enforcement for target API 36.

## App Launch management UI

- Settings displays one device-detected setup guide based on manufacturer, brand, model, Android release, and API level.
- It displays separate Auto-launch, Secondary launch, and Run in background explanations, with family-specific equivalents and explicit notes when a switch does not exist.
- On HONOR/Huawei, users are directed to search Settings → App launch, select ThiefAlertSeQure, disable Manage automatically, enable all three manual controls, and confirm.
- The old local-only Manage automatically / Manage manually selector was removed because it could be mistaken for a real OEM control. The app does not claim to read or change private switch state.
- Each action opens a supported Settings entry point. Undocumented OEM component intents are deliberately avoided because they are firmware-specific and can break across updates.

## Charger-event reliability

- Power receiver work is serialized on a single-thread executor so connect/disconnect tasks do not race on independent worker threads.
- A CONNECTED event is accepted only when the live external-power state is positively confirmed as connected.
- Disconnected or unknown live state does not overwrite the persisted baseline or re-arm protection.
- Connect and disconnect confirmation use three consecutive live samples (100 ms between connect samples; 120 ms between disconnect samples), followed by a final live-state read. This reduces confirmation delay while retaining consecutive-sample validation.
- A unit test covers true / false / unknown acceptance policy.
- Existing disconnect debounce, generation checks, countdown, reboot recovery, and alarm deadline validation are preserved.

## Alarm volume behavior

- The existing controller stores the pre-alarm alarm-stream volume and raises it to the stream maximum when the alarm starts.
- While `ALARM_TRIGGERED` remains latched, AlarmService reasserts the maximum alarm-stream volume every 500 ms.
- The saved volume is restored only after the protection state has left `ALARM_TRIGGERED` (the authenticated disarm path).
- This is best-effort enforcement, not a hardware volume-key lock. Android/OEM policy, fixed-volume devices, routing, external devices, or privileged system behavior can still limit the result.

## Lock-screen behavior

- MainActivity already uses `showWhenLocked` and `turnScreenOn`; the alarm notification requests a full-screen intent.
- Android 14+ full-screen intent access is exposed through the matching system settings page when available.
- Device Administrator enrollment is optional and user-approved; when active and the device has a secure credential, AlarmService can call `DevicePolicyManager.lockNow()`.
- The app does not repeatedly re-lock while the owner is authenticating. It cannot guarantee persistent screen wake, bypass Keyguard, or prevent hardware/firmware shutdown.

## Permission and distribution policy

The manifest declares the permissions needed by the implemented functions, including notifications, exact alarms, full-screen intents, wake lock, audio settings, foreground services, boot recovery, and the battery-optimization exemption request.

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is a special user-facing exemption request. Android documentation warns that most apps should not request it; the app must justify it as essential anti-theft background functionality and must pass any distribution-store policy review. Full-screen intent access is also subject to Android 14+ eligibility and user/system controls.

## Integration and verification status

- Source branch: `feat/custom-audio-and-reliability`.
- Draft PR #2 tracks the active source branch; PR #1 is obsolete and closed.
- Manufacturer-aware guidance and fallback Settings navigation are implemented in `AndroidDeviceCompatibility.kt`.
- Samsung, HONOR/Huawei, Xiaomi/Redmi/POCO, OPPO/realme/OnePlus, vivo/iQOO, Pixel, Motorola, and generic Android guidance is selected from the device's reported manufacturer and brand.
- Public Settings actions fall back to app info and general Settings; undocumented OEM component names are intentionally not used.
- Scrollable screens apply system-bar and cutout insets for Android 15+ edge-to-edge behavior.
- A source-level compatibility matrix is maintained in `docs/ANDROID_DEVICE_COMPATIBILITY.md`.
- CI must pass on the latest branch head before distributing a new APK. A blank workflow/status response is not a passing build.
- No physical-device acceptance test has been performed. Test settings navigation, plug/unplug timing, rapid reconnect, alarm volume-key behavior, lock-screen/full-screen presentation, OEM background restrictions, reboot recovery, and Device Administrator enrollment/rollback.

## Acceptance criteria

1. A genuine confirmed unplug starts exactly one 10-second countdown.
2. Reconnection cancels the countdown; unknown charger state never starts or completes one.
3. A stale connect event cannot re-arm the app while live external power is disconnected.
4. Alarm remains latched until Android owner authentication succeeds.
5. Alarm stream is repeatedly driven toward maximum while latched; original volume is restored after authenticated disarm.
6. Every system-setting entry has an `i` tutorial, and each destination is guarded with an Android-supported fallback.
7. CI passes on the current PR head and physical-device checks are recorded before release.
