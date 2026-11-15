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
- Phase 1 implementation/documentation is complete on the current FIXALL branch head; see `docs/PHASE_1_ARCHITECTURE_MAP.md` for source-backed UI → runtime → persistence → Android-device ownership paths.
- Corrected `AlarmDeadlineReceiver.kt`'s persisted-update lambda return label.
- Corrected Bluetooth registry Base64 compatibility test fixtures to use actual tab/newline delimiters; the previous failure was a malformed test fixture, not a production decoder defect.
- Exact-head workflow run `43727526607` on commit `bd67b8bbf43b576e4a19205952680f99cd20044a`: build job PASS, including unit tests, navigation verification, Android lint, asset/config checks, debug APK assembly/metadata, minified release APK checks, and APK package/version/signer verification.
- The Android instrumentation matrix in that workflow is a separate later-phase verification gate; it is not being represented as passed by Phase 1.

## Verification status
- Phase 1 build job passed on the exact branch head above.
- The separate Android instrumentation matrix for run `43727526607` is queued/running and is tracked for the later integration/testing phase; it is not represented as passed.
- Physical HONOR 200 Pro testing for changed v1.1.4 behavior remains pending.
- Production-signed APK/update continuity remains pending.
- This document does not assert release readiness.

## Remaining FIXALL work
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
