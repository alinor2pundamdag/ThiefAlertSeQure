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
- Architecture ownership and the UI → runtime → persistence → Android boundary map are documented in `docs/PHASE_1_ARCHITECTURE_MAP.md`.
- Corrected `AlarmDeadlineReceiver.kt`'s persisted-update lambda return label.
- Corrected Bluetooth registry Base64 compatibility test fixtures to use actual tab/newline delimiters; the previous failure was a malformed test fixture, not a production decoder defect.
- The previous exact-head build job on `b487f84b96fb29a6a1eb45f64b0b5a50c8d20dd3` passed compilation, unit tests, navigation verification, lint, asset/config checks, APK assembly, and package/version/signer checks.
- Follow-up instrumentation investigation found real test failures on API 26 and 28: missing `alarm_channel` readiness item and a biometric Keystore test that assumed an enrolled strong biometric. API 30 failed separately because its emulator stayed offline and was reported as API level 1.
- Corrective source/test changes are being committed on this branch. Exact-head CI rerun is required before Phase 1 can be accepted.

## Verification status
- Build job: PASS on the previous exact head; the new corrective head must be re-run.
- Instrumentation API 26/28: FAIL on the previous head; source/test causes identified and corrective changes prepared.
- Instrumentation API 30: infrastructure failure (ADB offline / invalid API-level detection); retry required after corrective commit.
- Instrumentation API 32/34/35/36: not yet all confirmed successful; check the final run's complete job matrix.
- Physical HONOR 200 Pro testing for changed v1.1.4 behavior remains pending and is not inferred from emulator results.
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
