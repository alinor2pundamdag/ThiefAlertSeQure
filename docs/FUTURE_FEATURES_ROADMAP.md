# Future Features Roadmap

Status: A1–A7 remain in the future-feature roadmap. A2 has been selected for implementation work now, but is not assigned to v1.1.4 or any other release. All release placement remains the owner's decision.

## Scope decision

Keep future work small and independently testable. Add only features that solve a verified theft-protection problem and preserve existing alarm behavior. This document is not a release commitment.

## Proposed anti-theft features

### A1 — Smart Movement Anti-Theft Protection
- Owner manually arms a movement sensor mode before leaving the phone unattended.
- Detect a meaningful pickup or movement pattern using the accelerometer; use other sensors only when available and justified.
- Start a configurable grace period, then use the existing authentication and alarm flow.
- Provide sensitivity calibration and a short arming-stabilization period to reduce false alarms.
- Do not run sensors continuously while disarmed. Do not claim it can distinguish an owner from a thief.

### A2 — Trusted Bluetooth Device Separation Alert
Implementation status: in progress on the development branch; release version unassigned.
- Optionally pair a trusted watch, earbuds, or other Bluetooth device.
- Warn when the trusted device disconnects while anti-theft protection is armed.
- Treat Bluetooth range and disconnect events as approximate; avoid claiming precise distance or guaranteed background detection.
- No Bluetooth scanning when this feature is disabled.

### A3 — Last Known Location on Owner Request
- Let the owner explicitly save or view the phone's last known location while protection is armed.
- Require clear location permission and visible user control; show timestamp and location accuracy.
- Keep location local by default. Any remote access or cloud storage would need a separate security/privacy design and explicit approval.
- Do not promise tracking when the phone is offline, powered off, denied location access, or out of battery.

### A4 — SIM / Network State Change Warning
- Explore a warning when the SIM or mobile-network state changes while protection is armed, where Android version and permission rules allow reliable detection.
- Explain limitations and do not request broad telephony permissions without a demonstrated need.
- Do not treat temporary signal loss or airplane mode as proof of theft.

### A5 — Unauthorized Access / Failed Unlock Awareness
- Explore whether Android exposes reliable, permitted signals for repeated failed unlock attempts to an ordinary app.
- If the platform does not expose them, do not use accessibility abuse, device-admin tricks, or covert collection as a workaround.
- Do not capture passwords, PINs, or biometric data.

### A6 — Physical Tamper / Port Disconnect Alert
- Consider detecting selected physical state changes, such as USB power/data connection changes, only when the device and Android version expose reliable events.
- Make the trigger configurable and avoid duplicate alarms with charger-removal protection already implemented.
- Never claim that an ordinary app can prevent a thief from powering off or physically damaging the phone.

### A7 — Owner-Triggered Lost Mode
- A clearly initiated mode that increases alert readiness and displays owner-defined recovery instructions on the lock screen where Android permits.
- Must not expose private owner information or bypass Android lock-screen security.
- Any remote activation would require a separately reviewed, authenticated command channel; no unauthenticated remote commands.

## Other small future maintenance features

### D1 — Manual Diagnostic Snapshot
- User-triggered local health screen showing app/device version, protection state, relevant permission status, and component health.
- No continuous polling, analytics, or network upload.

### D2 — Exportable Debug Report
- Owner previews and explicitly exports a redacted report containing timestamps, versions, component status, and non-sensitive error codes.
- No automatic email, cloud upload, or telemetry.

### D3 — Permission and Battery-Restriction Guidance
- Explain only the permission or system restriction relevant to a feature.
- Link to the appropriate Android settings screen when supported; never silently change settings.

### D4 — Safe Recovery for Verified Failures
- Add a bounded recovery action only after a documented, reproducible failure is found.
- Never clear protection state, interrupt a countdown/alarm, or create restart loops.

### D5 — Update Integrity Status
- Show installed version and the result of package, version, digest, and signer checks.
- Never install or downgrade automatically.

## Design and safety constraints

- No feature is assigned to a release until the owner explicitly chooses it.
- Preserve applicationId, signing certificate, monotonic versionCode, minSdk, existing settings, user data, and alarm behavior.
- No covert camera, microphone, or personal-data collection.
- No hidden persistence, invasive permissions, or always-on sensor polling when protection is disarmed.
- No remote lock, wipe, or remote command claims unless a separately reviewed design proves the required Android capabilities and threat model.
- Diagnostics and new sensors must never stop, delay, silence, or reset an active alarm.
- Keep core theft-alert operation available offline.
- Test normal, false-positive, failure, reboot, battery, and upgrade-over-install behavior where applicable.

## Not approved / deferred

A comprehensive autonomous self-healing engine, continuous cloud diagnostics, covert evidence capture, automatic configuration changes, and broad tamper-response logic are not approved. Revisit only if the owner requests them and a safe, technically supported design is available.
