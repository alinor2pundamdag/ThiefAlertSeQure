# v1.1.4 UX/UI and interaction audit

Status: active development audit; not a release certification.

## Verified baseline
- Branch: `feature/persistent-themes-and-setup-status`
- Baseline commit: `1cfa2696be72b7fe40fe078f4be05d1304e2c227`
- GitHub Actions run #1072 completed successfully on that exact commit.
- Unit tests, internal navigation/dialog checks, Android lint, asset validation, workflow YAML validation, APK assembly, package/version/signer checks, and artifact upload passed.
- No physical-device test has been performed by this audit.

## Implemented interaction contracts
- Theme selection remains a local preview until Apply; Cancel does not persist the selection.
- Theme preview represents dashboard, settings, setup readiness, and confirmation-dialog surfaces.
- Preview sample controls are visual-only and explicitly identified as non-interactive.
- Main, Bluetooth capability, Device Intelligence, and setup-status action buttons enforce at least 48dp minimum touch height.
- Dark/light filters remain tappable, visibly indicate selection, and use 48dp minimum height.
- All 16 palettes have JVM WCAG AA checks for primary/muted text on background and surface, button text on primary, primary accent on surface, and success/warning/danger status labels on surfaces.
- Trusted Bluetooth registry supports multiple locally registered accessories, per-device connection baselines, and independent 8-second disconnect grace timers.
- Unregister confirmation states that only app trust is removed; Android pairing is not modified.
- A2 alarm gates require enabled feature, trusted address, fresh per-device connection baseline, unchanged registry revision, continued disconnection, and ARMED protection state.
- Service restart clears Bluetooth baselines; a fresh connection event is required before a later disconnect can trigger A2.
- Charger unplug countdown remains 10 seconds; A2 grace remains 8 seconds.
- Setup status distinguishes verified, action-required, unsupported/neutral, and unknown states; action labels identify their destination and external Settings routes use ordered fallbacks with visible failure feedback.
- Multi-device A2 readiness is not marked verified until every registered accessory has a fresh connection baseline.
- Full-screen intent permission is optional while the owner-disabled full-screen dashboard is OFF; setup status requests it only after explicit opt-in.
- Bluetooth Connect permission and adapter availability are neutral while optional A2 is OFF; they become actionable prerequisites only after the owner enables A2.
- Setup status displays action buttons only for action-required or unconfirmed states; neutral, unsupported, and verified items do not show misleading action controls.
- Material dialogs in audited screens use the shared themed-dialog helper.
- CI statically checks manifest Activity declarations, internal Activity targets, themed Material dialogs, obvious empty click listeners, theme-preview/inset contracts, safe setup-action fallbacks, Bluetooth/Device Intelligence Settings fallbacks, and A2 readiness/timer invalidation contracts.

## Security invariants
- Owner authentication remains Android Keyguard / supported strong biometric verification; app code does not collect or store the device credential.
- Alarm state remains latched until the existing authenticated stop flow completes.
- Charger transition debounce and the 10-second countdown are not changed by this UX work.
- A2 does not pair/unpair devices, continuously scan, or modify Android Bluetooth settings.
- Root remains optional and diagnostic-only.
- No new permissions or background services are introduced by the theme preview.
- Production package ID, signing configuration, minSdk, and release version remain unchanged.

## Remaining audit and verification gates
- Review every click listener and settings route in every Activity, including fallback behavior when an Android Settings intent is unavailable.
- Verify Activity recreation, theme persistence, and preview cancellation paths.
- Verify Bluetooth unregister, reconnect, duplicate registration, two-device concurrency, and stale callback invalidation.
- Verify charger alarm, alarm state transitions, and authentication behavior with dedicated regression tests.
- Verify TalkBack semantics, focus order, large-font scaling, and touch targets on a real device.
- Verify notification, exact-alarm, full-screen-intent, foreground-service and battery-management behavior on HONOR 200 Pro.
- Verify OEM background restrictions do not suppress the monitor or delay alarm delivery on the target device.

## Physical-device checklist — pending
- HONOR 200 Pro: Android version, permission flows, Bluetooth adapter off/on, paired accessory connect/disconnect.
- Confirm A2 8-second behavior, reconnect cancellation, and independent monitoring of two trusted accessories.
- Confirm unregister does not remove Android pairing.
- Confirm theme selection survives process death and Cancel preserves the prior selection.
- Confirm TalkBack, font scaling, contrast, navigation and dialogs.
- Confirm charger countdown, alarm playback, flashlight preference and owner-authentication recovery.
- Confirm backgrounding, screen lock, reboot, and manufacturer battery-management behavior.
