# Batch Four — Phases 14–17

**Working branch:** `work/batch-4-implementation`  
**Historical Batch Three baseline:** `work/phase-0-12-coding`  
**Production identity:** `com.thiefalertsequre.app`  
**Current candidate:** `1.1.5_Developer_Alinor` / versionCode `7`  
**21 November verification:** Code-bearing commit `c64490788185fe2f9390cf21a87de003f4a1b430` passed Android CI #44860111389 and CodeQL #44860111390. The latest documentation-only PR head `e6cd3f2e6043f0e3de97027595c026ff4cd3eab2` also passed exact-head Android CI #44861309604 and CodeQL #44861309552. Unit tests, source/manifest/release contracts, debug/release lint, debug APK assembly, minified release metadata, package/version/signer verification and artifact upload passed. Phase 12 remains 0/25 PASS / 25/25 NOT TESTED; no instrumentation, emulator or physical-device test is claimed.

This record distinguishes existing capabilities reused by Batch Four from the additions made in this branch. Software CI passed on code-bearing commit `c644907`; production signing and physical-device acceptance remain unverified.

## Phase 14 — About, Version, Legal and Privacy

### Existing components reused (no duplicate subsystem created)

- `AboutActivity` already displays the installed versionName, versionCode, application ID, and Android API compatibility.
- `SettingsActivity` already contains the privacy/data summary, permission-related explanations, official update controls, and permission readiness guidance.
- `docs/PRIVACY_POLICY.md` is the existing full privacy policy and remains the source of truth for local data, optional camera/location features, retention, and platform permissions.
- `AppUpdatePolicy`, `AppUpdateManager`, and `.github/workflows/release.yml` already pin the production package and official signing-certificate SHA-256 and verify downloaded/release APK identity.
- The repository already has an Apache License 2.0 `LICENSE` file.

### Batch Four additions

- Candidate versionName is `1.1.5_Developer_Alinor`; candidate versionCode is `7`, preserving the v1.1.4 code-6 → v1.1.5 code-7 upgrade order.
- The isolated debug build remains a separate package (`.v114test`) and derives the explicit `1.1.5-test` label from the single Gradle version source; CI no longer duplicates the version code/name.
- New official release tags with the permanent suffix are accepted by the updater. Historical numeric-only tags remain accepted so an older release does not become unreadable.
- A new About button opens Android's app-specific details page using `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` and the `package:` URI. It does not change Android settings.
- About now reads the installed package signer, calculates its SHA-256 fingerprint, and compares the production package against the existing pinned official certificate. Development packages are identified separately rather than incorrectly shown as production-verified.
- About includes direct dependency license acknowledgments: AndroidX and Material runtime dependencies (Apache 2.0), JUnit test dependency (EPL 1.0), and the project Apache 2.0 license.

### Developer signature card

The Batch Three baseline did not contain the previously planned top developer credit, so Batch Four adds it exactly once inside the existing `AboutActivity` (no second About screen or duplicate branding module):

- Exact credit: `Developed by Alinor Pundamdag`.
- Fixed dark/black card at the top of About, immediately after the back navigation control.
- Twenty light-spectrum colors; a true frame-by-frame smooth letter-by-letter sweep, with one-second color progression and the repeating A–G stage cycle.
- Seven A–G animation stages cycle through the credit; the palette continues across loops.
- Reuses the existing `AppearanceStudioStore.reduceMotion` preference. Reduced-motion mode renders a stable high-contrast static credit with no animation.
- Animation is cancelled while the Activity is paused or destroyed and restarts on resume.
- The credit is a branding statement only; it is not represented as cryptographic proof. Cryptographic authenticity is separately verified by the signing-certificate fingerprint.

## Phase 15 — Integration, Quality and Release Readiness

- Existing package name, namespace, minSdk 26, compileSdk/targetSdk 36, debug package suffix, and release certificate pin are preserved.
- The release signing key and credentials were not changed, regenerated, copied, or exposed.
- The existing release workflow remains responsible for keystore-secret presence, pinned certificate validation, increasing versionCode, APK package/version metadata, APK signer, checksum, and release artifact checks.
- `scripts/verify_release_identity.py` now also rejects an official Gradle versionName that does not end in `_Developer_Alinor`.
- `AppUpdatePolicyTest` now covers suffixed tags, historical numeric tags, malformed tags, and the existing monotonic versionCode behavior.
- No production tag, release, merge, or publication is performed by this Batch Four branch.

**Update compatibility:** v1.1.4 code 6 → v1.1.5 code 7 with versionName `1.1.5_Developer_Alinor`. In-place update compatibility still depends on the exact production package ID, same official signing certificate, and strictly increasing versionCode. Debug APKs are not production update packages.

## Phase 16 — Local Tamper History and Final Verification

### Existing components reused

- `LocalTamperHistory` remains the bounded readable local event log.
- `LocalEvidenceVault` remains the separate encrypted AES-GCM event/location metadata archive with per-record SHA-256 validation and bounded retention.
- `CameraEvidenceVault` remains the optional private encrypted photo archive with per-photo SHA-256 verification and retention limits. New captures default to unencrypted Gallery storage in `Pictures/ThiefAlertSeQure`; Settings can route future captures to the encrypted vault. Existing images are never silently converted. The app-managed Gallery folder is independently bounded to 20 photos / 30 days and its deletion path is restricted to app-named files.
- Existing export, backup/restore, and owner-controlled deletion paths are preserved.

### Batch Four additions

- Settings now explicitly distinguishes the readable convenience event log from the separate encrypted evidence archive.
- Settings displays the encrypted metadata archive integrity result and provides an owner-triggered check for encrypted event metadata and encrypted photo evidence. It also shows the new photo storage mode, Gallery/vault counts, and an explicit encryption toggle. The privacy notice is versioned again so existing installs acknowledge the Gallery visibility change.
- Failed or unknown integrity results are not represented as verified. The check does not modify protection state or alarm state.

## Owner-directed implementation audit corrections

A source-to-original-plan audit found that the earlier Phase 13 implementation did not match the owner's cinematic brief: it rendered only expanding circles, added an unrequested `Skip intro` control, and omitted the requested title drops, seismic shake, concrete fractures, particle disintegration, cement-textured name reveal, dark-gray background reveal, and digital-wind exit. The Phase 13 plan above is corrected to the original specification, and the intro implementation is being replaced with the requested Compose/Canvas sequence. The About developer credit also had a spelling error and only updated its text color once per letter step; it is corrected to the owner's exact spelling and frame-by-frame color interpolation.

The dashboard now re-checks Android 13+ notification permission after returning from active-alarm recovery, so the permission prompt is not shown over owner authentication and is not permanently skipped when the alarm was active at launch. The foreground monitor and alarm use the dedicated monochrome shield as the required small notification icon; the launcher artwork is not misused as a large icon. They request immediate foreground-notification visibility and update silently once posted. Verified Android setup now checks the persistent protection channel separately from the alarm channel, detects blocked/minimum importance, and links directly to that channel's Android settings. Low-importance silent icons may still be hidden by the device's own status-bar preference. The OS still controls whether a status-bar icon is shown: notifications must be allowed, the corresponding foreground service must be running, and Android/OEM notification settings must permit it. The app does not and cannot draw system-owned 4G/VoLTE, headset, vibration, or battery indicators.

## Explicitly unchanged

- App name, launcher icon, production applicationId, release certificate pin, minSdk, targetSdk, charger countdown, A2 grace period, alarm latch, authentication, existing privacy/permission flows, and local data ownership.
- No new cloud backend, analytics, telemetry, account, or unrelated runtime permission.
- No instrumentation tests, emulator tests, physical-device tests, or GitHub Codespaces are part of this non-physical implementation pass.

### Phase 17 — additional cross-feature hardening
- Legacy Gallery ownership-index reads now use typed SafePreferences; corrupt values fail closed.
- Explicit legacy backup and Android 12+ cloud/device-transfer exclusion rules preserve the no-automatic-backup privacy contract.
- Managed Lock Task API 28 and thermal preview API 29 calls are guarded on the minSdk 26 range.
- ProtectionShieldView reuses Path instances to avoid per-frame drawing allocations; adaptive launcher icons now include monochrome layers.
- New photo storage remains Gallery-visible and unencrypted by default; future-capture encryption remains an explicit owner setting. Existing Gallery originals are not removed until complete encrypted-vault verification succeeds.

## Verification status

- Source changes are committed to the isolated Batch Four working branch.
- Filament artifacts are pinned together at 1.75.1 and Kotlin plugins remain aligned at 2.2.21 to preserve compileSdk 36 / AGP 8.10.1. Filament 1.80.1 and 1.81.0 require compileSdk 37 and were rejected rather than changing the approved build baseline.
- Actual production keystore/secret availability and a production-signed APK: not verified in this task.
- Android install-over-existing-device acceptance and HONOR 200 Pro behavior: not performed here.
- No claim of 100% release readiness until the exact final commit's required CI/security checks are observed and remaining owner/device gates are recorded.


## Post-audit continuation — camera storage and update intro

- Camera evidence remains opt-in and is captured only after successful owner authentication while the authentication Activity is visible. The default storage mode is now public Gallery JPEG (unencrypted); **Encrypt new camera photos** switches only future captures to the private AES-GCM vault. The setting is fail-closed to encryption if its stored type is corrupt.
- Gallery writes use MediaStore with pending/publish semantics on Android 10+ and the dedicated app-owned Pictures folder on Android 8–9. Legacy read/write permissions are requested only on API 26–28 when Gallery mode is selected. Gallery pruning/deletion filters by both the app folder and its file-name prefix; unrelated photos are not touched.
- Configuration backup schema 2 includes the new storage preference while accepting schema 1 backups without changing the current storage privacy choice.
- The cinematic starting page is version-gated: it appears once after each installed versionCode change, then is suppressed for that version. It is deferred while protection is not DISARMED so it cannot cover active protection or alarm recovery. The candidate versionCode is 7.
- Software verification passed on code-bearing commit `c644907`: JVM unit tests, source contracts, lint, debug APK assembly, minified release APK metadata, package/version/signer checks, artifact upload and CodeQL. The release APK is unsigned without permanent production signing secrets. No physical-device behavior is claimed from CI.
