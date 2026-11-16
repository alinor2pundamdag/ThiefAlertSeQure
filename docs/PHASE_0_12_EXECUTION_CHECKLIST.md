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
| **0 — Safety and baseline** | Preserve source baseline, package/signing identity, SDK floor, data, feature/permission inventory and production boundary | **PASS —** final diff has no deleted paths; minSdk 26, compile/target 36, package identity and signing contract guards pass; main remains untouched |
| **1 — Compilation and architecture** | One owner for state transitions, persistence, Android boundaries and UI/runtime dependencies | **PASS —** architecture map, state-ownership checks, Kotlin compilation and exact-head CI pass |
| **2 — Power and charger** | Event-time capture, live power validation, debounce, generation guards, reconnect cancellation, 10-second deadline and reboot recovery | **CODING/CI PASS —** implementation and JVM policy coverage pass; charger hardware/OEM behavior remains physical-device-only |
| **3 — Data integrity** | Safe typed preference reads, synchronous commits, corruption preservation/recovery, migration and test-state isolation | **PASS —** safe preference, manifest and state-ownership source checks plus JVM tests pass; physical storage-failure behavior remains device-dependent |
| **4 — Alarm and recovery** | Persist latch before playback; scheduler token integrity; recovery/retry; audio fallback, volume restoration, optional flashlight and authenticated disarm | **CODING/CI PASS —** policy/JVM regression checks and APK build pass; speaker/torch/Keyguard behavior remains physical-device-only |
| **5 — Bluetooth A2** | Encrypted trusted registry, ACL receiver lifecycle, per-device revisions, reconnect invalidation, 8-second grace period and safe fallback | **CODING/CI PASS —** encrypted registry, per-device deadlines and JVM policy coverage pass; real accessory/OEM callback behavior remains unverified |
| **6 — Authentication and security** | Ephemeral proof, Android credential/strong biometric gate, persisted throttle, anti-tampering and owner-only actions | **CODING/CI PASS —** ephemeral proof and auth-policy checks pass; actual Keyguard/strong-biometric hardware flows remain physical-device-only |
| **7 — Background services and lifecycle** | Foreground-service start/stop, notification channels, boot/package-replacement recovery, process recreation and Android-version restrictions | **CODING/CI PASS —** source checks and lint pass; OEM background execution and reboot delivery remain physical-device-only |
| **8 — UI and accessibility** | Preserve screens, navigation, themes, readiness flows, edge-to-edge insets and accessibility semantics | **CODING/CI PASS —** navigation, themed dialogs, theme and accessibility source checks pass; TalkBack/display perception remains device-unverified |
| **9 — Automated tests and CI** | JVM tests, source-contract checks, manifest/preferences/ownership checks, lint, assets, release build and APK identity checks | **PASS on exact head `475c1fa` —** [Android run 43950015535](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015535) and [CodeQL run 43950015623](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015623) both succeeded. Instrumentation compilation/execution and emulator matrices were not run by owner direction |
| **10 — Documentation, Privacy & Assets** | Reconcile README, privacy policy, permissions, feature matrix, threat model, update/install docs, asset formats/integrity and user-facing capability claims | **PASS —** privacy/permission claims reconciled, optional ranging is disclosed accurately, README/API scope corrected, packaged icon/audio assets validated and static privacy guards pass |
| **11 — Branch Integration & Release Candidate** | Review complete branch diff, branch ancestry, package/version/signer invariants, release workflow, secrets references, artifact identity, update path and release gates | **REVIEW PASS / INTEGRATION HELD —** 130 paths changed (90 added, 40 modified, none deleted). Main-only `PROJECT_STATE.md` commit creates a divergence to reconcile before integration. Exact-head debug APK and minified release metadata checks pass. Permanent signing-secret presence was not independently verified; no merge/tag/publication/release performed |
| **12 — Physical Device Testing & Final Acceptance** | Prepare a reproducible physical-device matrix, evidence template, safe recovery/cleanup and known platform/OEM limitations; record model/build/app commit for each test | **PREPARATION PASS / PHYSICAL ACCEPTANCE NOT TESTED —** reproducible HONOR 200 Pro scenario matrix and evidence record are prepared; only real-device execution can close this phase |

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

Use `docs/PHASE_12_DEVICE_ACCEPTANCE_RECORD.md`, `docs/HONOR_200_PRO_COMPATIBILITY_AUDIT.md`, `docs/ANDROID_DEVICE_COMPATIBILITY.md` and `docs/UX_UI_AUDIT.md`. Record exact device model, Android/API version, OEM OS/build fingerprint, app version, tested commit, date/time, settings, scenario ID, expected result, observed result and PASS/FAIL/NOT TESTED. Preserve logs and redact personal data. Do not uninstall, clear app data, factory-reset or bypass authenticated recovery as test cleanup.

## Mandatory execution order

1. Complete the Phase 0–9 source/integration audit and fix confirmed defects only.
2. Complete Phase 10 documentation, privacy and asset consistency.
3. Complete Phase 11 branch/release-candidate readiness without merging or publishing.
4. Run exact-head non-instrumentation JVM unit tests, static/source-contract checks, lint, release build, APK metadata/package/signer checks and CodeQL.
5. Keep Phase 12 physical execution separate; do not substitute instrumentation or emulator tests.
6. Re-read this ledger, `PROJECT_STATE.md`, current branch HEAD and latest workflow results before reporting completion.

**Completion rule:** Report software coding completion separately from physical-device acceptance and production release. No phase is marked 100% accepted without its own evidence.


## Latest code-bearing candidate verification snapshot — 2026-11-17

- Branch: `work/phase-0-12-coding`
- Code-bearing source commit: `475c1fa00b8c30c1280f77053d83eb91c0db7f64`
- Android workflow: [run 43950015535 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015535)
- CodeQL: [run 43950015623 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015623)
- JVM test source files: 29; unit-test step passed.
- Instrumentation: no instrumentation task, compilation or execution; no emulator matrix.
- Development artifact: `ThiefAlertSeQure-debug-apk`, ID `14266362844`, 6,615,813 bytes; package `com.thiefalertsequre.app.v114test`, version `1.1.4-test`, code 6, minSdk 26; APK SHA-256 `034d3cc076ca06ea3265bd468e42de8247352d3f8cf1f1882f7b4620971c641e`; CI debug signer SHA-256 `be3def2cc431cb7000e025693cb9705496a71b90d22eda372140e5a03b562c17`.
- Minified release build: package `com.thiefalertsequre.app`, version `1.1.4`, code 6; build and metadata checks passed, but this CI output was unsigned because permanent release signing was not configured. It is not a production release.
- Branch comparison: 130 changed paths, 90 added, 40 modified, 0 deleted. The main-only commit adds `PROJECT_STATE.md`; do not merge/rebase without separate approval.
- Physical acceptance on HONOR 200 Pro: **NOT TESTED**.

This snapshot records the latest code-bearing source commit. The subsequent Phase 10–12 follow-up commits in this documentation pass are documentation-only; no app source, Gradle, manifest, or workflow code changed. The final branch head is still checked by the current exact-head Android and CodeQL workflows.
