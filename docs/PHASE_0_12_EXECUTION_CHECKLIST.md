# FIXALL Phase 0–12 Coding and Acceptance Checklist

**Project:** ThiefAlertSeQure Solo  
**Working branch:** `work/phase-0-12-coding`  
**Production branch:** `main` — untouched; no merge, tag, publication or release without separate owner approval  
**Owner direction:** Complete all repository/code/GitHub work in scope. Do not run or compile Android instrumentation tests, do not run emulator matrices, and do not perform physical-device testing. Physical-device acceptance is excluded from this software-completion task.

This is the persistent source of truth for interrupted work. A phase is not complete merely because files exist or a generic build is green. Implementation, cross-file integration, failure handling, documentation and exact-head non-instrumentation verification must be satisfied. Physical-device results must never be inferred from CI.

## Non-negotiable invariants

- Preserve the existing feature set, permissions, access paths, Android integrations, package identity, production signing identity, user data and UI/UX scope.
- `minSdk 26`; compile/target SDK 36 unless the owner explicitly authorizes a change.
- Charger-removal grace period: exactly 10 seconds.
- Trusted Bluetooth A2 disconnect grace period: 8 seconds.
- A latched alarm can be cleared only after fresh, successful owner Android device-credential confirmation or BIOMETRIC_STRONG authentication.
- Protection remains local-first/offline. No cloud database, telemetry, advertising, scanning or unrelated features.
- Do not silently delete corrupted data, alter production identity, merge, tag, publish APKs or release.
- No instrumentation execution, instrumentation compilation, Android emulator runner, emulator matrix, or physical-device testing in this task.

## Phase acceptance ledger

| Phase | Required coding and integration work | Current disposition |
|---|---|---|
| **0 — Safety and baseline** | Preserve source baseline, package/signing identity, SDK floor, data, feature/permission inventory and production boundary | **PASS —** final diff has no deleted paths; minSdk 26, compile/target 36, package identity and signing contract guards pass; main remains untouched |
| **1 — Compilation and architecture** | One owner for state transitions, persistence, Android boundaries and UI/runtime dependencies | **PASS —** architecture map, state-ownership checks, Kotlin compilation and exact-head CI pass |
| **2 — Power and charger** | Event-time capture, live power validation, debounce, generation guards, reconnect cancellation, 10-second deadline and reboot recovery | **CODING/CI PASS —** implementation and JVM policy coverage pass; charger hardware/OEM behavior remains physical-device-only |
| **3 — Data integrity** | Safe typed preference reads, synchronous commits, corruption preservation/recovery, migration and test-state isolation | **PASS —** safe preference, manifest and state-ownership source checks plus JVM tests pass; physical storage-failure behavior remains device-dependent |
| **4 — Alarm and recovery** | Persist latch before playback; scheduler token integrity; recovery/retry; audio fallback, volume restoration, optional flashlight and authenticated disarm | **CODING/CI PASS —** policy/JVM regression checks and APK build pass; speaker/torch/Keyguard behavior remains physical-device-only |
| **5 — Bluetooth A2** | Encrypted trusted registry, ACL receiver lifecycle, per-device revisions, reconnect invalidation, 8-second grace period and safe fallback | **CODE FIX COMMITTED / EXACT-HEAD CI PENDING —** serialized encrypted deadline read-modify-write prevents concurrent accessory events from overwriting each other's records; JVM tests cover per-device merge preservation; exact-head CI still required |
| **6 — Authentication and security** | Ephemeral proof, Android credential/strong biometric gate, persisted throttle, anti-tampering and owner-only actions | **CODE FIX COMMITTED / EXACT-HEAD CI PENDING —** proof freshness now rejects asymmetric known/unknown boot identity; regression tests added; exact-head JVM/CI result still required |
| **7 — Background services and lifecycle** | Foreground-service start/stop, notification channels, boot/package-replacement recovery, process recreation and Android-version restrictions | **CODING/CI PASS —** source checks and lint pass; OEM background execution and reboot delivery remain physical-device-only |
| **8 — UI and accessibility** | Preserve screens, navigation, themes, readiness flows, edge-to-edge insets and accessibility semantics | **CODING/CI PASS —** navigation, themed dialogs, theme and accessibility source checks pass; TalkBack/display perception remains device-unverified |
| **9 — Automated tests and CI** | JVM tests, source-contract checks, manifest/preferences/ownership checks, lint, assets, release build and APK identity checks | **PRIOR CANDIDATE PASS / CURRENT HEAD PENDING —** commits `475c1fa` and `ffe5fda` have successful Android/CodeQL evidence listed below; later owner-scope documentation and the authentication freshness fix require exact-head non-instrumentation CI evidence. Instrumentation compilation/execution and emulator matrices remain excluded |
| **10 — Documentation, Privacy & Assets** | Reconcile README, privacy policy, permissions, feature matrix, threat model, update/install docs, asset formats/integrity and user-facing capability claims | **PASS —** privacy/permission claims reconciled, optional ranging is disclosed accurately, README/API scope corrected, packaged icon/audio assets validated and static privacy guards pass |
| **11 — Branch Integration & Release Candidate** | Review branch diff/ancestry, package/version/signer invariants, release workflow, secret references, artifact identity, update path and release gates | **REVIEW IN PROGRESS / INTEGRATION HELD —** compare reports 500 commits ahead (API cap), 1 behind `main`, 130 changed paths, no deletions. Draft PR #7 is open and GitHub reports `mergeable=false`; no merge/rebase/tag/publication/release performed. Permanent production signing-secret presence remains unverified |
| **12 — Physical-device acceptance** | Physical-device execution and evidence collection | **OUT OF SCOPE —** owner explicitly excluded physical-device testing from this software/GitHub completion task; do not run it or hold code completion on it |

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

## Phase 12 — excluded from this software task

Physical-device acceptance records and OEM scenario documents remain historical/reference material only. Do not execute physical-device scenarios, collect device evidence, run emulator tests, or compile/run instrumentation tests. None of these activities is a blocker for the requested code/GitHub completion.

## Mandatory execution order

1. Complete the Phase 0–9 source/integration audit and fix confirmed defects only.
2. Complete Phase 10 documentation, privacy and asset consistency.
3. Complete Phase 11 branch/release-candidate readiness without merging or publishing.
4. Run exact-head non-instrumentation JVM unit tests, static/source-contract checks, lint, release build, APK metadata/package/signer checks and CodeQL.
5. Re-read this ledger, `PROJECT_STATE.md`, current branch HEAD and latest non-instrumentation workflow results before reporting completion.

**Completion rule:** Completion is measured only against repository/code/GitHub deliverables and non-instrumentation CI on the exact branch head. Physical-device testing is explicitly excluded and must not block this task. Merge, tag, publication and production release remain separate actions requiring owner approval.


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

This snapshot records the latest code-bearing source commit and the final documentation snapshot `ffe5fda`, both verified by the Android and CodeQL runs listed above. The present verification-record update changes documentation only; no app source, Gradle, manifest, or workflow code changes.


## Latest code audit and PR snapshot — 2026-11-17

- Current working branch HEAD: `b59735ab72c3be2c84b737cc8cda05e93821d926` (documentation-only audit ledger update after the authentication fix and tests).
- Application source fix: `e17f482264eba0d027b92499010be3b72c6d0086` rejects an owner-authentication proof when only one side of the boot identity comparison is available.
- Regression tests: `80c8c52be927db24a31a7c08d699998ffb72d559` covers known-to-unknown, unknown-to-known, and both-unknown boot identity cases.
- Draft PR #7: https://github.com/alinor2pundamdag/ThiefAlertSeQure/pull/7; draft/unmerged. GitHub reported `mergeable=false` at creation.
- Latest exact-head Android/CodeQL CI evidence has not yet been returned by the available GitHub status queries. Do not report the new code/test change as CI-verified until exact-head runs are confirmed.
- Source audit reviewed the core charger, alarm, A2, persistence, boot, foreground-service and authentication paths listed in `PROJECT_STATE.md`; remaining repository-wide review is still in progress.
- Physical-device testing, emulator testing and instrumentation compilation/execution are explicitly out of scope and are not completion blockers.


## Additional code fixes and verification boundary — 2026-11-17

- Authentication proof freshness fix: `e17f482264eba0d027b92499010be3b72c6d0086` rejects a proof when only one of the issued/current boot identities is known.
- JVM regression cases: `80c8c52be927db24a31a7c08d699998ffb72d559` adds known-to-unknown, unknown-to-known, and both-unknown boot identity coverage.
- CI policy guard: `1e69aeb6b412ca52c9b79049e8be024cda8fb817` expands instrumentation/emulator wiring detection across all Gradle configuration files and all GitHub Actions workflow YAML files.
- Traceability update: `a5b7e2d5431f4cc4d1169a17a8a249f020d16c11` records these changes and the current verification boundary.
- Draft PR #7 is open and unmerged. The latest PR head has not returned confirmed exact-head CI evidence through the available status/run queries; prior green runs remain historical evidence for their own commits only.
- Do not compile/run dormant `androidTest` sources. The only required test execution is JVM unit tests plus non-instrumentation build/static/lint/security workflows.
- Full repository-wide source review and exact-head CI are still pending; no 100% completion claim is authorized until those are evidenced.


### A2 multi-accessory concurrency fix

- Confirmed race: concurrent ACL disconnect callbacks could both read the same encrypted pending-deadline list and then overwrite each other's per-device records.
- Fix: serialize schedule/find/cancel/clear operations on one scheduler lock; isolate failure cleanup to the same preferences/PendingIntent namespace; extract a pure per-device merge function.
- Source commits: `5cf2da0b7c1f134e9a1fcdfd4b913a2561aa85c3` and `679f22ac7c2721af15e298a851fdbbfbf8c4b31e`.
- JVM regression tests: `f5bf25576de8a903699a989924a515ea506401a6` adds coverage that updating one accessory preserves another accessory's deadline and address matching is case-insensitive.
- Exact-head JVM/CI execution is pending; no instrumentation or emulator execution is involved.
