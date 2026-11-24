# Live Protection Shield

**Current candidate status (24 November 2026):** Implemented in the v1.1.5 candidate. The shield silhouette remains a stable brand-blue color; the state glyph and adjacent status text carry the live state. Physical visual acceptance remains pending.

The dashboard shield is a visual projection of the existing persisted protection state. It does not arm, disarm, start the monitor, change a countdown, or trigger the alarm.

| State | Inner glyph / status color | Meaning |
|---|---|---|
| DISARMED | Muted dot | Ready, but not currently armed |
| ARMED + monitor enabled and running | Green check | Charger protection is armed and the foreground monitor is confirmed running |
| ARMED + monitor not confirmed running | Muted dot | State says armed, but the app does not claim active protection until monitor readiness is confirmed |
| COUNTDOWN | Amber clock | Confirmed charger-removal countdown is active; reconnecting power cancels it through the existing protection engine |
| ALARM_TRIGGERED | Red hooded-thief mark | Existing latched alarm state is active |

The shield is 68 dp, centered near the top of the live-status card. The shield silhouette and outline retain one stable brand-blue color in every state; the inner glyph and adjacent status text communicate the live state. The thief motif appears only for the alarm state.

Implementation notes:
- `ProtectionShieldView` draws its icon locally with Android Canvas; it introduces no image/network dependency.
- `MainActivity.render()` refreshes it from `ProtectionStore` and the monitor's enabled/running state.
- It is informational only; all protection transitions remain owned by the existing charging coordinator, protection engine, monitor service, alarm service, and owner-authentication flow.
- A green shield is withheld if the foreground monitor is not confirmed running.
- This UI has not yet been physically verified on the owner's target handset for the v1.1.5 candidate. Emulator/CI success cannot verify visual scale, color appearance, accessibility scaling, or live charger transitions on real hardware.
