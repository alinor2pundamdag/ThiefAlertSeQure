# FIXALL Phase 0–12 Execution Checklist

**Project:** ThiefAlertSeQure Solo  
**Working branch:** `work/phase-0-12-coding`  
**Production branch:** `main` — protected; no merge or release without separate owner approval  
**Sequencing rule:** Complete all coding and non-physical-device implementation work for Phases 0–12 first. Only then execute Android instrumentation. Physical-device acceptance remains a separate final activity.

This ledger is the persistent source of truth for resuming interrupted work. A phase is not complete merely because source files exist or a general CI build is green. Each phase must satisfy its implementation, cross-file integration, automated coverage, documentation, and exact-head verification requirements.

## Non-negotiable invariants

- Preserve the existing feature set, permissions, access paths, Android integrations, package identity, production signing identity, user data, and UI/UX scope.
- `minSdk 26`; compile/target SDK 36 unless the owner explicitly authorizes a change.
- Charger-removal grace period: exactly 10 seconds.
- Trusted Bluetooth A2 disconnect grace period: 8 seconds.
- A latched alarm can be cleared only after fresh, successful owner Android device-credential confirmation or BIOMETRIC_STRONG authentication.
- Protection remains local-first/offline. No cloud database, telemetry, advertising, scanning, or unrelated features are to be introduced.
- Do not silently delete corrupted data, alter production identity, merge, tag, publish APKs, or release.

## Phase acceptance ledger

| Phase | Required coding and integration work | Required non-physical verification before instrumentation | Current disposition |
|---|---|---|---|
| **0 — Safety and baseline** | Preserve source baseline, package/signing identity, SDK floor, data, feature/permission inventory, and production boundary | Compare working branch to predecessor; inspect manifest/build invariants; confirm no unapproved file/feature removal | Baseline safeguards exist; final all-requirements audit remains open |
| **1 — Compilation and architecture** | Keep one owner for state transitions, persistence, Android boundaries, and UI/runtime dependencies | Build, unit tests, architecture/source review, state-ownership and navigation checks | Run #1395 passed on exact head `367e3fd163a7f1cac04d24a08c05e19120d43650`; architecture/source map is checked; later commits must receive their own exact-head CI |
| **2 — Power and charger** | Preserve event-time capture, live power validation, debounce, generation guards, reconnect cancellation, 10-second deadline and reboot recovery | Unit coverage plus instrumentation-source compilation for persisted recovery; no instrumentation execution yet | Implementation and unit coverage exist; emulator execution deferred |
| **3 — Data integrity** | Safe typed preference reads, synchronous commits, corruption preservation/recovery, migration and test-state isolation | Audit every critical preference owner; compile corruption/failure-injection instrumentation tests | Hardening and test-isolation changes exist; compiled test-source verification is in place; runtime execution deferred |
| **4 — Alarm and recovery** | Persist latch before playback; scheduler token integrity; recovery/retry; audio fallback, volume restoration, optional flashlight; authenticated disarm | State/policy/unit review and instrumentation-source compilation | Core implementation and tests exist; media/service runtime verification remains pending |
| **5 — Bluetooth A2** | Encrypted trusted registry, ACL receiver lifecycle, per-device revisions, reconnect invalidation, 8-second grace period and safe fallback | Unit/policy review and instrumentation-source compilation | Implementation and tests exist; emulator execution and real accessory behavior remain pending |
| **6 — Authentication and security** | Ephemeral authentication proof, Android credential/strong biometric gate, persisted throttle, anti-tampering and owner-only actions | Audit every disarm path and failure/cancel path; compile crypto/auth tests | Source and policy tests exist; runtime instrumentation remains pending |
| **7 — Background services and lifecycle** | Foreground-service start/stop, notification channels, boot/package-replacement recovery, process recreation and Android-version restrictions | Manifest/static review; compile service/recovery/upgrade test sources | Source coverage exists; emulator execution remains pending |
| **8 — UI and accessibility** | Preserve all screens, navigation, themes, status/readiness flows, edge-to-edge insets and accessibility semantics | Static navigation/theme/heading checks; compile UI-facing tests where present | Accessibility/navigation improvements exist; complete UI/API visual acceptance remains open |
| **9 — Automated testing and CI** | Maintain unit, static, manifest, preference, ownership, lint, asset, APK identity and instrumentation coverage | Require exact-head green CI and successful Android instrumentation-source compilation; do not execute instrumentation yet | Run #1390 passed unit/static checks and instrumentation-source compilation on exact head `5620a059eb699ffe6360be6778bdf6e7261dac41`; final rerun required after remaining edits |
| **10 — Documentation, privacy and assets** | Align README, feature matrix, traceability, privacy, compatibility, master specification and packaged assets with actual source | Search for stale branch/SDK/test claims; validate assets and release workflow configuration | Historical branch estimates and stale branch references have been corrected in several docs; full consistency review remains open |
| **11 — Branch integration and release-candidate preparation** | Review the complete diff, preserve all additions/modifications, keep production branch isolated, prepare reviewable candidate | Compare against predecessor, verify no unintended deletions, exact-head CI and commit/PR review | Working branch was 71 commits ahead and 0 behind at the last comparison; no merge or production release performed |
| **12 — Physical-device acceptance preparation** | Prepare reproducible test cases, device/OS matrix, expected results, logs and rollback/recovery procedure | Finish software work and instrumentation first; keep owner-reported results distinct from independently verified results | Test plan preparation remains; no new physical-device testing is claimed |

## Mandatory order of execution

1. Finish the remaining source audits, fixes, test-source additions, test isolation, documentation, and integration review across Phases 0–12.
2. Compile every Android instrumentation test source and fix all compilation failures without running the tests.
3. Run the complete non-instrumentation CI on the final coding commit; require all build, unit, static, lint, asset, and APK verification steps to pass.
4. Only after Phases 0–12 coding are complete, run the Android emulator instrumentation matrix, including the previously failing API 36 and API 37 targets; fix actual app/test failures and rerun until the required matrix is green.
5. Keep physical-device acceptance separate and report it only with real device evidence.
6. Do not merge to `main`, create a production tag, publish an APK, or release without separate owner authorization.

## Resume protocol

When work is interrupted, first read this checklist, `PROJECT_STATE.md`, the current branch head, and the latest workflow run. Resume from the latest committed branch head. Do not restart completed work, discard prior decisions, change the scope, run instrumentation early, or ask the owner to repeat requirements already recorded here.

**Completion reporting rule:** Report a phase complete only after its acceptance criteria are satisfied on the exact current branch head. Clearly distinguish source implementation, unit/static verification, instrumentation execution, and physical-device acceptance.
