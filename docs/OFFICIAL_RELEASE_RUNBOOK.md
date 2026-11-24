# Official GitHub APK Release Runbook

**Authorized scope:** Owner requested continuation through the official release on 24 November 2026.  
**Selected release channel:** Existing GitHub Releases direct APK distribution.  
**Candidate:** `1.1.5_Developer_Alinor`, versionCode `7`, production package `com.thiefalertsequre.app`.  
**Last published official release:** `v1.1.3`, versionCode `5`.  
**Permanent signing certificate SHA-256:** `9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64`.

This runbook covers GitHub APK distribution only. Google Play is not included in this release.

## 1. Clean integration and review

- The original PR #7/#8 stack had 1,504 commits ahead of `main` and 196 changed paths. It has been consolidated into the single-snapshot PR #9 branch `release/v1.1.5-candidate`, based directly on `main`. PR #9 head `56418b68e422ded696705fd4d07d76605151d19a` passed Android CI #45307869027 and CodeQL #45307868989. The follow-up restoring PR #5 Bluetooth docs/tests requires fresh exact-head CI.
- PR #4 and #5 overlap the active implementation and must not be merged independently. PR #6 lowers minSdk to 23 and adds instrumentation; it conflicts with the approved minSdk 26 and no-instrumentation policy.
- Exact-head Android CI and CodeQL must pass on the clean release branch. Previous stack CI results do not substitute for clean-branch results.
- Review the CodeQL APK-installer finding. The current path validates GitHub HTTPS hosts, bounds the download, verifies SHA-256, package/version, pinned signer and app-private cache location, revalidates immediately before installer handoff, requires protection DISARMED and requires an explicit owner tap. The PR #7 review thread remains unresolved; do not claim it closed until the alert is formally reassessed.
- `main` must remain unchanged until the clean release PR has passed review and required checks.

## 2. Production signing secrets

In GitHub repository Settings → Secrets and variables → Actions, configure these **repository Actions secrets**:

- `ANDROID_KEYSTORE_BASE64` — base64 encoding of the permanent JKS/keystore file
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Never paste secret values into chat, source files, workflow logs, commits or pull-request comments. Do not replace the permanent keystore with the CI debug keystore.

The release workflow decodes the keystore in the runner's temporary directory and verifies that its certificate matches the pinned SHA-256 above before building. Missing or mismatched secrets cause the workflow to fail closed. Their current presence has not been independently verified.

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

Only after exact-head CI, security review and signed preflight pass. Physical-device acceptance is performed separately by the owner and is not a development gate:

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

No implementation GO was found for active shutdown/power-menu/reboot prevention, guaranteed protection while powered off, automatic front-camera capture on successful owner credential entry, new camera/flashlight conflict prevention, or maximum/premium expansion. The current camera evidence flow remains owner-initiated and opt-in. Do not add these features to this release without a separate owner GO.

## Current blockers

- Latest exact-head CodeQL passed; latest exact-head Android CI is running its final minified APK verification and artifact upload.
- CodeQL APK-installation review thread remains unresolved.
- Independent review is pending.
- Production signing-secret availability and signed preflight are not verified.
- Owner physical acceptance is pending and intentionally excluded from coding/merge gates.
- No release tag or production publication has occurred.

**Release status:** Not published. Do not create the release tag until CI, security reassessment and signed preflight are cleared. Physical acceptance is owner-operated after delivery.
