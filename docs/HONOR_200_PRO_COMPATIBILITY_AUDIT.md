# HONOR 200 Pro background-compatibility audit

**Scope:** ThiefAlertSeQure background charger monitoring, alarm recovery, reboot handling, notifications and optional trusted-Bluetooth monitoring on the HONOR 200 Pro.

**Evidence status:** repository inspection and official HONOR support guidance reviewed. Physical HONOR 200 Pro acceptance is pending; CI emulators cannot reproduce MagicOS power-manager policy or real charger/Bluetooth hardware.

## HONOR/MagicOS controls to verify on the actual phone

HONOR documents the following controls for apps that need reliable background operation. Menu names may vary by MagicOS release, region and firmware.

1. **App launch:** Settings → Apps → App launch → ThiefAlertSeQure. Turn **Manage automatically** off, then enable **Auto-launch**, **Secondary launch**, and **Run in background**.
2. **Battery optimization:** search Settings for **Battery optimization**, select **All apps**, open ThiefAlertSeQure, and select **Don't allow** (or the equivalent non-optimized setting shown by the phone).
3. **Recents lock:** open Recents and swipe down on the ThiefAlertSeQure card until its lock icon is visible.
4. **Power saving:** for reliability acceptance, disable Power saving and Ultra power saving. These modes can restrict background work.
5. **Notifications:** keep the app's protection notification/channel enabled and grant notification permission where Android requires it.
6. **Force stop:** do not force-stop the app during normal monitoring acceptance. Android does not promise automatic restart after an explicit force-stop.

Official HONOR guidance:
- [Background app restart behavior](https://www.honor.com/global/support/content/en-us00406916/)
- [Apps launch slowly](https://www.honor.com/uk/support/content/en-us00406923/)
- [Third-party notification troubleshooting](https://www.honor.com/sa-en/support/content/en-us00685514/)
- [Power Management](https://www.honor.com/global/support/content/en-us15887652/)

## Code paths reviewed

- `AndroidDeviceCompatibility.kt`: identifies HONOR/Huawei and provides manual MagicOS guidance; uses public Settings destinations rather than brittle undocumented OEM component names.
- `ProtectionMonitorService.kt`: persistent foreground monitor; power and optional Bluetooth event handling.
- `PowerTransitionReceiver.kt` / `PowerTransitionCoordinator.kt`: confirmed charger transition and debounce policy.
- `BootReceiver.kt`: boot baseline reconciliation and package-replacement recovery.
- `AlarmScheduler.kt` / `AlarmDeadlineReceiver.kt`: deadline scheduling and recovery.
- `AndroidManifest.xml`: boot receiver, foreground services, notifications, exact alarm, wake lock and related declarations.
- `SetupStatusActivity.kt`: owner-facing permission and background setup guidance.

## Required physical acceptance matrix

Record the exact HONOR model, Android version, MagicOS version/build number, app commit, date and evidence for every result.

| Scenario | Required result |
|---|---|
| App launch controls | All three App launch controls enabled; guidance matches actual menu labels |
| Battery optimization | App excluded from optimization where the firmware exposes the control |
| Recents clear | Locked app remains available; no claim that this overrides force-stop or system resource termination |
| Screen off / idle | Charger monitoring remains active for a controlled 30–60 minute interval |
| Battery Saver | Record behavior with it off and separately with it on; document expected restrictions |
| Charger connect/disconnect | Only a confirmed physical unplug after a confirmed connected baseline starts the countdown |
| Transient/unknown power state | No false countdown or alarm |
| Reconnect during countdown | Countdown cancels according to existing policy |
| Reboot while charging | Baseline is re-established without a false unplug alarm |
| Reboot while countdown active | Recovery follows persisted deadline/boot policy; no stale previous-boot baseline |
| App package replacement | Monitoring/alarm state is reconciled after `MY_PACKAGE_REPLACED` |
| Notifications | Protection notification visible and not silently blocked |
| Trusted Bluetooth | Test each registered accessory independently, including screen-off disconnect/reconnect |
| Battery restrictions / process death | Record whether MagicOS stops the service and whether the app reports/reconciles that state on next launch |

## Release decision

Do not mark HONOR 200 Pro compatibility as passed until the physical matrix is completed on the owner's actual firmware. Static CI checks do not validate MagicOS background scheduling, vendor battery-manager behavior, charger hardware, Bluetooth radio behavior, or alarm audibility. These require physical-device evidence.

## Evidence capture and safe recovery procedure

Before each physical scenario, record:
- Exact model, Android version/API, MagicOS version/build fingerprint, app package/version, tested commit SHA, date and local time.
- Monitor enabled/running state, notification permission/channel state, exact-alarm access, battery optimization setting, Bluetooth permission and A2 enabled state.
- Scenario ID, starting protection state, action timestamps, expected result, observed result, and pass/fail.

For reproducible logs, connect ADB before the test and capture:
```sh
adb logcat -c
adb logcat -v threadtime > thiefalertsequre-device-test-log.txt
```
Stop the log capture after the scenario. Also capture non-secret platform snapshots when a failure occurs:
```sh
adb shell dumpsys alarm > thiefalertsequre-alarms.txt
adb shell dumpsys activity services > thiefalertsequre-services.txt
adb shell dumpsys package com.thiefalertsequre.app > thiefalertsequre-package.txt
```
Review and redact personal identifiers or unrelated app data before sharing logs.

After each alarm scenario:
1. Complete the app-launched Android owner credential or BIOMETRIC_STRONG prompt; never use ordinary phone unlock as an alarm-clear test.
2. Confirm the persisted protection state is DISARMED or ARMED according to the connected-charger baseline, and confirm the alarm notification/audio/torch stop.
3. Confirm the original alarm-stream volume is restored; record any restore warning before proceeding.
4. Confirm only the test deadline is cancelled and the owner's pre-existing monitor preference/service state is restored.
5. Restore test-only A2 registrations/settings. Removing app trust must not unpair the Android accessory.
6. Do not clear app data, uninstall the production package, factory-reset the phone, or leave Device Owner Lock Task enabled as a test cleanup shortcut.

If a scenario fails, preserve the app state and logs, record the exact timestamp and commit, and stop repeating destructive steps until the cause is understood. For managed-mode tests, use the app's explicit rollback action and verify that Home, Overview, notifications, system information, global actions and Keyguard are available before ending the test.

This section is a prepared test procedure only. It does not claim that any new physical-device scenario has been executed.
