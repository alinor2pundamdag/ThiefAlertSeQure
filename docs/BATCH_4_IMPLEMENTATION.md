# Batch Four — Phases 14–16

This document defines the v1.1.4 integration acceptance criteria for the already-approved Batch Four scope. It does not authorize a merge, tag, publication, or release. Physical-device acceptance remains owner-operated and is not a blocker for the non-physical software work.

## Phase 14 — About, Version, Legal and Privacy

### Acceptance criteria
- About displays the installed package versionName, versionCode, application ID, Android API compatibility, installed signer digest, and pinned official signer digest.
- The version history distinguishes the v1.1.4 development candidate from the latest published v1.1.3 baseline. It must not describe a development APK as an official release.
- The complete Apache-2.0 project license and direct runtime dependency acknowledgements are available offline.
- The complete versioned Privacy Policy and Terms of Use are bundled as app assets and readable from the first-launch acknowledgement screen and About.
- The first-launch acknowledgement requires both current policy versions and stores their decisions/timestamps in separate app-private preferences. A failed synchronous commit must not advance to the dashboard.
- Settings privacy review can reset the privacy acknowledgement; About permits the owner to review and separately acknowledge the Terms.
- The public README, repository legal documents, in-app copy, policy version constants, and packaged assets agree on the current policy versions and data-handling claims.
- Legal-document dialogs remain scrollable and bounded to the available screen height; missing bundled documents fail closed with a user-visible error.
- Legal and privacy viewing must not require a network connection or transmit local data.

### Source and verification
- AboutActivity.kt, MainActivity.kt, LegalDocumentViewer.kt, PolicyAcknowledgementPolicy.kt
- app/src/main/assets/legal/privacy_policy.txt
- app/src/main/assets/legal/terms_of_use.txt
- app/src/main/assets/licenses/Apache-2.0.txt
- scripts/verify_legal_policy_contract.py
- scripts/verify_packaged_legal_assets.py

## Phase 15 — Integration, Quality and Release Readiness

### Acceptance criteria
- Protection state remains owned by ProtectionStore; notifications, Quick Settings, widgets, and appearance remain projections/settings and cannot bypass owner authentication or disarm a latched alarm.
- The existing charger countdown, trusted-Bluetooth grace period, owner-authentication boundary, encrypted evidence retention, optional permission boundaries, and production package/signing identity remain unchanged.
- Android CI and signed-release preflight run the same non-physical security and integration source-contract checks before tests/build.
- CI runs JVM unit tests, Android lint, asset/configuration checks, debug APK build and metadata/signer verification, minified release APK build/metadata checks, and CodeQL on the exact final integration head.
- Instrumentation sources are not compiled or executed; no emulator or physical-device test is inferred from CI.
- Release preflight verifies the permanent owner-controlled signer when authorized secrets are present. No production secret values may be exposed in logs or PR workflows.
- The official updater remains owner-initiated, HTTPS-only, digest/package/version/signer-verified, and hands the APK to Android Package Installer for explicit confirmation.
- Release-readiness findings such as signing-secret availability, branch protection, historical CodeQL alert disposition, and distribution channel remain explicitly tracked. These do not authorize a merge or release.

### Source and verification
- .github/workflows/android.yml, .github/workflows/codeql.yml, .github/workflows/release.yml
- AppUpdateManager.kt, AppUpdatePolicy.kt, app/build.gradle.kts
- scripts/verify_release_preflight_parity.py
- scripts/verify_release_identity.py
- All shared checks enumerated by verify_release_preflight_parity.py

## Phase 16 — Local Tamper History and Final Verification

### Acceptance criteria
- The event timeline is encrypted at rest with purpose-bound Android Keystore AES-GCM protection and retains no more than 100 entries.
- Event names/details are bounded and sanitized; common credentials, tokens, JWTs, URIs, filesystem paths, email addresses, and exception messages are redacted before persistence and legacy data is sanitized on migration/read.
- Access requires a fresh Android device-credential confirmation or a strong-biometric CryptoObject operation that completes an authenticated Keystore challenge. No data is shown before successful owner verification.
- The history viewer uses a monotonic two-minute session, blocks screenshots, and rechecks authentication before export or destructive actions.
- Local search, category filters, CSV export, integrity warnings, and separate deletion controls remain available.
- Clearing the event timeline must not delete the separate encrypted evidence metadata archive or front-camera photo vault. Deleting the metadata archive must not delete the timeline or photo vault.
- Migration from the v1 plaintext event preference must commit the encrypted replacement before removing the plaintext key. A failed plaintext removal must leave history unhealthy/locked; later reads retry cleanup. The application must never expose history as healthy while the legacy plaintext key remains.
- Corrupt encrypted data is preserved for diagnosis and must not be silently overwritten or exported.
- Teardown cancels the biometric prompt, clears challenge bytes, and removes session-expiry callbacks.

### Source and verification
- LocalTamperHistory.kt, LocalTamperHistoryPolicy.kt, LocalTamperHistoryActivity.kt
- LocalHistoryBiometricCrypto.kt, LocalHistorySessionPolicy.kt, LocalEvidenceVault.kt
- scripts/verify_local_history_auth.py
- scripts/verify_local_history_storage.py
- LocalTamperHistoryPolicyTest.kt, LocalHistorySessionPolicyTest.kt

## Final non-physical completion gate

The Batch Four implementation is not considered CI-verified until Android build/unit-test/lint/packaging checks and CodeQL succeed on the same final integration head. Any failed check must be corrected at its source and the new head reverified.

Separate owner-controlled gates remain:
- Availability and verification of the permanent production signing secrets, without exposing their values.
- GitHub branch-protection/ruleset access and the disposition of historical CodeQL alert #8.
- Owner decision on direct GitHub APK distribution versus Google Play.
- Physical-device acceptance by the owner.

None of these authorizes or triggers a merge, tag, publication, or official release. The integration PR remains draft/open until the owner explicitly approves a later action.
