# Owner Physical-Device Acceptance Checklist — v1.1.4

**Purpose:** A manual acceptance record for the owner after the software implementation and exact-head CI are complete. This checklist is not an implementation task and does not block the assistant's non-physical coding work.

**Candidate branch:** `release/v1.1.4-integration`  
**Production identity:** `com.thiefalertsequre.app` / `1.1.4_Developer_Alinor` / versionCode `6`  
**Development test identity:** `com.thiefalertsequre.app.v114test` / `1.1.4-test` / versionCode `6`  
**Execution status:** NOT TESTED — owner to fill in after receiving the final CI-verified test APK.  
**Instrumentation / emulator:** Do not compile or run instrumentation tests or emulator scenarios in this implementation pass.

## Device and candidate record

| Field | Owner record |
|---|---|
| Device manufacturer/model/code | NOT RECORDED |
| Android/API version | NOT RECORDED |
| OEM OS/MagicOS version | NOT RECORDED |
| Build fingerprint | NOT RECORDED |
| Region/carrier (if relevant) | NOT RECORDED |
| Candidate commit SHA | Fill in from final handoff |
| APK package/version/versionCode | Fill in from installed APK |
| Test date/time and timezone | NOT RECORDED |
| Initial battery/power mode | NOT RECORDED |
| Tester | Owner |

Use PASS, FAIL, NOT TESTED, or NOT APPLICABLE. A capability is N/A only when the device genuinely lacks it; record the reason. Do not infer physical acceptance from CI, JVM tests, screenshots, or emulator results.

## Scenario matrix

| ID | Physical scenario | Expected acceptance condition | Result |
|---|---|---|---|
| DEV-01 | Install test APK beside production | Test package installs without replacing production or altering its data; verify package, version and signer | NOT TESTED |
| DEV-02 | First launch / legal disclosures | Privacy and Terms open offline; acknowledgement is explicit and persists; missing/failed content does not bypass acknowledgement | NOT TESTED |
| DEV-03 | Deny/revoke permissions | Notifications, Bluetooth, camera, location, exact alarm and full-screen access show accurate degraded readiness; no false “protected” status | NOT TESTED |
| DEV-04 | Establish connected charger baseline | ARMED only after a current live connected state is confirmed; no countdown from a snapshot alone | NOT TESTED |
| DEV-05 | Physically unplug charger | Confirmed OS unplug starts the exact configured 10-second grace from the original event time | NOT TESTED |
| DEV-06 | Reconnect within grace | Pending countdown is cancelled and state returns to ARMED only after live connected confirmation | NOT TESTED |
| DEV-07 | Unknown/flapping power state | UNKNOWN or unstable samples never create a false countdown/alarm; pending candidate revalidation is bounded and recoverable | NOT TESTED |
| DEV-08 | Inconclusive unplug / delayed revalidation | Durable same-boot candidate retries with bounded backoff; reconnect invalidates it; no false alarm after reboot/elapsed-clock reset | NOT TESTED |
| DEV-09 | Reconnect after alarm latch | ALARM_TRIGGERED remains latched; reconnect, tile, widget or notification cannot clear it | NOT TESTED |
| DEV-10 | Cancel/deny/fail owner authentication | Alarm remains active; no stale, cancelled or weak authentication clears the latch | NOT TESTED |
| DEV-11 | Successful credential / BIOMETRIC_STRONG | Only fresh Android device credential or strong biometric proof clears the alarm; verify state persistence | NOT TESTED |
| DEV-12 | Alarm sound/haptics/volume | Selected tone and haptics behave as configured; original alarm-stream volume is restored after authenticated stop | NOT TESTED |
| DEV-13 | Flashlight unavailable/busy/thermal | Flash failure or thermal suppression does not stop alarm audio; no camera capture or CAMERA permission is used for flash | NOT TESTED |
| DEV-14 | Reboot while charger is connected | Boot reconciliation uses current live power; no stale prior-boot unplug is inferred | NOT TESTED |
| DEV-15 | Reboot during unplug/countdown | Prior-boot monotonic deadline is not trusted; approved fresh grace/recovery policy is followed; alarm state remains authoritative | NOT TESTED |
| DEV-16 | Exact-alarm access denied | App reports limitation and fallback honestly; verify actual OEM delivery timing without assuming exact background delivery | NOT TESTED |
| DEV-17 | Notification/lock-screen controls | Alarm notification and full-screen behavior match granted settings; dismissing notification does not disarm or clear state | NOT TESTED |
| DEV-18 | Two trusted Bluetooth accessories | Each device has an independent baseline, identity and 8-second disconnect grace | NOT TESTED |
| DEV-19 | Bluetooth reconnect during grace | Only the matching accessory's candidate/deadline is invalidated; the other device remains protected | NOT TESTED |
| DEV-20 | Unregister trusted accessory | App trust is removed without unpairing the Android Bluetooth accessory | NOT TESTED |
| DEV-21 | Bluetooth off/on and process/service restart | UI recovers real receiver/service state; no duplicate receiver, stale baseline or false alarm | NOT TESTED |
| DEV-22 | Screen-off, Doze, battery saver and OEM background restrictions | Record monitor/ACL/alarm delivery and recovery; no claim of guaranteed delivery where OS/OEM suppresses it | NOT TESTED |
| DEV-23 | Device Owner enrollment/rollback | If owner intentionally provisions Device Owner, verify allowed restrictions and safe rollback; otherwise mark N/A | NOT TESTED |
| DEV-24 | Shutdown observation | Verify only the documented platform-supported behavior; no claim of universal shutdown interception or unauthorized lock bypass | NOT TESTED |
| DEV-25 | Explicit location consent | One-shot location is requested only after opt-in and permission; no background tracking; denial clears no protection state | NOT TESTED |
| DEV-26 | Optional front-camera evidence | Capture only from visible authenticated UI after opt-in; stopped/disabled/denied states do not capture; verify encryption and bounded size/count | NOT TESTED |
| DEV-27 | Evidence/history integrity and cleanup | Migration, fresh-auth gate, redaction, corrupt-record handling, export/delete separation, retention and actual orphan cleanup behave safely | NOT TESTED |
| DEV-28 | Backup/restore | User-selected document destination only; encrypted payload and metadata validation; malformed/oversized backup fails closed; restore does not weaken alarm/auth state | NOT TESTED |
| DEV-29 | Themes and personalization | All 120 palettes, theme transitions, preview Apply/Cancel isolation, custom icon/background/motion/audio options and dialog colors work | NOT TESTED |
| DEV-30 | Rotation, font scaling, cutouts and TalkBack | No clipped/overlapping controls; readable contrast; headings/focus/touch targets and system-bar insets remain usable | NOT TESTED |
| DEV-31 | Bypass Charging capability boundary | Without a certified OEM adapter, app reports UNKNOWN/unsupported and disables ON/OFF; manual OEM setting is not represented as app-verified | NOT TESTED |
| DEV-32 | Owner-initiated update | Update offered only in allowed protection state; HTTPS, digest, package, version and signer mismatch reject; Android installer confirmation/cancel path works | NOT TESTED |
| DEV-33 | Tiles, widgets and state projections | Refresh/recreate widgets and Quick Settings tile; actions cannot disarm, reset baselines or clear a latched alarm without owner auth | NOT TESTED |
| DEV-34 | Lifecycle/resource soak | Repeated navigation, background/foreground, Activity recreation, service restart, low storage and offline network; no stale camera/audio/receiver/worker, unbounded growth or lost persisted alarm state | NOT TESTED |

## Evidence record (repeat per scenario)

- Scenario ID and result:
- Device/API/OEM build and candidate SHA:
- Initial state/settings:
- Action and local timestamp:
- Expected vs observed:
- Relevant event-history entry/error:
- Non-secret log/screenshot evidence path:
- Recovery/cleanup completed:
- Notes or follow-up defect:

## Safe cleanup

1. Save only relevant, non-secret `logcat -v threadtime`, alarm/service/package snapshots and screenshots; redact personal data.
2. Stop a latched alarm only through fresh Android credential or BIOMETRIC_STRONG authentication; verify persisted state and sound/flash cleanup.
3. Restore prior monitor, trusted-accessory and Android settings. Removing app trust must not unpair devices.
4. Do not clear app data, uninstall the production package, factory-reset, bypass authentication, or leave Device Owner/Lock Task restrictions enabled as test cleanup.
5. On failure, preserve state and evidence; stop destructive retries until root cause is understood.

## Acceptance summary

- PASS: 0 / 34 recorded
- FAIL: 0 / 34 recorded
- NOT TESTED: 34 / 34
- Physical acceptance: NOT TESTED — owner execution pending

Never mark this record PASS based on GitHub Actions. Physical acceptance is owner-run and separate from software implementation completion.
