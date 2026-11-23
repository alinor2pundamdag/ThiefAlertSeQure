# ThiefAlertSeQure — Universal Maximum Protection Specification

**Status:** Master implementation plan; not a claim that every item is implemented or physically verified.  
**Source branch:** `feat/custom-audio-and-reliability`  
**Baseline commit reviewed:** `49a7571c397d09fb76108148218cea348ace2159`  
**Minimum Android version:** API 26 (Android 8.0), per current Gradle configuration.  
**Current target SDK:** API 36. Re-audit platform behavior before changing target SDK.  
**Product rule:** One universal local protection engine, Android-version-aware platform integration, and optional manufacturer guidance. HONOR is a test device, not the compatibility boundary.

---

## 1. Executive decision

Build and maintain one Android application with three layers:

1. **Universal protection domain:** charger state, countdown, alarm latch, authentication, persistence, audio, and recovery. This layer must not contain OEM-specific branches.
2. **Android platform adapter:** permissions, services, AlarmManager, boot lifecycle, Keyguard, audio APIs, DevicePolicyManager, and version-specific restrictions.
3. **Manufacturer guidance adapter:** detects reported manufacturer/brand/model/build and provides manual setup instructions and safe public Settings destinations. It must not pretend to toggle or read private OEM controls.

Standard mode remains a normal user-installed Android application. Optional managed mode is available only when Android confirms that this app is already the authorized Device Owner and the owner explicitly enables the policy. The app must never attempt privilege escalation or silently enable kiosk restrictions.

The product must describe resistance honestly. A standard APK cannot universally block shutdown, forced hardware power-off, force-stop, uninstall, recovery/bootloader reset, battery removal, or firmware-level actions. Device Owner + Lock Task can hide the ordinary global-actions menu and apply supported restrictions on eligible managed devices, but does not defeat hardware or firmware controls.

## 2. Current repository baseline (reviewed)

The active PR #2 is draft and unmerged. This plan is written against its head commit, not the default branch. Do not merge PR #2 or release an APK as part of this planning change.

Existing implementation files identified on the active branch:

| Concern | Existing source of truth |
|---|---|
| Android components and permissions | `app/src/main/AndroidManifest.xml` |
| SDK, dependencies, build and signing | `app/build.gradle.kts` |
| Main dashboard and protection controls | `MainActivity.kt` |
| Settings and system-setting tutorials | `SettingsActivity.kt` |
| Manufacturer/model/OS guidance and Settings fallbacks | `AndroidDeviceCompatibility.kt` |
| Charger broadcast entry point | `PowerTransitionReceiver.kt` |
| Serialized power-event coordination and debounce | `PowerTransitionCoordinator.kt` |
| Live external-power read | `ExternalPowerState.kt` |
| Authoritative transition rules | `ProtectionEngine.kt` |
| State model | `ProtectionState.kt` |
| Persisted protection and authentication throttle | `ProtectionStore.kt` |
| Monotonic clock and boot identity | `ProtectionClock.kt` |
| Monitoring foreground service | `ProtectionMonitorService.kt` |
| Monitoring eligibility policy | `ProtectionMonitorPolicy.kt` |
| Deadline scheduling and receiver | `AlarmScheduler.kt`, `AlarmDeadlineReceiver.kt` |
| Boot and package-replacement recovery | `BootReceiver.kt` |
| Alarm foreground service and notification | `AlarmService.kt` |
| Alarm audio selection/storage | `AlarmSoundStore.kt` |
| Alarm stream volume policy | `AlarmVolumeController.kt` |
| Authentication entry coordination | `OwnerAuthenticationCoordinator.kt`, `AuthenticationThrottle.kt` |
| Optional managed-device policy | `ManagedProtectionController.kt`, `ThiefAlertAdminReceiver.kt`, `res/xml/device_admin.xml` |
| Local tamper/event history | `LocalTamperHistory.kt` |
| Existing automated tests | `app/src/test/java/com/thiefalertsequre/app/*Test.kt` |
| CI | `.github/workflows/android.yml` |

Existing project documentation to keep aligned with this master specification:
- `docs/PROTECTION_RULES.md`
- `docs/ANDROID_SETTINGS_AND_ALARM_HARDENING.md`
- `docs/ANDROID_DEVICE_COMPATIBILITY.md`
- `docs/DEVICE_OWNER_MODE.md`
- `docs/SECURITY_AND_PLATFORM_AUDIT.md`
- `docs/FEATURE_COMPLETION_MATRIX.md`
- `docs/APK_INSTALL_AND_UPDATE.md`
- `docs/PRIVACY_POLICY.md`
- `docs/REQUIREMENT_TRACEABILITY_MATRIX.md`

**Do not duplicate implementations.** Extend the existing owner of each responsibility. Add a new class only when the current file has a clearly separable responsibility and the dependency contract is documented here and tested.

## 3. System dependency and event flow

### 3.1 Runtime dependency graph

```text
Android OS / OEM firmware
  ├─ power broadcasts ─> PowerTransitionReceiver
  │                        └─> PowerTransitionCoordinator
  │                              ├─> ExternalPowerState
  │                              ├─> ProtectionClock
  │                              ├─> ProtectionEngine
  │                              └─> ProtectionStore
  │                                    └─> AlarmScheduler
  │                                          └─> AlarmDeadlineReceiver
  │                                                └─> ProtectionEngine
  │                                                      └─> AlarmService
  ├─ boot / package replaced ─> BootReceiver
  │                              ├─> ProtectionStore
  │                              ├─> ExternalPowerState
  │                              ├─> ProtectionClock
  │                              ├─> AlarmScheduler / AlarmRecoveryNotice
  │                              └─> ProtectionMonitorService (only when allowed)
  ├─ Android Keyguard / BiometricPrompt ─> OwnerAuthenticationCoordinator
  │                                          └─> ProtectionStore / ProtectionEngine
  │                                                └─> AlarmService stop + volume restore
  ├─ Audio system ─> AlarmService ─> AlarmSoundStore + AlarmVolumeController
  └─ DevicePolicyManager ─> ManagedProtectionController
                               └─> explicit Device Owner / Lock Task policy

MainActivity / SettingsActivity
  ├─ render state from ProtectionStore and service status
  ├─ invoke domain operations through existing coordinators
  ├─ show capability/permission status from Android APIs
  └─ use AndroidDeviceCompatibility for OEM guidance and safe Settings intents
```

### 3.2 Ownership rules

- `ProtectionEngine` owns pure state-transition decisions.
- `ProtectionStore` owns persisted protection state and serialized updates.
- `PowerTransitionCoordinator` owns ordering, validation, and debounce of power events.
- `AlarmScheduler` owns deadline and recovery scheduling.
- `BootReceiver` owns boot/package-replacement reconciliation; it does not invent a new unplug event. BOOT_COMPLETED invalidates the previous charger baseline even when BOOT_COUNT is unavailable; an unknown first power sample remains pending until live power is confirmed.
- `AlarmService` owns alarm playback, alarm notification, and active-alarm volume enforcement.
- `OwnerAuthenticationCoordinator` is the only application path allowed to clear a latched alarm after the explicit successful result from app-launched Android device-credential confirmation or BIOMETRIC_STRONG authentication; keyguard dismissal alone is not proof.
- `ManagedProtectionController` owns explicit managed-policy enable/disable and rollback.
- `AndroidDeviceCompatibility` owns manufacturer-family identification, manual guidance, and safe Settings-intent fallback only.
- UI Activities display state and invoke coordinators; they must not write alarm state directly or duplicate business rules.
- Cloud systems, if introduced in a future feature, must never own the local alarm state or be required for local alarm operation.

### 3.3 Authoritative state machine

Allowed states: `DISARMED`, `ARMED`, `COUNTDOWN`, `ALARM_TRIGGERED`.

| Current state | Event | Next state | Rule |
|---|---|---|---|
| DISARMED | Confirmed charger connected | ARMED | Establish baseline |
| ARMED | Confirmed physical disconnect | COUNTDOWN | Persist one 10-second monotonic deadline |
| COUNTDOWN | Confirmed reconnect before latch | ARMED | Cancel matching deadline |
| COUNTDOWN | Deadline reached and live power confirms disconnected | ALARM_TRIGGERED | Persist latch before starting alarm |
| COUNTDOWN | Power state unknown | COUNTDOWN | Never infer unplug or expiry from unknown; after reboot, keep a pending reconciliation marker until the first reliable power sample, then start a fresh grace period only if disconnected |
| ALARM_TRIGGERED | Charger reconnect/disconnect | ALARM_TRIGGERED | Charger events cannot clear a latched alarm |
| ALARM_TRIGGERED | Service/process restart | ALARM_TRIGGERED | Restore same alarm episode, no duplicate |
| ALARM_TRIGGERED | Reboot | ALARM_TRIGGERED | Recover subject to OS start rules and user-unlock state |
| ALARM_TRIGGERED | Authentication cancelled/failed | ALARM_TRIGGERED | No disarm |
| ALARM_TRIGGERED | Verified owner authentication | DISARMED, then normal re-arm policy | One shared coordinator clears latch and restores prior audio volume |

**Invariant:** Persist `ALARM_TRIGGERED` before starting playback or UI. Never clear it due to charger events, process death, service recreation, boot, app update, permission changes, or monitoring opt-out. Only the authenticated owner flow may clear it.

### 3.4 Countdown and clock integrity

- Use `elapsedRealtime` plus boot identity for same-boot countdown deadlines.
- Never expire a prior-boot monotonic deadline using wall-clock time.
- On reboot during COUNTDOWN, read live charger state. If connected, return to ARMED; if confirmed disconnected, start a fresh full 10-second grace period; if unknown, remain pending and retry safely.
- A stale deadline callback must include and match the stored deadline identity/generation before it can transition state.
- Wall-clock changes, timezone changes, daylight-saving changes, and manual date/time changes must not shorten or extend a same-boot monotonic countdown.
- Exact-alarm denial/revocation must be represented as degraded timing, not as proof of failure or success.

## 4. Power-off, restart, and tamper-resistance contract

### 4.1 Standard application mode

The normal APK can:
- Keep the alarm foreground service active while Android continues running and permits it.
- Persist the alarm episode before shutdown.
- Observe supported lifecycle signals such as orderly shutdown where delivered, for diagnostics only.
- Recover after supported boot/package events using Android-permitted scheduling and service-start paths.
- Explain missing permissions and OEM background controls.

The normal APK cannot guarantee:
- Blocking the system power dialog or Restart menu.
- Cancelling Android shutdown from a shutdown broadcast.
- Preventing hardware key-combination shutdown, battery depletion/removal, crash, force-stop, uninstall, recovery/bootloader reset, firmware changes, or factory reset.
- Playback while the device is powered off.
- Immediate audio restoration before Android's required user unlock or when OS policy blocks background starts.

### 4.2 Optional Device Owner / managed mode

The existing `ManagedProtectionController` is the sole owner of this feature. Keep it separate from standard mode.

Before enabling:
1. Verify `DevicePolicyManager.isDeviceOwnerApp(packageName)`.
2. Show a clear explanation of kiosk/global-actions and Settings restrictions.
3. Require explicit owner confirmation.
4. Verify that an in-app rollback path is visible and tested on that exact build.
5. Record the applied policy state and last verification time.
6. Enable only documented, supported policies.
7. Do not claim hardware-level shutdown prevention.

Managed mode may use Lock Task Mode and omit `LOCK_TASK_FEATURE_GLOBAL_ACTIONS` on supported configurations. This can hide the ordinary global-actions dialog while kiosk mode is active. It does not stop physical forced shutdown or firmware/recovery controls. Do not enable Device Owner by code or by an ordinary permission prompt. Provisioning is a deliberate Android Enterprise operation and may require a new/factory-reset device.

Device Admin activation alone is not Device Owner. UI and documentation must label them separately.

### 4.3 Tamper events

Record only events the app can actually observe:
- Device Admin enabled/disabled callbacks.
- Managed-policy state mismatch when rechecked.
- Permission/capability changes discovered on app resume or boot.
- Exact-alarm access change.
- App package replacement.
- Alarm recovery scheduling/start failures.
- Invalid or unreadable persisted state.
- Detected wall-clock/boot-identity anomalies.
- User-requested managed lockdown enable/disable.

Do not claim reliable detection of force-stop, uninstall attempts, every Settings change, hardware-key activity, or all OEM private setting changes. Tamper history is diagnostic, not a tamper-proof security log. Keep it local unless the user explicitly enables a separately designed cloud feature.

## 5. Universal Android compatibility strategy

### 5.1 Supported baseline and runtime capability detection

- Current minSdk: 26. Do not lower or raise without a compatibility and test decision.
- Current targetSdk: 36. Re-audit all target-SDK behavior before raising to 37 or later.
- Read `Build.MANUFACTURER`, `Build.BRAND`, `Build.MODEL`, `Build.VERSION.RELEASE`, `Build.VERSION.SDK_INT`, and relevant build identifiers where appropriate.
- Treat these values as hints, not proof of a specific OEM settings page.
- Detect capabilities from Android APIs whenever possible: notification permission, exact-alarm access, full-screen-intent access, battery-optimization exemption, secure lock-screen state, Device Admin/Device Owner, lock-task policy, and service/receiver configuration.
- Distinguish status values: **Granted/verified**, **Needs user action**, **Unavailable on this OS/device**, **Unknown/not publicly readable**, and **Not applicable**. Never convert unknown into enabled.
- After returning from Settings, re-query the capability. Successfully opening Settings is not proof the setting was changed.

### 5.2 Manufacturer families

Provide best-effort guidance for Samsung One UI; HONOR MagicOS; Huawei EMUI; Xiaomi/Redmi/POCO MIUI and HyperOS; OPPO ColorOS; OnePlus OxygenOS; realme UI; vivo Funtouch OS and OriginOS; Google Pixel; Motorola; Sony Xperia; ASUS; Nothing; Lenovo; Tecno; Infinix; ZTE; and unknown Android builds.

Group related brands only when the settings guidance is genuinely shared. For unknown devices, provide generic Android App info/Battery guidance and explain that a separate auto-start switch may not exist.

Never:
- Use undocumented/private OEM Activity component names as a required path.
- Use Accessibility overlays or deceptive UI to intercept power controls.
- Claim the app can silently change OEM Auto-launch/Secondary launch/Run-in-background switches.
- Display HONOR-specific controls as if they exist on every Android brand.

### 5.3 Android release review matrix

Maintain and update `docs/ANDROID_DEVICE_COMPATIBILITY.md` whenever minSdk, targetSdk, compileSdk, permissions, or service types change. At minimum review:
- API 26–27 background execution and notification channels.
- API 28–30 background limits and package visibility.
- API 31–32 exact alarms and foreground-service starts.
- API 33 notification runtime permission.
- API 34 full-screen-intent access and foreground-service type requirements.
- API 35 boot receiver / mediaPlayback foreground-service restrictions for target-35+ apps.
- API 36 edge-to-edge and large-screen behavior for target-36 apps.
- API 37 runtime background-audio hardening and target-37-specific requirements.
- Every later Android release before raising targetSdk.

The matrix is a source-level compatibility review, not a claim of physical certification for every Android build.

## 6. Permission, system settings, and capability lifecycle

Create one shared readiness model (extend existing code if a suitable model already exists; do not duplicate checks across Activities). Each row must have: capability ID, API availability, current verified status, required user action, Settings destination fallback, last checked time, and explanation of impact.

Review:
- Notifications and alarm notification channel.
- Exact alarms / Alarms & reminders.
- Full-screen intent access.
- Battery optimization exemption and per-app battery settings.
- Foreground-service declarations and runtime start eligibility.
- Secure screen lock / Keyguard.
- Biometric strong enrollment availability.
- Do Not Disturb alarm exceptions (user-configured; do not silently change DND).
- Device Admin enrollment.
- Device Owner status and managed policy status.
- OEM auto-start/background controls (manual and generally not readable).
- Audio route, alarm stream, selected sound, and vibration capability.
- Boot/package receiver registration and app version state.

Check on first launch, Activity resume, after returning from Settings, after permission-result callbacks, after package replacement, and after boot recovery. Do not poll aggressively or run a permanent health loop solely to inspect settings.

Respect Android and store policies for special permissions. Request only permissions essential to the user-enabled feature, explain why, and provide graceful degradation. Never make an unavailable permission look enabled.

## 7. Persistence, recovery, and data integrity

The current `ProtectionStore` and `LocalTamperHistory` are the existing persistence owners. Audit their serialization, commit/error handling, migration, and concurrency before changing storage technology. Do not migrate to Room/DataStore merely for fashion; migrate only if a concrete atomicity, transaction, or schema requirement cannot be safely met by the existing store.

Persist and validate:
- State and schema version.
- Alarm episode/event ID.
- Transition source and timestamps.
- Countdown epoch deadline, elapsed-realtime deadline, and boot identity.
- Alarm trigger time.
- Previous alarm volume and whether it was captured for this episode.
- Selected audio URI and permission/persistable access state where relevant.
- Recovery scheduling state and attempt metadata.
- Authentication throttle state and boot identity.
- Managed policy state and audit timestamps.

Requirements:
- Atomic/serialized writes; check persistence failures.
- Versioned migrations with safe defaults.
- Corrupt or partial data must not silently clear a latched alarm. Enter a safe recovery state and notify the owner.
- Idempotent restoration: one event ID maps to at most one active AlarmService playback instance and one current deadline.
- No stale PendingIntent/deadline may affect a newer event.
- Store only the minimum data needed; never store device PIN, pattern, password, biometric template, or unnecessary location.
- Keep alarm-critical data available under the storage model actually supported. Current app is not Direct-Boot-aware; do not claim pre-first-unlock recovery. Any future Direct Boot work needs a separate device-protected storage design and tests.

## 8. Alarm, audio, lock-screen, and authentication contracts

### AlarmService
- Owns MediaPlayer lifecycle, audio focus/attributes as currently implemented, ongoing notification, and volume enforcement.
- Must confirm persisted state is ALARM_TRIGGERED before starting playback.
- Must be idempotent across repeated start intents and service recreation.
- If playback fails, use the documented fallback (currently repeating vibration); record the failure.
- On service destruction, do not restore saved volume while alarm remains latched.
- Release MediaPlayer, focus, callbacks, wake locks, and receivers deterministically.
- Do not claim absolute sound output; DND, hardware, routing, OS policy, and OEM behavior remain constraints.

### Volume
- Use the maximum alarm-stream index reported by Android, not a fabricated percentage.
- Save prior volume once per alarm episode and restore only after authorized disarm.
- Periodic reassertion is best-effort volume enforcement, not interception or disabling of physical volume keys.
- Test Bluetooth, wired headphones, speaker, silent mode, DND, audio focus loss, fixed-volume devices, and device-specific audio routing.

### Authentication
- Device PIN/pattern/password are verified only by Android Keyguard; never read/store/transmit them.
- Accept BIOMETRIC_STRONG only where available.
- Authentication cancellation/failure never clears alarm.
- Keep the single `OwnerAuthenticationCoordinator` disarm path.
- Android does not disclose failed system credential attempts to ordinary apps. Do not treat `ACTION_USER_PRESENT`, `isDeviceSecure`, or an unlocked keyguard as proof of a fresh credential entry; trusted-unlock flows can dismiss the keyguard. Require a successful app-launched device-credential confirmation or BIOMETRIC_STRONG prompt.
- Emergency/owner recovery must remain documented, usable, and tested. Do not create a hidden bypass.

## 9. UI and navigation contract

### MainActivity
- Display authoritative protection state from the existing state/store flow.
- Show charger state separately from inferred protection state.
- Show countdown deadline/remaining time only when boot identity is valid.
- Show active alarm and authenticated owner action.
- Keep optional motion alarm clearly separate and OFF by default unless the owner enables it.
- Do not duplicate state transitions or write persisted state directly.

### SettingsActivity
Organize controls into:
1. Protection behavior and monitoring.
2. Alarm sound and volume behavior.
3. Android permissions and notification settings.
4. Battery/background readiness.
5. Manufacturer-specific setup guide.
6. Device Admin / optional Device Owner management.
7. Privacy, local event history, and diagnostics.

Each system-setting action must:
- Explain why it is needed.
- Show current verified status and last checked time.
- Open the most specific public Settings page available.
- Fall back to App info, then general Settings.
- Recheck after the user returns.
- Clearly state when OEM state cannot be read automatically.

Do not show inert buttons, fake toggles, fake green checks, or claims that a setting was enabled merely because an Intent opened.

### Device Protection Center (planned UI)
A single dashboard section should summarize:
- Protection engine state.
- Alarm/monitor service state.
- Exact-alarm, notification, full-screen intent, and battery capabilities.
- Manufacturer setup guidance.
- Device Admin and Device Owner status separately.
- Last boot recovery result.
- Last capability scan.
- Warnings and recommended user actions.

Use accessible text, large touch targets, scroll-safe layouts, system-bar/cutout insets, and clear warning hierarchy. UI status must come from real capability queries, not locally selected cosmetic toggles.

## 10. Cloud, privacy, and network boundary

The local protection core remains offline and has no backend, analytics, or advertising SDK. The manifest declares INTERNET only for the separate, owner-initiated GitHub release updater; alarm detection, state, countdown, authentication, and alarm output must continue to work without a network connection.

Supabase, Firebase, Cloudflare, or any app backend is **not part of the local alarm dependency graph**. Do not add network permission or SDKs to charger detection, alarm, authentication, or resistance features. The existing INTERNET permission is reserved exclusively for the manual official-release updater.

If remote event sync/notifications are approved as a separate future feature:
- Local alarm and recovery must work offline.
- Cloud is an optional event mirror, never the authority for alarm state.
- Authenticate every device/account action; reject unauthorized remote disarm.
- Use least-privilege row-level security and per-device authorization.
- Make event upload idempotent and retry-safe.
- Minimize data and avoid continuous location collection.
- Provide opt-in, clear privacy notice, retention/deletion rules, and offline status.
- Never include credentials, device lock PINs, biometrics, or secret tokens in logs/events.

## 11. Threat model and explicit non-goals

### In-scope threat actors
- Casual thief removing the charger.
- Person attempting ordinary UI-level interference while alarm is active.
- Accidental app/service/process interruption.
- OEM background management that delays or stops background work.
- Network outage (local alarm must remain independent).

### Out of scope / cannot guarantee
- Physical destruction or battery removal.
- Hardware forced shutdown or firmware-level power control.
- Root, bootloader unlock, custom recovery, firmware flashing, or factory reset.
- Android force-stop/uninstall in standard mode.
- Guaranteed sound while device is off, muted by privileged policy, disconnected from speaker, or damaged.
- Universal detection of every tampering action.
- Universal prevention of all settings changes on consumer devices.

The app must not use covert persistence, exploit APIs, root, hidden accessibility interception, deceptive overlays, or interfere with emergency/system controls.

## 12. Test architecture: prevent repeated test/fix cycles

### 12.1 Test pyramid

**Layer A — Pure unit tests (fast, run on every relevant commit)**
- All state transitions and illegal transitions.
- Countdown, stale deadline, generation/event ID.
- Boot identity and monotonic clock behavior.
- Unknown charger state and stale broadcast ordering.
- Authentication throttle and cancellation.
- Managed-mode policy decision logic (pure logic separated from Android calls where practical).
- Compatibility family selection and fallback guidance.

**Layer B — Android integration / instrumentation tests**
- Manifest declarations and receiver/service exported flags.
- AlarmManager scheduling/cancellation and permission-race behavior.
- Service start/idempotency/stop lifecycle.
- Notification channel and full-screen-intent handling.
- Audio resource decode/fallback and volume restoration.
- Keyguard and DevicePolicyManager behavior where testable.

**Layer C — Emulator / OS matrix**
- Minimum supported API 26 baseline where emulator image is available.
- API 31/32 exact-alarm behavior.
- API 33 notification permission.
- API 34 full-screen-intent behavior.
- API 35 boot FGS restrictions.
- API 36 edge-to-edge and target behavior.
- API 37 background-audio hardening before/when supported image is available.

**Layer D — Physical OEM acceptance**
- At least one device per OEM family before advertising that family as physically validated.
- Record exact model, build fingerprint/OS version, API level, permission status, test ID, expected result, actual result, and evidence.
- HONOR 200 Pro remains the initial reference device; it does not certify Samsung/Xiaomi/Pixel/etc.

### 12.2 Mandatory scenario matrix

| ID | Scenario | Expected result |
|---|---|---|
| PWR-01 | Confirmed unplug while ARMED | Exactly one countdown |
| PWR-02 | Unknown/stale power broadcast | No false countdown |
| PWR-03 | Reconnect during countdown | Cancel matching countdown |
| PWR-04 | Reconnect after alarm latch | Alarm remains latched |
| PWR-05 | Duplicate/out-of-order broadcasts | Serialized, idempotent state |
| CLK-01 | Wall-clock/timezone changed during countdown | Monotonic deadline unchanged |
| BOOT-01 | Reboot while ARMED and charger connected | Correct baseline; no invented unplug |
| BOOT-02 | Reboot while COUNTDOWN and charger connected | Return to ARMED |
| BOOT-03 | Reboot while COUNTDOWN and charger disconnected | Fresh 10-second grace period |
| BOOT-04 | Reboot while ALARM_TRIGGERED | Preserve latch; recover via permitted route |
| BOOT-05 | Exact-alarm permission absent/revoked | Clear degraded warning; no false recovery claim |
| APP-01 | App package updated during countdown | Preserve/reconcile same event |
| APP-02 | App package updated during alarm | Preserve latch; one playback instance |
| PROC-01 | Process/service recreated | Reconcile from store; no duplicate |
| PROC-02 | User force-stops app | Document Android limitation; do not promise auto-restart |
| AUD-01 | Volume down during alarm | Reassert max available index where allowed |
| AUD-02 | Alarm stops after authenticated disarm | Restore saved volume exactly once |
| AUD-03 | Corrupt/missing custom audio | Fallback and record diagnostic |
| AUTH-01 | Auth cancelled/failed | Alarm remains latched |
| AUTH-02 | Successful secure owner authentication | One disarm transition |
| SET-01 | User returns from permission Settings | Re-query real capability |
| OEM-01 | Unknown manufacturer | Safe generic guidance; no crash |
| ADMIN-01 | Device Admin only | Never label as Device Owner |
| ADMIN-02 | Device Owner not provisioned | No attempt to self-elevate |
| ADMIN-03 | Managed lockdown enabled | Verify exact restrictions and rollback |
| ADMIN-04 | Managed lockdown disabled | Verify Lock Task exit and all applied restrictions cleared |
| ADMIN-05 | Device Owner mode reboot | Re-verify policy and app availability |
| NET-01 | Offline / backend unavailable | No effect on local protection |
| PRIV-01 | Data export/deletion | Only owner-selected local data; no secrets |

Power-off tests must separately cover orderly power menu, restart menu, hardware-forced shutdown, battery depletion/removal where safe, and recovery. The expected result is not always “blocked”; it must match the documented platform boundary.

### 12.3 Test traceability and change-impact policy

- Give every requirement a stable ID (e.g. STATE-*, BOOT-*, OEM-*, ADMIN-*, UI-*, PRIV-*).
- Link each requirement to owner file(s), unit test(s), integration test(s), and physical test(s).
- Before editing, identify the affected dependency graph and regression tests.
- Run focused tests during development; run the full unit/lint/APK CI gate before commit/PR update.
- Do not repeat physical tests for unrelated UI/docs changes.
- Any change to a shared contract (state model, persistence schema, alarm service lifecycle, manifest permissions, targetSdk, receiver behavior) triggers its defined regression subset.
- Fix root cause, add a regression test that fails before the fix, then run the affected suite once and full CI once.
- Avoid unrelated refactors in a reliability fix.
- Never mark physical tests complete from unit tests or CI.

## 13. Implementation sequence and commit boundaries

### Phase 0 — Baseline freeze and audit
- Confirm active branch/head and CI result.
- Preserve all existing changes and review main-only commits before integration.
- Capture file ownership, manifest component graph, permission inventory, and current test baseline.
- No feature refactor until the baseline is reproducible.

### Phase 1 — Requirement traceability
- Convert this specification into requirement IDs.
- Map each ID to current owner files and existing tests.
- Mark status as implemented/source-reviewed, unit-tested, CI-verified, device-tested, or not implemented.
- Resolve conflicting documentation before code changes.

### Phase 2 — State, persistence, and recovery hardening
- Audit ProtectionStore write success, corruption behavior, schema versioning, and concurrency.
- Verify event identity/generation and service idempotency.
- Verify boot, exact-alarm, package replacement, and first-unlock limitations.
- Add only missing tests/logic; do not rewrite already-correct countdown/state behavior.

### Phase 3 — Android capability/readiness model
- Centralize supported API checks.
- Separate verified capability from manual OEM setting.
- Add permission recheck on resume and after settings return.
- Ensure safe Settings fallbacks and truthful status.

### Phase 4 — Managed-device safeguards
- Audit Device Owner enable/disable symmetry.
- Confirm every restriction applied is explicitly tracked and removed by rollback.
- Test rollback on a dedicated, backed-up, factory-reset test device.
- Do not test Device Owner provisioning on the owner's daily-use phone.

### Phase 5 — UI integration
- Add/finish Device Protection Center using shared readiness/state models.
- Remove any duplicate/fake controls.
- Add clear limitations, recovery guidance, and accessible status.
- Ensure UI never becomes a second state machine.

### Phase 6 — Security/privacy and documentation
- Verify local protection behavior offline; INTERNET permission must be used only by the explicit GitHub release updater and must not become a dependency of alarm operation.
- Align README, privacy policy, feature matrix, protection rules, compatibility guide, Device Owner guide, install/update guide, and this spec.
- Remove absolute claims such as “unstoppable”, “cannot be switched off”, or universal device certification.

### Phase 7 — Verification and release readiness
- Run traceability-based unit and integration tests.
- Run lint and debug/release APK assembly.
- Inspect manifest, APK signature, versionCode/versionName, packaged audio/icon, and artifact hash.
- Verify CI on exact final commit.
- Complete physical-device tests and record exact device/build.
- Keep PR draft until all required gates are satisfied. Do not merge or publish without explicit owner instruction.

## 14. Definition of Done

A requirement is complete only when all applicable evidence exists:
1. One clearly identified owner module/file.
2. Defined input/output and state invariants.
3. Error and fallback behavior.
4. Permission/API/OEM limitations documented.
5. Regression test mapped to the requirement.
6. Unit/integration tests pass.
7. CI passes on the exact commit.
8. Physical device evidence exists when hardware/OEM behavior is involved.
9. UI accurately reflects actual capability and status.
10. Documentation and privacy claims match the implementation.

Project-level completion requires:
- No duplicate source of truth for protection state.
- No duplicate countdown/alarm instances.
- No path other than authenticated owner recovery clearing ALARM_TRIGGERED.
- No OEM-specific code in the universal state engine.
- No fake permission/admin status.
- No network dependency for local alarm.
- No unsupported claims about shutdown prevention or all-device certification.
- No unresolved critical/high-severity audit findings.
- PR remains unmerged until explicitly approved.

---

## 15. Open decisions that must be resolved by evidence, not guesswork

1. Whether current ProtectionStore persistence is sufficiently atomic and corruption-safe; inspect implementation and tests before changing it.
2. Whether a separate readiness model is needed or can be cleanly added to existing Settings helpers.
3. Exact supported Android 17 behavior once final SDK/tooling and test devices are available.
4. Physical compatibility coverage: a brand family is “validated” only after a representative physical device passes its acceptance tests.
5. Whether any cloud feature is desired later; it is explicitly excluded from this local protection scope for now.

**Implementation rule:** Do not start broad code changes from this document alone. First complete Phase 0–1 and produce the requirement-to-file-to-test traceability matrix. This is the control that prevents repeated blind testing and regression fixes.
