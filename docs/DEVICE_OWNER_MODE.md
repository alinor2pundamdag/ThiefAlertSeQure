<div align="right">
  <a href="../README.md"><img src="../ThiefAlertSeQureIcon.jpg" alt="ThiefAlertSeQure icon" width="64" /></a>
</div>

[← Back to ThiefAlertSeQure overview](../README.md)

---

# Managed Device Protection (Android Enterprise)

## Purpose and safety model
ThiefAlertSeQure has two explicit modes:

1. Standard Android mode: a normal local app using Android Keyguard, BIOMETRIC_STRONG, charger broadcasts, AlarmManager, a media-playback foreground service and a visible ongoing notification.
2. Managed Device Owner mode: optional enterprise/kiosk hardening for a device intentionally enrolled as fully managed. It uses Android DevicePolicyManager and Lock Task Mode. This is not an ordinary runtime permission and the app cannot grant itself Device Owner status.

No hidden accessibility service, overlay interception, root exploit, firmware patch, bootloader change, or covert persistence is used.

## What managed mode adds
Only when Android reports this app as Device Owner, the user can explicitly enable:
- Allowlist only com.thiefalertsequre.app for Lock Task Mode.
- Keep Keyguard enabled in kiosk mode.
- Omit LOCK_TASK_FEATURE_GLOBAL_ACTIONS, hiding the normal global-actions power dialog while Lock Task Mode is active on supported Android builds.
- Apply supported restrictions to block factory reset through Settings, safe boot, USB file transfer, adding users and debugging.
- Keep an in-app, explicit exit action that removes the policies.

This applies only while the app is actually in Lock Task Mode. It does not stop hardware long-press forced shutdown, battery disconnect, bootloader/recovery reset, OEM service mode, or firmware-level actions.

## Enrollment prerequisites
Device Owner provisioning is intended for a new or factory-reset device. Use official Android Enterprise QR provisioning where available. An authorized developer/test workflow may use:

    adb shell dpm set-device-owner com.thiefalertsequre.app/.ThiefAlertAdminReceiver

This is not a normal app permission request. It can fail if the device is already provisioned, has accounts, has another device owner, or the OEM build disallows the provisioning path. Back up all data before any factory reset or test enrollment. Do not use Device Owner mode on a work-managed, school-managed, shared, or another person's device without administrator authorization.

Official enrollment guide: https://developers.google.com/android/management/provision-device

## Enable and disable
1. Install and open ThiefAlertSeQure on the intentionally prepared test device.
2. Verify Device Owner is reported in the app's Device Hardening section.
3. Read the in-app warning and explicitly choose Enable managed lockdown.
4. Confirm kiosk mode starts and the in-app Disable managed lockdown control remains available.
5. To exit, choose Disable managed lockdown. This calls stopLockTask(), clears restrictions, restores global actions and removes the app from the lock-task allowlist.

If provisioning has not occurred, the button only displays enrollment guidance. It does not attempt privilege escalation.

## Platform-specific boundaries

| Requirement | Standard app | Device Owner / Lock Task | OEM / firmware |
|---|---|---|---|
| Hide normal power dialog | No | Yes, while Lock Task is active and supported | Additional controls may exist |
| Prevent hardware forced shutdown | No | No universal guarantee | Requires OEM/firmware support |
| Restrict Settings factory reset | No | Yes, supported policy | OEM controls may add more |
| Block recovery/bootloader wipe | No | No universal guarantee | OEM/bootloader policy |
| Hide Home/Recents/status UI | No | Yes, by Lock Task features | Varies |
| Globally block volume keys | No | No standard DPM API | OEM-specific only |
| Set alarm stream to max available | Yes, subject to user/OEM policy | Same | Device-dependent |
| Force above 100% volume | No | No | Not a supported Android control |
| Survive force-stop/uninstall | No | Some user actions are restricted; not absolute | System/OEM-level |
| Start media playback directly from BOOT_COMPLETED on Android 15+ | No | No direct exception | OEM/system integration |
| Resume after boot with exact alarm access | Best effort via exact alarm | Same | Device-dependent |
| Override DND globally on target API 35+ | No | Not through this app | User/OEM system policy |

## Recovery and rollback
- The in-app exit action is the normal rollback path.
- If the app is damaged before policies can be cleared, Device Owner policies may require an authorized management recovery path or a factory reset.
- Do not enable kiosk mode until the exit action has been tested on the exact device build.
- Keep owner credentials and recovery instructions available.
