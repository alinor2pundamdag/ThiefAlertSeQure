# v1.1.4 UX/UI and interaction audit

Status: source-level UX/UI implementation and CI audit passed on the recorded development HEAD; physical-device acceptance remains pending. This is not a release certification.

## Verified baseline
- Branch: `feature/persistent-themes-and-setup-status`
- Verified HEAD: `a0c739471ad08740b2c33921ecc6f03a5096dc8e`
- GitHub Actions run [#1090](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43192440662) completed successfully on that exact commit.
- Unit tests, internal navigation/dialog checks, Android lint, asset validation, workflow YAML validation, APK assembly, package/version/signer checks, and artifact upload passed.
- The successful run produced the `ThiefAlertSeQure-debug-apk` artifact (ID `13931621964`; development-only test package).
- No physical-device test has been performed by this audit.

## Implemented interaction contracts
- Theme selection remains a local preview until Apply; Cancel does not persist the selection.
- Theme preview represents dashboard, settings, setup readiness, and confirmation-dialog surfaces.
- Preview sample controls are visual-only and explicitly identified as non-interactive.
- Main, Settings, Bluetooth capability, Device Intelligence, Theme, and setup-status action buttons enforce at least 48dp minimum touch height.
- All six app screens apply system-bar and display-cutout insets for edge-to-edge devices and reapply the saved/preview theme when returning to the screen.
- Dark/light filters remain tappable, visibly indicate selection, and use 48dp minimum height.
- All 16 palettes have JVM WCAG AA checks for primary/muted text on background and surface, button text on primary, primary accent on surface, and success/warning/danger status labels on surfaces.
- Trusted Bluetooth registry supports multiple locally registered accessories, per-device connection baselines, and independent 8-second disconnect grace timers.
- Unregister confirmation states that only app trust is removed; Android pairing is not modified.
- A2 alarm gates require enabled feature, trusted address, fresh per-device connection baseline, unchanged registry revision, continued disconnection, and ARMED protection state.
- Service restart clears Bluetooth baselines; a fresh connection event is required before a later disconnect can trigger A2.
- Charger unplug countdown remains 10 seconds; A2 grace remains 8 seconds.
- Setup status distinguishes verified, action-required, unsupported/neutral, and unknown states; action labels identify their destination and external Settings routes use ordered fallbacks with visible failure feedback.
- Multi-device A2 readiness is not marked verified until every registered accessory has a fresh connection baseline.
- Full-screen intent permission is optional while the owner-disabled full-screen dashboard is OFF; setup status requests it only after explicit opt-in. If the owner enables the dashboard without Android 14+ access, the Settings toggle explicitly labels the missing system access and offers the correct settings route.
- Bluetooth Connect permission and adapter availability are neutral while optional A2 is OFF; they become actionable prerequisites only after the owner enables A2.
- Device Intelligence labels optional A2, full-screen dashboard, exact-alarm scheduling, and API 36 ranging requirements according to whether their dependent feature is enabled.
- Bluetooth Center separates the monitor preference from a confirmed running service state and does not claim accessory monitoring when the service is not confirmed running.
- Setup status displays action buttons only for action-required or unconfirmed states; neutral, unsupported, and verified items do not show misleading action controls.
- Material dialogs in audited screens use the shared themed-dialog helper.
- External Android Settings, battery-optimization, unknown-source install-access, and package-installer launches use a centralized guarded launcher with ordered fallbacks and visible failure feedback.
- Explicit device-credential launch failure is handled without clearing or stopping the latched alarm; the failure is recorded locally and owner authentication can be retried.
- CI statically checks manifest Activity declarations, internal Activity targets, themed Material dialogs, obvious empty click listeners, theme-preview/inset/reapply contracts, safe setup-action fallbacks, external Settings/installer launch guards, credential-launch failure handling, Bluetooth/Device Intelligence Settings fallbacks, and A2 readiness/timer invalidation contracts.
- Charger countdown, reconnect cancellation, latched alarm state, reboot recovery, authentication throttle/cooldown, and authenticated alarm clearing have JVM regression coverage. A2 timer invalidation, unregister, reconnect, armed-state gating, and multiple-device registry encoding have JVM policy/registry coverage.

## Security invariants
- Owner authentication remains Android Keyguard / supported strong biometric verification; app code does not collect or store the device credential.
- Alarm state remains latched until the existing authenticated stop flow completes.
- Charger transition debounce and the 10-second countdown are not changed by this UX work.
- A2 does not pair/unpair devices, continuously scan, or modify Android Bluetooth settings.
- Root remains optional and diagnostic-only.
- No new permissions or background services are introduced by the theme preview.
- Production package ID, signing configuration, minSdk, and release version remain unchanged.

## Remaining device-acceptance gates
- Verify Activity recreation, theme persistence after process death, and preview cancellation on device.
- Verify Bluetooth unregister, reconnect, duplicate registration, two-device concurrency, and stale callback invalidation on device.
- Verify TalkBack semantics, focus order, large-font scaling, contrast perception, and touch targets on the actual display.
- Verify notification, exact-alarm, full-screen-intent, foreground-service and battery-management behavior on HONOR 200 Pro.
- Verify OEM background restrictions do not suppress the monitor or delay alarm delivery on the target device.
- Repeat charger countdown, reconnect, alarm latch, audio/flashlight, and owner-authentication recovery tests on the target device; JVM tests do not substitute for Android hardware/system behavior.

## Physical-device checklist — pending (owner has not tested this v1.1.4 development build)
- HONOR 200 Pro: Android version, permission flows, Bluetooth adapter off/on, paired accessory connect/disconnect.
- Confirm A2 8-second behavior, reconnect cancellation, and independent monitoring of two trusted accessories.
- Confirm unregister does not remove Android pairing.
- Confirm theme selection survives process death and Cancel preserves the prior selection.
- Confirm TalkBack, font scaling, contrast, navigation and dialogs.
- Confirm charger countdown, alarm playback, flashlight preference and owner-authentication recovery.
- Confirm backgrounding, screen lock, reboot, and manufacturer battery-management behavior.
