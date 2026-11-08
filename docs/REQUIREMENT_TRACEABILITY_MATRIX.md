# Requirement Traceability Matrix — ThiefAlertSeQure

> Baseline: `feat/custom-audio-and-reliability` at `96a534664af31530bc2974069649b788a189d183` (2026-11-08).
> Contract: [Universal Maximum Protection Specification](UNIVERSAL_MAXIMUM_PROTECTION_SPEC.md).
> Status labels: **Implemented** means source code exists; **Unit-covered** means a named local test exists; **CI-verified** means the exact baseline commit passed GitHub Actions; **Device-unverified** means no physical-device evidence is recorded for that case. These labels are deliberately not interchangeable.

## 1. Verification snapshot

| Area | Evidence at baseline | Status / limitation |
|---|---|---|
| Branch / PR | PR #2 is open and draft; branch head is the baseline SHA above | Not merged |
| CI | Android build and tests run 42456409652 completed with conclusion `success` on the baseline SHA | CI-verified only |
| Unit tests | `:app:testDebugUnitTest` in workflow | Unit tests execute in CI |
| Lint | `:app:lintDebug` in workflow | CI-verified |
| APK | Workflow assembles release only when persistent signing secrets exist; otherwise debug APK | Packaging verified; signing mode depends on secrets |
| Instrumentation / emulator matrix | No `app/src/androidTest` suite or emulator matrix exists in the inspected tree | Not implemented |
| Physical OEM acceptance | User-reported HONOR 200 Pro checks exist in project handoff; other OEM evidence not recorded | Partial user-reported evidence; not independently verified |
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
| CORE-09 | Reboot during countdown grants a fresh grace period only after live disconnected confirmation | `BootReceiver.kt`, engine | reboot countdown tests | Unit-covered; boot broadcast/device behavior unverified |
| CORE-10 | ARMED + unplugged boot snapshot must not invent a physical transition | `BootReceiver.kt`, engine | `rebootWhileArmedAndUnpluggedDoesNotInventTransition` | Unit-covered |
| CORE-11 | ALARM_TRIGGERED is latched; reconnect, reboot, or service recovery cannot clear it | engine, coordinator, boot receiver | reconnect/reboot latch tests | Unit-covered |
| CORE-12 | Only authenticated owner flow clears latched alarm | `OwnerAuthenticationCoordinator.kt`, auth coordinator, engine | auth and keyguard state tests | Unit-covered; real biometric/keyguard integration needs devices |
| CORE-13 | Stale AlarmManager callbacks cannot affect a newer countdown | `ProtectionEngine.kt`, deadline receiver, scheduler | `staleDeadlineCallbackCannotMatchNewCountdown` | Unit-covered |
| CORE-14 | Persistence write failures do not report an uncommitted state as saved | `ProtectionStore.kt` | Store-level failure injection not present in inspected tests | Needs dedicated tests |
| CORE-15 | Corrupt or partially written state fails safely and is recoverable | `ProtectionStore.kt`, boot recovery | No corruption/recovery test identified | Needs dedicated tests |
| CORE-16 | Service process death / duplicate receiver / queue saturation do not create duplicate or lost transitions | receiver, coordinator, monitor service | No instrumentation/process-death suite identified | Needs integration tests |
| CORE-17 | Monitoring opt-out stops monitoring without clearing a latched alarm | monitor service, boot receiver, policy | `ProtectionMonitorPolicyTest` | Unit-covered; lifecycle integration unverified |

## 3. Alarm, audio, lock, and authentication

| ID | Requirement / acceptance condition | Primary owner(s) | Existing test / evidence | Baseline status |
|---|---|---|---|---|
| ALARM-01 | Alarm starts only from persisted ALARM_TRIGGERED state | `AlarmService.kt`, deadline receiver | source guard | Implemented; integration test needed |
| ALARM-02 | Alarm runs as foreground service with ongoing notification | `AlarmService.kt`, manifest | CI lint/build | Implemented; notification permission/channel behavior device-unverified |
| ALARM-03 | Built-in alarm loops; invalid custom audio falls back to built-in, then vibration | `AlarmService.kt`, `AlarmSoundStore.kt` | no media instrumentation test identified | Implemented; device audio matrix required |
| ALARM-04 | Preserve user's prior alarm-stream volume and restore only after authenticated disarm | `AlarmVolumeController.kt`, auth coordinator | no controller unit test identified | Needs unit tests and device checks |
| ALARM-05 | Reassert maximum alarm stream while latched, without claiming hardware-level volume control | `AlarmService.kt`, volume controller | source inspection | Implemented; OEM/fixed-volume/Bluetooth behavior varies |
| ALARM-06 | Device lock is optional and only requested when secure keyguard + active Device Admin permit it | `AlarmService.kt`, admin receiver, settings UI | no DPM instrumentation test identified | Implemented; physical test required |
| ALARM-07 | Do not relock after owner unlock during the same alarm episode | `AlarmService.kt` | persisted per-episode lock timestamp | Implemented; keyguard transition device test required |
| AUTH-01 | Android verifies PIN/pattern/password/biometric; app never reads credential material | auth coordinator, MainActivity | source inspection | Implemented by design; integration test required |
| AUTH-02 | Failed authentication is throttled and persisted across process restart | `AuthenticationThrottle.kt`, store | `AuthenticationThrottleTest` | Unit-covered |
| AUTH-03 | System unlock clears alarm only when device is secure and keyguard is actually dismissed | engine, user-present receiver, alarm service | keyguard predicate tests | Unit-covered; broadcast behavior device-dependent |

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
| OEM-08 | ASUS, Sony, Nothing, Tecno, Infinix, ZTE, and other vendors are not rejected; use generic fallback unless a stable public route is verified | compatibility helper | generic fallback only | Fallback implemented; dedicated recognition and device validation outstanding |
| OEM-09 | Never depend on undocumented OEM component names or claim the app can toggle private auto-start controls | compatibility helper, SettingsActivity | source inspection | Implemented by design |
| OEM-10 | Manufacturer/model/Android version are shown to help tailor instructions | compatibility helper, settings UI | device-summary helper; guide test | Implemented; verify rendered UI |

## 5. Privacy, security, administration, and documentation

| ID | Requirement / acceptance condition | Primary owner(s) | Existing test / evidence | Baseline status |
|---|---|---|---|---|
| PRIV-01 | No INTERNET permission, backend, analytics, advertising, or cloud dependency in local protection core | manifest, Gradle dependencies, app code | manifest/dependency inspection | Implemented at baseline; re-audit every dependency change |
| PRIV-02 | No unnecessary contacts/location/microphone/camera/SMS permissions | manifest | manifest inspection | Implemented at baseline |
| PRIV-03 | Backup disabled for sensitive local protection state | manifest | manifest inspection | Implemented at baseline |
| PRIV-04 | Local event history bounded and contains no owner credential material | local tamper history | source inspection; capacity test not identified | Implemented; boundedness test needed |
| PRIV-05 | Audio document URI permissions are scoped to user-selected files | audio store | source inspection | Implemented; provider revocation test needed |
| SEC-01 | No accessibility-based power-menu interception, privilege escalation, or hidden system API | app source/dependencies | source review | Prohibited by design; audit on each change |
| SEC-02 | Device Admin and Device Owner are clearly distinguished | managed controller, docs, UI | source/docs review | Implemented; provisioning tests needed |
| SEC-03 | Managed restrictions require explicit owner/administrator provisioning and have a safe rollback path | managed controller, Device Owner guide | no provisioning instrumentation test identified | Needs managed-device test |
| SEC-04 | Do not claim protection against force-stop, uninstall, physical shutdown, battery removal, recovery/bootloader, firmware, or factory reset | UI, privacy notice, docs | source/docs review | Documented limitation |
| DOC-01 | Master spec is implementation contract | `UNIVERSAL_MAXIMUM_PROTECTION_SPEC.md` | file exists | Present |
| DOC-02 | Every requirement maps to owner files, tests, and evidence | this matrix | file being added | In progress |
| DOC-03 | User-facing docs and README make privacy, setup, compatibility, and limitations consistent | README and docs | files exist; consistency audit outstanding | Needs final documentation audit |
| DOC-04 | Release APK uses persistent owner-controlled signing key and versionCode increments | Gradle, CI | workflow conditionally verifies signer | CI path depends on secrets; release signing must be verified before distribution |

## 6. Required test matrix before calling the app release-ready

### Automated
- Unit tests for state transitions, stale event rejection, monotonic deadlines, reboot identity, authentication throttling, compatibility-family mapping, alarm volume restoration, corrupted storage, and failed persistence writes.
- Android instrumentation tests for runtime receiver registration, foreground-service start restrictions, exact-alarm permission changes, notification permission/channel behavior, keyguard dismissal, Device Admin lock behavior, audio routing, and process recreation.
- Emulator runs for API 26, 28, 29, 30, 31, 32, 33, 34, 35, and 36 (at minimum, representative API levels plus boundary versions).
- Static checks: lint, manifest/permission audit, dependency audit, packaged asset integrity, APK package/version/signer verification.

### Physical OEM acceptance
- HONOR 200 Pro / MagicOS (existing user-reported baseline; repeat after implementation changes).
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
