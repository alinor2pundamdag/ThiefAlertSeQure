# Bluetooth & Root Capability Intelligence

## Purpose
ThiefAlertSeQure detects Android release, manufacturer/model, Bluetooth hardware flags, adapter state, permission state, platform API families, trusted-device monitor readiness, and best-effort root indicators. The dashboard separates OS API presence from verified local hardware and remote-accessory support.

The system is local-first. It does not upload device fingerprints, Bluetooth identifiers, root state, diagnostics, or event history.

## Operating modes

### Standard Android mode
- Android 8–11: legacy BLUETOOTH / BLUETOOTH_ADMIN install-time permissions. Scanning has additional location requirements depending on Android version and target SDK; current A2 does not scan.
- Android 12–17: BLUETOOTH_CONNECT is requested for paired-device access. BLUETOOTH_SCAN and BLUETOOTH_ADVERTISE are not required by current A2.
- Adapter state, hardware feature flags, Android API level, selected trusted device, and monitor baseline are reported independently.

### Root-enhanced diagnostics mode
Root detection has two stages:
1. **Passive startup detection:** checks common su/Magisk paths and build tags. It never launches a root manager prompt. Results are "no common indicators" or "possible root indicators"; neither proves the device's true root state.
2. **Owner-authorized verification:** only after the owner taps the diagnostics action, the app runs the read-only command 'su -c id'. A root manager may display an authorization prompt. A successful uid=0 response verifies that this app received root authorization for that diagnostic request.

After authorization, the implementation reads:
- dumpsys bluetooth_manager
- Bluetooth-related system properties via getprop
- /sys/class/bluetooth directory metadata

These are diagnostic reads only. They do not change radio power, pairing, discoverability, controller parameters, firmware, system properties, SELinux policy, or vendor configuration. Root permission is not treated as permanent authorization; future privileged operations require explicit owner approval and narrow scope.

Root does not create missing controller features, antenna arrays, firmware support, peer support, or Android framework APIs.

## Android 8–17 compatibility matrix

| Android | API | Permission / platform model | Detection and behavior |
|---|---:|---|---|
| 8.0 | 26 | Legacy Bluetooth permissions | Classic/BLE hardware flags; paired-device access; legacy scan rules if a scan feature is separately approved |
| 8.1 | 27 | Same as 8.0 | Same as API 26 |
| 9 | 28 | Legacy permissions | Respect background activity and battery behavior |
| 10 | 29 | Legacy permissions | Background activity launches restricted; no UI launch from background receiver |
| 11 | 30 | Legacy permissions | Legacy Bluetooth permission declarations capped at API 30 |
| 12 | 31 | Nearby Devices runtime permission group | BLUETOOTH_CONNECT for paired devices; SCAN only for discovery; ADVERTISE only for advertising |
| 12L | 32 | Same as Android 12 | Same permission separation |
| 13 | 33 | Nearby Devices plus separate notification permission | Check CONNECT separately from POST_NOTIFICATIONS; LE Audio generation exists but hardware/peer support still matters |
| 14 | 34 | Bluetooth permissions plus foreground-service type/start restrictions | Monitor remains in the declared user-visible foreground service |
| 15 | 35 | Bluetooth permissions unchanged; target-SDK behavior evolves | OEM background/battery settings remain manual and device-specific |
| 16 | 36 | Android Ranging module/API introduced | Query runtime RangingManager capabilities; API presence alone does not prove BLE Channel Sounding, RSSI ranging, or UWB support |
| 17 | 37 | Ranging module plus RFCOMM behavior change for apps targeting API 37+ | RFCOMM InputStream.read() returns -1 on closed/dropped socket; future RFCOMM loops must handle EOF |

The app currently compiles against SDK 36 and targets API 36. On Android 16+, the owner can explicitly grant the RANGING permission and request a RangingManager capability callback. The app reports BLE Channel Sounding, BLE RSSI ranging, UWB, and Wi-Fi NAN RTT availability from the platform; Android 17 Wi-Fi Proximity Detection is queried when running on API 37+. This is a capability query only, not a ranging session or distance measurement. Android 17 behavior is documented for forward compatibility; do not claim API 37-target validation until SDK 37 builds and device tests exist.

## Capability inventory

States:
- **Available:** local hardware feature flag is present.
- **API present — verify device/peer:** Android exposes a platform API family, but chipset, firmware, stack, role, and accessory support need runtime checks.
- **Peer / connection required:** a compatible paired or connected device and supported profile/service are required.
- **Hardware-dependent:** special controller, antenna, firmware, or endpoint hardware is required.
- **Root-only diagnostics:** read-only system inspection requires explicit root authorization.
- **Not detected / not implemented:** required feature is absent or not implemented by the app.

### Bluetooth radio and core transport
- Bluetooth Classic BR/EDR
- Bluetooth Low Energy (LE)
- Dual-mode Classic + LE
- Adapter power/state
- Paired-device inventory (Connect permission)
- ACL connection/disconnection state
- GATT client and GATT server
- RFCOMM sockets / Serial Port Profile (SPP)
- LE L2CAP CoC where supported by stack and peer
- BLE advertising and scanning (not used by current A2; require separate permission and feature approval)

### Classic Bluetooth profiles
- A2DP audio source/sink
- AVRCP media remote control
- HFP/HSP hands-free audio
- HID host/device
- PBAP phonebook access
- MAP message access
- OPP object push
- PAN network access
- Profile role and codec negotiation

Profile API presence is not the same as local role support or an active connection. The app does not access contacts, messages, files, microphone audio, or network payloads. These entries are inventory/guidance only unless a separately approved feature implements them.

### BLE and audio capabilities
- GATT services and characteristics
- BLE central/peripheral role
- Extended advertising and periodic advertising
- LE Audio / LC3
- LE Audio broadcast source and assistant / Auracast
- Accessory battery service when exposed by the peer
- RSSI signal-level observation when a supported API/event supplies it

These require per-device and per-peer checks. BLE hardware presence alone does not prove each item is active.

### Advanced ranging and direction
- Bluetooth RSSI ranging: approximate/variable; not an exact distance measurement.
- Bluetooth Channel Sounding (BLE CS): Android Ranging API from API 36; both endpoints and controller/firmware must support BLE CS and a supported ranging session.
- Bluetooth Direction Finding AoA/AoD: requires suitable antenna-array/controller hardware and platform support. A phone compass reports phone orientation, not remote-device bearing.
- UWB: not Bluetooth, but Android Ranging can expose it as a peer-ranging technology when both endpoints support it.
- Wi-Fi NAN RTT: not Bluetooth; listed only as another Android Ranging technology.
- Android 17 Wi-Fi proximity detection: not Bluetooth.

The app must show unsupported/unknown when it cannot verify capability. Never invent meters, cardinal direction, signal accuracy, or confidence values.

## Root-specific feature boundary

### Supported root-enhanced work
- Verify whether this app can obtain uid=0 after explicit owner action.
- Read Bluetooth manager service diagnostics.
- Read Bluetooth-related system properties.
- Inspect Bluetooth sysfs directory metadata.
- Display a local diagnostic report after owner action, with sensitive addresses redacted by default in any future export.

### Not enabled
- Flashing Bluetooth firmware or controller NVM.
- Changing undocumented HCI/controller parameters.
- Disabling SELinux or changing kernel policy.
- Bypassing Android runtime permissions or pairing consent.
- Forcing unsupported profiles, codecs, ranging, or direction finding.
- Modifying vendor battery manager or background restrictions.
- Persisting a root daemon or silently running root commands in the background.

Any future privileged feature requires a separate threat model, explicit owner approval, narrow command allowlist, audit log, timeout, failure recovery, and device-specific verification. Root-only operations must never be prerequisites for core charger protection or A2 standard mode.

## Dashboard contract
The dashboard displays:
1. Manufacturer, brand, model, Android release, and API level.
2. Bluetooth Classic and BLE hardware flags.
3. Adapter ON/OFF/unknown state.
4. Bluetooth Connect permission state.
5. A2 enabled/disabled state and selected trusted accessory name.
6. Trusted-device baseline established/not established.
7. Android Ranging API presence and individual capability result when queried.
8. Root passive status: no common indicators / possible indicators.
9. Root authorization status: not checked / granted / denied / timed out / error.
10. A clear state label for each inventory item.

Distinguish not checked from not supported, permission required, temporarily unavailable, and supported. Passive root checks must not imply verified root status.

## Safety, privacy, and compatibility constraints
- No automatic root prompt on app launch.
- No root command unless the owner explicitly starts root diagnostics.
- Root commands are read-only and time-bounded.
- No Bluetooth scan, advertise, location, contacts, microphone, or storage permission added for the capability dashboard.
- No cloud upload, analytics, telemetry, or automatic diagnostic export.
- Do not interrupt charger alarm, authentication, alarm audio, flashlight, or local protection state.
- Preserve applicationId com.thiefalertsequre, minSdk 26, targetSdk 36, signing certificate, preferences, and upgrade-over-install behavior.
- Keep A2 separate from the v1.1.4 hardening PR and do not assign a release version without owner approval.
- CI success does not replace tests on actual Android 8–17 devices and representative accessories.

## Validation plan
1. Unit-test Android API label mapping and capability-state classification.
2. Build on SDK 36 and run the complete existing test suite.
3. Verify Android 8–11 permission and adapter paths.
4. Verify Android 12–15 Connect prompt, denial, revocation, and adapter-off states.
5. Verify Android 16 Ranging API availability separately from actual technology support.
6. Verify Android 17 RFCOMM EOF behavior if RFCOMM transport is added.
7. Test root passive detection on rooted and non-rooted devices, including hidden-root false negatives.
8. Test explicit root authorization granted, denied, cancelled, and timed out.
9. Confirm root diagnostics never modify Bluetooth state or system configuration.
10. Confirm charger alarm and A2 remain functional when root is absent, denied, or unavailable.

## Per-install controller capability probes (2026-11-12)

The capability inventory now runs locally on each installation and queries the current device rather than relying only on the Android version label.

Direct controller probes include:
- LE 2M PHY and LE Coded PHY.
- LE Extended Advertising and LE Periodic Advertising.
- Multiple BLE advertising support.
- Controller offloaded filtering and scan batching.
- Android 13+ LE Audio, Broadcast Source, and Broadcast Assistant support status.

The inventory also reports adapter presence/power, Bluetooth Connect permission state, Classic/BLE hardware feature flags, and the number of paired devices when the adapter is on and permission allows access. It does not copy paired-device names or addresses into the report.

The UI distinguishes supported by the local controller, API present but requiring further capability/session verification, compatible peer required, permission required, adapter off, unsupported/not detected, unknown, not implemented by this app, and root-only read-only diagnostics.

A controller probe returning supported does not mean the app has implemented an end-user workflow using that capability. Advertising hardware support does not mean the app starts advertising; LE Audio support does not mean the app is an audio player; profile inventory does not mean the app reads contacts/messages or transfers files. Android API errors and permission restrictions are surfaced as unknown/permission-required rather than being treated as unsupported or silently enabled.

The main dashboard displays a compact per-device summary. Settings exposes the full inventory. The inventory is refreshed when the relevant screen is opened; it is not a background scanner and does not continuously discover nearby devices.

## Owner-request persistence

The owner requested that future Bluetooth feature requests be implemented where technically possible and recorded for continuity. The project-level instruction and implementation log are maintained in docs/OWNER_BLUETOOTH_IMPLEMENTATION_POLICY.md. ChatGPT project Memory is disabled in this project, so repository documentation is the durable record rather than a claim of persistent assistant memory.


## Dedicated Bluetooth capability center UI (2026-11-12)

Bluetooth inventory is presented on its own BluetoothCenterActivity page, reachable from the dashboard and Settings. The page groups results into radio/connection, controller capabilities, profiles/transports, ranging/proximity, and root/restricted operations. Each item displays a distinct state and explanatory detail rather than placing the entire inventory in a long Settings screen or a single oversized dialog.

The page provides explicit refresh, Android Bluetooth Settings navigation, an owner-triggered Bluetooth Connect permission request when needed, an Android 16+ Ranging capability query, and optional read-only root diagnostics. Opening the page does not scan, advertise, pair, stream, or modify Bluetooth configuration. It does not show paired-device addresses.

The inventory is comprehensive for Android APIs and controller capability signals exposed to an ordinary app, but it is not a promise that every Bluetooth SIG profile, vendor extension, firmware command, or accessory feature is implemented. Profiles requiring peer negotiation remain peer-required; APIs without a reliable local hardware probe remain API-present/unknown; dangerous firmware writes remain intentionally unimplemented.


### Trusted accessory controls

The dedicated Bluetooth center also exposes A2 owner controls: choose an already-paired accessory, enable/disable separation alerts, and inspect whether the connection baseline has been established. Android 12+ Bluetooth Connect permission is requested only when the owner chooses a paired accessory or enables A2. No Scan or Advertise permission is requested. Enabling A2 does not silently re-enable the overall protection monitor; if the owner has disabled that monitor, the page explains that A2 cannot run until the monitor is explicitly re-enabled from the main dashboard. After service startup or accessory changes, the owner must reconnect the accessory to establish a fresh baseline.
