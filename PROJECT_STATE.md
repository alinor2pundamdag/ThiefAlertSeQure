# ThiefAlertSeQure — Project State

## Active FIXALL branch
- Working branch: `work/phase-0-12-coding` (current coding branch; exact head recorded in the verification snapshot below)
- Production branch: `main` (untouched)
- Production package identity and signing configuration must remain unchanged.
- Approved minimum SDK: 26. Do not lower without explicit approval.
- Preserve charger countdown (10 seconds), A2 grace period (8 seconds), mandatory authentication for disarm, and local-only protection architecture.
- No merge, tag, APK publication, or production release without explicit approval.

## FIXALL work completed so far

The authoritative coding scope and acceptance ledger is [docs/PHASE_0_12_EXECUTION_CHECKLIST.md](docs/PHASE_0_12_EXECUTION_CHECKLIST.md). The owner has directed that this work cover Phases 0–9 only and that no Android instrumentation tests, instrumentation compilation, or emulator test matrices be run.

The bullets below include earlier work on predecessor branches; they are project history, not a claim that all Phases 0–9 are complete on the current branch.
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

## Current coding-head status (2026-11-17)

- Current coding branch: `work/phase-0-12-coding`.
- Current candidate head: `fa82258965003b4807574f5b51cf355a050f5d37`.
- Owner-directed scope: finish Phases 0–9 coding and non-instrumentation verification only. Do not run or compile instrumentation tests; do not run emulator matrices.
- Android workflow was changed to remove the instrumentation job, dispatch input, and instrumentation-source compilation step. The prior matrix run was cancelled by the replacement workflow.
- The exact-head non-instrumentation Android build and CodeQL workflows have been triggered; their results are pending. No 100% completion claim is made yet.
- Older build/CodeQL and emulator results below are historical evidence for their own commits only.
- `main` remains untouched. No merge, tag, APK publication, or production release has been performed or authorized.

## Historical verification status
- Source-head run #1270: PASS for the build job and all seven Android instrumentation jobs.
- Instrumentation reports: 175 total test executions across the matrix; 168 passed and 7 intentionally skipped (one per API); zero test failures/errors.
- Diagnostic review: complete; the single skipped test is explicitly guarded by the test's enrolled-strong-biometric precondition. Emulator system-service crashes are not app-process crashes.
- The historical v1.1.4 audit's minSdk 23 statement has been explicitly labeled as an older branch setting; the current coding branch remains minSdk 26.
- Physical HONOR 200 Pro acceptance remains excluded from Phase 1 and is reserved for Phase 12. Emulator results are not represented as physical-device evidence.
- Production-signed APK/update continuity, merge, tag, APK publication, and production release remain separate gates and are not authorized by this Phase 1 work.

## Remaining non-instrumentation verification

1. Complete the Phase 0–9 source and cross-file integration audit; fix only confirmed defects or missing acceptance requirements.
2. Run exact-head JVM unit tests, static/source-contract checks, Android lint, release build, APK metadata/package/signer checks, and CodeQL.
3. Review the final diff and confirm production identity, local-first behavior, data preservation, permissions, and branch isolation.
4. Keep physical-device acceptance, production merge, tag, APK publication and release as separate gates requiring real device evidence or explicit owner approval.

## Rules
- Do not silently change scope or product behavior.
- Do not erase corrupted user data automatically.
- Do not claim a test passed unless the exact branch head and test job passed.
- Keep all changes on this development branch until the owner explicitly approves integration.
