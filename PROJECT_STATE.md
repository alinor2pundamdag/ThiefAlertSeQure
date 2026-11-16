# ThiefAlertSeQure — Project State

## Active FIXALL branch
- Working branch: `work/phase-0-12-coding` (current coding branch; exact head recorded in the verification snapshot below)
- Production branch: `main` (untouched)
- Production package identity and signing configuration must remain unchanged.
- Approved minimum SDK: 26. Do not lower without explicit approval.
- Preserve charger countdown (10 seconds), A2 grace period (8 seconds), mandatory authentication for disarm, and local-only protection architecture.
- No merge, tag, APK publication, or production release without explicit approval.

## FIXALL work completed so far

The authoritative phase sequencing and acceptance ledger is [docs/PHASE_0_12_EXECUTION_CHECKLIST.md](docs/PHASE_0_12_EXECUTION_CHECKLIST.md). Read it first whenever resuming interrupted work.

The bullets below include earlier work on predecessor branches; they are project history, not a claim that all Phases 0–12 are complete on the current branch.
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

## Historical Phase 1 — Compilation and architecture baseline
- Architecture ownership and the UI → runtime → persistence → Android boundary map are documented in `docs/PHASE_1_ARCHITECTURE_MAP.md`; the map was checked against the current source for UI entry points, services/receivers, state persistence, authentication, permissions, and Android boundaries.
- Corrected `AlarmDeadlineReceiver.kt`'s persisted-update lambda return label so a stale callback cannot be mistaken for a committed transition.
- Corrected Bluetooth registry Base64 compatibility test fixtures to use actual tab/newline delimiters; the earlier failure was a malformed test fixture, not a production decoder defect.
- Exact source-head workflow run [#1270](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43734061025) passed on `72cfad5e3f7e539a385cc09d6dd820d0de80dffa`: build, unit tests, navigation checks, lint, asset/config checks, APK assembly, and package/version/signer validation.
- Instrumentation jobs passed on API 26, 28, 30, 32, 34, 35, and 36 on that same head.
- All seven diagnostic artifacts were inspected. Each API report records 25 tests, zero failures, zero errors, and one intentional skip for the Keystore biometric test when the emulator has no enrolled strong biometric. Emulator system-process crash entries were distinguished from app failures; no ThiefAlertSeQure process crash was found.
- Source review confirmed the protected invariants remain in source: minSdk 26, 10-second charger countdown, 8-second A2 grace period, latched-alarm disarm through fresh Android authentication proof, and local-only protection state.

## Historical Phase Two — detection reliability baseline

- Predecessor branch: `dev/fixall-data-integrity`; historical Phase One baseline: `72cfad5e3f7e539a385cc09d6dd820d0de80dffa`. Do not modify `main`, merge, or release without owner approval.
- Charger detection: event-receipt timestamp anchoring, bounded multi-sample confirmation, persisted transition recovery, reconnect cancellation, and alarm-deadline reconciliation are implemented in the branch.
- Trusted Bluetooth detection: live foreground-service ACL receiver plus a manifest ACL receiver dynamically enabled only while monitoring, A2, and BLUETOOTH_CONNECT permission are active. Per-device encrypted disconnect deadlines use AlarmManager and reconnect/revision/boot/state guards; the foreground Handler remains the fast path while service is alive.
- Exact-alarm access unavailable: inexact idle-aware fallback is used and recorded. Android may defer inexact alarms and throttle exact idle alarms, especially in Doze; eight-second delivery is a target, not a platform guarantee. Force-stop and OEM/hardware behavior remain outside app control.
- Boot behavior: old-boot A2 deadlines and connection baselines are invalidated; a fresh ACL connection event is required after reboot.
- Verification: final build/lint/unit/instrumentation matrix must pass on the final branch head. Physical-device testing is explicitly separate and is not a blocker for finishing Phase Two software work.
- Phase Two is not complete until final CI, documentation consistency, and branch review are verified. Do not claim production release readiness from emulator CI alone.

## Last verified coding-head status (2026-11-16)
- Current coding branch: `work/phase-0-12-coding`.
- Latest verified application/test coding head: `1141b10db19afc9ffa085b549adfd61ae0c39629`.
- Android build run [#1410](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43872054032): PASS. JVM unit tests, Android instrumentation-source compilation, navigation/preferences/manifest/state checks, Android lint, asset and release-workflow validation, debug APK assembly, minified release APK metadata, package/version/signer verification, and artifact upload all passed.
- CodeQL run [#14](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43872054576): completed successfully for Java/Kotlin and GitHub Actions code on the FIXALL work branch.
- Fixed the #1394 instrumentation-source compilation blocker by capturing/restoring the local event-history snapshot. Fixed the generic instrumentation suite's upgrade-test argument issue with an explicit JUnit assumption and added a cleanup mode/trap so interrupted in-place upgrade verification restores emulator test state.
- Updated the Android update contract to minSdk 26 and corrected the Android 17 / API 37 compatibility matrix documentation.
- The CodeQL workflow now includes `work/**`; the Android workflow is configured to run the full representative API 26–37 matrix on this branch after coding closure.
- The earlier #1270 instrumentation results remain historical evidence for their older commit only, not current-head verification.
- Coding phases 0–12 are complete at source, unit/static, documentation, branch-review and test-preparation level. The remaining non-physical acceptance gate is execution of the full emulator instrumentation matrix on the final workflow head. Physical-device acceptance remains separate and is not inferred from emulator CI.
## Historical verification status
- Source-head run #1270: PASS for the build job and all seven Android instrumentation jobs.
- Instrumentation reports: 175 total test executions across the matrix; 168 passed and 7 intentionally skipped (one per API); zero test failures/errors.
- Diagnostic review: complete; the single skipped test is explicitly guarded by the test's enrolled-strong-biometric precondition. Emulator system-service crashes are not app-process crashes.
- The historical v1.1.4 audit's minSdk 23 statement has been explicitly labeled as an older branch setting; the current coding branch remains minSdk 26.
- Physical HONOR 200 Pro acceptance remains excluded from Phase 1 and is reserved for Phase 12. Emulator results are not represented as physical-device evidence.
- Production-signed APK/update continuity, merge, tag, APK publication, and production release remain separate gates and are not authorized by this Phase 1 work.

## Remaining non-physical verification
1. Execute the final Android instrumentation matrix on API 26, 28, 30, 31, 32, 33, 34, 35, 36 and 37.
2. Inspect every API report and diagnostic artifact; fix app/test failures, distinguish emulator infrastructure failures, and rerun affected APIs until all required jobs pass.
3. Keep physical-device acceptance, production merge, tag, APK publication and release as separate gates requiring real device evidence or explicit owner approval.

## Rules
- Do not silently change scope or product behavior.
- Do not erase corrupted user data automatically.
- Do not claim a test passed unless the exact branch head and test job passed.
- Keep all changes on this development branch until the owner explicitly approves integration.
