# Owner Bluetooth Implementation Policy

Status: active project instruction, not a release assignment.

## Owner direction
- Treat Bluetooth capability work as an ongoing, comprehensive engineering scope for ThiefAlertSeQure.
- Whenever the owner requests a Bluetooth feature, inspect the existing implementation first, implement the feature when Android APIs and hardware permit, add tests, update the capability inventory and compatibility documentation, and record the result here.
- Automatically inspect the capabilities of each device where the app is installed. Use local Android APIs and controller capability probes; never upload device fingerprints, Bluetooth addresses, root status, or diagnostics.
- Enable a feature only when the local device, Android version, permissions, controller/firmware, and remote peer support it.
- For unsupported, unverified, permission-blocked, adapter-off, or peer-dependent capabilities, display the precise reason and a safe next step. Never mark a feature active based only on Android API presence.
- Root is optional. Core protection and standard Bluetooth functions must not depend on root. Root diagnostics require an explicit owner action, narrow read-only commands, timeouts, and no silent privileged background activity.
- Preserve applicationId, minSdk 26, the existing release signing identity, preferences, local data, charger alarm, authentication flow, and upgrade compatibility.
- Do not assign a release version, merge a draft PR, create a tag, or publish an APK without explicit owner approval.
- Do not promise universal Bluetooth compatibility. Device and peer limitations are physical/platform constraints, not implementation defects.

## Implementation record
- 2026-11-12: Expanded runtime capability inventory to probe adapter/controller support for LE PHYs, extended/periodic advertising, multi-advertising, offloaded scan filtering/batching, and Android 13+ LE Audio source/assistant support. Added explicit states for permission required, adapter off, peer required, unknown, unsupported, and not implemented.
- 2026-11-12: Capability inventory distinguishes actual controller probes from API-only/profile inventory. A2 ACL separation remains implemented; physical accessory/OEM validation is pending.

## Feature-request log
Append each future owner-requested Bluetooth feature here with date, branch/PR, implementation status, tests, and hardware validation status.
