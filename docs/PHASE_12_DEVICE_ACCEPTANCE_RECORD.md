# Phase 12 — Physical-device acceptance record

**Project:** ThiefAlertSeQure  
**Candidate branch:** `release/v1.1.5-candidate`  
**Current clean release candidate:** `56418b68e422ded696705fd4d07d76605151d19a` passed Android CI #45307869027 and CodeQL #45307868989; physical testing not run  
**Current build:** `1.1.5-test` / versionCode `7`  
**Development package:** `com.thiefalertsequre.app.v114test`  
**Status:** **NOT TESTED — physical-device acceptance remains pending**

This document is a blank record for real physical-device execution. CI, JVM tests, source checks, and emulator results do not count as physical acceptance. No result may be marked PASS until it is observed on the named device and build.

## Device and build identity

| Field | Record |
|---|---|
| Device model / model code | NOT RECORDED |
| Android version / API level | NOT RECORDED |
| OEM OS / MagicOS version | NOT RECORDED |
| Build fingerprint | NOT RECORDED |
| Region / carrier (if relevant) | NOT RECORDED |
| App package / version / versionCode | `com.thiefalertsequre.app.v114test` / `1.1.5-test` / `7` |
| Tested commit | NOT TESTED — candidate source head `56418b68e422ded696705fd4d07d76605151d19a` |
| Test date / local timezone | NOT RECORDED |
| Tester | NOT RECORDED |
| Initial battery / power mode | NOT RECORDED |

## Result definitions

- **PASS** — expected behavior observed and supporting evidence saved.
- **FAIL** — expected behavior not observed; preserve state/logs and record the exact failure.
- **NOT TESTED** — not executed on this physical device.
- **NOT APPLICABLE** — only when the hardware/API genuinely does not expose the capability; explain why.

## Scenario matrix

| ID | Physical scenario | Expected acceptance condition | Result |
|---|---|---|---|
| P12-01 | Install development APK side-by-side | Development package installs without replacing production package or altering production app data | NOT TESTED |
| P12-02 | First launch / privacy notice | Notice is shown once until acknowledged; acknowledgement persists locally | NOT TESTED |
| P12-03 | Charger connected baseline | Protection reports ARMED only after a live connected baseline is confirmed | NOT TESTED |
| P12-04 | Physical charger unplug | A confirmed unplug starts exactly the 10-second grace period | NOT TESTED |
| P12-05 | Transient / unknown power snapshot | Unknown state does not start a countdown or alarm | NOT TESTED |
| P12-06 | Reconnect before 10-second deadline | Pending countdown cancels and state returns to ARMED | NOT TESTED |
| P12-07 | Reconnect after alarm latch | ALARM_TRIGGERED remains latched; reconnect does not clear it | NOT TESTED |
| P12-08 | Cancel or fail owner authentication | Alarm remains active; no unauthenticated path clears it | NOT TESTED |
| P12-09 | Successful Android credential / strong biometric | Only fresh Android device-credential or BIOMETRIC_STRONG success clears the alarm | NOT TESTED |
| P12-10 | Alarm volume and recovery | Prior alarm-stream volume is restored after authenticated disarm | NOT TESTED |
| P12-11 | Rear LED unavailable / busy | Audio alarm remains independent; no camera capture or CAMERA permission is used | NOT TESTED |
| P12-12 | Reboot while charging | Live charger state is revalidated; no false unplug alarm occurs | NOT TESTED |
| P12-13 | Reboot while unplugged / pending countdown | Recovery uses the approved fresh-sample and grace-period policy; no stale-boot deadline is trusted | NOT TESTED |
| P12-14 | Notification permission denied | App reports the degraded state accurately; core protection state is not silently cleared | NOT TESTED |
| P12-15 | Exact-alarm access denied | Inexact fallback limitation is disclosed; no promise of exact 8/10-second background delivery | NOT TESTED |
| P12-16 | Two trusted Bluetooth accessories | Each accessory has an independent fresh baseline and independent 8-second disconnect grace | NOT TESTED |
| P12-17 | Bluetooth reconnect during grace | Only that accessory's matching pending deadline is invalidated | NOT TESTED |
| P12-18 | Unregister trusted accessory | App trust is removed; Android pairing remains unchanged | NOT TESTED |
| P12-19 | Screen off / battery saver / OEM background limits | Record monitor, ACL receiver and alarm delivery behavior; do not infer guaranteed delivery | NOT TESTED |
| P12-20 | Foreground service stop / restart / process recreation | UI reports actual runtime state; recovery does not create false baseline or clear a latched alarm | NOT TESTED |
| P12-21 | Themes / rotation / font scaling / cutouts | All 16 themes, dialogs, insets, text scaling and screen recreation remain usable | NOT TESTED |
| P12-22 | TalkBack / accessibility | Page headings, focus order, labels and touch targets are usable with TalkBack | NOT TESTED |
| P12-23 | Android 16+ ranging capability check | Owner action only; local availability result or safe timeout; no peer discovery, session, location access or fabricated distance | NOT TESTED |
| P12-24 | Root diagnostics declined / unavailable | No root action without explicit owner confirmation; denial/unavailability is reported safely | NOT TESTED |
| P12-25 | Update identity / package boundary | Development APK is not offered as production update; production package/version/signer checks remain intact | NOT TESTED |

## Evidence record — repeat for each scenario

| Field | Value |
|---|---|
| Scenario ID | NOT RECORDED |
| Start state / settings | NOT RECORDED |
| Action and timestamp | NOT RECORDED |
| Expected result | NOT RECORDED |
| Observed result | NOT RECORDED |
| PASS / FAIL / NOT TESTED | NOT TESTED |
| Log / screenshot evidence path | NOT RECORDED |
| App event-history reference | NOT RECORDED |
| Relevant error code / exception | NONE RECORDED |
| Recovery completed and verified | NOT TESTED |

## Safe evidence and cleanup rules

1. Capture the exact device model, Android/API, OEM build fingerprint, app package/version, candidate commit, date/time and relevant settings before each scenario.
2. Save `adb logcat -v threadtime` output and only the relevant non-secret `dumpsys alarm`, service and package snapshots. Redact personal identifiers and unrelated app data before sharing.
3. For alarm scenarios, stop only through the app's fresh Android credential or BIOMETRIC_STRONG flow. Confirm the persisted state, audio/torch stop, and volume restoration.
4. Restore the prior monitor preference, A2 registration and Android accessory state. Removing app trust must not unpair an accessory.
5. Never clear app data, uninstall the production package, factory-reset the phone, bypass authentication, or leave Device Owner / Lock Task restrictions enabled as test cleanup.
6. On any failure, preserve state and logs and stop destructive retries until the cause is understood.

## Acceptance decision

- Physical scenarios passed: **0 / 25 recorded**
- Physical scenarios failed: **0 / 25 recorded**
- Physical scenarios not tested: **25 / 25**
- Overall physical acceptance: **NOT TESTED**
- Final reviewer / approval: NOT RECORDED

Do not change this status to PASS based on GitHub Actions. Physical acceptance closes only after real-device evidence is recorded and reviewed.
