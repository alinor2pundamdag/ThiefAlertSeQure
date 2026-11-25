# Bypass Charging Integration — Product Roadmap Phase 4

**Status:** Integration framework implemented; direct OEM ON/OFF control is not yet available in this build.

## Required outcome

The intended feature is an ON/OFF control for every Android device whose hardware and firmware genuinely support Bypass Charging. Support must be established per model and firmware, not inferred from manufacturer name or Android version. The UI may show ON/OFF as verified only after a successful OEM state read-back.

## Android platform boundary

The public Android SDK exposes battery and charging information but no universal public application API to switch vendor Bypass Charging hardware. BatteryManager telemetry is not a bypass control API. AOSP Health HAL is a vendor/platform integration boundary, not a normal third-party application permission.

A normal APK cannot grant itself platform-signature or privileged permissions. A privileged system integration requires OEM-authorized deployment and the appropriate partition/permission allowlist. Device Owner status is separate enterprise enrollment and does not itself expose a universal bypass command.

## Current implementation

- BypassChargingController.kt defines the model/firmware-specific adapter contract.
- BypassChargingAdapterRegistry is intentionally empty until an OEM interface is documented and reviewed.
- The controller requires a positive capability probe, explicit authorization, and a readable current state before enabling ON/OFF.
- After a command, the controller reads the mode again. A command return value alone never produces a verified state.
- Settings displays actual mode as UNKNOWN and keeps the switch disabled when no adapter is available.
- The owner can open Android battery settings and follow model-specific manual guidance.
- No new Android permission, service, backend, telemetry, root command, hidden API, or database was added.
- No desired ON/OFF preference is replayed at boot without a certified OEM persistence contract.

## OEM feature evidence and integration status

| OEM | Public/system feature evidence | Third-party ON/OFF adapter in this build |
|---|---|---|
| HONOR | Bypass Charging exists on selected models; official guidance places it under Battery → More battery settings. Model and MagicOS version matter. | Not available; no public third-party control API verified |
| Samsung | Selected Galaxy devices offer conditional “Pause USB PD charging when gaming”; charger/game/battery conditions apply. | Not available; no public third-party control API verified |
| ASUS ROG | Selected ROG devices expose Bypass Charging / Smart Bypass in game features. | Not available; no public third-party control API verified |
| OnePlus | Bypass Charging has been reported on selected OxygenOS models; availability varies. | Not available; no public third-party control API verified |
| Xiaomi / Redmi / POCO | Game features do not establish a universal bypass control interface. | Not established |
| OPPO, realme, vivo, Motorola, Lenovo, others | No qualifying public third-party control API has been certified for this project. | Not established |

A device may have an OEM feature while ThiefAlertSeQure cannot control or read it. The UI distinguishes system feature availability from app control certification.

## Adapter certification requirements

Before registering an adapter, verify: exact model/regional SKU/firmware/API; hardware evidence; official OEM API/service or explicitly authorized privileged deployment; ON and OFF behavior; actual read-back; behavior without charger, at full battery, high temperature and OEM auto-exit; reboot persistence; timeout/authorization/service failures; safe rollback; JVM regression tests and exact-head CI. Device certification remains a separately authorized physical-device task.

## Security and privacy

- Never request WRITE_SECURE_SETTINGS, DEVICE_POWER, platform signature permissions, or other privileged access as though a runtime prompt could grant it.
- Do not use Accessibility, overlays, hidden APIs, arbitrary shell commands, su, sysfs writes, or undocumented service calls as a bypass.
- Root authorization alone is not proof of bypass hardware or a safe command. A future root bridge requires a reviewed device-specific adapter and explicit owner authorization.
- No charging mode, battery state, device identifier, or diagnostic data is uploaded.
- The Settings entry is local and adds no runtime permission.

## Acceptance result

**Verified in source design:** fail-closed adapter registry; support/authorization gates; actual-state read-back; failure/unknown states; no privileged permission or shell path; disabled UI without an adapter.

**Not implemented / not verified:** direct ON/OFF control on any OEM, model-level capability certification, persistence/reboot restoration, and physical charging-path behavior. These require a documented and authorized OEM interface.


## Public API investigation — 2026-11-25

The current adapter gap was checked against public platform and OEM documentation before considering an implementation:

- Android's public [BatteryManager API](https://developer.android.com/reference/android/os/BatteryManager) exposes charging/battery queries (including `isCharging()` and battery properties); it does not document a vendor Bypass Charging mode setter.
- [Settings.Global](https://developer.android.com/reference/android/provider/Settings.Global) documents that apps may read global settings but may not write them. A general `WRITE_SETTINGS` grant does not certify access to an OEM-private charging key or service.
- [HONOR's user guidance](https://iknow-dl.service.hihonor.com/ctkbfm/applet/simulator/en-us15903291/index.html) describes Bypass Charging as a model/software-dependent system setting. The [HONOR MDM SDK API reference](https://contentplatform-drcn.hihonorcdn.com/developerPlatform/MagicOS_MDM%E8%83%BD%E5%8A%9BSDK_API%E6%8E%A5%E5%8F%A3/MagicOS_MDM%E8%83%BD%E5%8A%9BSDK_API%E6%8E%A5%E5%8F%A3.pdf) and [certificate application guide](https://developer.honor.com/cn/kitdoc?category=System&docId=cert_apply.md&kitId=11015&navigation=guides) describe certificate-gated device-management APIs; the public API material reviewed did not identify a Bypass Charging toggle or read-back method.

**Research conclusion:** no safe, documented, universal third-party control interface was found. This does not prove that no private OEM interface exists; it means none is currently eligible for this app's adapter registry. A future adapter requires written/documented OEM authorization or a reviewed supported SDK contract, then model/firmware-specific read-back and rollback validation. Until then this requirement remains **INCOMPLETE / OEM-BLOCKED**, not 100% implemented.
