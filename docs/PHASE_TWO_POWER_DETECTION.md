# Phase Two — Power Detection and Charger Reliability

Status: implementation and automated verification in progress; this document records the source contract, not universal device certification.

## Detection path

1. Android emits the protected `ACTION_POWER_CONNECTED` or `ACTION_POWER_DISCONNECTED` system broadcast.
2. `ProtectionMonitorService` registers `PowerTransitionReceiver` while the user-enabled foreground monitor is active.
3. `PowerTransitionReceiver` captures wall-clock, elapsed-realtime, and boot identity immediately, then queues work on the single-thread executor to preserve event order.
4. `ExternalPowerState` reads Android's sticky `ACTION_BATTERY_CHANGED` snapshot and interprets `EXTRA_PLUGGED`. Missing or malformed values remain unknown; charging-current/charging-status is not substituted for external-power presence.
5. `PowerTransitionSamplingPolicy` confirms three consecutive matching samples, at 100 ms intervals, with a five-read upper bound. It exits immediately after the third match. Unknown or opposite samples reset the consecutive count.
6. `PowerTransitionCoordinator` validates the current-boot connected baseline, generation token, persisted candidate provenance, and current live power state before it changes protection state.
7. The disconnect countdown uses the original OS event's elapsed-realtime timestamp. Confirmation latency does not add to the owner's 10-second grace period.
8. Reconnection invalidates the pending generation, re-arms protection, and cancels only the matching deadline. A latched alarm is never cleared by power events.
9. Persisted pending candidates are replayed only when generation and same-boot identity still match. A repeated recovery cannot create a second countdown or move the original deadline.

## Latency and reliability policy

- Stable-state sampling normally needs three reads and two 100 ms pauses (about 200 ms of sampling wait, plus Android snapshot and persistence overhead), rather than always waiting through all five reads.
- The five-read cap remains for transient/stale snapshots. Reducing the sample interval further without device evidence would trade reliability for an unverified latency claim.
- The disconnect event time is captured before sampling and queueing. The countdown deadline is therefore anchored to the OS event, not to the completion of debounce.
- The single-thread event queue is intentionally retained: parallel handling could allow a stale disconnect to race a newer reconnect.
- A failed durable write never authorizes an alarm transition. Unknown state never counts as connected or disconnected evidence.

## Related device/phone connection detection

The existing A2 trusted-Bluetooth separation path remains a separate signal from charger power detection:

- It listens for Android ACL connect/disconnect events only while the optional A2 feature is enabled and Bluetooth Connect permission is available.
- Only an owner-registered trusted device with a fresh connection baseline can start the existing 8-second separation grace period.
- Reconnection, unregistration, re-registration, monitor revision changes, or a latched/non-ARMED protection state invalidate the pending timer.
- Service restart clears connection baselines; the owner must reconnect the accessory to establish a fresh baseline. This avoids treating an unknown disconnected snapshot as theft.
- No continuous Bluetooth discovery/scanning, location permission, automatic pairing, or background root activity is introduced. Controller/API inventory is not represented as a working peer connection.

## Scope boundaries

- Preserve the authoritative DISARMED / ARMED / COUNTDOWN / ALARM_TRIGGERED state machine, the 10-second charger grace period, owner authentication, alarm latch, package identity, minSdk 26, target/compile SDK 36, local-only privacy, and existing signing identity.
- Android/OEM scheduling, receiver delivery, exact-alarm access, force-stop, process death, and hardware/firmware behavior can affect end-to-end delivery. The app must report these as platform limitations rather than claim zero-delay or guaranteed universal detection.
- No manifest-only implicit power receiver is added. Android's background broadcast restrictions and receiver-delivery behavior must be respected; the existing runtime receiver remains tied to the visible foreground monitor.
- Do not add Bluetooth scanning, new permissions, cloud telemetry, or unrelated anti-theft features as part of this phase.

## Verification record

- Unit coverage: `PowerTransitionSamplingPolicyTest` covers early confirmation, unknown/opposite sample resets, bounded rejection, and unknown-only input.
- Android integration coverage: `PowerTransitionRecoveryInstrumentationTest` seeds a persisted same-boot disconnect candidate, confirms recovery from Android's live battery snapshot, and checks replay idempotency.
- Still device-dependent: real charger attach/detach timing, OEM receiver/service survival, actual process kill/restart timing, exact-alarm delivery under Doze, and real Bluetooth accessory ACL behavior.
