# FIXALL Phase 0–12 Coding and Acceptance Checklist

**Project:** ThiefAlertSeQure Solo  
**Working branch:** `work/phase-0-12-coding`  
**Production branch:** `main` — untouched; no merge, tag, publication or release without separate owner approval  
**Owner direction:** Complete all software coding in Phases 0–12. Do not run or compile Android instrumentation tests. Do not run emulator matrices. Phase 12's physical-device scenarios are prepared here and require real hardware to execute.

This is the persistent source of truth for interrupted work. A phase is not complete merely because files exist or a generic build is green. Implementation, cross-file integration, failure handling, documentation and exact-head non-instrumentation verification must be satisfied. Physical-device results must never be inferred from CI.

## Non-negotiable invariants

- Preserve the existing feature set, permissions, access paths, Android integrations, package identity, production signing identity, user data and UI/UX scope.
- `minSdk 26`; compile/target SDK 36 unless the owner explicitly authorizes a change.
- Charger-removal grace period: exactly 10 seconds.
- Trusted Bluetooth A2 disconnect grace period: 8 seconds.
- A latched alarm can be cleared only after fresh, successful owner Android device-credential confirmation or BIOMETRIC_STRONG authentication.
- Protection remains local-first/offline. No cloud database, telemetry, advertising, scanning or unrelated features.
- Do not silently delete corrupted data, alter production identity, merge, tag, publish APKs or release.
- No instrumentation execution, instrumentation compilation, Android emulator runner, or emulator matrix in any workflow.

## Phase acceptance ledger

| Phase | Required coding and integration work | Current disposition |
|---|---|---|
| **0 — Safety and baseline** | Preserve source baseline, package/signing identity, SDK floor, data, feature/permission inventory and production boundary | Baseline review recorded; verify again against final diff |
| **1 — Compilation and architecture** | One owner for state transitions, persistence, Android boundaries and UI/runtime dependencies | Architecture map and state-ownership checks exist; exact-head non-instrumentation CI required after final edits |
| **2 — Power and charger** | Event-time capture, live power validation, debounce, generation guards, reconnect cancellation, 10-second deadline and reboot recovery | Implementation and JVM policy coverage exist; source integration audit and final CI required |
| **3 — Data integrity** | Safe typed preference reads, synchronous commits, corruption preservation/recovery, migration and test-state isolation | Safe preference/state ownership code and JVM coverage exist; audit all critical stores and final CI required |
| **4 — Alarm and recovery** | Persist latch before playback; scheduler token integrity; recovery/retry; audio fallback, volume restoration, optional flashlight and authenticated disarm | Implementation and policy/unit coverage exist; source integration audit and final CI required |
| **5 — Bluetooth A2** | Encrypted trusted registry, ACL receiver lifecycle, per-device revisions, reconnect invalidation, 8-second grace period and safe fallback | Implementation and JVM policy/registry coverage exist; accessory/OEM behavior is device-unverified |
| **6 — Authentication and security** | Ephemeral proof, Android credential/strong biometric gate, persisted throttle, anti-tampering and owner-only actions | Implementation and JVM auth-policy coverage exist; audit every disarm/failure/cancel path and final CI required |
| **7 — Background services and lifecycle** | Foreground-service start/stop, notification channels, boot/package-replacement recovery, process recreation and Android-version restrictions | Implementation and source checks exist; Android/OEM runtime behavior remains device-dependent |
| **8 — UI and accessibility** | Preserve screens, navigation, themes, readiness flows, edge-to-edge insets and accessibility semantics | UI implementation and static navigation/theme checks exist; TalkBack/display behavior remains device-unverified |
| **9 — Automated tests and CI** | JVM tests, source-contract checks, manifest/preferences/ownership checks, lint, assets, release build and APK identity checks | Non-instrumentation Android CI and CodeQL passed on commit `9168d1d`; rerun on final exact head. Instrumentation is excluded by owner direction |
| **10 — Documentation, Privacy & Assets** | Reconcile README, privacy policy, permissions, feature matrix, threat model, update/install docs, asset formats/integrity and user-facing capability claims | Documentation and assets exist; stale phase/status statements and CI guard coverage are being corrected. Do not claim complete until consistency audit and CI pass |
| **11 — Branch Integration & Release Candidate** | Review complete branch diff, branch ancestry, package/version/signer invariants, release workflow, secrets references, artifact identity, update path and release gates | Candidate remains on development branch. No merge/tag/release authorized. Release workflow must contain no instrumentation or emulator steps; signed release preflight remains a separate owner-approved gate |
| **12 — Physical Device Testing & Final Acceptance** | Prepare a reproducible physical-device matrix, evidence template, safe recovery/cleanup and known platform/OEM limitations; record model/build/app commit for each test | Test procedure and HONOR/OEM matrix are documented. New v1.1.4 candidate physical acceptance is NOT TESTED until actual device evidence is supplied; CI cannot satisfy this phase |

## Phase 10 — documentation, privacy and assets

- Verify privacy claims against manifest permissions, dependencies, network paths, local storage and exported data.
- Verify all image/audio assets are present, non-empty, correctly encoded and packaged under correct resource names/extensions.
- Keep update checks owner-initiated; verify HTTPS host allow-list, checksum, package ID, versionCode and pinned signer checks.
- Label historical release/test records as historical; do not let predecessor evidence imply current-head or current-device acceptance.
- Keep capability and limitation wording accurate: no guarantee against force-stop, uninstall, shutdown, battery removal, recovery/bootloader, firmware or OEM process killing.

## Phase 11 — branch integration and release-candidate readiness

- Compare the working branch to `main`; inspect ahead/behind status, changed files, deletions and package/signing/version drift.
- Verify Android and CodeQL workflows on the exact final candidate commit.
- Verify release workflow fails closed for missing/wrong signing secrets, wrong tag, non-monotonic versionCode, wrong package or wrong certificate.
- Never expose secret values in source, logs, artifacts or reports.
- Do not merge, create tags, publish APKs or create a GitHub Release without separate explicit owner approval.
- A green development/debug CI artifact is not a production-signed release.

## Phase 12 — physical-device acceptance preparation

Use `docs/HONOR_200_PRO_COMPATIBILITY_AUDIT.md`, `docs/ANDROID_DEVICE_COMPATIBILITY.md` and `docs/UX_UI_AUDIT.md`. Record exact device model, Android/API version, OEM OS/build fingerprint, app version, tested commit, date/time, settings, scenario ID, expected result, observed result and PASS/FAIL/NOT TESTED. Preserve logs and redact personal data. Do not uninstall, clear app data, factory-reset or bypass authenticated recovery as test cleanup.

## Mandatory execution order

1. Complete the Phase 0–9 source/integration audit and fix confirmed defects only.
2. Complete Phase 10 documentation, privacy and asset consistency.
3. Complete Phase 11 branch/release-candidate readiness without merging or publishing.
4. Run exact-head non-instrumentation JVM unit tests, static/source-contract checks, lint, release build, APK metadata/package/signer checks and CodeQL.
5. Keep Phase 12 physical execution separate; do not substitute instrumentation or emulator tests.
6. Re-read this ledger, `PROJECT_STATE.md`, current branch HEAD and latest workflow results before reporting completion.

**Completion rule:** Report software coding completion separately from physical-device acceptance and production release. No phase is marked 100% accepted without its own evidence.
