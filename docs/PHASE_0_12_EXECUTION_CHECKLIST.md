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
| **0 — Safety and baseline** | Preserve source baseline, package/signing identity, SDK floor, data, feature/permission inventory, and production boundary | Compare working branch to predecessor; inspect manifest/build invariants; confirm no unapproved file/feature removal | PASS: current candidate compared with its predecessor; no deleted files in the FIXALL delta; minSdk 26, compile/target 36, package identity and release signer contract verified; `main` untouched |
| **1 — Compilation and architecture** | Keep one owner for state transitions, persistence, Android boundaries, and UI/runtime dependencies | Build, unit tests, architecture/source review, state-ownership and navigation checks | Coding complete; architecture/source map checked; run #1410 passed exact coding head |
| **2 — Power and charger** | Preserve event-time capture, live power validation, debounce, generation guards, reconnect cancellation, 10-second deadline and reboot recovery | Unit coverage plus instrumentation-source compilation for persisted recovery | Coding and test-source complete; unit tests and instrumentation compilation PASS; included in API 26–37 matrix |
| **3 — Data integrity** | Safe typed preference reads, synchronous commits, corruption preservation/recovery, migration and test-state isolation | Audit every critical preference owner; compile corruption/failure-injection instrumentation tests | Coding and test-source complete; preference-boundary checks, unit tests and corruption instrumentation compilation PASS |
| **4 — Alarm and recovery** | Persist latch before playback; scheduler token integrity; recovery/retry; audio fallback, volume restoration, optional flashlight; authenticated disarm | State/policy/unit review and instrumentation-source compilation | Coding and test-source complete; scheduler, recovery and volume instrumentation sources compile; API matrix will exercise platform integration |
| **5 — Bluetooth A2** | Encrypted trusted registry, ACL receiver lifecycle, per-device revisions, reconnect invalidation, 8-second grace period and safe fallback | Unit/policy review and instrumentation-source compilation | Coding and test-source complete; encrypted registry and A2 scheduler tests compile; real accessory/OEM behavior remains physical-device acceptance |
| **6 — Authentication and security** | Ephemeral authentication proof, Android credential/strong biometric gate, persisted throttle, anti-tampering and owner-only actions | Audit every disarm path and failure/cancel path; compile crypto/auth tests | Coding and test-source complete; auth policy/source checks and instrumentation compilation PASS; biometric hardware enrollment is an intentional emulator precondition skip |
| **7 — Background services and lifecycle** | Foreground-service start/stop, notification channels, boot/package-replacement recovery, process recreation and Android-version restrictions | Manifest/static review; compile service/recovery/upgrade test sources | Coding and test-source complete; manifest checks PASS; upgrade regression has explicit seed/verify/cleanup modes and is skipped in the generic suite |
| **8 — UI and accessibility** | Preserve all screens, navigation, themes, status/readiness flows, edge-to-edge insets and accessibility semantics | Static navigation/theme/heading checks; compile UI-facing tests where present | Coding and test-source complete; navigation/dialog/accessibility source checks PASS; visual runtime coverage included in API matrix |
| **9 — Automated testing and CI** | Maintain unit, static, manifest, preference, ownership, lint, asset, APK identity and instrumentation coverage | Require exact-head green CI and successful Android instrumentation-source compilation; execute the complete emulator matrix after coding closes | Code-level PASS: run #1410 on `1141b10db19afc9ffa085b549adfd61ae0c39629`; CodeQL #14 PASS. API 26, 28, 30, 31, 32, 33, 34, 35, 36 and 37 matrix enabled for the next push |
| **10 — Documentation, privacy and assets** | Align README, feature matrix, traceability, privacy, compatibility, master specification and packaged assets with actual source | Search for stale branch/SDK/test claims; validate assets and release workflow configuration | Coding documentation audit complete; corrected minSdk 26 contract, API 37 notes, branch snapshots, traceability, UX/security audit and device evidence/recovery procedure |
| **11 — Branch integration and release-candidate preparation** | Review the complete diff, preserve all additions/modifications, keep production branch isolated, prepare reviewable candidate | Compare against predecessor, verify no unintended deletions, exact-head CI and commit/PR review | Branch review complete; working branch remains isolated from `main`; current comparison to `main` is 438 ahead / 1 behind (diverged). No merge, tag, APK publication or release |
| **12 — Physical-device acceptance preparation** | Prepare reproducible test cases, device/OS matrix, expected results, logs and rollback/recovery procedure | Finish software work and instrumentation first; keep owner-reported results distinct from independently verified results | Preparation complete: HONOR 200 Pro scenarios, evidence capture, ADB diagnostics and safe recovery procedure documented; physical execution remains separate and is not claimed by emulator CI |

## Mandatory order of execution

1. Finish the remaining source audits, fixes, test-source additions, test isolation, documentation, and integration review across Phases 0–12.
2. Compile every Android instrumentation test source and fix all compilation failures without running the tests.
3. Run the complete non-instrumentation CI on the final coding commit; require all build, unit, static, lint, asset, and APK verification steps to pass.
4. Coding phases 0–12 are complete at source/test/documentation level. Run the complete Android emulator matrix on API 26, 28, 30, 31, 32, 33, 34, 35, 36 and 37; fix actual app/test failures and rerun until every required API job is green.
5. Keep physical-device acceptance separate and report it only with real device evidence.
6. Do not merge to `main`, create a production tag, publish an APK, or release without separate owner authorization.

## Resume protocol

When work is interrupted, first read this checklist, `PROJECT_STATE.md`, the current branch head, and the latest workflow run. Resume from the latest committed branch head. Do not restart completed work, discard prior decisions, change the scope, run instrumentation early, or ask the owner to repeat requirements already recorded here.

**Completion reporting rule:** Report a phase complete only after its acceptance criteria are satisfied on the exact current branch head. Clearly distinguish source implementation, unit/static verification, instrumentation execution, and physical-device acceptance.
