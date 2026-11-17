# Batch Three — Phases 10–13

This document records the implementation scope and integration boundaries for Batch Three. It does not mark the batch complete until the exact final commit passes the repository's required Android build, unit-test, lint, packaging, and security workflows.

## Phase 10 — Notification Shield & Controls

- Protection-monitor notification text and accent color are derived from the persisted `ProtectionStore` mode.
- The alarm notification uses the danger palette, expanded text, a shield small icon, app artwork, and an action that opens owner verification.
- Notification actions do not authenticate, disarm, or clear a latched alarm.
- The Quick Settings tile is a read-only state projection. Tapping it opens the app; it cannot toggle protection or bypass owner authentication.
- API 34+ uses `TileService.startActivityAndCollapse(PendingIntent)`; API 26–33 retain the compatible Intent overload behind a narrowly scoped lint suppression.
- Tile status distinguishes an armed state from a monitor runtime that is not confirmed.

## Phase 11 — Home Screen Widgets

- Seven provider entries: Protection, Protection Status, Shield Status, Protection Overview, Charger Countdown, Alarm Status, and Monitor Status.
- Small, medium, and large RemoteViews layouts share the same authoritative persisted protection-state projection.
- Countdown widgets show remaining seconds; armed widgets distinguish enabled preferences from a confirmed running monitor.
- Widgets are read-only. Tapping opens the app; no widget action can disarm or silence an alarm.
- State integrity and persistence failures produce a review/stale indication.
- Updates are requested from the monitor/alarm service state-refresh paths and the foreground dashboard refresh loop.

## Phase 12 — Appearance Studio / Premium Graphics

- 120 themes: the existing 16 curated palettes plus 104 generated spectrum palettes.
- Palette unit tests check uniqueness, 60/60 dark/light distribution, opacity, and WCAG AA contrast for text, buttons, accents, and status colors.
- 15 distinct in-app shield/brand marks, 8 background treatments, and 5 button interaction animations.
- Five built-in alarm-tone presets reuse the existing Android ToneGenerator-backed alarm implementation.
- Theme, mark, background, animation, sound, reduced-motion, and intro audio/haptic selections are persisted locally.
- Appearance controls do not alter protection state, alarm latching, or owner authentication.
- Custom owner-selected alarm audio remains higher priority than a built-in preset; the UI discloses this and links to the existing Sounds / Flash controls for preview and management.

## Phase 13 — Seismic Genesis Intro

- First-launch-only cinematic sequence runs before the existing Privacy Notice & Terms Acceptance screen. Replay remains available from Appearance Studio.
- **Duration:** approximately 9 seconds, with four choreographed stages:
  1. **Core Emergence:** pitch-black `#000000`; the original ThiefAlertSeQure icon emerges from a microscopic center point and scales from 0 to 1 with spring/overshoot motion.
  2. **Seismic Impact:** `Developed By:` drops from the top in light red `#FF5252`; the impact triggers a 300 ms left-right seismic shake, concrete fractures/cracks, and particle-dust disintegration of the icon.
  3. **Architect Reveal:** `Alinor Pundamdag` drops next with a concrete/cement-block texture. The strongest quake shatters the `Developed By:` title; fractures reveal dark gray `#121212` behind the original black. The developer name remains intact and receives an infinite per-letter transition through 20 light colors, advancing at one-second intervals.
  4. **Final Collapse:** a final shake sends the intact name toward the bottom while dark-gray cracks and digital-wind particles exit into the existing Privacy Notice & Terms Acceptance flow.
- Implemented with Jetpack Compose UI, Canvas drawing, Path-based fracture geometry, matrix-backed translation/rotation for particle transforms, and responsive device sizing.
- **There is no Skip Intro button or skip path.** Back navigation is consumed while the first-launch cinematic is active; it cannot silently bypass the intro or privacy acknowledgement.
- Existing reduced-motion preference receives a short, static fallback. Existing optional audio/haptic preferences remain opt-in and do not gate the visual sequence.
- First-launch completion is committed before navigating to the existing privacy-acceptance flow. A failed completion write is retried and never falsely marks the intro complete.
- The intro is deferred while protection is armed, counting down, or alarm-triggered. It must never delay an active protection countdown or owner authentication. If an alarm is already latched before privacy acknowledgement, the owner-authentication dashboard takes priority; the privacy notice remains mandatory for normal use but cannot cover alarm recovery.

## Cross-phase integration and invariants

- Protection state remains owned by `ProtectionStore`; widgets, notifications, and Quick Settings are projections, not independent state stores.
- Appearance preferences use `SafePreferences` for typed reads and synchronous commits for user-selected values.
- No new backend, cloud account, telemetry, or sensitive permission is introduced by Batch Three.
- Preserve the 10-second charger countdown, 8-second trusted-Bluetooth grace period, and mandatory Android owner authentication before disarming a latched alarm.
- No instrumentation tests, emulator tests, physical-device tests, or GitHub Codespaces are part of this non-physical-device implementation pass.

## Verification gate

The initial Batch Three CI candidates exposed two correctable issues: typed preference reads outside `SafePreferences`, then the deprecated Quick Settings Intent overload. Both were corrected in follow-up commits. The current branch must still pass all required checks on its final head before Batch Three is called complete or handed over for user testing.

## Batch Four — preserved sequence

- **Phase 14:** About, Version, Legal and Privacy.
- **Phase 15:** Integration, Quality and Release Readiness.
- **Phase 16:** Local Tamper History and Final Verification.

Only the Batch Four phase names and high-level objectives have been recovered. Its detailed acceptance criteria must be taken from the original agreed plan before implementation; they are intentionally not invented here.
