# Batch Four — Phases 14–16

**Working branch:** `work/batch-4-implementation`  
**Baseline:** `work/phase-0-12-coding` (Batch Three handoff)  
**Production identity:** `com.thiefalertsequre.app`  
**Current candidate:** `1.1.4_Developer_Alinor` / versionCode `6`

This record distinguishes existing capabilities reused by Batch Four from the additions made in this branch. It is not a claim that CI, production signing, or device acceptance has passed.

## Phase 14 — About, Version, Legal and Privacy

### Existing components reused (no duplicate subsystem created)

- `AboutActivity` already displays the installed versionName, versionCode, application ID, and Android API compatibility.
- `SettingsActivity` already contains the privacy/data summary, permission-related explanations, official update controls, and permission readiness guidance.
- `docs/PRIVACY_POLICY.md` is the existing full privacy policy and remains the source of truth for local data, optional camera/location features, retention, and platform permissions.
- `AppUpdatePolicy`, `AppUpdateManager`, and `.github/workflows/release.yml` already pin the production package and official signing-certificate SHA-256 and verify downloaded/release APK identity.
- The repository already has an Apache License 2.0 `LICENSE` file.

### Batch Four additions

- Production versionName is now `1.1.4_Developer_Alinor`; versionCode remains `6`, preserving the v1.1.3 code-5 → v1.1.4 code-6 upgrade order.
- The isolated debug build remains a separate package (`.v114test`) and keeps the explicit `1.1.4-test` label.
- New official release tags with the permanent suffix are accepted by the updater. Historical numeric-only tags remain accepted so an older release does not become unreadable.
- A new About button opens Android's app-specific details page using `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` and the `package:` URI. It does not change Android settings.
- About now reads the installed package signer, calculates its SHA-256 fingerprint, and compares the production package against the existing pinned official certificate. Development packages are identified separately rather than incorrectly shown as production-verified.
- About includes direct dependency license acknowledgments: AndroidX and Material runtime dependencies (Apache 2.0), JUnit test dependency (EPL 1.0), and the project Apache 2.0 license.

### Branding reconciliation

The fetched Batch Three baseline `AboutActivity.kt` did not contain the exact “Developed by Alinor Pundandag” line or an infinite glowing animation. No second developer-credit label was added in this branch, in accordance with the owner's instruction not to duplicate existing branding. The claimed existing branding must be reconciled against the intended source/build before declaring that particular visual element verified.

## Phase 15 — Integration, Quality and Release Readiness

- Existing package name, namespace, minSdk 26, compileSdk/targetSdk 36, debug package suffix, and release certificate pin are preserved.
- The release signing key and credentials were not changed, regenerated, copied, or exposed.
- The existing release workflow remains responsible for keystore-secret presence, pinned certificate validation, increasing versionCode, APK package/version metadata, APK signer, checksum, and release artifact checks.
- `scripts/verify_release_identity.py` now also rejects an official Gradle versionName that does not end in `_Developer_Alinor`.
- `AppUpdatePolicyTest` now covers suffixed tags, historical numeric tags, malformed tags, and the existing monotonic versionCode behavior.
- No production tag, release, merge, or publication is performed by this Batch Four branch.

**Update compatibility:** v1.1.3 code 5 → v1.1.4 code 6. The next official release is planned as v1.1.5 code 7 with versionName `1.1.5_Developer_Alinor`. In-place update compatibility still depends on the exact production package ID, same official signing certificate, and strictly increasing versionCode. Debug APKs are not production update packages.

## Phase 16 — Local Tamper History and Final Verification

### Existing components reused

- `LocalTamperHistory` remains the bounded readable local event log.
- `LocalEvidenceVault` remains the separate encrypted AES-GCM event/location metadata archive with per-record SHA-256 validation and bounded retention.
- `CameraEvidenceVault` remains the separate encrypted photo archive with per-photo SHA-256 verification and retention limits.
- Existing export, backup/restore, and owner-controlled deletion paths are preserved.

### Batch Four additions

- Settings now explicitly distinguishes the readable convenience event log from the separate encrypted evidence archive.
- Settings displays the encrypted metadata archive integrity result and provides an owner-triggered check for both encrypted event metadata and encrypted photo evidence.
- Failed or unknown integrity results are not represented as verified. The check does not modify protection state or alarm state.

## Explicitly unchanged

- App name, launcher icon, production applicationId, release certificate pin, minSdk, targetSdk, charger countdown, A2 grace period, alarm latch, authentication, existing privacy/permission flows, and local data ownership.
- No new cloud backend, analytics, telemetry, account, or unrelated runtime permission.
- No instrumentation tests, emulator tests, physical-device tests, or GitHub Codespaces are part of this non-physical implementation pass.

## Verification status

- Source changes are committed to the isolated Batch Four working branch.
- CI and CodeQL results for the final Batch Four head: pending observation.
- Actual production keystore/secret availability and a production-signed APK: not verified in this task.
- Android install-over-existing-device acceptance and HONOR 200 Pro behavior: not performed here.
- No claim of 100% release readiness until the exact final commit's required CI/security checks are observed and remaining owner/device gates are recorded.
