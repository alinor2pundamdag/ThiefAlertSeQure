# Phase Two — charger and trusted Bluetooth detection

This document records the non-physical-device engineering contract for Phase Two. Emulator/CI evidence does not substitute for physical-device acceptance.

## Charger and power detection

- `PowerTransitionReceiver` captures the OS event's wall-clock and elapsed-realtime timestamps before asynchronous queueing.
- The live `ACTION_BATTERY_CHANGED` snapshot must produce three consecutive matching samples, with a five-sample hard cap and 100 ms spacing. Stable confirmation normally adds about 200 ms; the cap is about 400 ms, excluding Android broadcast delivery and hardware latency.
- The ten-second charger grace period is anchored to the original disconnect receipt, not the end of sampling.
- Unknown or opposite samples reset the consecutive count. A disconnected snapshot alone never creates a countdown without a previously confirmed connected baseline and a valid disconnect event.
- Pending disconnect provenance is persisted before confirmation so a restarted monitor can revalidate same-boot identity and current power state. Reconnect increments the generation and cancels the candidate/countdown. Stale or cross-boot candidates cannot arm an alarm.
- If the confirmed unplug cannot commit the `COUNTDOWN` state, one durable recovery callback retries the persisted candidate. That retry is deliberately one-shot; if storage still fails, the marker remains for the next monitor-start recovery instead of creating an unbounded alarm loop.

## Trusted Bluetooth separation (A2)

- A2 is opt-in and local-only. It uses paired-device identity; it does not scan, pair, or request location.
- The foreground service registers the live ACL receiver while A2 and the monitor are active. A manifest ACL receiver is also enabled only while the monitor, A2, and `BLUETOOTH_CONNECT` permission are active.
- The manifest receiver is intentionally `exported=true`: Android's Bluetooth stack can send from a privileged non-system UID. The receiver filter is limited to the platform-protected `ACL_CONNECTED` and `ACL_DISCONNECTED` actions; the controller keeps the component disabled outside the active A2 state. No new permission was added for this correction.
- A single serialized event queue and the encrypted per-device registry make duplicate deliveries idempotent. Per-device and monitor revisions, the connected baseline, boot identity, and the expected event timestamp prevent stale callbacks from latching an alarm or cancelling another device's deadline.
- Each accessory has its own encrypted AlarmManager deadline and immutable PendingIntent identity. Reconnect cancels both exact and fallback callbacks for that accessory; unregistering removes only this app's trust relationship and does not unpair Android Bluetooth.
- On reboot, old-boot deadlines and all connection baselines are invalidated. A fresh current-boot ACL connection event is required before a later disconnect can start an A2 grace period.
- When the service is alive, the Handler path is the fast path. The manifest receiver plus PendingIntent deadline provides a durable process-absent path where Android permits it. Force-stop remains outside app control.

## Exact-alarm, Doze, and background-service limits

- `SCHEDULE_EXACT_ALARM` is user-controlled special app access. When available, A2 schedules `setExactAndAllowWhileIdle`; a distinct `setAndAllowWhileIdle` fallback is also retained so exact-alarm revocation does not remove the only persisted callback.
- Android can throttle idle exact alarms and defer inexact alarms, particularly in Doze. The eight-second A2 grace period is a target, not a guaranteed wall-clock delivery time.
- Android explicitly exempts exact alarms from background foreground-service launch restrictions. An inexact fallback does not carry that documented exemption. If it fires while the app process/service is absent, the callback can latch `ALARM_TRIGGERED` but Android may reject starting the continuous audio foreground service.
- That failure now enters `AlarmRecoveryCoordinator`: an owner-visible recovery notice is attempted and up to three bounded retries are scheduled. The notice requires `POST_NOTIFICATIONS`; if notifications are denied or Android continues to block background playback, the owner must reopen the app to resume the active alarm. The persisted alarm remains latched until successful owner authentication.
- Do not claim zero latency, guaranteed eight-second playback without exact-alarm access, or immunity to force-stop/OEM battery controls. No battery-optimization exemption or other new intrusive permission was added.

## Automated evidence

- Unit: `PowerTransitionSamplingPolicyTest`, `A2DisconnectPolicyTest`, `A2DisconnectSchedulerMergeTest`, and `A2BluetoothEventQueueTest`.
- Instrumentation: `PowerTransitionRecoveryInstrumentationTest`, `A2DisconnectSchedulerInstrumentationTest`, `A2BluetoothReceiverControllerInstrumentationTest`, `A2BluetoothAclReceiverInstrumentationTest`, `AlarmSchedulerInstrumentationTest`, and `AlarmDeadlineReceiverInstrumentationTest`.
- The receiver-controller test verifies the manifest receiver's exported contract and enable/disable gating. The shared-processor test runs with the service stopped; it does not kill the app process or prove actual Bluetooth-stack delivery on every OEM.
- Android's broadcast guidance explains why Bluetooth-originated broadcasts may require an exported receiver, and the alarm/FGS guidance defines the exact-versus-inexact background-start boundary:
  - [Broadcast receiver guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts)
  - [Manifest implicit-broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
  - [Alarm scheduling and exact-alarm permission](https://developer.android.com/develop/background-work/services/alarms)
  - [Background foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

## Explicitly outstanding physical-device acceptance

Physical-device testing is excluded from this engineering task and remains outstanding. On the target phone, verify actual ACL delivery after process death, Doze behavior, exact-alarm access denied and revoked, notification permission denied, reboot, multi-device identity, rapid reconnect/disconnect, charger transitions, OEM battery controls, and audible alarm recovery. Record device model, Android/MagicOS version, and observed timing. Do not represent emulator CI as physical-device acceptance.
