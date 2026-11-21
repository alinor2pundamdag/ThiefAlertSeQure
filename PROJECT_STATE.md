# Current Batch Four follow-up audit snapshot — 21 November 2026

- Working branch: `work/batch-4-implementation`; PR #8 remains draft/open/unmerged; `main` is untouched.
- Candidate identity: `com.thiefalertsequre.app`, versionName `1.1.5_Developer_Alinor`, versionCode `7`, minSdk 26, compileSdk/targetSdk 36.
- Exact branch head: `5ba8c527a612858ee48fe0a6e21d05b418c369ad`. Latest code-bearing change: `a95e5b65d837db41efce07e50fa25274ce959c47`; later commits in this snapshot are documentation/contract-verification updates.
- Existing full-source audit inventory: 89 production Kotlin files, 46 JVM unit-test source files, 10 Python contract scripts, Android manifest/resources, Gradle/version catalog, GitHub Actions workflows, and project/privacy/release documentation. The current follow-up audit rechecked high-risk cross-feature seams and their regressions.
- Follow-up fixes committed on this branch: authenticated disarm and safe package replacement now invalidate old A2 Bluetooth deadlines atomically across the in-process and AlarmManager paths; in-process A2 alarms cancel their durable deadline; alarm recovery cleanup cannot block successful owner disarm or bounded retry scheduling; monitor restart failures are owner-visible; corrupted protection/recovery metadata receives a separate typed emergency snapshot before the corruption latch is repaired; Gallery retention pruning no longer holds the global protection-state lock across bulk MediaStore work; notification channel importance is verified; active alarm and Settings flash patterns use dedicated timing workers; root Bluetooth diagnostics no longer invoke broad paired-device dumps; diagnostics-export disclosures now match the actual redacted JSON and owner-selected destination behavior.
- Previously audited safeguards remain in scope: active-state mutation boundaries; owner-only evidence review/export; Device Owner/A2 state locking; Bluetooth/alarm deadline persistence retries; bounded ACL receiver concurrency; primary-alarm audio priority; evidence queue loss reporting; crash-safe configuration restore journal; fail-closed MediaStore enumeration; complete-vault verification before Gallery-source deletion; bounded photo preview decoding; GPS validation; root diagnostic timeout cleanup; and Filament resource cleanup/reduced-motion behavior.
- Photo storage contract: new photos remain unencrypted and Gallery-visible by default. Settings can encrypt future captures and can explicitly migrate existing app-owned Gallery photos. Migration verifies the complete encrypted vault before removing any Gallery original. The Android Keystore key is non-exportable; clearing app data/uninstalling may make encrypted photos unrecoverable. The separate event-metadata backup does not contain photo bytes.
- Privacy contract: trusted Bluetooth identities remain app-private; root diagnostics use allowlisted non-identifier queries; optional diagnostics JSON exports contain only redacted device/protection status and event codes, and are written only to the owner-selected Android destination (which may be cloud-backed). The app does not upload these reports. Privacy notice namespace advanced to v9 to require acknowledgement of the expanded disclosure.
- Source/test inventory at this head: 89 production Kotlin files, 46 JVM unit-test files, 10 Python contract scripts, and 15 dormant instrumentation-test source files. The owner-directed policy prohibits compiling or executing instrumentation tests and emulator matrices; no such run was performed.
- Exact-head Android CI and CodeQL checks are **QUEUED / NOT YET VERIFIED** as of this snapshot. Static contracts from the preceding run passed, but they are not a substitute for the current exact-head build, JVM test, lint, APK metadata/signing, and CodeQL results.
- Physical-device acceptance for the current v1.1.5 candidate remains **NOT TESTED**: Phase 12 records 0/25 PASS and 25/25 NOT TESTED. README's 11/11 owner-reported physical scenarios are explicitly historical v1.1.3 results and do not certify this candidate.
- No Codespace was opened or used. No emulator, instrumentation build, or instrumented test was run. Physical-device testing is reserved for the owner.
- Platform limits remain explicit: exact-alarm delivery can be deferred when access is unavailable; Android/OEM background restrictions can affect service recovery; ranging remains a local capability query only and does not start a ranging session or request the RANGING permission.
- No merge, rebase into `main`, tag, production publication, or production release has been performed or authorized.

---

# ThiefAlertSeQure — Project State

## Historical FIXALL branch snapshot (superseded by the current Batch Four candidate)
- Historical FIXALL working branch: `work/phase-0-12-coding` (the current Batch Four branch is listed in the snapshot above)
- Production branch: `main` (untouched)
- Production package identity and signing configuration must remain unchanged.
- Approved minimum SDK: 26. Do not lower without explicit approval.
- Preserve charger countdown (10 seconds), A2 grace period (8 seconds), mandatory authentication for disarm, and local-only protection architecture.
- No merge, tag, APK publication, or production release without explicit approval.

## FIXALL work completed so far

The [docs/PHASE_0_12_EXECUTION_CHECKLIST.md](docs/PHASE_0_12_EXECUTION_CHECKLIST.md) is a historical FIXALL checklist for predecessor v1.1.4 work; its Phase 0–12 numbering is not the active product roadmap. The active approved scope is the four-batch, 17-phase product roadmap recorded below and summarized in [docs/FEATURE_COMPLETION_MATRIX.md](docs/FEATURE_COMPLETION_MATRIX.md). Current Batch 2 is Phases 5–9. Instrumentation compilation/execution and emulator matrices remain prohibited; physical-device acceptance is separate and must never be inferred from CI.

The bullets below are predecessor FIXALL history; they are not current Batch 1–4 phase acceptance claims. Current branch/version status is recorded in the Batch Four snapshot above.
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

- Historical FIXALL coding branch: `work/phase-0-12-coding`.
- Latest code-bearing source commit: `475c1fa00b8c30c1280f77053d83eb91c0db7f64`. Subsequent commits in this status-documentation pass change documentation only; no Kotlin, Gradle, manifest, or workflow source was changed after the verified code commit.
- Android non-instrumentation CI [run 43950015535](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015535): **PASS** on the code-bearing commit. The final documentation snapshot `ffe5fdae9150cb8ca791478d48cfc68263776ff8` also passed [Android run 43951488247](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43951488247); JVM unit tests, source-contract checks, lint, assets, APK packaging, minified release metadata, package/version/signer checks, and artifact upload all succeeded.
- CodeQL [run 43950015623](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015623): **PASS** for Java/Kotlin and GitHub Actions on the code-bearing commit. The final documentation snapshot also passed [CodeQL run 43951488080](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43951488080).
- The branch contains 29 JVM unit-test source files. No instrumentation test compilation/execution or emulator matrix ran; this is an explicit owner-directed constraint.
- Uploaded development artifact: `ThiefAlertSeQure-debug-apk`, latest artifact ID `14266538974`, 6,615,813 bytes. APK package `com.thiefalertsequre.app.v114test`, versionName `1.1.4-test`, versionCode `6`, minSdk `26`. APK SHA-256: `034d3cc076ca06ea3265bd468e42de8247352d3f8cf1f1882f7b4620971c641e`. Debug signer SHA-256: `be3def2cc431cb7000e025693cb9705496a71b90d22eda372140e5a03b562c17`. This is a development-only debug APK, not a production update.
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


## Current owner scope clarification (2026-11-17)

- The active task is limited to code/repository work and GitHub actions on the working branch.
- Physical-device testing and physical acceptance are explicitly excluded from this task and are not completion blockers.
- Do not run or compile Android instrumentation tests, do not run emulator matrices, and do not perform physical-device testing.
- Software verification is limited to JVM unit tests, static/source-contract checks, lint, build/APK metadata and package/signing-contract checks, and CodeQL on the exact working-branch head.
- Preserve the existing no-merge/no-tag/no-publication/no-production-release boundary unless the owner separately approves it.


## Owner-approved code-only execution override and audit progress — 2026-11-17

This section supersedes earlier historical next-action notes where they conflict with the current owner instruction.

- Finish all repository/code/GitHub tasks that can be completed without a physical device.
- Physical-device testing, emulator testing, and instrumentation compilation/execution are explicitly excluded and are not completion blockers.
- Do not merge/rebase into `main`, tag, publish APKs, or release without separate owner approval.
- Draft PR #7 is open for exact-head review/CI: https://github.com/alinor2pundamdag/ThiefAlertSeQure/pull/7. It remains draft and unmerged.
- Current branch HEAD at the time of this audit note: `80c8c52be927db24a31a7c08d699998ffb72d559`.

### Confirmed code finding fixed

- Finding: `OwnerAuthenticationProofPolicy.isFresh` previously accepted a proof when exactly one of the issued/current boot identities was known. A missing current boot identity prevented proving that the ephemeral credential proof belonged to the current boot.
- Fix: reject asymmetric known/unknown boot-identity states; continue to allow the bounded elapsed-time check when both identities are unavailable; continue to reject mismatched known identities and expired/reset elapsed clocks.
- Source commit: `e17f482264eba0d027b92499010be3b72c6d0086` (`OwnerAuthenticationProof.kt`).
- Regression tests: `80c8c52be927db24a31a7c08d699998ffb72d559` adds tests for known-to-unknown, unknown-to-known, and both-unknown boot identity states in `OwnerAuthenticationProofPolicyTest.kt`.
- Verification status: code and test changes are committed on the working branch; exact-head JVM/CI execution is still pending and must not be claimed as passed.

### Source audit coverage in this resumed session

Reviewed source paths include `ProtectionStore.kt`, `ProtectionEngine.kt`, `PowerTransitionCoordinator.kt`, `PowerTransitionReceiver.kt`, `PowerTransitionSamplingPolicy.kt`, `PowerTransitionRecoveryPolicy.kt`, `AlarmScheduler.kt`, `AlarmDeadlineReceiver.kt`, `AlarmRecoveryCoordinator.kt`, `BootReceiver.kt`, `ProtectionMonitorService.kt`, `A2DisconnectScheduler.kt`, `A2BluetoothAclReceiver.kt`, `TrustedBluetoothRegistry.kt`, `LocalPreferenceCipher.kt`, `OwnerAuthenticationProof.kt`, `OwnerAuthenticationCoordinator.kt`, and `OwnerAuthenticationCrypto.kt`. This is a documented review slice, not a claim that every repository file has been audited.

### Current blockers to software completion

- Continue the remaining source and cross-component review; fix only confirmed defects and add JVM regression tests.
- Obtain Android build/unit/static/lint/APK checks and CodeQL evidence for the latest PR head. Connector status currently does not expose a confirmed run result for that head.
- Resolve the branch divergence only within the existing no-merge/no-rebase approval boundary; do not alter `main`.
- Reconcile all completion documentation to this code-only scope and the latest exact commit.


### Additional confirmed code finding fixed — A2 multi-device deadline race

- Finding: concurrent ACL callbacks for separate trusted accessories could race through the encrypted pending-deadline read-modify-write and overwrite one another's records.
- Fix: `A2DisconnectScheduler` now serializes schedule/find/cancel/clear operations with a shared lock, uses namespace-correct failure cleanup, and exposes a pure per-device merge helper.
- Source commits: `5cf2da0b7c1f134e9a1fcdfd4b913a2561aa85c3` and `679f22ac7c2721af15e298a851fdbbfbf8c4b31e`.
- JVM regression tests: `f5bf25576de8a903699a989924a515ea506401a6` covers preserving another accessory's pending deadline and case-insensitive replacement.
- Verification status: committed only; exact-head JVM/CI execution remains pending.


### 2026-11-17 continuation — latest confirmed actions

- Fixed the CodeQL suppression location for the official APK installer handoff in `SettingsActivity.kt`. The installer is still reachable only after private-cache, SHA-256, production package, version and pinned-signer validation. The CodeQL PR gate and both CodeQL analysis jobs passed on the corrected code-bearing head.
- Restricted manual signed preflight in `.github/workflows/release.yml` to `main` only; this closes the extra compatibility-branch path and matches the documented release boundary. No preflight or release was triggered.
- Final Android CI must be checked against the post-hardening branch HEAD before marking the software phases complete. The current scope excludes all emulator, instrumentation and physical-device testing.
- PR #7 remains draft/open. Branch integration is held because the working branch is one commit behind `main` and contains a separate `PROJECT_STATE.md` history; no merge or rebase was performed.


## Current authoritative repository status — 2026-11-17

This section supersedes earlier candidate snapshots and pending-verification notes above where they conflict.

- Working branch: `work/phase-0-12-coding`; exact verified source/workflow head: `a1a567bf53f97189be2547ccf872ae665c30a54b`.
- Android build and tests passed on this exact head: [run #44005105554](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44005105554). The successful job includes JVM unit tests, source-contract guards, Android lint, APK assembly and metadata/package/signer checks. The instrumentation-disabled guard also passed.
- CodeQL passed on this exact head: [run #44005105442](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44005105442), including Java/Kotlin and Actions analysis.
- The A2 per-accessory pending-deadline serialization fix and owner-authentication proof freshness fix are included in the exact-head Android/JVM CI pass.
- Recursive tree audit returned 222 entries without truncation and identified 64 production Kotlin source files. Automated marker/pattern sweeps covered the source set, with focused manual review of high-risk charger, alarm, authentication, persistence, Bluetooth, and updater/install paths. This is not claimed as exhaustive line-by-line formal verification; no runtime or device behavior is inferred.
- The PR has one inline review thread and it is resolved. It references CodeQL alert #8. The available GitHub connector cannot retrieve the repository Code Scanning Alerts endpoint or private security page; the alert's active/closed state remains **NOT VERIFIED**. A successful CodeQL workflow is not proof that all alerts are closed.
- Branch comparison: 530 commits ahead, 0 behind `main`; 131 changed paths (90 added, 41 modified, 0 deleted). GitHub reports PR #7 mergeable. PR #7 remains draft/open/unmerged.
- The protected release workflow requires the four permanent signing secrets and checks the pinned certificate, tag ancestry, package, versionCode and signer before publishing. Secret presence and production-signed APK availability are **NOT VERIFIED**; CI development artifacts are not production releases.
- The release workflow YAML and Android workflow YAML passed the current Android workflow's validation steps. The Android workflow confirms instrumentation is disabled.
- `main` remains untouched. No merge, rebase, tag, publication or production release was performed.
- Explicit exclusions remain: no Codespaces, no emulator, no instrumentation compilation/execution, and no physical-device testing.
- The active phase checklist is `docs/PHASE_0_12_EXECUTION_CHECKLIST.md`; consult its latest exact-head snapshot for phase dispositions and remaining Phase 11 verification boundaries.


## Final exact-head verification — 2026-11-17

- Documentation-reconciled branch head at verification: `e97817903976689f02cba8bb485fc160a7122fd3`.
- Android workflow [#44010766603](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010766603): **SUCCESS**. All steps passed, including the instrumentation-disabled guard, 30 JVM unit-test files, source-contract guards, lint, assets, debug APK build, minified release metadata, package/version/signer verification and artifact upload.
- CodeQL workflow [#44010774991](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010774991): **SUCCESS**. Java/Kotlin and Actions analysis completed; SARIF results uploaded successfully.
- Artifact: `ThiefAlertSeQure-debug-apk`, 6,616,246 bytes; development-only.
- Current branch compare: 530 ahead / 0 behind; 131 changed paths (90 added, 41 modified, 0 deleted). PR #7 is draft/open/unmerged and GitHub reports mergeable.
- One inline PR review thread is resolved. CodeQL alert #8's underlying active/closed state remains **NOT VERIFIED**; green CodeQL execution and a resolved review thread do not prove alert closure.
- Permanent production signing-secret presence and a production-signed APK remain **NOT VERIFIED**.
- Automated source inventory/pattern sweeps covered all 64 production Kotlin source files; targeted manual review covered high-risk flows. This is not a claim of exhaustive line-by-line formal verification.
- No instrumentation compilation/execution, emulator, physical-device test, merge, rebase, tag, publication or production release was performed.


## Final verified candidate snapshot — 2026-11-17

- Exact code/documentation candidate `e97817903976689f02cba8bb485fc160a7122fd3` passed Android [#44010766603](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010766603) and CodeQL [#44010774991](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010774991).
- All Android workflow steps succeeded: instrumentation-disabled guard, 30 JVM unit-test files, source-contract checks, lint, asset validation, debug APK packaging/metadata, minified release metadata, package/version/signer verification and artifact upload.
- Development artifact `ThiefAlertSeQure-debug-apk`: 6,616,246 bytes; not a production release.
- All changes after source commit `a1a567bf53f97189be2547ccf872ae665c30a54b` through `e97817903976689f02cba8bb485fc160a7122fd3` are limited to five documentation files; no app source/build/manifest/workflow code changed.
- Branch compare immediately before this final docs refresh: 530 ahead / 0 behind; this one commit makes it 531 ahead / 0 behind. Changed paths remain 131 (90 added, 41 modified, 0 deleted).
- Alert #8 closure and permanent production signing-secret availability remain not verified.


## Authoritative latest verification — 2026-11-17

This entry supersedes earlier candidate SHAs and run IDs above.

- Exact working-branch HEAD: `524754f683d43450c844bca05147e66c15baf3d6`.
- Android CI [#44011669247](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44011669247) passed on this exact HEAD; all job steps succeeded, including 30 JVM unit-test source files, instrumentation-disabled guard, source-contract checks, lint, assets, APK build/metadata, minified release metadata, package/version/signer checks and artifact upload.
- CodeQL [#44011663424](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44011663424) passed on this exact HEAD; Java/Kotlin and Actions analysis completed.
- Debug artifact `ThiefAlertSeQure-debug-apk`, ID `14296523837`, 6,616,246 bytes, SHA-256 `66de655101691e40380db2de4593491d1152f453319dfb353f30f1b8ea6b00f7`, expires 2026-12-01. Development-only; not production-signed.
- Branch is 531 commits ahead / 0 behind `main`; 131 changed paths (90 added, 41 modified, 0 deleted). PR #7 remains draft/open/unmerged and mergeable.
- CodeQL alert #8 closure and permanent production signing-secret presence remain not verified. The resolved inline review thread and successful CodeQL scan do not prove the alert is closed.
- Phases 0–10 implementation/permitted software checks are passed as recorded; Phase 11 has the two external verification gaps; Phase 12 physical-device acceptance is excluded and untested.
- No instrumentation, emulator, Codespaces, physical-device test, merge, rebase, tag, publication or production release was performed.


## Product roadmap Batch 1 — Phases 0–4 (2026-11-17)

- Owner authorized implementation of product-roadmap Batch 1: Phase 0 baseline preservation, Phase 1 security architecture/persistent state, Phase 2 permission/capability readiness, Phase 3 charger and trusted Bluetooth protection, and Phase 4 Bypass Charging integration.
- Phases 0–3 were already implemented on the branch and were preserved rather than duplicated. Exact pre-batch head d174669ec522a77ab24b95999c0cf0ece606c9f1 passed Android CI [#44013936060](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44013936060) and CodeQL [#44013936266](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44013936266).
- Phase 4 adds a model-specific adapter contract, fail-closed registry, explicit authorization gate, post-command state read-back, Settings status/control UI, local event audit for attempted operations, JVM regression tests, and a CI privilege-boundary guard.
- No OEM adapter is registered because no verified public third-party ON/OFF API has been certified. The ON/OFF switch remains disabled and actual mode is UNKNOWN until a model/firmware-specific authorized adapter is added. This does not claim that the phone's OEM feature is absent.
- No new permission, service, backend, telemetry, shell command, root path, hidden API, or manifest component was added. No charging preference is replayed at boot without a certified OEM persistence contract.
- Non-physical verification passed on code-bearing commit e8aabcb4dc42df7817b4ec4c2fb7e7c1ac392523: Android workflow #44048497535 SUCCESS and CodeQL #44048497474 SUCCESS. JVM unit tests, Bypass Charging privilege/state guard, instrumentation-disabled guard, existing source guards, Android lint, manifest/protection invariants, debug APK assembly/metadata/signer checks, and minified release APK metadata checks all passed.
- CI debug artifact ID 14312886783, SHA-256 628cd98ceab0e24af26d29f0502862456ed4e3d2f8615cc85130084a2b19a945, expires 2026-12-01. It is a development debug artifact (applicationId com.thiefalertsequre.app.v114test, versionName 1.1.4-test). The minified release variant verified package com.thiefalertsequre.app, versionCode 6, versionName 1.1.4, compileSdk 36, minSdk 26, but is unsigned because permanent owner signing secrets are not configured; it is not a production-distributable APK.
- Batch 1 non-physical implementation and CI verification are complete for the available software scope. Direct OEM Bypass Charging ON/OFF control, exact model/firmware certification, persistent hardware mode restoration, and physical charging-path behavior remain unimplemented/unverified until a documented, authorized OEM interface is available. Instrumentation, emulator, Codespaces, and physical-device testing remain prohibited. PR #7 remains draft/open/unmerged; no merge, tag, APK publication or production release was performed.

## Current four-batch roadmap (supersedes the older Phase 0–12 sequencing)

The active roadmap is **17 phases numbered Phase 0 through Phase 16**, organized into four batches. The older `docs/PHASE_0_12_EXECUTION_CHECKLIST.md` remains a historical implementation checklist and must not be used to renumber the active four-batch roadmap.

| Batch | Phases | Active scope |
|---|---:|---|
| Batch 1 | 0–4 | Baseline and Preservation; Security Architecture and Persistent State; Permission Center and Device Capability; Charger and Bluetooth Protection; Bypass Charging Integration Framework |
| Batch 2 | 5–9 | Power-menu and Shutdown Interruption; Alarms, Sounds, Flash and Haptics; Authentication, Evidence and GPS; Device Intelligence and Diagnostics; Dashboard and Navigation |
| Batch 3 | 10–13 | Notification Shield and Controls; Widgets; Appearance Studio; Seismic Genesis Intro |
| Batch 4 | 14–16 | About, Version, Legal and Privacy; Integration, Quality and Release Readiness; Local Tamper History and Final Verification |

**Batch 2 completion gate:** all Phase 5–9 implementation, privacy boundaries, UI/UX, error handling, static contract checks, JVM unit tests, Android lint/build and CodeQL must pass on the exact current branch HEAD. Hardware/OEM behavior remains explicitly unverified until physical-device validation; no claim of universal shutdown/torch/camera control is permitted.

**Historical Phase 7 camera evidence boundary (superseded):** the predecessor branch stored all photos in the encrypted vault. The current Batch Four candidate saves new captures unencrypted to Gallery by default and offers a Settings toggle for future-capture AES-GCM storage; existing images are not silently converted. Capture remains optional and OFF by default, after successful owner authentication while the authentication Activity is visible. No background camera service, video, microphone capture, or cloud upload is allowed. Camera failure must never reverse successful authentication or alarm disarm.

**Phase 9 dashboard boundary:** the fixed-color 136dp main shield stays above a compact, horizontally swipeable nine-destination tab strip (Bluetooth Settings; Device Hardening & Reliability; Sounds & Flash Alert; Authentication Evidence; Device Intelligence; Appearance Studio; Local Tamper History; More; About). The tab strip uses accessible 48dp touch targets and destination-specific navigation.

**Workflow constraints remain:** no GitHub Codespaces; no instrumentation/emulator tests during the agreed non-physical implementation pass; do not merge PR #7 or modify `main` without explicit owner approval. Batch 3 must not begin until Batch 2's exact-head completion gate is green.


## Batch 2 verification continuation — 2026-11-17

- **Code-bearing source commit:** `e2c166ee37cccaa691a83eaec39fcee4231bb1e1` on `work/phase-0-12-coding`.
- **Android CI:** [run #44080775151](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44080775151) — **SUCCESS**, all 59 job steps. This includes the instrumentation-disabled guard, Batch 2 privacy and cross-file contracts, JVM unit tests, navigation/preferences/manifest/state-ownership checks, Android lint, assets, release-workflow validation, debug APK assembly, minified release APK metadata, package/version/signer checks, and development artifact upload.
- **CodeQL:** [run #44080775264](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44080775264) — **SUCCESS** for Java/Kotlin and GitHub Actions analysis.
- Confirmed fixes in this Batch 2 pass:
  - Thermal flash now fails closed when the thermal listener or initial thermal status is unavailable; alarm audio remains independent.
  - Owner-requested current location tries every enabled GPS/network provider, with one 20-second bound and cancellation/cleanup.
  - Android 14+ managed restrictions use the supported API scope; `DISALLOW_ADD_USER` stays on the local restriction API. Lock Task features and managed restrictions are read back before activation; rollback and emergency recovery are read back before clearing recovery metadata.
  - Redacted diagnostics export event codes only; free-form event details and malformed lines are excluded.
  - Front-camera evidence capture is bound to the visible authentication Activity lifecycle and is cancelled when that Activity stops or is destroyed.
- **Batch 2 non-physical software gate: PASS** on the exact code-bearing commit. Phases 5–9 source integration, privacy/failure boundaries, JVM tests, static contracts, lint/build/APK validation and CodeQL passed. This is not a claim of physical/OEM validation.
- **Not tested by design:** enrolled Device Owner runtime and actual power-menu behavior; real GPS/network fixes; camera and torch hardware output; OEM thermal behavior; handset-specific UI/background behavior. No instrumentation, emulator, or physical-device test was run.
- PR #7 remains draft/open/unmerged; `main` remains untouched. No merge, tag, production signing, APK publication or release was performed. Permanent production signing-secret availability and active CodeQL alert #8 closure remain independently unverified.
- Batch 3 has not started; wait for explicit owner direction.
