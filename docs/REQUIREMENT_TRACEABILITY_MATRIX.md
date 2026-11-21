> **Current Batch Four candidate (21 November 2026):** `work/batch-4-implementation`, version `1.1.5_Developer_Alinor` / versionCode `7`, PR #8 draft/open/unmerged. Exact branch head `974e450db00f60c959f599279bc3cf41c58bd56b`; latest code-bearing commit `a95e5b65d837db41efce07e50fa25274ce959c47`. Follow-up fixes cover A2 deadline invalidation after authenticated disarm, emergency protection-state corruption snapshots, non-blocking alarm recovery cleanup, owner-visible monitor recovery, dedicated torch timing workers, and diagnostics privacy. Exact-head Android CI and CodeQL are queued, not yet verified. Current Phase 12 physical-device acceptance remains 0/25 PASS and 25/25 NOT TESTED. Current camera photo storage defaults to unencrypted Gallery with an explicit future-capture AES-GCM option; the cinematic intro is shown once per installed versionCode.

> Historical FIXALL candidate: v`1.1.4` / versionCode `6`, package `com.thiefalertsequre.app`; side-by-side debug test variant is `com.thiefalertsequre.app.v114test`. Production identity and signing configuration are protected invariants. No production release is authorized by this verification work.
> Contract: [Universal Maximum Protection Specification](UNIVERSAL_MAXIMUM_PROTECTION_SPEC.md).
> Status labels: **Implemented** means source code exists; **Unit-covered** means a named JVM unit test exists; **CI-verified** means the exact commit passed the named non-instrumentation GitHub Actions checks. Physical-device and emulator acceptance are explicitly outside the current owner-authorized completion scope and are not blockers. Dormant `androidTest` source files and historical instrumentation records are reference-only; they must not be compiled or executed.

## 1. Verification snapshot

**Historical code-bearing verification snapshot (superseded by the current Batch Four head):** Android run [43950015535](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015535) and CodeQL run [43950015623](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015623) passed on source commit `475c1fa00b8c30c1280f77053d83eb91c0db7f64`. Subsequent Phase 10–12 follow-up commits changed documentation only. The final documentation snapshot `ffe5fdae9150cb8ca791478d48cfc68263776ff8` passed Android run [43951488247](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43951488247) and CodeQL run [43951488080](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43951488080). The current owner policy prohibits instrumentation execution/compilation and emulator matrices; only JVM unit tests, source-contract checks, lint, APK build/metadata/signer checks and CodeQL are in scope.

| Area | Current evidence | Status / limitation |
|---|---|---|
| Branch / identity | Historical FIXALL coding branch `work/phase-0-12-coding`, latest code-bearing commit `475c1fa00b8c30c1280f77053d83eb91c0db7f64`; production branch `main` remains untouched; package `com.thiefalertsequre.app`; minSdk 26, compile/target SDK 36; Java/Kotlin 17; versionCode 6 / versionName 1.1.4 | Working candidate only; not merged or released |
| Phase One baseline build and static checks | Workflow run [#1270](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43734061025), exact Phase One baseline; build, unit tests, navigation verification, lint, asset/config checks, APK assembly and package/version/signer validation | PASS on that exact Phase One source head |
| Last exact-head non-instrumentation CI baseline | Run [43950015535](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015535) and CodeQL [43950015623](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015623) on `475c1fa`; JVM unit tests, source contracts, lint, APK build/metadata/signer checks and CodeQL | PASS on that exact code-bearing head; the final documentation snapshot also passed exact-head CI |
| Historical instrumentation API 26 / 28 | 25 tests per API; zero failures and errors; one documented biometric-enrollment skip per API on the earlier Phase One baseline | Historical PASS only; not evidence for current coding-branch head |
| Historical instrumentation API 30 | 25 tests; zero failures and errors; one documented biometric-enrollment skip on the earlier Phase One baseline | Historical PASS only; current coding-branch instrumentation has not run |
| Historical instrumentation API 32 / 34 / 35 / 36 | 25 tests per API; zero failures and errors; one documented biometric-enrollment skip per API on the earlier Phase One baseline | Historical PASS only; current coding-branch instrumentation has not run |
| Historical diagnostics | All seven API diagnostic artifacts from run #1270 on the earlier Phase One baseline were inspected, including XML reports and logcat/crash context | Historical review found no ThiefAlertSeQure process crash or test failure; not a current-head instrumentation result |
| Architecture / event ownership | Source-backed map covers charger, countdown/alarm, authentication, Bluetooth, boot, settings, theme, history and updater paths | Documented and source-checked for Phase 1; hardware/OEM behavior remains device-dependent |
| Physical-device acceptance | HONOR 200 Pro testing for changed v1.1.4 behavior | Reserved for Phase 12; not a Phase 1 blocker and not inferred from emulator results |
| Release boundary | No merge, tag, production APK publication or official release in this work | Not authorized |

## 2. Core protection requirements

| ID | Requirement / acceptance condition | Primary owner(s) | Existing test / evidence | Baseline status |
|---|---|---|---|---|
| CORE-01 | A single authoritative state machine: DISARMED, ARMED, COUNTDOWN, ALARM_TRIGGERED | `ProtectionState.kt`, `ProtectionEngine.kt` | `ProtectionEngineTest` | Unit-covered |
| CORE-02 | Confirmed plug arms; unplug only starts countdown from ARMED | `PowerTransitionCoordinator.kt`, `ProtectionEngine.kt` | `unplugStartsTenSecondCountdown`; coordinator tests | Unit-covered; physical event ordering device-dependent |
| CORE-03 | No countdown from a disconnected snapshot, unknown state, or service startup | `PowerTransitionCoordinator.kt`, `ExternalPowerState.kt` | `ExternalPowerStateTest`, coordinator tests | Unit-covered |
| CORE-04 | Debounce charger state; reject stale or superseded connect/disconnect candidates; stop sampling as soon as three consecutive live-state reads confirm the event | `PowerTransitionCoordinator.kt`, `PowerTransitionSamplingPolicy.kt`, `PowerTransitionReceiver.kt` | `PowerTransitionSamplingPolicyTest`; generation-token and same-boot recovery tests | Unit-covered for stable/unstable/unknown samples; live OS event ordering remains device-dependent |
| CORE-05 | Start countdown from captured OS event time, not after debounce delay | `PowerTransitionReceiver.kt`, `PowerTransitionCoordinator.kt` | Source inspection; owner-reported charger countdown PASS | Implemented; target-device timing reported PASS |
| CORE-06 | Reconnect before expiry cancels countdown and arms | `ProtectionEngine.kt`, coordinator, deadline receiver | `reconnectBeforeDeadlineRearms`, `reconnectAtDeadlineCancelsAlarmEvenIfDeadlineHasElapsed` | Unit-covered |
| CORE-07 | Unknown power state never expires countdown; retry without false alarm | `ProtectionEngine.kt`, `AlarmDeadlineReceiver.kt`, monitor service | `unknownChargerStateNeverTriggersCountdown` | Unit-covered |
| CORE-08 | Monotonic elapsed-time deadline and boot identity; never compare old-boot monotonic time | `ProtectionClock.kt`, `ProtectionState.kt`, engine/store | wall-clock, boot identity and reboot tests | Unit-covered |
| CORE-09 | Reboot invalidates the old charger baseline even when BOOT_COUNT is unavailable; unknown initial power sample waits on a dedicated marker; first confirmed disconnected sample starts a fresh grace period | `BootReceiver.kt`, `PowerTransitionCoordinator.kt`, engine, state decoder | Unit tests plus owner-reported reboot recovery PASS | Source/unit-covered; target-device reboot recovery reported PASS |
| CORE-10 | ARMED + unplugged boot snapshot must not invent a physical transition | `BootReceiver.kt`, engine | `rebootWhileArmedAndUnpluggedDoesNotInventTransition` | Unit-covered |
| CORE-11 | ALARM_TRIGGERED is latched; reconnect, reboot, or service recovery cannot clear it | engine, coordinator, boot receiver | reconnect/reboot latch tests | Unit-covered |
| CORE-12 | Only successful app-launched Android device-credential confirmation or BIOMETRIC_STRONG authentication clears the latched alarm | `MainActivity.kt`, `OwnerAuthenticationCoordinator.kt`, `ProtectionEngine.kt` | state transition tests plus owner-reported authentication protection PASS | State transition unit-covered; target-device authentication reported PASS |
| CORE-13 | Stale AlarmManager callbacks cannot affect a newer countdown | `ProtectionEngine.kt`, deadline receiver, scheduler | `staleDeadlineCallbackCannotMatchNewCountdown` | Unit-covered |
| CORE-14 | Persistence write failures do not report an uncommitted state as saved | `ProtectionStore.kt` | `writeUnlocked` uses synchronous `commit()`; `update()` returns the prior state if commit fails; `ProtectionStoreUpdateResultTest` includes failed-commit coverage | Unit/source coverage exists; current Android instrumentation execution is prohibited by owner direction |
| CORE-15 | Corrupt or partially written state fails safely and is recoverable | `ProtectionState.fromPersisted`, `ProtectionStore.kt`, boot recovery | `ProtectionEngineTest.partialOrCorruptPersistedStateFailsClosed`, `validPersistedCountdownRetainsItsDeadline`; malformed preference reads are caught in store | Fail-closed decoder implemented and JVM policy-covered; Android SharedPreferences backend fault-injection is excluded with instrumentation, so physical persistence/recovery acceptance remains device-dependent |
| CORE-16 | Persisted disconnect candidates recover idempotently after monitor/process interruption; stale or duplicate events cannot create a second countdown | receiver, coordinator, monitor service | `PowerTransitionRecoveryInstrumentationTest.persistedDisconnectCandidateRecoversIdempotently`; serialized receiver queue and generation-token guards | Legacy instrumentation source exists but is not compiled or executed; JVM recovery policy is covered, while actual OS process-kill/OEM lifecycle and queue-saturation behavior remain device/lifecycle acceptance items |
| CORE-17 | Monitoring opt-out stops monitoring without clearing a latched alarm | monitor service, boot receiver, policy | `ProtectionMonitorPolicyTest` plus source ownership checks | Unit/source-covered; actual foreground-service lifecycle remains physical-device acceptance |

## 3. Alarm, audio, lock, and authentication

| ID | Requirement / acceptance condition | Primary owner(s) | Existing test / evidence | Baseline status |
|---|---|---|---|---|
| ALARM-01 | Alarm starts only from persisted ALARM_TRIGGERED state | `AlarmService.kt`, deadline receiver | source guard | Implemented with source/state ownership checks; trigger lifecycle remains physical-device acceptance |
| ALARM-02 | Alarm runs as foreground service with ongoing notification | `AlarmService.kt`, manifest | CI lint/build plus owner-reported lock-screen presentation PASS | Implemented; target-device presentation reported PASS; denied-notification permission case remains unverified |
| ALARM-03 | Built-in alarm loops; invalid custom audio falls back to built-in, then vibration | `AlarmService.kt`, `AlarmSoundStore.kt` | audio fallback policy is source-reviewed; media decode/routing remains physical-device acceptance | Implemented; device audio matrix required |
| ALARM-04 | Preserve user's prior alarm-stream volume and restore only after authenticated disarm; malformed saved-volume metadata must not crash recovery | `AlarmVolumeController.kt`, auth coordinator | source review plus owner-reported volume restoration PASS | Implemented; target-device volume restoration reported PASS |
| ALARM-05 | Reassert maximum alarm stream while latched, without claiming hardware-level volume control | `AlarmService.kt`, volume controller | source inspection | Implemented; OEM/fixed-volume/Bluetooth behavior varies |
| ALARM-06 | Device lock is optional and only requested when secure keyguard + active Device Admin permit it | `AlarmService.kt`, admin receiver, settings UI | Device Admin/Keyguard runtime behavior remains physical-device acceptance | Implemented; physical test required |
| ALARM-07 | Do not relock after owner unlock during the same alarm episode | `AlarmService.kt` | persisted per-episode lock timestamp | Implemented; keyguard transition device test required |
| ALARM-08 | Full-screen dashboard requires local owner opt-in and Android 14+ system access; otherwise retain normal alarm notification | `AlarmPresentationPolicy.kt`, `AlarmService.kt`, SettingsActivity | `AlarmPresentationPolicyTest` plus owner-reported lock-screen presentation PASS | Unit-covered policy; target-device presentation reported PASS; permission-denied fallback remains separately unverified |
| ALARM-09 | Optional camera LED pulses only while persisted mode is ALARM_TRIGGERED; it stops on authenticated disarm/service teardown; flash failure never blocks audio; owner can disable it | `AlarmFlashController.kt`, `AlarmService.kt`, `SettingsActivity.kt`, manifest | `AlarmFlashPolicyTest`; source guards; owner-reported flashlight independence and camera-conflict PASS | Implemented; cadence/state unit-covered; target-device flashlight and audio independence reported PASS; thermal/long-duration behavior remains device-dependent and is not independently verified in this candidate |
| AUTH-01 | Android verifies PIN/pattern/password/biometric; app never reads credential material | auth coordinator, MainActivity | source inspection | Implemented by design; integration test required |
| AUTH-02 | Failed authentication is throttled and persisted across process restart | `AuthenticationThrottle.kt`, store | `AuthenticationThrottleTest` | Unit-covered |
| AUTH-03 | Only explicit app-launched Android device-credential confirmation or BIOMETRIC_STRONG success clears the alarm; keyguard dismissal alone never clears it | `MainActivity.kt`, `OwnerAuthenticationCoordinator.kt` | authentication result handling; owner-reported authentication protection PASS | Implemented by source; target-device authentication reported PASS |

## 4. Android platform and universal OEM compatibility

| ID | Requirement / acceptance condition | Primary owner(s) | Existing test / evidence | Baseline status |
|---|---|---|---|---|
| AND-01 | minSdk 26 and target/compile SDK 36 are consistent with the owner-approved project invariant | `app/build.gradle.kts` | Android run #1421 on predecessor `9168d1d` | Source configuration minSdk 26; exact-head build/lint on the current head must be rechecked after final edits; instrumentation is excluded |
| AND-02 | Charger events are registered at runtime from foreground monitor; do not rely on manifest-only implicit broadcast delivery | monitor service, receiver, manifest | source inspection | Implemented; Android-version device matrix required |
| AND-03 | Foreground-service declarations/types/permissions match service behavior and target SDK | manifest, monitor/alarm service | CI lint/build | Manifest, source-contract and lint checks are the non-instrumentation evidence; API 34–37 foreground-service runtime behavior remains physical-device/platform-unverified |
| AND-04 | Exact-alarm denial does not silently claim precise delivery; fallback/recovery is surfaced | scheduler, boot receiver, UI | `A2DisconnectSchedulerInstrumentationTest`; Settings and Setup Status expose exact-alarm access | Inexact idle-aware fallback is implemented and recorded; Android can defer it substantially in Doze, so precise eight-second delivery is not claimed. Historical instrumentation source is not run under current policy |
| A2-01 | Trusted ACL connect/disconnect is detected while the foreground service is alive and while its process is absent; per-device encrypted grace deadlines survive process death, reconnect invalidates the matching deadline, and stale/old-boot callbacks cannot latch an alarm | `A2BluetoothAclReceiver.kt`, `A2DisconnectScheduler.kt`, `ProtectionMonitorService.kt`, `AlarmDeadlineReceiver.kt`, `TrustedBluetoothRegistry.kt` | `A2DisconnectPolicyTest`, `A2DisconnectSchedulerInstrumentationTest`, `A2BluetoothReceiverControllerInstrumentationTest` | Implementation and JVM policy/registry coverage exist; instrumentation source is dormant and not compiled/executed; Doze throttling, force-stop, process-absent delivery and OEM ACL behavior remain device-dependent |
| AND-05 | Notification permission/channel denial is reflected in readiness UI | `SetupStatusSnapshot.kt`, `NotificationSetupStatusPolicy.kt`, Settings and Setup Status screens | `NotificationSetupStatusPolicyTest` covers enabled/disabled app notifications and missing/blocked/available alarm channels | State mapping is unit-covered; actual OS permission-dialog and notification-delivery behavior remains physical-device acceptance |
| AND-06 | Full-screen intent access is treated separately from app lock-screen dashboard preference | alarm service, settings UI | source/UI inspection | Implemented; API 34+ policy and behavior require verification |
| AND-07 | Edge-to-edge/system bars/cutouts keep controls reachable | compatibility helper, both activities | no screenshot/instrumentation test identified | Implemented; TalkBack, font scaling, edge-to-edge and OEM display behavior require physical-device acceptance |
| OEM-01 | Detect Samsung One UI and provide manual background guidance | compatibility helper | compatibility unit tests | Unit-covered guidance; device menus vary |
| OEM-02 | Detect HONOR MagicOS / Huawei EMUI and explain Manage automatically + three manual switches | compatibility helper | HONOR guide unit tests | Unit-covered guidance; user-reported HONOR 200 Pro test only |
| OEM-03 | Detect Xiaomi / Redmi / POCO (MIUI / HyperOS) | compatibility helper | family unit tests | Unit-covered detection; device menus unverified |
| OEM-04 | Detect OPPO / realme / OnePlus | compatibility helper | family unit tests | Unit-covered detection; device menus unverified |
| OEM-05 | Detect vivo / iQOO | compatibility helper | family unit tests | Unit-covered detection; device menus unverified |
| OEM-06 | Detect Google Pixel and Motorola / Lenovo | compatibility helper | family unit tests | Unit-covered detection; device menus unverified |
| OEM-07 | Unknown manufacturers receive safe generic guidance and standard Settings fallbacks | compatibility helper | unknown-brand test | Unit-covered generic fallback |
| OEM-08 | ASUS, Sony Xperia, Nothing OS, Lenovo, TECNO / Infinix / itel, ZTE / nubia, and other vendors are identified where possible; use safe generic fallback unless a stable public route is verified | compatibility helper, SettingsActivity | `recognizesAdditionalAndroidManufacturersWithSafeGenericGuidance` | Family recognition and generic fallback implemented; dedicated physical-device validation outstanding |
| OEM-09 | Never depend on undocumented OEM component names or claim the app can toggle private auto-start controls | compatibility helper, SettingsActivity | source inspection | Implemented by design |
| OEM-10 | Manufacturer/model/Android version are shown to help tailor instructions | compatibility helper, settings UI | device-summary helper; guide test | Implemented; verify rendered UI |

## 5. Privacy, security, administration, and documentation

| ID | Requirement / acceptance condition | Primary owner(s) | Existing test / evidence | Baseline status |
|---|---|---|---|---|
| PRIV-01 | Local protection core has no backend, analytics, advertising, or cloud dependency; INTERNET permission is restricted to explicit GitHub release checks | manifest, updater, Gradle dependencies, app code | manifest/dependency inspection and updater code review | Implemented; re-audit every dependency and network change |
| PRIV-02 | No unnecessary contacts/SMS/microphone permissions; CAMERA permission is optional and requested only after owner opt-in for front-camera evidence; torch control remains CAMERA-permission-free; camera/flash hardware remain optional | manifest, `AlarmFlashController.kt`, `CameraEvidenceCapture.kt`, `CameraEvidenceVault.kt` | manifest + consent-flow static checks, JVM camera policy tests, Android lint/build | Implemented in software; actual front-camera output and OEM camera contention remain physical-device-only |
| PRIV-03 | Backup disabled for sensitive local protection state | manifest | manifest inspection | Implemented at baseline |
| PRIV-04 | Local event history bounded and contains no owner credential material | `LocalTamperHistory.kt` | `LocalTamperHistoryPolicyTest` verifies the 100-entry cap, newest-first reads, length bounds and control/line-separator sanitization without Android instrumentation | Implementation and pure retention/sanitization policy are JVM-covered; Android preference persistence and CSV export remain physical-device acceptance items |
| PRIV-05 | Audio document URI permissions are scoped to user-selected files | audio store | source inspection | Implemented; provider permission revocation remains physical-device acceptance |
| SEC-01 | No accessibility-based power-menu interception, privilege escalation, or hidden system API | app source/dependencies | source review | Prohibited by design; audit on each change |
| SEC-02 | Device Admin and Device Owner are clearly distinguished | managed controller, docs, UI | source/docs review; user-reported separate-device enrollment/rollback PASS | Implemented; user-reported physical pass, independent reproduction not recorded |
| SEC-03 | Managed restrictions require explicit Device Owner provisioning; snapshot admin-scoped restriction values and the DPM-visible Lock Task baseline; roll back partial enable; expose recovery for damaged metadata; keep Safe Boot and USB debugging available; never auto-reenter kiosk while rollback is pending | `ManagedProtectionController.kt`, `ManagedPolicySnapshot.kt`, MainActivity, Device Owner guide | `ManagedPolicySnapshotTest`; exact-source/workflow CI run `42479548944`; no Device Owner instrumentation test | Source and unit coverage implemented. New builds restrict Settings factory reset, adding users and USB file transfer only. Safe Boot and USB debugging remain recovery paths. Emergency rollback clears this feature's restrictions, removes its allowlist, and re-enables Home, Overview, notifications, system info, global actions and Keyguard. Android 14+ Lock Task getters return resolved policy rather than per-admin values; a multi-admin device may not expose enough information to reconstruct another admin's contribution. User reports Device Owner enrollment and in-app rollback PASS on a separate test device; device model/build and independent reproduction are not recorded |
| SEC-04 | Do not claim protection against force-stop, uninstall, physical shutdown, battery removal, recovery/bootloader, firmware, or factory reset | UI, privacy notice, docs | source/docs review | Documented limitation |
| DOC-01 | Master spec is implementation contract | `UNIVERSAL_MAXIMUM_PROTECTION_SPEC.md` | file exists | Present |
| DOC-02 | Every requirement maps to owner files, tests, and evidence | this matrix | updated source baseline and explicit device-unverified labels | Implemented; update when code or test evidence changes |
| DOC-03 | User-facing docs and README make privacy, setup, compatibility, authentication and limitations consistent | README and docs | README, Device Owner guide, security audit, protection rules and master spec reconciled | Updated; repeat consistency audit when behavior changes |
| DOC-04 | Release APK uses persistent owner-controlled signing key and versionCode increments | Gradle, CI | workflow conditionally verifies signer | CI path depends on secrets; release signing must be verified before distribution |

## 6. Required test matrix before calling the app release-ready

### Automated
- Unit tests for state transitions, stale event rejection, monotonic deadlines, reboot identity, authentication throttling, compatibility-family mapping, alarm volume restoration, corrupted storage, and failed persistence writes.
- JVM unit tests for pure state, policy, crypto, preference-decoding and compatibility logic; source-contract checks for manifest, permissions, state ownership, navigation and release identity; Android lint; debug/release APK packaging and metadata/signer verification; CodeQL.
- Android instrumentation tests and emulator matrices are excluded by owner direction. Do not compile or execute `app/src/androidTest` sources.
- Physical OEM acceptance remains a separate real-device gate for process lifecycle, alarm delivery/audio, Bluetooth accessories, Keyguard, Device Owner, permissions, TalkBack and manufacturer battery policies.
- Static checks: lint, manifest/permission audit (confirm CAMERA is declared but requested only after explicit owner opt-in, no camera FGS or microphone/background-location permission), dependency audit, packaged asset integrity, APK package/version/signer verification.
- Alarm flashlight unit checks for ALARM_TRIGGERED-only gating and slow pulse cadence; physical test must confirm LED pulses with audio and turns off after successful authentication.

### Physical OEM acceptance
- HONOR 200 Pro / MagicOS (user-reported v1.1.2 core PASS; repeat after v1.1.3 flash implementation).
- Samsung One UI.
- Xiaomi / Redmi / POCO HyperOS or MIUI.
- OPPO / realme / OnePlus.
- vivo / iQOO.
- Google Pixel / stock Android.
- Motorola / Lenovo.
- ASUS, Sony, Nothing, Tecno, Infinix, and ZTE when hardware is available.
- For each device: plug/unplug event timing; reconnect; screen off; foreground/background; battery saver; app swipe-away; force-stop boundary; reboot; exact-alarm denied/granted; notification denied/granted; audio route/volume; secure lock screen; Device Admin opt-in/out; OS update; app update.

## 7. Definition of Done — code/GitHub scope

A requirement is **complete** for this task only when:
1. Its implementation is present and reviewed across the connected source files.
2. Its software failure modes and cross-file interactions are covered by JVM tests or explicit static/source-contract checks where testable.
3. Android build, JVM unit tests, lint, APK metadata/package/signing-contract checks, and CodeQL pass on the exact final commit.
4. UI, security claims, permissions, and documentation accurately describe implemented behavior and platform limitations.
5. No unrelated feature, cloud dependency, or destructive migration is introduced.
6. The Draft PR remains unmerged until the owner explicitly approves integration.

Physical-device tests, emulator tests, and instrumentation compilation/execution are excluded from this task and do not block code/GitHub completion. **A green CI run does not imply universal hardware/OEM compatibility.**


## 8. Recent audit changes

- `f8f434c`: added this traceability matrix.
- `ba2f07a` and `7b7cbdb`: expanded OEM family recognition and added unit coverage for ASUS, Sony Xperia, Nothing OS, TECNO / Infinix / itel, ZTE / nubia, and Lenovo.
- `e260ec0`: fixed the Kotlin exhaustive-`when` compile error in SettingsActivity found by CI.
- `dba4c81` / `bf74087`: added an explicit full-screen dashboard policy and connected it to the alarm notification only when the owner opts in and Android grants access.
- `ad80ec6`: added unit tests for API 26–37 full-screen-intent gating.
- `58d2271`: removed the optional full-screen dashboard permission from core readiness requirements when the feature is off.
- `75c00ae` / `b390038`: recognized the Transsion manufacturer alias and aligned the unknown-OEM test with the new generic fallback wording.
- `bb0e884`: added conservative persisted-state decoding: absent state remains first-install `DISARMED`; unknown modes and malformed countdown records become latched `ALARM_TRIGGERED` rather than silently disarming.
- `8d5490c`: connected `ProtectionStore.read()` to the conservative persisted-state decoder.
- `3f72955`: added `ProtectionStatePersistenceTest` coverage for missing, malformed, valid, and latched persisted states.
- `2086fe2` / `77522e8` / `6f86965`: strengthened fail-closed handling for partial/corrupt preferences (including wrong preference value types) and added state-engine regression coverage.
- `953a9c6`: increased Gradle wrapper distribution download timeout from 10 seconds to 120 seconds after repeated runner-side timeout/HTTP 502 failures; this changes CI resilience only, not app runtime behavior.
- Managed Device Owner safety audit: synchronous snapshots preserve pre-existing package/feature policies and local/global restriction values. New policy application leaves Safe Boot and USB debugging available. Pending rollback remains retryable; emergency recovery re-enables normal navigation controls. `ManagedPolicySnapshotTest` covers snapshot semantics. Source-head CI passed; user-reported Device Owner enrollment and rollback PASS on a separate test device; device/build details and independent reproduction are not recorded.
- Countdown recovery fixes: newly created countdowns use monotonic deadlines only when a valid boot identity is available; otherwise they use a wall-clock fallback. BOOT_COMPLETED now invalidates the previous charger baseline even if BOOT_COUNT is unavailable. If the first post-boot power sample is unknown, a dedicated pending marker retries until a reliable sample arrives; only a confirmed disconnected sample starts the fresh 10-second grace period.
- Authentication hardening: removed `ACTION_USER_PRESENT` and keyguard-state polling as alarm-disarm signals because a trusted-unlock flow can dismiss keyguard without a fresh PIN or biometric. Only explicit app-launched device-credential confirmation or BIOMETRIC_STRONG success clears the alarm. Malformed throttle preferences now recover with a bounded cooldown while preserving the Android credential fallback.
- Latched-alarm recovery: malformed persisted state is normalized to a persisted ALARM_TRIGGERED state; monitor and deadline recovery paths restart the alarm instead of rejecting a stale countdown token.
- Alarm volume recovery: malformed saved-volume preferences no longer crash service teardown or owner authentication; the invalid value is discarded and an incomplete-restore event is recorded because the original volume cannot be reconstructed.
- Unknown-power reboot recovery: `BOOT_COMPLETED` explicitly invalidates the previous charger baseline even if BOOT_COUNT is unavailable. A pending countdown with an unknown first power sample is represented by a dedicated persisted marker; the first confirmed disconnected sample starts a new full 10-second countdown. Added pure-engine and persistence regression tests.
- CI debug signing: the debug keystore path is created before cache restore so the cache action can save the first generated development key; push builds can reuse that identity. This is not a production release key.
- Documentation corrections align charger receiver registration with the runtime foreground-service registration, and align the stated target SDK with Gradle (API 36).
- Android 17 background-audio behavior is documented against the official platform guidance: [Android 17 background audio hardening](https://developer.android.com/develop/background-work/services/fgs/changes) and [Android 17 audio hardening details](https://developer.android.com/about/versions/17/changes/bg-audio).


## Phase 10–12 additional traceability

| ID | Requirement / acceptance condition | Primary owner(s) | Existing test / evidence | Status / limitation |
|---|---|---|---|---|
| DEV-INT-01 | Android 16+ ranging capability check is explicitly owner-initiated from Device Intelligence or Bluetooth Center; both screens share one bounded local availability callback; do not declare/request RANGING or location permission; never discover peers or create a ranging session | `DeviceIntelligenceActivity.kt`, `BluetoothCenterActivity.kt`, `RangingCapabilityQuery.kt`, `RangingCapabilityStatus.kt` | `RangingCapabilityStatusTest`; `verify_ranging_privacy.py`; exact-head Kotlin compile/JVM CI; Android API 36 RangingManager contract review | Implemented and CI-verified; callback is bounded to 8 seconds and lifecycle-cleaned; OEM capability response requires physical API 36+ device verification |
| PRIV-06 | Privacy policy accurately states that no RANGING permission is requested and no peer/session/location data is collected or uploaded | `docs/PRIVACY_POLICY.md`, Device Intelligence screen | Manifest permission inventory and source review | Documentation aligned; exact-head CI still required |
| CI-10 | No Android instrumentation source compilation/execution or emulator matrix in Android/release workflows | `scripts/verify_no_instrumentation.py`, Android and release workflows, Gradle config | CI policy guard | Enforced by source-contract step; physical-device and emulator acceptance excluded |
| REL-11 | Release candidate remains on development branch; no merge, tag, production APK publication or official release without separate owner approval | Release workflow and branch policy | GitHub branch/PR/release inventory review | No production release performed; integration conflict review remains separate |
| DEV-12 | Physical acceptance records exact device, OS/API, OEM build, app version, tested commit, scenario and observed result | Device compatibility and HONOR 200 Pro acceptance documents | Physical-device evidence template | OUT OF SCOPE for current code/GitHub completion; retained as reference only |


## Owner scope and current GitHub review — 2026-11-17

- Authorized completion scope: repository code, documentation, non-instrumentation CI, and GitHub branch/PR readiness only.
- Explicitly excluded: physical-device tests, emulator tests, instrumentation compilation/execution, and hardware/OEM acceptance.
- Working branch HEAD: `c711bc16cb9a42452e10c8ec47f40b7d20fefe8a`.
- Draft review PR: [#7 — ThiefAlertSeQure Phases 0–12 code completion](https://github.com/alinor2pundamdag/ThiefAlertSeQure/pull/7); draft and unmerged.
- Exact-head CI for PR #7 has not yet been observed in the connector results at the time of this update. The last evidenced Android/CodeQL success remains the earlier commits listed above.
- Branch comparison at this snapshot: 500 commits ahead (API comparison cap), 1 behind `main`; divergence remains unresolved. No merge or rebase was performed.


## Continued software audit — 2026-11-17

- Confirmed authentication freshness defect fixed in `e17f482264eba0d027b92499010be3b72c6d0086`: mismatched availability of issued/current boot identity now rejects the ephemeral proof.
- Regression coverage added in `80c8c52be927db24a31a7c08d699998ffb72d559`: known-to-unknown, unknown-to-known, and both-unknown identity cases.
- Instrumentation policy guard strengthened in `1e69aeb6b412ca52c9b79049e8be024cda8fb817`: it now scans every Gradle build/configuration file (`.gradle`, `.gradle.kts`, `.toml`) and all GitHub Actions workflow YAML files for forbidden instrumentation/emulator build wiring.
- Historical FIXALL changes were committed on `work/phase-0-12-coding`; they have not yet received independently confirmed exact-head Android/JVM/CodeQL CI results.
- Source review in this continuation covers the charger, protection state/persistence, alarm scheduling/recovery, boot, monitor lifecycle, A2 Bluetooth registry/scheduler/receiver, encryption, updater, alarm audio/volume, and authentication flows. Repository-wide audit remains open; no claim of 100% source audit is made.
- Physical-device, emulator, and instrumentation execution remain explicitly excluded from the owner's completion criteria.


### A2 concurrent deadline-store hardening — 2026-11-17

- A2 pending deadline persistence now serializes schedule/find/cancel/clear operations to prevent concurrent ACL callbacks for different accessories from overwriting the shared encrypted list.
- Failed schedule cleanup now uses the same isolated preference/PendingIntent namespace as the originating operation.
- Pure per-device merge helper preserves unrelated accessory records and replaces only a case-insensitive match.
- JVM tests added in `app/src/test/java/com/thiefalertsequre/app/A2DisconnectSchedulerMergeTest.kt`.
- Source commits: `5cf2da0b7c1f134e9a1fcdfd4b913a2561aa85c3`, `679f22ac7c2721af15e298a851fdbbfbf8c4b31e`; test commit: `f5bf25576de8a903699a989924a515ea506401a6`.
- Exact-head CI remains pending; this is not yet CI-verified.


## 2026-11-17 continuation snapshot

The CodeQL gate passed after the APK-installer suppression comments were moved to the exact flagged sink lines in `SettingsActivity.kt`. The underlying updater still verifies its private-cache APK by SHA-256, production package ID, increasing versionCode and pinned release certificate. The release workflow's manual signed preflight allowlist is restricted to `main` only. Android JVM/static/lint/APK verification is rerun on the final follow-up branch HEAD; do not reuse a prior commit's result as exact-head evidence. Physical-device, emulator and instrumentation testing remain outside the owner-authorized task scope.


## Exact-head verification update — 2026-11-17

- Exact working-branch HEAD: `a1a567bf53f97189be2547ccf872ae665c30a54b`.
- Android/JVM/static/lint/APK workflow: [#44005105554 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44005105554).
- CodeQL Java/Kotlin and Actions analysis: [#44005105442 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44005105442).
- Branch comparison: 530 ahead / 0 behind; 131 changed files (90 added, 41 modified, 0 deleted).
- Recursive tree: 64 production Kotlin source files; no truncation. Automated marker/pattern sweeps covered the source set, with focused manual review of high-risk paths; this is not claimed as exhaustive line-by-line formal verification.
- PR #7 has one resolved inline review thread. The thread references CodeQL alert #8, but the alert's actual active/closed state could not be retrieved through the available GitHub connector. Alert closure remains **NOT VERIFIED**.
- Permanent release signing-secret presence and production-signed APK availability remain **NOT VERIFIED**. Release workflow is fail-closed and production publication remains unauthorized.
- Instrumentation compilation/execution, emulator tests and physical-device tests were not performed and remain excluded.
- PR #7 remains draft/open/unmerged. `main` remains untouched.


## Exact-head verification closeout — 2026-11-17

- Android [#44010766603 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010766603) and CodeQL [#44010774991 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010774991) on `e97817903976689f02cba8bb485fc160a7122fd3`.
- 30 JVM unit-test source files; all passed in the Android workflow. Fifteen dormant `androidTest` source files were not compiled or executed.
- Android lint, manifest/preferences/navigation/state-ownership checks, asset validation, release workflow YAML validation, release identity check, debug APK metadata, minified release metadata, package/version/signer checks and artifact upload all passed.
- Branch comparison is 530 ahead / 0 behind, 131 changed files (90 added, 41 modified, 0 deleted). PR #7 remains draft/open/unmerged and mergeable.
- CodeQL alert #8 closure and permanent release signing-secret availability remain unverified; neither is inferred from workflow success.
- No physical-device, emulator or instrumentation test was performed.


## Final verified candidate snapshot — 2026-11-17

- Exact candidate `e97817903976689f02cba8bb485fc160a7122fd3`: Android [#44010766603 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010766603), CodeQL [#44010774991 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010774991).
- 30 JVM unit-test source files passed; 15 dormant instrumentation source files were not compiled or executed.
- Source checks, lint, asset validation, release workflow YAML, release identity, APK metadata/package/version/signer checks and artifact upload passed.
- Branch comparison before this one documentation-only refresh: 530 ahead / 0 behind; after it: 531 ahead / 0 behind. PR #7 remains draft/open/unmerged.
- Alert #8 closure and permanent production signing-secret availability remain unverified.


## Authoritative latest candidate verification — 2026-11-17

- Exact candidate `524754f683d43450c844bca05147e66c15baf3d6`: Android [#44011669247 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44011669247), CodeQL [#44011663424 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44011663424).
- Android passed the 30-file JVM unit suite, instrumentation-disabled guard, source-contract checks, lint, asset validation, APK packaging/metadata, release metadata, package/version/signer checks and artifact upload. CodeQL Java/Kotlin and Actions analyses passed.
- The artifact is development-only; production signing is not verified. CodeQL alert #8's active/closed status is not verified.
- Branch comparison: 531 ahead / 0 behind; 131 changed paths (90 added, 41 modified, 0 deleted). PR #7 remains draft/open/unmerged.
- No instrumentation compilation/execution, emulator or physical-device testing was performed.


## Bypass Charging integration — product roadmap Phase 4

| ID | Requirement | Source / evidence | Status |
|---|---|---|---|
| BYPASS-01 | Per-device OEM adapter must explicitly probe hardware support; manufacturer/model name alone is not proof | BypassChargingController.kt, BypassChargingAdapterRegistry, BypassChargingControllerTest | Framework implemented; registry intentionally empty pending certified OEM interface |
| BYPASS-02 | ON/OFF command requires explicit authorization and a readable initial state | BypassChargingController.kt, JVM tests | Unit-covered policy |
| BYPASS-03 | Actual mode must be read back after every command; no optimistic green state | BypassChargingController.kt, JVM tests | Unit-covered policy |
| BYPASS-04 | UI must not offer a functional toggle without a verified adapter; unknown must not be labelled OFF | SettingsActivity.kt, source guard | Implemented; switch remains disabled on current devices |
| BYPASS-05 | Do not add hidden/privileged permissions, arbitrary shell, sysfs, hidden APIs, Accessibility workarounds or privilege escalation | Manifest review, verify_bypass_charging_contract.py | Source guard added; no new permission |
| BYPASS-06 | OEM persistence/reboot behavior and physical charging path must be certified per model/firmware | docs/BYPASS_CHARGING_INTEGRATION.md | Not verified; requires authorized OEM interface and separate device acceptance |
