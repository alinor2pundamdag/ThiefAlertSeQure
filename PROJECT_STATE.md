# ThiefAlertSeQure — Project State

## Active FIXALL branch
- Working branch: `dev/fixall-data-integrity`
- Production branch: `main` (untouched)
- Production package identity and signing configuration must remain unchanged.
- Approved minimum SDK: 26. Do not lower without explicit approval.
- Preserve charger countdown (10 seconds), A2 grace period (8 seconds), mandatory authentication for disarm, and local-only protection architecture.
- No merge, tag, APK publication, or production release without explicit approval.

## FIXALL work completed so far
- Created a dedicated safety/work branch from the reviewed development candidate.
- Restored the approved minSdk 26 invariant in the build candidate and aligned compatibility documentation.
- Hardened charger-state handling so missing battery extras remain unknown rather than being treated as unplugged.
- Hardened boot-identity recovery policy and added policy tests.
- Added bounded alarm recovery coordination and distinct protection-monitor recovery notification.
- Added ephemeral owner-authentication proof and changed system credential-result call sites to require successful Android callbacks.
- Hardened encrypted Bluetooth registry reads and mutations against malformed payloads and invalid addresses; added corruption-preservation tests.
- Improved A2 rollback persistence feedback.
- Fixed CI instrumentation diagnostics to run in a single shell process and retain emulator logs.
- Updated CI branch filters to include `dev/**` and `work/**`.
- Corrected README, privacy, protection-rules, traceability, and Android compatibility documentation.

## Phase 1 — Compilation and architecture
- Architecture ownership and the UI → runtime → persistence → Android boundary map are documented in `docs/PHASE_1_ARCHITECTURE_MAP.md`; the map was checked against the current source for UI entry points, services/receivers, state persistence, authentication, permissions, and Android boundaries.
- Corrected `AlarmDeadlineReceiver.kt`'s persisted-update lambda return label so a stale callback cannot be mistaken for a committed transition.
- Corrected Bluetooth registry Base64 compatibility test fixtures to use actual tab/newline delimiters; the earlier failure was a malformed test fixture, not a production decoder defect.
- Exact source-head workflow run [#1270](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43734061025) passed on `72cfad5e3f7e539a385cc09d6dd820d0de80dffa`: build, unit tests, navigation checks, lint, asset/config checks, APK assembly, and package/version/signer validation.
- Instrumentation jobs passed on API 26, 28, 30, 32, 34, 35, and 36 on that same head.
- All seven diagnostic artifacts were inspected. Each API report records 25 tests, zero failures, zero errors, and one intentional skip for the Keystore biometric test when the emulator has no enrolled strong biometric. Emulator system-process crash entries were distinguished from app failures; no ThiefAlertSeQure process crash was found.
- Source review confirmed the protected invariants remain in source: minSdk 26, 10-second charger countdown, 8-second A2 grace period, latched-alarm disarm through fresh Android authentication proof, and local-only protection state.

## Phase Two — detection reliability

- Working branch: `dev/fixall-data-integrity`; Phase One verified baseline: `72cfad5e3f7e539a385cc09d6dd820d0de80dffa`. Do not modify `main`, merge, or release without owner approval.
- Charger detection: power broadcasts are registered at runtime while the foreground monitor is active; delivered events use event-receipt timestamp anchoring, three-consecutive-sample confirmation (five-sample cap), persisted transition recovery, reconnect cancellation, and alarm-deadline reconciliation. A sticky-service restart may recover a missed same-boot disconnect only from a durable connected baseline while monitoring remains enabled and protection is ARMED; its fresh ten-second grace begins at recovery detection. Force-stop and OS/OEM non-restart remain limitations.
- Trusted Bluetooth detection: foreground ACL receiver plus an exported manifest receiver limited to platform-protected ACL actions. Export is required for the privileged Bluetooth UID; the controller enables the component only while monitoring, A2, and BLUETOOTH_CONNECT are active. Per-device encrypted deadlines use immutable PendingIntents and reconnect/revision/boot/state guards.
- Exact-alarm access denied or revoked: an inexact idle-aware fallback remains, but Android may delay it in Doze. If it fires while the app process is absent, Android may block starting the continuous audio foreground service. The state remains latched; an owner-visible recovery notice and up to three retries are attempted. Notification denial or continued FGS restriction may require the owner to reopen the app. Eight-second delivery is a target, not a guarantee.
- Boot behavior: old-boot A2 deadlines and connection baselines are invalidated; a fresh current-boot ACL connection event is required after reboot. Force-stop and OEM battery controls remain outside app control.
- Last inspectable target-branch Android workflow [#44655654144](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44655654144) checked out `752e3e972ed3706beb2278609e03522ab996888a`: the build job passed unit, navigation, lint, assets/configuration, APK assembly, package/version/signer checks, and artifact upload; API 36 failed before app tests because hosted acceleration was unavailable and TCG was not enabled; API 37 failed after PackageManager disappeared during install and the target package was absent during post-grant verification. CodeQL [#44655654167](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44655654167) passed both Java/Kotlin and Actions analysis on that older source.
- Subsequent branch CI hardening explicitly enables TCG, leaves emulator boot polling to android-emulator-runner, requires a positive InputManager readiness response, and retries the complete instrumentation install/grant/package-verification transaction at most three times for transient PackageManager loss. Real Gradle/configuration/ABI and undeclared-permission failures are not retried as infrastructure.
- `scripts/test_android_instrumentation_runner.sh` simulates a successful Gradle install followed by transient PackageManager/package visibility loss and verifies bounded reboot/reinstall/grant recovery. It is invoked by `scripts/test_adb_readiness_wrapper.sh`; the fake-ADB/fake-Gradle harness passed locally. This is not emulator CI.
- Exact-head Phase Two CI remains pending. Do not declare non-device Phase Two complete until the newest branch head passes build, unit, lint, API 36 and API 37 instrumentation, and CodeQL. No target-branch PR has been created; physical-device testing remains excluded and outstanding.
- Do not claim production release readiness from emulator CI alone.

## Phase One verification status
- Source-head run #1270: PASS for the build job and all seven Android instrumentation jobs.
- Instrumentation reports: 175 total test executions across the matrix; 168 passed and 7 intentionally skipped (one per API); zero test failures/errors.
- Diagnostic review: complete; the single skipped test is explicitly guarded by the test's enrolled-strong-biometric precondition. Emulator system-service crashes are not app-process crashes.
- A documentation consistency correction is being applied to remove an obsolete minSdk 23 statement; the corrected documentation commit must receive its own exact-head CI run before Phase 1 is formally closed.
- Physical HONOR 200 Pro acceptance remains excluded from Phase 1 and is reserved for Phase 12. Emulator results are not represented as physical-device evidence.
- Production-signed APK/update continuity, merge, tag, APK publication, and production release remain separate gates and are not authorized by this Phase 1 work.

## Remaining broader FIXALL work
1. Review and test alarm retry scheduling, cancellation, and process-death behavior.
2. Review all critical SharedPreferences readers and fail-closed behavior.
3. Verify Bluetooth revision migration and user-facing recovery/registration failures.
4. Complete scheduler-token and connected-baseline persistence fixes.
5. Audit all alarm-volume restore and cleanup commit results.
6. Reconcile all docs and assets against the exact candidate.
7. Complete source/API, manifest, UI, accessibility, and integration review.
8. Run exact-head CI and address actual app/test failures separately from emulator infrastructure failures.
9. Prepare focused physical-device acceptance steps for changed behavior.

## Rules
- Do not silently change scope or product behavior.
- Do not erase corrupted user data automatically.
- Do not claim a test passed unless the exact branch head and test job passed.
- Keep all changes on this development branch until the owner explicitly approves integration.
