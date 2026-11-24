# Official GitHub APK Release Runbook

**Authorized scope:** Owner requested continuation through the official release on 24 November 2026.  
**Selected release channel:** Existing GitHub Releases direct APK distribution.  
**Candidate:** `1.1.5_Developer_Alinor`, versionCode `7`, production package `com.thiefalertsequre.app`.  
**Last published official release:** `v1.1.3`, versionCode `5`.  
**Permanent signing certificate SHA-256:** `9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64`.

This runbook covers GitHub APK distribution only. Google Play is not included in this release.

## 1. Clean integration and review

- The original PR #7/#8 stack was consolidated into the single-snapshot PR #9 branch `release/v1.1.5-candidate`, based directly on `main`. PR #9 includes the approved Batch Three/Four implementation, restored Bluetooth regression coverage and policy/design documents. The candidate head recorded on 24 November 2026 is `9ae3d15d9de909ab1a75e88f120ca7c9ff72b3c4` (197 changed files); `main` remains unchanged.
- PR #4 and #5 overlap the active implementation and must not be merged independently. PR #6 lowers minSdk to 23 and adds instrumentation; it conflicts with the approved minSdk 26 and no-instrumentation policy.
- Exact-head Android CI and CodeQL must pass on the clean release branch. The exact-head push and pull-request runs are now both green; see the evidence section below.
- **APK-installer security reassessment:** The old CodeQL review thread on PR #7 was formally reassessed against the successor PR #9 source and resolved on 24 November 2026. The assessment verified owner-initiated installation, exact GitHub HTTPS host/port validation for every redirect, bounded download, SHA-256, production package/version and pinned-signer verification, app-private cache confinement, repeated pre-handoff verification, DISARMED-state enforcement, non-exported FileProvider, and Android's user-confirmed package-installer handoff. PR #9's exact-head CodeQL check reports “No new alerts in code changed by this pull request.” **The repository-wide CodeQL alert record #8 itself was not independently readable/dismissible through the available GitHub interface in this session; do not claim that alert record is closed.** The old PR #7 review thread resolution is not equivalent to dismissing the Security alert.
- No formal approval review has been recorded on PR #9. The implementation/security reassessment above is not represented as an independent third-party approval.
- `main` remains unchanged until the clean release PR has passed review and required checks.

## 2. Production signing secrets

In GitHub repository Settings → Secrets and variables → Actions, configure these **repository Actions secrets**:

- `ANDROID_KEYSTORE_BASE64` — base64 encoding of the permanent JKS/keystore file
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Never paste secret values into chat, source files, workflow logs, commits or pull-request comments. Do not replace the permanent keystore with the CI debug keystore.

The release workflow decodes the keystore in the runner's temporary directory and verifies that its certificate matches the pinned SHA-256 above before building. Missing or mismatched secrets cause the workflow to fail closed. Their current presence has not been independently verified; only a successful signed preflight can verify that the configured secrets are usable and match the pinned certificate.

## 3. Signed preflight on main

After the clean release PR is merged into `main`:

1. Open GitHub Actions → **Official signed Android release**.
2. Select **Run workflow** with branch `main`.
3. This manual run is a preflight only; it does not publish a GitHub Release.
4. The workflow verifies the permanent keystore, version/tag format, monotonic versionCode, unit tests, release lint, minified release APK metadata, package identity, official signer and APK checksum.
5. Download and retain the signed preflight artifact before its three-day retention expires.
6. The owner will perform physical-device acceptance after receiving the signed APK. Physical acceptance is intentionally not a coding, CI, or merge gate.

If preflight fails, do not create the release tag. Fix the failure, rerun CI and preflight, then hand the signed artifact to the owner for physical acceptance.

## 4. Publish the official GitHub Release

Only after exact-head CI, security reassessment and signed preflight pass. The signed preflight APK is then handed to the owner for physical testing. Physical testing is not a coding, CI or merge gate, but public tag publication should wait until the owner has completed the device test and authorized publication:

- Create and push the exact tag `v1.1.5_Developer_Alinor` on the approved commit already merged into `main`.
- The tag push starts the **Official signed Android release** workflow.
- The workflow independently rechecks tag ancestry, signing key, monotonic versionCode, tests, release lint, APK metadata and signer.
- On success, it publishes:
  - `ThiefAlertSeQure-v1.1.5_Developer_Alinor-release.apk`
  - `ThiefAlertSeQure-v1.1.5_Developer_Alinor-release.apk.sha256`
- The updater expects this asset naming convention and validates the APK checksum, package, version and pinned official signer before opening Android's user-confirmed installer.

Do not manually upload an unsigned PR artifact or debug APK as an official release.

## 5. Post-publication verification

1. Confirm the release is published, not draft or prerelease.
2. Confirm both the APK and `.sha256` assets are present.
3. Recompute the APK SHA-256 and compare it with the sidecar.
4. Confirm package `com.thiefalertsequre.app`, versionCode `7`, versionName `1.1.5_Developer_Alinor`, and the pinned signing certificate.
5. On a device with the previous official release installed, open Settings → Check for official updates. Confirm the update is offered, the user must explicitly tap Install, Android presents its package installer, and app data remains intact.
6. Record release URL, tag commit, APK SHA-256, signer fingerprint, device/OS, and observed test results.

If a published release has a defect, publish a corrected release with a **higher versionCode**. Never downgrade users or replace an already published asset in place.

## 6. Google Play is a separate channel

The current updater uses `REQUEST_INSTALL_PACKAGES` for owner-initiated GitHub APK installation. Google Play restricts this permission and does not permit ordinary apps to use it for self-updating. A Play build requires a separate Play-managed update design and policy review for restricted foreground-service and full-screen-intent use. Do not upload the GitHub-sideload build to Play Console without that work.

The current targetSdk 36 meets the published Google Play target-API requirement for new apps and updates as of 31 August 2026. That alone does not establish policy approval.

## Explicitly deferred — not part of v1.1.5

No implementation GO was found for active shutdown/power-menu/reboot prevention, guaranteed protection while powered off, unconditional, non-opt-in or background front-camera capture, new camera/flashlight conflict prevention, or maximum/premium expansion. The existing camera evidence feature remains opt-in and permission-gated; when enabled, its documented single-photo trigger is successful owner authentication while the app is visible. No covert/background capture is included. Do not add these features to this release without a separate owner GO.

## Current blockers and verified state (24 November 2026)

- **Android CI — passed on exact candidate head:** push run [#45311423677](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/45311423677) and PR run [#45311432586](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/45311432586). Unit tests, lint, integration/security contracts, debug APK assembly, release APK metadata/minification and signer-metadata checks passed. The uploaded artifact is a **debug APK**, not the production-signed release APK.
- **CodeQL — passed on exact candidate head:** push run [#45311423737](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/45311423737) and PR run [#45311432532](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/45311432532). Java/Kotlin and GitHub Actions analysis passed. The PR check reports no new alerts in changed code.
- **Old PR #7 APK-installer review thread:** reassessed and resolved. **Repository Security alert #8 dismissal/closure remains unverified**; do not conflate thread resolution with alert dismissal.
- **PR #9 formal review:** no review approval has been recorded. The candidate has been source-reviewed and CI-verified, but independent approval is still outstanding.
- **Production signing secrets and signed preflight:** not verified. PR CI deliberately does not receive production signing secrets. A manual signed preflight on `main` is still required after merge.
- **Owner physical acceptance:** owner-operated after delivery of the signed APK; intentionally excluded from coding, CI and merge gates.
- **Merge, release tag and production publication:** not performed. No production-signed APK has been generated or published.

**Release status:** Candidate implementation and exact-head automated checks are complete; official signed preflight and release publication are not. Do not create the release tag until the security alert record is reconciled, the candidate is merged, and signed preflight passes. Physical acceptance remains owner-operated after delivery.
