# Repository-Wide Engineering Audit

**Audit status:** In progress — not a release certification  
**Working branch:** `fix/completion-hardening`  
**Baseline reviewed:** `feature/persistent-themes-and-setup-status` at `6c13531089ec1fbcb026068ffa786bc748048d47`  
**Scope exclusion:** Google Play listing, store eligibility, and Play Store hardware filtering are intentionally excluded by the owner.

## Audit standard

A feature is not marked complete because a button exists, a preference is saved, a CI build passes, or a device reports a capability. Completion requires:
1. A real user action and a correct destination/result.
2. A verified state transition with explicit unknown/error handling.
3. Regression coverage for security-sensitive behavior.
4. Android runtime validation on supported API levels and the target HONOR device.
5. Documentation that distinguishes implemented, partial, unavailable, and unverified behavior.

## Repository inventory

At the reviewed baseline, the repository tree contained 126 files, including 44 production Kotlin source files and 19 JVM unit-test files. There were no Android instrumentation tests on the feature branch baseline. The repository includes app source/resources, Gradle configuration, CI/release workflows, verification scripts, and feature/security documentation.

## Changes applied on this working branch

| Finding | Severity | Change | Verification still required |
|---|---|---|---|
| A charger unplug could be rejected after the initial debounce if Android's live power state was delayed/unknown | High | Persist the original OS disconnect event and retry only after a later battery snapshot confirms disconnected state. A battery snapshot alone cannot create a disconnect transition. | Unit/CI result and physical HONOR 200 Pro timing tests |
| Retry work could be spawned for every battery update | Medium | Added a cheap pending-event guard and a single in-flight retry guard. | CI and device load/behavior test |
| Foreground monitor startup failure could leave the enabled preference true | High | Service failure clears enabled/running state; synchronous Settings launch failures roll back the corresponding toggles. | CI and induced Android FGS-start failure test |
| Alarm recovery notification could remain after successful owner authentication/alarm service recovery | Medium | Added explicit cancellation on authenticated disarm and successful alarm FGS startup. | Notification permission/API-level device test |
| Corrupt/wrong-type local preferences could crash A2/history/audio reads | Medium | Added defensive reads and safe fallback/removal for malformed local values. | Corrupt-preference instrumentation tests |
| Trusted Bluetooth registry accepted arbitrary address strings | Medium | Added MAC-address format validation at registry input/read boundary. | Unit tests and paired-device test |
| Theme persistence failure had no user feedback | Low | Apply now reports save failure and preserves prior selected theme. | UI test |
| Theme palette contrast had no regression gate | Medium | Added WCAG contrast calculations for text, muted, primary, semantic colors, and button foreground across all 16 palettes. | CI result and visual/TalkBack verification |
| Fix/security branches did not trigger Android CI | Medium | Added `fix/**` and `security/**` to Android workflow push filters. | Confirm latest workflow run |

## Remaining audit work

### P0 — Protection and alarm reliability
- Prove the charger event retry under delayed, stale, unknown, and contradictory battery broadcasts.
- Verify event ordering, stale generation rejection, reboot reconciliation, and the 10-second deadline.
- Verify alarm latch, foreground-service recovery, notification fallback, and authenticated stop under Android process death.
- Verify 8-second A2 grace, reconnect cancellation, multiple trusted devices, unregister invalidation, and service restart baseline reset.
- Preserve alarm/authentication invariants; no unauthenticated path may disarm a latched alarm.

### P1 — Local security and data integrity
- Encrypt trusted Bluetooth identifiers at rest with Android Keystore and test migration from existing plaintext registry values without losing registered devices.
- Test corrupted, wrong-type, missing, revoked, and inaccessible preference/URI data.
- Review every SharedPreferences read/write for type mismatch, commit failure, stale values, and migration atomicity.
- Review update download/install flow, signature continuity, rollback/failure behavior, and production data preservation.
- Review exported components, PendingIntent mutability, broadcast trust boundaries, backup policy, and sensitive log contents.

### P1 — UI actions and state accuracy
- Exercise every button, toggle, dialog choice, permission result, settings deep link, and back/navigation path.
- Ensure toggles reflect confirmed service/runtime state, not only a stored preference.
- Verify all screens, dialogs, empty/error/permission-denied states, and notification surfaces under all 16 themes.
- Verify minimum 48dp touch targets, TalkBack labels/order, keyboard/focus behavior, large font scaling, and contrast in context.
- Confirm no decorative or capability-catalog action falsely implies an implemented hardware workflow.

### P1 — Android compatibility and instrumentation
- Add Android instrumentation for preferences, migration, Android Keystore, notification cancellation, service startup failure, permission denial, and upgrade preservation.
- Run supported API-level instrumentation. Diagnose emulator/ADB startup failures separately from app test failures.
- Test on the HONOR 200 Pro: cold start, background/foreground, screen off, battery optimization, charger connect/disconnect, Bluetooth separation/reconnect, reboot, alarm audio/vibration, owner authentication, and recovery.
- Verify an in-place update using the actual permanent production signing identity and existing user data. Never use a debug APK to update the production install.

### P2 — UX and documentation
- Finish full Activity/dialog/accessibility review and responsive layout testing.
- Update README install/package instructions and remove stale version/package references.
- Reconcile feature-completion and requirement-traceability documents with actual implementation and test evidence.
- Keep catalog-only Bluetooth capabilities clearly labelled as hardware support, not completed app functionality.

## Security and compatibility invariants

- Production application ID remains `com.thiefalertsequre.app`.
- Production signing identity must remain unchanged.
- `minSdk 26`, compile/target SDK 36 remain unchanged.
- Production release version remains v1.1.3 until an approved release plan changes it.
- Development test package remains isolated from production.
- Charger countdown remains 10 seconds.
- A2 disconnect grace remains 8 seconds.
- Root remains optional.
- No user-data wipe, force reinstall, silent unpairing, new cloud dependency, or release/merge without approval.

## Verification record

Do not mark any check PASS unless the exact commit, workflow, test output, and (where required) device evidence are recorded here. CI success proves only the checks executed by that workflow. It does not prove physical charger/Bluetooth behavior, OEM background survival, TalkBack quality, or production signing-key upgrade compatibility.

| Check | Status |
|---|---|
| Repository/static source inventory | Reviewed at baseline; follow-up edits require re-review |
| JVM unit tests on current working HEAD | Pending CI |
| Android lint/build/APK metadata | Pending CI |
| Android instrumentation | Not yet complete |
| HONOR 200 Pro physical validation | Pending owner device test |
| Production-signing in-place upgrade | Pending; requires production signing identity |
| Release readiness | Not certified |

**Release decision:** keep this branch in development. Do not merge or publish based on this audit document alone.
