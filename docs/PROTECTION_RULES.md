# Protection Rules, State Machine and Operational Policies

## 1. Authoritative state

The local persisted state is one of:

- DISARMED — no charger protection is active.
- ARMED — charger connection observed; monitor for disconnect.
- COUNTDOWN — charger disconnect observed; deadline is active.
- ALARM_TRIGGERED — terminal alarm state until successful owner authentication.

The state machine is authoritative. Notifications, UI labels, service state and audio playback are projections of this state; none of them can independently disarm protection.

## 2. State transition rules

| Current state | Event | Next state | Required side effect |
|---|---|---|---|
| DISARMED | Charger connected | ARMED | Persist state; cancel stale deadline |
| ARMED | Charger disconnected | COUNTDOWN | Persist 10-second deadline; schedule alarm |
| COUNTDOWN | Charger connected before deadline | ARMED | Persist re-arm; cancel deadline |
| COUNTDOWN | Deadline reached | ALARM_TRIGGERED | Persist latch; start alarm service |\n| ARMED | Motion threshold exceeded while motion alarm is explicitly enabled | ALARM_TRIGGERED | Persist latch; record motion trigger source |
| COUNTDOWN | Deadline receiver fires early | COUNTDOWN | Recompute remaining time and reschedule |
| ALARM_TRIGGERED | Charger connected | ALARM_TRIGGERED | Keep alarm active |
| ALARM_TRIGGERED | Charger disconnected | ALARM_TRIGGERED | Keep alarm active |
| ALARM_TRIGGERED | Authentication cancelled | ALARM_TRIGGERED | Keep alarm active |
| ALARM_TRIGGERED | Authentication fails | ALARM_TRIGGERED | Increment biometric failure count only |
| DISARMED / ARMED / COUNTDOWN | Owner disables background monitoring | DISARMED | Cancel pending deadline and stop charger/motion monitoring; never clear ALARM_TRIGGERED |
| ALARM_TRIGGERED | Device credential succeeds | DISARMED | Stop service, cancel deadline, restore prior alarm volume |
| ALARM_TRIGGERED | Strong biometric succeeds | DISARMED | Stop service, cancel deadline, restore prior alarm volume |

Motion alarm is independent and may trigger while the charger is connected, but it is disabled by default. Keep it OFF for charger-removal-only behavior.\n\nNo other event may clear ALARM_TRIGGERED.

## 3. Countdown integrity

- Use elapsedRealtime plus boot count on the same boot. This clock is not affected by user wall-clock changes.
- If reboot occurs during COUNTDOWN, revalidate the live charger state. If connected, return to ARMED. If disconnected, restart a full 10-second grace period. If charger state or boot identity is unknown, do not expire the old deadline; wait for reliable state/boot identity. Never use wall-clock expiry for a persisted monotonic deadline when boot identity cannot be verified.
- Re-check the deadline inside AlarmDeadlineReceiver before changing state.
- Prefer setExactAndAllowWhileIdle when Android permits it.
- Android 12+ requires user-granted SCHEDULE_EXACT_ALARM access for this app's exact-alarm route.
- If exact access is unavailable or revoked, use setAndAllowWhileIdle as a best-effort fallback and clearly report that the ten-second deadline can be delayed.
- Exact-alarm idle quotas and OEM battery behavior can still affect real-world delivery.

## 4. Authentication policy

### Device credentials
- Use Android Keyguard to verify the existing device PIN, pattern or password.
- Never request a second app password.
- Never read, store, log or transmit the device credential.
- Android does not expose failed device-credential attempts to ordinary apps. Android's own lockout remains authoritative.

### Biometrics
- Use AndroidX BiometricPrompt with BIOMETRIC_STRONG only.
- Accept only enrolled authenticators that Android reports as strong.
- Weak face unlock is not accepted.
- Count each onAuthenticationFailed callback.
- On the fifth consecutive biometric failure, persist a 30-second cooldown and cancel the current prompt.
- During cooldown, do not start another biometric prompt from this app; the verified Android device credential remains an allowed owner-recovery path.
- After cooldown expires, reset the counter and permit a new group of five failures.
- Android biometric lockout events also start the app's 30-second cooldown.
- Cancellation is not a failure and never disarms the alarm.

## 5. Alarm audio policy

- Play the packaged Ogg Opus alarm asset in a loop through MediaPlayer.
- If selected and packaged audio both fail, use a repeating vibration fallback; there is no ToneGenerator fallback in the current implementation.
- Use USAGE_ALARM and request the maximum alarm-stream index reported by Android.
- Save the prior alarm-stream volume once, before raising it.
- Restore the prior volume only after verified disarm. Do not restore it if the service is destroyed while ALARM_TRIGGERED remains latched.
- Do not claim 105% volume, hardware volume-key interception, or guaranteed sound output.
- Do not silently modify global DND settings. The user configures alarm exceptions in Android settings.

## 6. Managed-device policy

Managed lockdown is a separate, opt-in mode. It is enabled only if DevicePolicyManager confirms the app is already Device Owner.

- Allowlist only this package for Lock Task Mode.
- Keep Keyguard available.
- Omit LOCK_TASK_FEATURE_GLOBAL_ACTIONS while kiosk mode is active.
- Apply supported restrictions for Settings factory reset, safe boot, USB file transfer, adding users and debugging.
- Keep an in-app exit action that stops Lock Task and removes every applied restriction.
- Do not use Accessibility to intercept global buttons, hidden overlays, root, firmware changes or deceptive persistence.
- Device Owner is not an app permission. Provisioning usually requires Android Enterprise enrollment on a new or factory-reset device.
- Lock Task does not stop physical forced shutdown, recovery/bootloader reset, battery removal or OEM firmware-level controls.

## 7. Privacy and least privilege

- No Internet permission or network stack is needed.
- No cloud/backend/analytics/ad SDK.
- Do not collect device passwords, patterns or PINs.
- No unnecessary contacts, location, camera, microphone, SMS or storage permissions.
- Charger receiver is not exported to third-party apps.
- DeviceAdminReceiver is exported only with BIND_DEVICE_ADMIN protection.
- Keep app backup disabled so security state is not restored into a different device unexpectedly.

## 8. Release acceptance

A release candidate is acceptable only when:
1. Unit tests pass on the latest branch commit.
2. Debug APK assembles and is uploaded by CI.
3. The app installs and launches on the intended Android device.
4. The original icon and app label are exact.
5. Charger state transitions and deadline are tested physically.
6. The original Opus audio decodes, loops and restores volume.
7. PIN, pattern, password and supported strong biometrics are tested.
8. Five-failure cooldown and repeat cycle are tested.
9. Cancel and failed authentication never clear the alarm.
10. Android 15+ reboot recovery is tested after first unlock with exact-alarm access granted and denied; pre-unlock Direct Boot protection is not provided.
11. DND, notification denial, OEM battery controls and process death are tested.
12. Device Owner mode is tested only on a prepared, backed-up test device, including the in-app rollback.
