# ThiefAlertSeQure — Project State

## Active FIXALL branch
- Working branch: `work/phase-0-12-coding` (current coding branch; exact head recorded in the verification snapshot below)
- Production branch: `main` (untouched)
- Production package identity and signing configuration must remain unchanged.
- Approved minimum SDK: 26. Do not lower without explicit approval.
- Preserve charger countdown (10 seconds), A2 grace period (8 seconds), mandatory authentication for disarm, and local-only protection architecture.
- No merge, tag, APK publication, or production release without explicit approval.

## FIXALL work completed so far

The authoritative coding scope and acceptance ledger is [docs/PHASE_0_12_EXECUTION_CHECKLIST.md](docs/PHASE_0_12_EXECUTION_CHECKLIST.md). The owner has directed completion of coding for Phases 0–12. Android instrumentation tests, instrumentation compilation, and emulator test matrices are prohibited. Phase 12 physical-device acceptance is prepared in software/documentation but can only be completed with real-device evidence.

The bullets below include earlier work on predecessor branches; they are project history, not a claim that every Phase 0–12 acceptance gate is complete on the current branch.
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
- Historical verification note: the former instrumentation matrix requirement is superseded by the owner's current prohibition on instrumentation execution/compilation and emulator matrices. Current software acceptance uses JVM unit tests, source-contract checks, lint, release-build/APK checks and CodeQL; physical-device behavior remains separate.
- Phase Two is not complete until final CI, documentation consistency, and branch review are verified. Do not claim production release readiness from emulator CI alone.

## Latest code-bearing candidate status (2026-11-17)

- Current coding branch: `work/phase-0-12-coding`.
- Latest code-bearing source commit: `475c1fa00b8c30c1280f77053d83eb91c0db7f64`. Subsequent commits in this status-documentation pass change documentation only; no Kotlin, Gradle, manifest, or workflow source was changed after the verified code commit.
- Android non-instrumentation CI [run 43950015535](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015535): **PASS** on this exact commit. JVM unit tests, source-contract checks, lint, assets, APK packaging, minified release metadata, package/version/signer checks, and artifact upload all succeeded.
- CodeQL [run 43950015623](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015623): **PASS** for Java/Kotlin and GitHub Actions on this exact commit.
- The branch contains 29 JVM unit-test source files. No instrumentation test compilation/execution or emulator matrix ran; this is an explicit owner-directed constraint.
- Uploaded development artifact: `ThiefAlertSeQure-debug-apk`, artifact ID `14266362844`, 6,615,813 bytes. APK package `com.thiefalertsequre.app.v114test`, versionName `1.1.4-test`, versionCode `6`, minSdk `26`. APK SHA-256: `034d3cc076ca06ea3265bd468e42de8247352d3f8cf1f1882f7b4620971c641e`. Debug signer SHA-256: `be3def2cc431cb7000e025693cb9705496a71b90d22eda372140e5a03b562c17`. This is a development-only debug APK, not a production update.
- The minified release variant also built and passed package/version metadata checks as `com.thiefalertsequre.app`, versionName `1.1.4`, versionCode `6`; the build was unsigned because permanent release signing was not configured in this CI run. No production APK was published.
- Branch comparison against `main`: 130 changed paths (90 added, 40 modified, 0 deleted). The sole main-only commit adds `PROJECT_STATE.md`, so the branch and main have diverged around that file. This was reviewed and intentionally not merged; do not merge/rebase without separate approval.
- Phase 0–11 coding/source checks and Phase 12 physical-test preparation are recorded in the execution checklist. Actual v1.1.4 physical-device acceptance remains **NOT TESTED**.
- `main` remains untouched. No merge, tag, publication, or production release has been performed or authorized.

## Historical verification status
- Source-head run #1270: PASS for the build job and all seven Android instrumentation jobs.
- Instrumentation reports: 175 total test executions across the matrix; 168 passed and 7 intentionally skipped (one per API); zero test failures/errors.
- Diagnostic review: complete; the single skipped test is explicitly guarded by the test's enrolled-strong-biometric precondition. Emulator system-service crashes are not app-process crashes.
- The historical v1.1.4 audit's minSdk 23 statement has been explicitly labeled as an older branch setting; the current coding branch remains minSdk 26.
- Physical HONOR 200 Pro acceptance remains excluded from Phase 1 and is reserved for Phase 12. Emulator results are not represented as physical-device evidence.
- Production-signed APK/update continuity, merge, tag, APK publication, and production release remain separate gates and are not authorized by this Phase 1 work.

## Phase 0–12 completion gates

1. Finish source and cross-file integration audits for Phases 0–9; fix only confirmed defects or missing acceptance requirements.
2. Complete Phase 10 documentation/privacy/assets consistency and integrity checks.
3. Complete Phase 11 branch diff, package/signing/version, workflow and release-candidate review. Do not merge or publish.
4. Run exact-head non-instrumentation JVM unit tests, static/source-contract checks, Android lint, release build, APK metadata/package/signer checks, and CodeQL.
5. Keep Phase 12 physical-device execution separate; use the documented matrix and record real model/build/commit evidence. No emulator or instrumentation substitute is allowed.
6. Keep production merge, tag, APK publication and release as separate gates requiring explicit owner approval.

## Rules
- Do not silently change scope or product behavior.
- Do not erase corrupted user data automatically.
- Do not claim a test passed unless the exact branch head and test job passed.
- Keep all changes on this development branch until the owner explicitly approves integration.
