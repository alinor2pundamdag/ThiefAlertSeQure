# Device Intelligence

Status: implemented on the development branch; release version unassigned.

## Purpose

Provide a dedicated, organized, local-only device intelligence page. Avoid expanding the Settings screen into one very long scroll view. The dashboard and Settings provide short entry points to the separate Bluetooth and Device Intelligence pages.

## Current local inspection

The Device Intelligence page reads Android public APIs and package-manager feature flags for:

- Manufacturer, brand/family, model, device/product codenames, Android release/API, security patch, build type/tags, supported ABIs, app version, target SDK, and configured min SDK.
- RAM totals/availability and low-memory state.
- App-visible storage volume totals/availability.
- Logical processor count.
- Battery level/charging state, BatteryManager capacity when exposed, Battery Saver, thermal status, and whether Android reports the app exempt from battery optimization.
- Display pixel dimensions, density, refresh rate, and active display mode.
- Presence and Android-reported name, vendor, maximum range, resolution, and power figures for accelerometer, gyroscope, magnetometer, proximity, light, barometer, step counter, step detector, rotation vector, and significant-motion sensors.
- Package-manager feature flags for Bluetooth Classic/LE, Wi-Fi, NFC, GPS/network location hardware capability, telephony, camera/flash, fingerprint, USB host/accessory, and selected sensor categories.
- ThiefAlertSeQure readiness signals: notification enablement, full-screen alarm-intent access where applicable, exact-alarm access, secure lock-screen configuration, Bluetooth Connect permission, Android Ranging permission where applicable, monitor preference/runtime-last-recorded state, and current protection state.
- Device idle/doze mode and app data filesystem volume metrics.
- Manufacturer-family background execution guidance and passive root indicators.

## Privacy and safety boundaries

- Inspection is local and on demand; no telemetry or cloud upload is performed.
- Do not read IMEI, SIM identifiers, serial number, MAC/BSSID, Wi-Fi identifiers, contacts, messages, files, precise location, microphone content, or camera content.
- Build fingerprint is deliberately withheld from the displayed report.
- No runtime permission is requested automatically by opening the page.
- No system setting is changed automatically.
- Passive root indicators are shown separately from active root authorization. The Device Intelligence page provides an explicit **Check Root Status (run su -c id)** action. After owner confirmation, it executes the read-only command off the UI thread and displays ROOT ACCESS GRANTED (uid=0), ROOT ACCESS NOT GRANTED, SU NOT AVAILABLE, POSSIBLE ROOT, TIMEOUT, or ERROR. A missing su command and no common indicators are reported as no root access detected, not as proof that hidden root is impossible. No root prompt runs automatically on page open.
- Capability presence does not imply that a corresponding ThiefAlertSeQure feature is implemented or enabled.

## UI/UX structure

- Dedicated Device Intelligence page, separate from Settings.
- Grouped cards: Device Identity, Android Platform, Performance and Power, Display, Sensor Capabilities, Android Hardware Features, ThiefAlertSeQure Readiness, Root Indicators, and Manufacturer Background Guidance.
- Explicit refresh action; no continuous polling or long-running sensor sampling.
- Root status includes a real owner-triggered `su -c id` check and a visible result, not only passive filesystem/build-tag hints.
- Dashboard and Settings contain concise navigation cards rather than duplicating the full report.

## Compatibility and validation

- Uses public APIs compatible with minSdk 26; API-specific checks are guarded.
- Validate API 26–37 behavior, including older BatteryManager/thermal/display paths and newer exact-alarm/ranging permission checks.
- Physical testing is required to confirm vendor-specific reporting. Hardware feature flags and sensor presence are platform declarations, not proof of a particular sensor's accuracy or performance.
- Keep all future feature assignments separate from the A1–A7 roadmap. This addition is device intelligence, not a release-version assignment.
