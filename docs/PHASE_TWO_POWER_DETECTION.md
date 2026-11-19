# Phase Two — charger and trusted Bluetooth detection

This document records the non-physical-device engineering contract for Phase Two. Emulator/CI evidence does not substitute for physical-device acceptance.

## Charger and power detection

- `PowerTransitionReceiver` captures the OS event's wall-clock and elapsed-realtime timestamps before asynchronous queueing.
- The receiver is manifest-registered for Android's protected `ACTION_POWER_CONNECTED` and `ACTION_POWER_DISCONNECTED` broadcasts. Android documents these actions as receivable through manifest receivers and as waking apps; the monitor service no longer registers a duplicate runtime receiver.
- The live `ACTION_BATTERY_CHANGED` snapshot must produce three consecutive matching samples, with a five-sample hard cap and 100 ms spacing. Stable confirmation normally adds about 200 ms; the cap is about 400 ms, excluding Android broadcast delivery and hardware latency.
- The ten-second charger grace period is anchored to the original disconnect receipt, not the end of sampling.
- Unknown or opposite samples reset the consecutive count. A disconnected snapshot alone never creates a countdown without a previously confirmed connected baseline and a valid disconnect event.
- Pending disconnect provenance is persisted before confirmation so a restarted monitor can revalidate same-boot identity and current power state. Reconnect increments the generation and cancels the candidate/countdown. If a newer unplug arrives while an older countdown is still persisted after a failed reconnect-state commit, the newer event replaces the old deadline and receives a fresh ten-second grace period.
- If the confirmed unplug cannot commit the `COUNTDOWN` state, one durable recovery callback retries the persisted candidate. That retry is deliberately one-shot; if storage still fails, the marker remains for the next monitor-start recovery instead of creating an unbounded alarm loop. Recovery and the live confirmation path both recheck the monitor opt-out, so a queued broadcast cannot start a countdown after the owner turns monitoring OFF.
- At countdown expiry, the service requires a confirmed `updatePersisted` result before treating reconciliation as complete. A failed commit schedules a one-second in-process retry and an AlarmManager retry, avoiding an immediate Handler spin and an uncommitted alarm transition.
- Charger and A2 schedulers attempt exact and inexact callbacks independently. If Android accepts either callback, it remains scheduled even when the other call fails; scheduling is reported as failed only when neither callback is accepted.
- Pending charger recovery and durable A2 deadlines require a verifiable boot identity. If Android does not expose `BOOT_COUNT`, the live in-process paths still work, but an event that has not yet committed its durable deadline is not replayed after process death; it waits for a fresh connected baseline rather than risking cross-boot elapsed-time confusion.

## Trusted Bluetooth separation (A2)

- A2 is opt-in and local-only. It uses paired-device identity; it does not scan, pair, or request location.
- The foreground service registers the live ACL receiver while A2 and the monitor are active. A manifest ACL receiver is also enabled only while the monitor, A2, and `BLUETOOTH_CONNECT` permission are active.
- The manifest receiver is intentionally `exported=true`: Android's Bluetooth stack can send from a privileged non-system UID. The receiver filter is limited to the platform-protected `ACL_CONNECTED` and `ACL_DISCONNECTED` actions; the controller keeps the component disabled outside the active A2 state. On boot or receiver re-enable, it requires a successful encrypted-baseline reset first; if the baseline is corrupt, the manifest and runtime listeners remain disabled and the source is preserved. No new permission was added for this correction.
- A single serialized event queue and the encrypted per-device registry make duplicate deliveries idempotent. Per-device and monitor revisions, the connected baseline, boot identity, and the expected event timestamp prevent stale callbacks from latching an alarm or cancelling another device's deadline.
- Each accessory has its own encrypted AlarmManager deadline and immutable PendingIntent identity. Reconnect cancels both exact and fallback callbacks for that accessory; unregistering removes only this app's trust relationship and does not unpair Android Bluetooth. If Android rejects both callback channels, the encrypted pending record is retained and retried when the monitor service starts again; no immediate process-absent delivery is claimed until Android accepts a callback.
- Exact and inexact callbacks are independently attempted. If Android accepts either one, that callback remains scheduled even when the other scheduling call fails; the deadline is rejected only when neither callback is accepted.
- An unreadable or corrupt encrypted connection baseline is treated as connected/unknown during deadline validation, never as proof of disconnection. Registry mutations stop and preserve the corrupt source rather than overwriting it.
- On reboot, old-boot deadlines are cleared and the manifest ACL receiver is gated behind a successful baseline reset. A disabled receiver is not re-enabled until the encrypted baseline is readable and cleared; corrupt baseline data is preserved and the runtime/manifest listeners remain disabled until reset succeeds. A fresh current-boot ACL connection event is required before a later disconnect can start an A2 grace period.
- When the service is alive, the Handler path is the fast path. The manifest receiver plus PendingIntent deadline provides a durable process-absent path when a verifiable boot identity is available. Without it, the in-process timer remains available but a process-absent callback is not persisted. Force-stop remains outside app control.

## Exact-alarm, Doze, and background-service limits

- `SCHEDULE_EXACT_ALARM` is user-controlled special app access. When available, A2 schedules `setExactAndAllowWhileIdle`; a distinct `setAndAllowWhileIdle` fallback is also retained so exact-alarm revocation does not remove the only persisted callback.
- Android can throttle idle exact alarms and defer inexact alarms, particularly in Doze. The eight-second A2 grace period is a target, not a guaranteed wall-clock delivery time.
- Android explicitly exempts exact alarms from background foreground-service launch restrictions. An inexact fallback does not carry that documented exemption. If it fires while the app process/service is absent, the callback can latch `ALARM_TRIGGERED` but Android may reject starting the continuous audio foreground service.
- That failure now enters `AlarmRecoveryCoordinator`: an owner-visible recovery notice is attempted and up to three bounded retries are scheduled. The notice requires `POST_NOTIFICATIONS`; if notifications are denied or Android continues to block background playback, the owner must reopen the app to resume the active alarm. The persisted alarm remains latched until successful owner authentication.
- Do not claim zero latency, guaranteed eight-second playback without exact-alarm access, or immunity to force-stop/OEM battery controls. No battery-optimization exemption or other new intrusive permission was added.

## Automated evidence

- Unit: `PowerTransitionSamplingPolicyTest`, `A2DisconnectPolicyTest`, `A2DisconnectSchedulerMergeTest`, and `A2BluetoothEventQueueTest`.
- Instrumentation: `PowerTransitionRecoveryInstrumentationTest`, `A2DisconnectSchedulerInstrumentationTest`, `A2BluetoothReceiverControllerInstrumentationTest`, `A2BluetoothAclReceiverInstrumentationTest`, `TrustedBluetoothRegistryEncryptionTest`, `AlarmSchedulerInstrumentationTest`, and `AlarmDeadlineReceiverInstrumentationTest`.
- The receiver-controller tests verify exported/enable gating, fresh-baseline reset before re-enable, and fail-closed behavior when a baseline is corrupt. The scheduler instrumentation test verifies that a persisted deadline can be reinstalled after service restart. The shared-processor test runs with the service stopped; it does not kill the app process or prove actual Bluetooth-stack delivery on every OEM.
- Android's broadcast guidance explains why Bluetooth-originated broadcasts may require an exported receiver, and the alarm/FGS guidance defines the exact-versus-inexact background-start boundary:
  - [Broadcast receiver guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts)
  - [Manifest implicit-broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
  - [Alarm scheduling and exact-alarm permission](https://developer.android.com/develop/background-work/services/alarms)
  - [Background foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

## Explicitly outstanding physical-device acceptance

Physical-device testing is excluded from this engineering task and remains outstanding. On the target phone, verify actual ACL delivery after process death, Doze behavior, exact-alarm access denied and revoked, notification permission denied, reboot, multi-device identity, rapid reconnect/disconnect, charger transitions, OEM battery controls, and audible alarm recovery. Record device model, Android/MagicOS version, and observed timing. Do not represent emulator CI as physical-device acceptance.
