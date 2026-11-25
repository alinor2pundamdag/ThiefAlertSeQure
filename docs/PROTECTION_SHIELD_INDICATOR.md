# Live Protection Shield

The dashboard shield is a visual projection of the existing persisted protection state. It does not arm, disarm, start the monitor, change a countdown, or trigger the alarm.

| State | Visual | Meaning |
|---|---|---|
| DISARMED | Neutral shield | Ready, but not currently armed |
| ARMED + monitor enabled and running | Green shield with check | Charger protection is armed and the foreground monitor is confirmed running |
| ARMED + monitor not confirmed running | Neutral shield | State says armed, but the app does not claim active protection until monitor readiness is confirmed |
| COUNTDOWN | Yellow shield with clock | Confirmed charger-removal countdown is active; reconnecting power cancels it through the existing protection engine |
| ALARM_TRIGGERED | Red shield with hooded-thief mark | Existing latched alarm state is active |

The shield is 68 dp, centered near the top of the live-status card. This approximates a small coin-sized dashboard emblem while remaining scalable with Android density and accessibility scaling. The thief motif appears only for the alarm state.

Implementation notes:
- `ProtectionShieldView` draws its icon locally with Android Canvas; it introduces no image/network dependency.
- `MainActivity.render()` refreshes it from `ProtectionStore` and the monitor's enabled/running state.
- It is informational only; all protection transitions remain owned by the existing charging coordinator, protection engine, monitor service, alarm service, and owner-authentication flow.
- A green shield is withheld if the foreground monitor is not confirmed running.
- This UI has not yet been physically verified on the owner's HONOR 200 Pro. Emulator/CI success cannot verify the visual scale, color appearance, or live charger transitions on real hardware.
