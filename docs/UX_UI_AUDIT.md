# v1.1.4 UX/UI and interaction audit

Status: development audit; not a release certification.

## Verified repository baseline
- Branch: `feature/persistent-themes-and-setup-status`
- Baseline commit: `53651288e86f9d2561cee0b34fac29b282373e58`
- GitHub Actions run #1064 completed successfully on that exact commit.
- No physical-device test has been performed by this audit.

## Findings confirmed before this change
| Severity | Area | Finding |
|---|---|---|
| High | Bluetooth A2 | Only one trusted Bluetooth address was stored; no app-level unregister flow existed. |
| High | Bluetooth A2 | A2 state and connection baseline were represented by single-device preferences. |
| Medium | Themes | Material dialogs use separate windows and are not recolored by the Activity view-tree theme pass. |
| Medium | Themes | Theme preview is a sample card, not a full-screen preview of all screens. |
| Medium | Accessibility | Contrast, TalkBack order, touch target sizing, and large-font behavior have not been device-audited. |
| High | Device validation | HONOR 200 Pro behavior, OEM background restrictions, Bluetooth ACL delivery, and alarm presentation remain unverified on hardware. |

## Changes in this commit
- Add a local multi-device trusted Bluetooth registry with legacy single-device migration.
- Add WCAG AA contrast regression tests for primary text, muted text, and button labels across all 16 palettes.
- Clear per-device connection baselines when A2 is disabled or re-enabled.
- Add per-device connection baselines and per-device 8-second disconnect timers.
- Add explicit confirmation before unregistering an accessory.
- Unregister affects only the app trust registry; it never unpairs Android Bluetooth devices.
- Removing the final trusted accessory automatically disables A2.
- Pending disconnect callbacks are invalidated when the registry changes, including A2 disable/re-enable and service restart.
- Setup status now checks registered-device baselines rather than the legacy single-device baseline flag.
- A2 setup readiness distinguishes disabled monitor, unconfirmed service, missing baseline, permission failure, adapter failure, and verified readiness.
- Setup action labels describe their actual destinations; the monitor action opens monitor settings instead of generic Android app details.
- Add JVM tests for registry encoding, duplicate handling, malformed records, and empty state.

## Security invariants
- Charger unplug countdown remains 10 seconds.
- A2 disconnect grace remains 8 seconds.
- Existing alarm state machine, authentication flow, and alarm presentation are not intentionally changed.
- Root remains optional.
- No new Android permissions or background services are introduced.
- Production package ID, signing configuration, minSdk, and release version remain unchanged.

## Required verification before phone testing
- Run unit tests and Android lint on the new commit.
- Theme hex parsing is pure Kotlin so JVM tests exercise the exact palette RGB values; contrast calculations use bit extraction rather than Android SDK color helpers.
- Inspect merged manifest and debug APK package/version/signer metadata.
- Verify no A2 receiver path can alarm for an unregistered address.
- Verify reconnect cancels only the matching device's pending timer.
- Verify unregister during the grace period prevents that device from triggering an alarm.
- Verify a second registered device remains independently monitored.
- Verify service restart requires fresh ACL_CONNECTED baseline events.
- Verify all dialogs, all 16 themes, accessibility, navigation, and every settings action.

## Physical-device checklist — pending
- HONOR 200 Pro: Android version, Bluetooth permission flow, adapter off/on, paired accessory connect/disconnect.
- Confirm 8-second A2 behavior and reconnect cancellation.
- Confirm unregister does not remove Android pairing.
- Confirm app survives backgrounding, screen lock, and manufacturer battery-management behavior.
- Confirm charger countdown and authentication regression behavior on hardware.
