# Requirement Traceability Matrix — ThiefAlertSeQure

> Previous verified application baseline: `1.1.2` / versionCode `4`, with exact-head CI run [42479548944](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/42479548944). Current feature branch now contains v`1.1.3` / versionCode `5` with synchronized alarm flashlight and acknowledgement-only onboarding; exact-head CI and APK verification are pending.
> Verified APK: artifact `ThiefAlertSeQure-debug-apk`, artifact ID `13617306483`, run `42479548944`, expires 2026-11-22. Package `com.thiefalertsequre.app`; APK SHA-256 `5dc6467e90364aa7f4f730d1caeb99777ac29d69058e2981b3021a6c966dc287`; V2 signer SHA-256 `c115de2ef097e5eab7243874ad7bf212adf94f419d8fc2d963d78c8fdaebf00c`. This is a development/debug APK; permanent release-signing secrets are not configured. Earlier 1.1.1 debug artifacts used different signers, so an in-place update from an older debug install is not guaranteed.
> Contract: [Universal Maximum Protection Specification](UNIVERSAL_MAXIMUM_PROTECTION_SPEC.md).
> Status labels: **Implemented** means source code exists; **Unit-covered** means a named local test exists; **CI-verified** means the exact baseline commit passed GitHub Actions; **Device-unverified** means no physical-device evidence is recorded for that case. These labels are deliberately not interchangeable.

## 1. Verification snapshot

| Area | Evidence at baseline | Status / limitation |
|---|---|---|
| Branch / PR | PR #2 remains open and draft; managed-policy safety implementation is on the current feature branch | Not merged |
| CI | Previous v1.1.2 baseline push workflow run `42479548944` passed unit tests, Android lint, asset validation, version extraction, APK assembly, package/version verification, signer verification and artifact upload. | Previous baseline CI-verified; current v1.1.3 exact-head CI pending |
| Unit tests | `:app:testDebugUnitTest` in workflow | Unit tests execute in CI |
| Lint | `:app:lintDebug` in workflow | CI-verified |
| APK | Previous artifact ID `13617306483` from run `42479548944`; versionCode 4 / versionName 1.1.2; APK SHA-256 `5dc6467e90364aa7f4f730d1caeb99777ac29d69058e2981b3021a6c966dc287`; signer SHA-256 `c115de2ef097e5eab7243874ad7bf212adf94f419d8fc2d963d78c8fdaebf00c` | Historical v1.1.2 debug APK; v1.1.3 artifact pending exact-head CI; no release-signing secrets configured |
| Instrumentation / emulator matrix | No `app/src/androidTest` suite or emulator matrix exists in the inspected tree | Not implemented |
| Physical OEM acceptance | User-reported HONOR 200 Pro: charger detection/countdown, reconnect cancellation, latched alarm, explicit PIN/biometric recovery, incorrect credential rejection, cancelled prompt, volume restoration, screen-off/background and reboot recovery all PASS. App Launch cards are present; direct OEM shortcut routing falls back to Settings. User reports Device Owner enrollment and rollback PASS on a separate test device. | User-reported evidence; not independently reproduced. New flashlight feature still requires physical test. Android 17 audio remains deferred. Force-stop resilience is not supported as a guarantee. |
| Universal claim | Android/OEM policies differ and ordinary apps cannot defeat privileged system controls | Never claim universal guarantee |

## 2. Core protection requirements

| ID | Requirement / acceptance condition | Primary owner(s) | Existing test / evidence | Baseline status |
|---|---|---|---|---|
| CORE-01 | A single authoritative state machine: DISARMED, ARMED, COUNTDOWN, ALARM_TRIGGERED | `ProtectionState.kt`, `ProtectionEngine.kt` | `ProtectionEngineTest` | Unit-covered |
| CORE-02 | Confirmed plug arms; unplug only starts countdown from ARMED | `PowerTransitionCoordinator.kt`, `ProtectionEngine.kt` | `unplugStartsTenSecondCountdown`; coordinator tests | Unit-covered; physical event ordering device-dependent |
| CORE-03 | No countdown from a disconnected snapshot, unknown state, or service startup | `PowerTransitionCoordinator.kt`, `ExternalPowerState.kt` | `ExternalPowerStateTest`, coordinator tests | Unit-covered |
| CORE-04 | Debounce charger state; reject stale or superseded connect/disconnect candidates | `PowerTransitionCoordinator.kt`, `PowerTransitionReceiver.kt` | `acceptsPowerConnectedEvent` regression; inspect coordinator tests | Partially unit-covered; event races need instrumentation |
| CORE-05 | Start countdown from captured OS event time, not after debounce delay | `PowerTransitionReceiver.kt`, `PowerTransitionCoordinator.kt` | Source inspection; event timestamp passed through | Implemented; device timing unverified |
| CORE-06 | Reconnect before expiry cancels countdown and arms | `ProtectionEngine.kt`, coordinator, deadline receiver | `reconnectBeforeDeadlineRearms`, `reconnectAtDeadlineCancelsAlarmEvenIfDeadlineHasElapsed` | Unit-covered |
| CORE-07 | Unknown power state never expires countdown; retry without false alarm | `ProtectionEngine.kt`, `AlarmDeadlineReceiver.kt`, monitor service | `unknownChargerStateNeverTriggersCountdown` | Unit-covered |
| CORE-08 | Monotonic elapsed-time deadline and boot identity; never compare old-boot monotonic time | `ProtectionClock.kt`, `ProtectionState.kt`, engine/store | wall-clock, boot identity and reboot tests | Unit-covered |
| CORE-09 | Reboot invalidates the old charger baseline even when BOOT_COUNT is unavailable; unknown initial power sample waits on a dedicated marker; first confirmed disconnected sample starts a fresh grace period | `BootReceiver.kt`, `PowerTransitionCoordinator.kt`, engine, state decoder | `unknownPowerAtBootCreatesAOneSecondReconciliationMarker`, `rebootCountdownWaitsForReliablePowerSampleBeforeStartingFreshGracePeriod`, state persistence sentinel test | Source and unit-covered; BOOT_COMPLETED and charger broadcasts remain device-unverified |
| CORE-10 | ARMED + unplugged boot snapshot must not invent a physical transition | `BootReceiver.kt`, engine | `rebootWhileArmedAndUnpluggedDoesNotInventTransition` | Unit-covered |
| CORE-11 | ALARM_TRIGGERED is latched; reconnect, reboot, or service recovery cannot clear it | engine, coordinator, boot receiver | reconnect/reboot latch tests | Unit-covered |
| CORE-12 | Only successful app-launched Android device-credential confirmation or BIOMETRIC_STRONG authentication clears the latched alarm | `MainActivity.kt`, `OwnerAuthenticationCoordinator.kt`, `ProtectionEngine.kt` | state transition tests plus source review of Android authentication result callbacks | State transition unit-covered; physical prompt integration device-unverified |
| CORE-13 | Stale AlarmManager callbacks cannot affect a newer countdown | `ProtectionEngine.kt`, deadline receiver, scheduler | `staleDeadlineCallbackCannotMatchNewCountdown` | Unit-covered |
| CORE-14 | Persistence write failures do not report an uncommitted state as saved | `ProtectionStore.kt` | `writeUnlocked` uses synchronous `commit()`; `update()` returns the prior state if commit fails | Source behavior reviewed; failure-injection test still missing |
| CORE-15 | Corrupt or partially written state fails safely and is recoverable | `ProtectionState.fromPersisted`, `ProtectionStore.kt`, boot recovery | `ProtectionEngineTest.partialOrCorruptPersistedStateFailsClosed`, `validPersistedCountdownRetainsItsDeadline`; malformed preference reads are caught in store | Fail-closed decoder implemented and unit-covered; Android SharedPreferences corruption injection still needs instrumentation coverage |
| CORE-16 | Service process death / duplicate receiver / queue saturation do not create duplicate or lost transitions | receiver, coordinator, monitor service | No instrumentation/process-death suite identified | Needs integration tests |
| CORE-17 | Monitoring opt-out stops monitoring without clearing a latched alarm | monitor service, boot receiver, policy | `ProtectionMonitorPolicyTest` | Unit-covered; lifecycle integration unverified |

## 3. Alarm, audio, lock, and authentication

| ID | Requirement / acceptance condition | Primary owner(s) | Existing test / evidence | Baseline status |
|---|---|---|---|---|
| ALARM-01 | Alarm starts only from persisted ALARM_TRIGGERED state | `AlarmService.kt`, deadline receiver | source guard | Implemented; integration test needed |
| ALARM-02 | Alarm runs as foreground service with ongoing notification | `AlarmService.kt`, manifest | CI lint/build | Implemented; notification permission/channel behavior device-unverified |
| ALARM-03 | Built-in alarm loops; invalid custom audio falls back to built-in, then vibration | `AlarmService.kt`, `AlarmSoundStore.kt` | no media instrumentation test identified | Implemented; device audio matrix required |
| ALARM-04 | Preserve user's prior alarm-stream volume and restore only after authenticated disarm; malformed saved-volume metadata must not crash recovery | `AlarmVolumeController.kt`, auth coordinator | source review of guarded preference reads and explicit incomplete-restore event | Implemented; AudioManager integration and volume restoration remain device-unverified |
| ALARM-05 | Reassert maximum alarm stream while latched, without claiming hardware-level volume control | `AlarmService.kt`, volume controller | source inspection | Implemented; OEM/fixed-volume/Bluetooth behavior varies |
| ALARM-06 | Device lock is optional and only requested when secure keyguard + active Device Admin permit it | `AlarmService.kt`, admin receiver, settings UI | no DPM instrumentation test identified | Implemented; physical test required |
| ALARM-07 | Do not relock after owner unlock during the same alarm episode | `AlarmService.kt` | persisted per-episode lock timestamp | Implemented; keyguard transition device test required |
| ALARM-08 | Full-screen dashboard requires local owner opt-in and Android 14+ system access; otherwise retain normal alarm notification | `AlarmPresentationPolicy.kt`, `AlarmService.kt`, SettingsActivity | `AlarmPresentationPolicyTest` | Unit-covered policy; real notification/lock-screen behavior device-unverified |
| ALARM-09 | Optional camera LED pulses only while persisted mode is ALARM_TRIGGERED; it stops on authenticated disarm/service teardown; flash failure never blocks audio; owner can disable it | `AlarmFlashController.kt`, `AlarmService.kt`, `SettingsActivity.kt`, manifest | `AlarmFlashPolicyTest`; source guards; CI pending | Implemented; cadence/state unit-covered; physical LED behavior and stop timing device-unverified |
| AUTH-01 | Android verifies PIN/pattern/password/biometric; app never reads credential material | auth coordinator, MainActivity | source inspection | Implemented by design; integration test required |
| AUTH-02 | Failed authentication is throttled and persisted across process restart | `AuthenticationThrottle.kt`, store | `AuthenticationThrottleTest` | Unit-covered |
| AUTH-03 | Only explicit app-launched Android device-credential confirmation or BIOMETRIC_STRONG success clears the alarm; keyguard dismissal alone never clears it | `MainActivity.kt`, `OwnerAuthenticationCoordinator.kt` | authentication result handling; system-unlock bypass removed | Implemented by source; physical credential/biometric integration remains device-unverified |

## 4. Android platform and universal OEM compatibility

| ID | Requirement / acceptance condition | Primary owner(s) | Existing test / evidence | Baseline status |
|---|---|---|---|---|
| AND-01 | minSdk 26 and target/compile SDK 36 are consistent with current project build | `app/build.gradle.kts` | CI build/lint | CI-verified at baseline; re-evaluate on SDK upgrades |
| AND-02 | Charger events are registered at runtime from foreground monitor; do not rely on manifest-only implicit broadcast delivery | monitor service, receiver, manifest | source inspection | Implemented; Android-version device matrix required |
| AND-03 | Foreground-service declarations/types/permissions match service behavior and target SDK | manifest, monitor/alarm service | CI lint/build | Needs API 34–36 emulator/instrumentation validation and policy review |
| AND-04 | Exact-alarm denial does not silently claim precise delivery; fallback/recovery is surfaced | scheduler, boot receiver, UI | scheduler test not identified | Needs explicit tests and UI state verification |
| AND-05 | Notification permission/channel denial is reflected in readiness UI | MainActivity, SettingsActivity | no UI/instrumentation test identified | Needs UI tests |
| AND-06 | Full-screen intent access is treated separately from app lock-screen dashboard preference | alarm service, settings UI | source/UI inspection | Implemented; API 34+ policy and behavior require verification |
| AND-07 | Edge-to-edge/system bars/cutouts keep controls reachable | compatibility helper, both activities | no screenshot/instrumentation test identified | Implemented; emulator UI matrix required |
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
| PRIV-02 | No unnecessary contacts/location/microphone/CAMERA/SMS permissions; optional flash uses CameraManager torch control without camera capture permission; camera and flash hardware are optional features | manifest, `AlarmFlashController.kt` | manifest inspection; no CAMERA permission added | Implemented by source; Google Play device filtering and torch behavior require final merged-manifest/physical validation |
| PRIV-03 | Backup disabled for sensitive local protection state | manifest | manifest inspection | Implemented at baseline |
| PRIV-04 | Local event history bounded and contains no owner credential material | local tamper history | source inspection; capacity test not identified | Implemented; boundedness test needed |
| PRIV-05 | Audio document URI permissions are scoped to user-selected files | audio store | source inspection | Implemented; provider revocation test needed |
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
- Android instrumentation tests for runtime receiver registration, foreground-service start restrictions, exact-alarm permission changes, notification permission/channel behavior, keyguard dismissal, Device Admin lock behavior, audio routing, and process recreation.
- Emulator runs for API 26, 28, 29, 30, 31, 32, 33, 34, 35, and 36 (at minimum, representative API levels plus boundary versions).
- Static checks: lint, manifest/permission audit (confirm no CAMERA permission and optional camera/flash hardware features), dependency audit, packaged asset integrity, APK package/version/signer verification.
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

## 7. Definition of Done

A requirement is **complete** only when:
1. Its owner implementation is present and reviewed.
2. Its failure modes and cross-file interactions are covered by automated tests where testable.
3. CI passes on the exact commit.
4. Device-dependent behavior is validated on the named Android/OEM targets, or explicitly remains marked **Device-unverified**.
5. UI and documentation accurately describe actual capability and limitations.
6. No unrelated feature or cloud dependency is introduced.
7. PR remains unmerged until the owner explicitly approves integration.

**No universal compatibility guarantee is implied by generic fallback guidance or a successful CI run.**


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
