# Future Features Roadmap

Status: planning only. This roadmap does not authorize a v1.1.4 release or commit the project to every item.

## Scope decision

Do not build a large, always-running self-repair system. Future work will be split into small, independently testable features. Add only features that solve a verified problem and preserve the existing alarm behavior.

## Prioritized small features

### F1 — Manual Diagnostic Snapshot
- Add a user-triggered diagnostic screen.
- Show app version, Android version, protection state, required permission status, and whether the app's monitoring components report healthy.
- Display checks locally; no background polling, analytics, or network upload.
- Never expose secrets, authentication material, private file paths, or personal data.

### F2 — Exportable Debug Report
- Let the owner explicitly export a redacted text report.
- Include timestamps, app/device version, relevant component status, and non-sensitive error codes.
- Preview the report before sharing. Export only after an explicit user action.
- No automatic email, cloud upload, or telemetry.

### F3 — Permission and Battery-Restriction Guidance
- Detect only the specific permission or system restriction relevant to a failed feature.
- Explain the impact and provide a link to the appropriate Android settings screen when supported.
- Do not change settings or request new permissions automatically.

### F4 — Safe Recovery for Verified Failures
- Add recovery actions only for a documented, reproducible failure.
- Recovery must be bounded, avoid restart loops, and never clear protection state or interrupt an active countdown/alarm.
- Show what action was taken and whether the health check passed afterward.

### F5 — Update Integrity Status
- Show the installed version and report whether downloaded update metadata/APK checks pass the existing package, version, digest, and signer validation.
- Do not install or downgrade automatically.

## Implementation order

1. F1 manual diagnostic snapshot.
2. F2 user-controlled redacted report.
3. F3 targeted permission/restriction guidance.
4. F4 narrowly scoped recovery, only after a real failure is reproduced.
5. F5 update integrity status, aligned with the update-channel audit.

## Mandatory constraints

- Keep applicationId, release signing certificate, version monotonicity, minSdk, existing settings, user data, and alarm behavior compatible.
- No always-running watchdog, broad background polling, invasive permissions, hidden persistence, or automatic network reporting.
- Diagnostics must not stop, delay, silence, or reset an active alarm.
- Never silently modify settings or protection state.
- Keep core theft-alert operation available offline.
- Every feature needs tests for normal, failure, reboot, and upgrade-over-install paths where applicable.
- No v1.1.4 tag or public release until the owner finishes feature additions and explicitly approves publication.

## Deferred / not approved

A comprehensive autonomous self-healing engine, continuous device monitoring, cloud diagnostics, automatic configuration changes, and broad tamper-response logic are intentionally out of scope unless a concrete requirement is later approved.
