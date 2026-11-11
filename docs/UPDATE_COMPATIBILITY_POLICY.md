# Update Compatibility Policy

This policy applies to every future ThiefAlertSeQure update, beginning with the v1.1.4 development line. It is a release requirement, not a promise that every old Android device can support every future feature.

## Non-negotiable compatibility invariants

1. **Application identity:** Keep applicationId `com.thiefalertsequre.app`. Do not change it for ordinary updates.
2. **Signing identity:** Sign every upgrade APK with the existing permanent release key. The pinned certificate SHA-256 is `9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64`. Never generate or substitute a new key for an update.
3. **Monotonic versioning:** Increase versionCode for each published release. Never reuse or decrease a published versionCode.
4. **Existing user data:** Preserve existing preference names, schemas, files, event history and user-selected alarm audio. Any schema change requires an explicit, tested, crash-safe migration.
5. **No forced reset:** Do not require uninstall/reinstall, clear app data, or silently reset protection settings as a normal update path.
6. **Alarm safety:** Preserve charger detection, countdown/reconnect cancellation, latched alarm, owner authentication, audio-volume restoration, optional flashlight independence, boot recovery and notification behavior.
7. **Permissions and privacy:** Do not add a permission, network destination, telemetry, analytics, advertising SDK or data upload without a documented need and owner approval.
8. **Android compatibility:** Preserve minSdk 26 unless the owner explicitly approves dropping older supported Android versions. Test behavior across supported API levels and relevant OEM restrictions.
9. **Update trust:** Verify package identity, version policy, APK digest and pinned signing certificate before offering an update. A digest alone is not a signature.
10. **No release without approval:** Development, CI and test artifacts may be produced on the hardening branch. Do not create a v1.1.4 release tag or publish a v1.1.4 APK until the owner says feature work is complete and explicitly authorizes release.

## Compatibility test gate for every update

- Install the new APK over the oldest supported installed release available for testing, without uninstalling.
- Confirm Android accepts the upgrade using the same application ID and signing certificate.
- Verify existing settings, alarm sound selection, event history and protection state survive.
- Test upgrade while DISARMED, ARMED, COUNTDOWN and ALARM_TRIGGERED where safely reproducible.
- Re-test charger disconnect/reconnect, authentication, alarm persistence, volume restoration, flashlight failure isolation, reboot recovery and lock-screen/notification behavior.
- Confirm update download validation rejects wrong package IDs, wrong certificates, corrupted APKs and prohibited downgrades.
- Run unit tests, lint, minified release build, APK metadata/signature verification and regression tests.
- Record device model, Android version, OEM build, test result and any limitation.

## Known historical migration exception

The v1.1.2 release used a different signing certificate, and its original private key is unavailable. That specific installation cannot be updated in place to the v1.1.3 signing identity. Do not conceal this limitation or claim compatibility with v1.1.2. The v1.1.3 permanent certificate is the continuity anchor for all future upgrades.

## Security hardening compatibility

R8/ProGuard obfuscation is a build-time transformation and does not change installed package identity or signing certificate. Reflection, resource lookup, manifest component names and serialization can still be affected by shrinker rules. A minified build must pass regression tests before it is considered upgrade-safe.

Encryption of existing local data is a separate schema migration. Do not switch current SharedPreferences or files to encrypted storage in place until a tested migration can read old data, write encrypted data, recover safely from interruption, and preserve alarm state. Never encrypt the release signing key into the APK; it belongs only in protected GitHub Actions secrets.

## Owner-controlled release approval

Passing CI does not authorize publication. Only explicit owner approval after feature freeze authorizes the official GitHub release workflow.
