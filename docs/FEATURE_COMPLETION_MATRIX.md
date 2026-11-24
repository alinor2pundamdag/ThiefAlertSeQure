<div align="right">
  <a href="../README.md"><img src="../ThiefAlertSeQureIcon.jpg" alt="ThiefAlertSeQure icon" width="64" /></a>
</div>

[← Back to ThiefAlertSeQure overview](../README.md)

---

# ThiefAlertSeQure — Feature Completion Matrix

> **Current release candidate (24 November 2026):** clean integration branch `release/v1.1.5-candidate`, PR #9. The prior source head `56418b68e422ded696705fd4d07d76605151d19a` passed Android CI #45307869027 and CodeQL #45307868989; later candidate changes include Bluetooth regression coverage, updater URL validation tests and CI workflow hardening. Exact-head checks are running on the latest candidate. Candidate identity: `com.thiefalertsequre.app`, `1.1.5_Developer_Alinor`, versionCode `7`, minSdk `26`, compileSdk/targetSdk `36`. The latest development artifact is ID `14881119651` (archive SHA-256 `3e34f8a8cfd5cef750a3ab31b265e4aeaaf37406bee70547d678b08ada424e99`; APK SHA-256 `c0de410c4f26616d7629fef7251991414d2feba3141527f05bfbf636bb5771d1`). The minified release APK passed metadata verification but is unsigned in PR CI; production signing availability is unverified. Physical acceptance is owner-operated after APK delivery and is not a coding/merge gate. Bluetooth capability tests/docs and updater URL allowlist tests are included; exact-head CI is running.

> **Historical v1.1.4 feature-branch assessment:** [V1.1.4 Feature Implementation Status](V1.1.4_FEATURE_IMPLEMENTATION_STATUS.md). Its 94% figure is an estimate for the earlier audited branch only; it is not a completion percentage for the current FIXALL work branch.

> **Historical predecessor-branch status (2026-11-17; PR #7, not current PR #8):** The latest completed exact-head code/documentation snapshot `e97817903976689f02cba8bb485fc160a7122fd3` passed [Android CI #44010766603](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010766603) and [CodeQL #44010774991](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010774991). The Android workflow passed the 30-file JVM unit suite, instrumentation-disabled guard, source checks, lint, asset validation, APK build/metadata/signer checks and artifact upload. This predecessor-branch result is historical only. At that historical snapshot, PR #8 code-bearing commit `c644907` has since passed Android CI and CodeQL (runs #44860111389 and #44860111390). Branch comparison after this single follow-up commit is 530 ahead / 0 behind `main`, with 131 changed paths (90 added, 41 modified, 0 deleted). PR #7 remains draft/open/unmerged. Alert #8 closure and permanent production signing-secret availability remain unverified. No merge, tag, publication or production release is claimed.

> **Historical predecessor baseline: v1.1.3 (versionCode 5).** This release line preserves the completed v1.1.2 feature set; its intended functional addition is the synchronized rear-LED flashlight during a latched alarm. No v1.1.2 protection, alarm, authentication, privacy, settings, history, or managed-device feature is intentionally removed. Flashlight availability is optional and cannot block audio.


## Batch Four development-only UX/UI and interaction work (v1.1.5 candidate)

The production baseline remains v1.1.3. The current feature branch contains the following v1.1.5 development work; it is not merged or released.

| Feature | Implementation / verification |
|---|---|
| Persistent appearance system | 120 locally persisted palettes (16 curated base palettes plus 104 generated variants; 8 curated dark and 8 curated light); live preview is isolated until Apply; Cancel preserves the selected palette |
| Cross-screen theme consistency | Main, Settings, Bluetooth Center, Device Intelligence, Theme, and Setup Status screens reapply the selected palette; app-owned Material dialogs use shared theme styling |
| Theme accessibility | JVM WCAG AA contrast assertions cover primary/muted text, button text, primary accents, and success/warning/danger status labels across all palettes |
| Theme preview | Representative dashboard, settings, setup-readiness, and confirmation-dialog surfaces; sample actions are explicitly preview-only |
| Seismic Genesis cinematic intro | Filament 3D PBR shield with procedural HDR studio IBL, 48-cell Voronoi shield fracture plus fine chips, articulated 15-bone tactical intruder rig that breaks into independent rigid-body debris, gravity/contact/floor response, directional wind, bloom/ambient occlusion/depth-of-field/dynamic resolution, and a version-gated developer signature sequence; reduced-motion fallback and lifecycle-safe native-resource teardown |
| Setup readiness | Live checks distinguish verified, action-required, neutral, unsupported, and unknown; optional Bluetooth and full-screen features do not demand permissions while disabled |
| Optional Android ranging capability check | API 36+ `RangingManager` local availability callback; no RANGING permission, peer discovery, ranging session, distance measurement or location access; JVM status-label tests |
| Setup navigation | Action buttons appear only for actionable/unconfirmed states and use destination-specific labels with guarded Android Settings fallbacks |
| Multi-accessory A2 | Local multi-device trust registry, per-device baselines/revisions, dynamically enabled manifest ACL receiver for process-death events, encrypted per-device AlarmManager deadlines, foreground Handler fast path, reconnect cancellation, authenticated-disarm/package-update invalidation epochs, and unregister-without-unpairing semantics; exact-alarm denial uses inexact idle-aware fallback and may be delayed in Doze |
| Navigation and interaction audit | CI static checks cover declared Activity destinations, obvious empty click listeners, themed dialogs, edge-to-edge insets, theme reapplication, and external launch fallbacks |
| Alarm/authentication regression protection | JVM tests cover the existing 10-second charger countdown, reconnect cancellation, latched alarm behavior, reboot recovery, authentication cooldown, and A2 disconnect gates |
| Development build | Historical v1.1.4 exact-head CI run [43950015535](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/43950015535) uploaded artifact `ThiefAlertSeQure-debug-apk` (ID `14266362844`): package `com.thiefalertsequre.app.v114test`, versionCode `6`, versionName `1.1.4-test`, minSdk `26`; APK SHA-256 `034d3cc076ca06ea3265bd468e42de8247352d3f8cf1f1882f7b4620971c641e`; CI debug certificate SHA-256 `be3def2cc431cb7000e025693cb9705496a71b90d22eda372140e5a03b562c17`. Development-only, not a production update or release |

## Batch 2 implementation additions — Phases 5–9

> **Historical Batch 2 non-physical software status:** PASS on code-bearing commit `e2c166ee37cccaa691a83eaec39fcee4231bb1e1`. Android CI [#44080775151](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44080775151) passed all 59 steps; CodeQL [#44080775264](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44080775264) passed Java/Kotlin and Actions analysis. Instrumentation, emulator, Codespaces, and physical-device testing were not run. Hardware/OEM behavior remains unverified and is not inferred from CI.

| Phase | Current implementation on this branch | Remaining boundary |
|---|---|---|
| 5 — Shutdown / managed protection | Dynamically observed orderly shutdown event; optional Device Owner Lock Task hardening; Android 14+ restriction scopes use supported APIs; restrictions and exact Lock Task features are read back before activation; rollback/emergency recovery are read back. | Standard apps cannot universally block power-menu shutdown or hardware shutdown. Actual enrolled Device Owner and OEM behavior remains unverified. Event observation is diagnostic, not shutdown prevention. |
| 6 — Alarm outputs | Seven flash patterns; five Android-generated built-in tone presets plus packaged default and custom main/secondary audio; tone preview; audio focus; packaged-audio/vibration fallback; severe-thermal flash suppression independent of alarm audio; flash fails closed if thermal monitoring or initial status is unavailable. | Tone/torch output and OEM thermal behavior still require handset verification. |
| 7 — Evidence | Bounded encrypted AES-GCM event metadata vault; owner-requested cached location and one-shot requests across every enabled GPS/network provider (20-second timeout); fix age/provider/accuracy; password-derived PBKDF2 + AES-GCM metadata backup/restore; successful owner-authentication and received biometric-failure events recorded without credential material; optional front-camera still only after successful owner authentication disarms a latched alarm while the authentication Activity is visible; lifecycle-bound cancellation on Activity stop/destroy; explicit Settings opt-in and CAMERA runtime permission; unencrypted Gallery JPEGs by default in the app-owned Pictures/ThiefAlertSeQure folder; explicit Settings option to encrypt future captures and a separate verified migration for existing app-owned Gallery photos into the Android Keystore AES-GCM photo vault; SHA-256 read-back integrity verification before any unchanged Gallery original is removed; 20-photo / 30-day / 2 MiB bounds per storage location; encrypted-vault view/export and app-scoped deletion. | Camera permission is not requested silently or at first launch. Migration is explicit, requires encryption enabled and DISARMED protection, and leaves originals in Gallery whenever verification, source recheck, deletion, or vault capacity prevents a safe move. No background camera service, continuous capture, video, microphone, or cloud upload. Android does not expose ordinary third-party apps' failed device-PIN attempts; only callbacks the app actually receives are recorded. Actual camera and location hardware behavior remains unverified. Camera failure never reverses alarm disarm. |
| 8 — Diagnostics / readiness | Readiness score explicitly labeled as setup readiness, not a security guarantee; redacted JSON diagnostics export limited to validated event codes (no free-form event details); encrypted evidence backup/restore. | No device-integrity guarantee; OEM-specific controls remain capability-dependent and unverified until tested. |
| 9 — Dashboard | 136dp fixed-color main shield remains above a compact, horizontally swipeable nine-destination tab strip: Bluetooth Settings; Device Hardening & Reliability; Sounds & Flash Alert; Authentication Evidence; Device Intelligence; Appearance Studio; Local Tamper History; More; About. Tabs retain 48dp touch targets and destination-specific deep links. | The tabs are a compact horizontal destination strip, not a full content-swiping pager; the main protection shield stays above them and remains immediately visible. |

The metadata evidence backup contains only the encrypted event/location records, not camera image files or a complete export of every app preference or Android system setting. Its separate backup password is not stored by the app. Camera photos are individually exported only after owner selection. The capture occurs from the visible authentication Activity after successful owner authentication, so the feature does not attempt to start a camera foreground service from the background. Android's official restrictions are documented at [foreground-service background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) and [Android 14 foreground-service camera requirements](https://developer.android.com/about/versions/14/changes/fgs-types-required).

## Implemented in this branch

| Feature | Implementation |
|---|---|
| Installed app name | Exact label ThiefAlertSeQure |
| Original launcher icon | Original JPG copied without image edits |
| Original alarm sound | Packaged Ogg Opus asset in res/raw, looping MediaPlayer (resource filename must use `.ogg`) |
| Audio fallback | Repeating vibration if selected and packaged audio both fail |
| Alarm volume | Saves prior alarm-stream volume, raises to device maximum during alarm, restores after successful disarm; malformed saved-volume metadata is detected and removed rather than crashing recovery (the original value cannot be reconstructed if corrupted) |
| Charger auto-arm | Runtime-registered PowerTransitionReceiver from the user-enabled foreground monitor plus persisted connected-baseline validation |
| 10-second unplug countdown | ProtectionEngine + AlarmScheduler |
| Reconnect before deadline | Returns to ARMED |
| Reconnect after trigger | Cannot clear ALARM_TRIGGERED |
| Same-boot clock integrity | elapsedRealtime + boot count |
| Reboot recovery | Revalidate charger state; if connected, return to ARMED; if disconnected, start a fresh 10-second grace period; if the first post-boot power sample is unknown, retain a short retry marker and start the full grace period only after a live disconnected sample; this also works when BOOT_COUNT is unavailable |
| Android 15 boot recovery | After BOOT_COMPLETED/first user unlock, schedules exact alarm when granted; no Direct Boot protection before first unlock |
| Local-first protection | Alarm and protection logic operate locally/offline; INTERNET is used only for owner-initiated GitHub release checks. No backend, analytics or advertising SDK |
| Background protection monitor | User-enabled, stoppable foreground service with persistent notification; disabling cancels pending countdown and disarms but cannot clear a latched alarm |
| Independent motion alarm | Explicit opt-in, OFF by default; can trigger while charger remains connected; only monitored while ARMED |
| Local event export | CSV export through Android's system document picker |
| Alarm foreground notification | Ongoing high-priority alarm notification; optional full-screen dashboard only when owner-enabled and Android full-screen access is granted; otherwise normal notification fallback |
| Synchronized camera LED alert | Optional slow rear-LED pulses only while ALARM_TRIGGERED; default ON, owner can disable it; stops on authenticated disarm; no CAMERA permission or image/video capture; unavailable hardware never blocks audio |
| First-launch privacy notice | Notice is shown until the owner acknowledges it; one “I understand & continue” action proceeds to the dashboard; no Decline button |
| Dedicated Settings screen | Android alarm, notification, battery/background, DND, app-info and privacy-notice controls |
| Premium adaptive UI | Material 3 dark theme, responsive scroll layout, high-contrast status states, large touch targets and explicit setup controls |
| Device PIN / pattern / password | Explicit app-launched Android device-credential confirmation; credentials are never read or stored; ordinary phone unlock alone does not stop the alarm |
| Fingerprint / face | BIOMETRIC_STRONG only, if enrolled and supported |
| Five failed biometric attempts | Persisted counter; fifth consecutive failure starts 30-second cooldown |
| Repeated cooldown | Counter resets after cooldown; each subsequent five failures starts another cooldown |
| Cancel authentication | Does not count as failure and never clears alarm |
| Successful authentication | Clears triggered state and restores prior alarm volume |
| Optional Device Owner mode | DevicePolicyManager + Lock Task Mode; requires explicit Android Enterprise enrollment and opt-in |
| Managed power dialog restriction | Omits LOCK_TASK_FEATURE_GLOBAL_ACTIONS while app is in Lock Task Mode on supported builds |
| Managed device restrictions | Settings factory reset, add user and USB file transfer; Safe Boot and USB debugging remain available as recovery paths |
| Managed rollback | In-app recovery restores the saved pre-enable policy; damaged/legacy snapshots trigger emergency navigation defaults and retain a retry path if any DPM call fails |
| Unit tests | Protection state machine, authentication throttle, and recognized OEM-family guidance including safe generic fallbacks |

## Bypass Charging integration — product roadmap Phase 4

| Feature | Implementation / verification |
|---|---|
| Device-specific adapter contract | Implemented; adapters must prove hardware support and authorization |
| ON/OFF command verification | Implemented in controller policy; actual state must be read back after every command |
| Safe Settings UI | Implemented; switch stays disabled and actual mode shows UNKNOWN until a reviewed adapter is registered |
| OEM adapters | Not available; no public model/firmware-specific third-party control API has been certified |
| Persistence and reboot restoration | Not enabled until an OEM adapter documents safe persistence/recovery behavior |
| Physical charging-path behavior | Not verified; excluded from current non-physical-device work |

A built-in OEM feature is not proof that a third-party APK can control it. See [Bypass Charging Integration](BYPASS_CHARGING_INTEGRATION.md).

## Authentication boundary
The five-failure / 30-second app cooldown applies to biometric failures reported through AndroidX BiometricPrompt. Device PIN/pattern/password remains available during that app cooldown; Android Keyguard independently enforces credential lockout. Android does not disclose incorrect device PIN/pattern/password attempts to ordinary apps. Android Keyguard enforces its own credential lockout. The app never collects, stores, logs or transmits device credentials. `ACTION_USER_PRESENT`, `isDeviceSecure`, and keyguard dismissal are not accepted as proof of a fresh credential; the owner must complete the explicit app-launched credential or strong-biometric prompt.

Strong face authentication is available only if Android reports the enrolled face modality as BIOMETRIC_STRONG. Weak face unlock is intentionally not accepted.

## Permission and reliability rules
- Notification permission is requested on Android 13+; denial degrades gracefully.
- Exact-alarm special access is user-controlled on Android 12+. Without it, the app uses an inexact fallback and the ten-second deadline may be delayed.
- Android 15+ blocks a mediaPlayback foreground service launched directly from BOOT_COMPLETED. The app now schedules a user-authorized exact alarm for recovery where exact-alarm access is granted. Without that access, immediate playback after reboot is not guaranteed.
- DND is not changed by the app. Alarm audio uses USAGE_ALARM; users configure alarm exceptions in Android sound/DND settings.
- Maximum alarm volume means the device's reported maximum alarm-stream index, not 105% output. Prior volume is restored after successful disarm.
- The visual alarm uses CameraManager.setTorchMode independently of the optional front-camera photo feature. Torch control itself does not open the camera for capture. The flash feature is optional hardware, slow-pulsed (700 ms on / 700 ms off), user-disableable, and independent of audio. Camera contention, thermal/OEM policy or missing flash can prevent light output; no universal torch guarantee is possible.
- Flashing light may affect photosensitive users. The owner can disable the visual alert in Settings; the feature does not strobe at high frequency.
- Standard mode cannot block the power menu, hardware forced shutdown, global volume keys, force-stop, uninstall, or recovery reset.
- Managed mode can hide the normal global-actions dialog while Lock Task Mode is active and restrict selected Settings actions. It cannot defeat hardware shutdown, bootloader/recovery, battery removal, or firmware controls.
- Device Owner enrollment is a deliberate enterprise provisioning operation, not a runtime permission and not something the app can self-grant.

## Release gates
1. Exact-head non-instrumentation GitHub Actions and CodeQL passed on `475c1fa` (runs 43950015535 and 43950015623): JVM unit tests, source-contract checks, lint, APK assembly/metadata/signer checks and CodeQL.
2. Debug test APK assembles with its isolated development package identity.
3. Install on physical Android 8–17 devices where available (API 26–37); API 23–25 are below the approved minSdk and are not supported.
4. Verify selected audio URI survives reboot, decodes, previews and loops; verify vibration fallback on missing/corrupt audio.
5. Verify foreground monitor, motion sensitivity, battery threshold crossing and stop action on physical devices.
6. Verify icon and label before and after installation.
7. Verify charger events, ten-second timing with monitor enabled, AlarmManager backup, reconnect and reboot.
8. Verify PIN, pattern, password, fingerprint and supported strong face.
9. Verify repeated five-failure / 30-second cooldown and cancellation.
10. Verify volume restoration, DND, silent mode, notification denial, battery settings, and full-screen dashboard with Android 14+ access both granted and denied.
11. Device Owner enrollment, lock-task restrictions and in-app rollback are user-reported PASS on a separate test device; record its Android build and repeat after managed-policy changes.
12. Complete the Google Play foreground-service declaration for specialUse; approval is not guaranteed. The repository references four permanent release-signing secrets, but their current presence and a signed production APK were not independently verified in this session. Branch CI uploads only the isolated debug test APK. Keep the branch unmerged until owner review and explicit approval. The 11 HONOR 200 Pro v1.1.3 scenarios are historical owner-reported PASS; they do not establish acceptance of the v1.1.5 candidate. Android 17 and remaining OEM/permission edge cases stay pending.


> **Exact-head verification closeout:** Android run [#44010766603](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010766603) and CodeQL run [#44010774991](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010774991) both passed on `e97817903976689f02cba8bb485fc160a7122fd3`. The Android job passed the 30-file JVM unit suite, instrumentation-disabled guard, source checks, lint, asset validation, debug APK packaging/metadata, minified release metadata, signer checks and artifact upload. The development artifact is not a production release. Branch comparison: 530 ahead / 0 behind, 131 changed paths, no deletions. Alert #8 closure and permanent production signing-secret availability remain unverified.


> **Final verified candidate:** Android run [#44010766603](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010766603) and CodeQL run [#44010774991](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44010774991) both passed on `e97817903976689f02cba8bb485fc160a7122fd3`. The Android workflow passed all 30 JVM unit-test files, instrumentation-disabled guard, static/source checks, lint, assets, APK build, metadata/package/version/signer checks and artifact upload. Development artifact only. Alert #8 closure and production signing-secret availability remain unverified. This final docs-only refresh changes no application source.


## Authoritative latest candidate status — 2026-11-17

- Exact candidate `524754f683d43450c844bca05147e66c15baf3d6`: Android [#44011669247 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44011669247), CodeQL [#44011663424 — SUCCESS](https://github.com/alinor2pundamdag/ThiefAlertSeQure/actions/runs/44011663424).
- Android passed 30 JVM unit-test source files, instrumentation-disabled guard, static/source checks, lint, asset validation, APK build and metadata/package/version/signer checks. CodeQL Java/Kotlin and Actions analysis passed.
- Artifact is a development/debug APK only; no production-signed APK or release is verified.
- Branch comparison: 531 ahead / 0 behind; 131 changed paths (90 added, 41 modified, 0 deleted). PR #7 remains draft/open/unmerged.
- CodeQL alert #8 closure and permanent production signing-secret availability remain unverified. Phase 12 device acceptance remains untested/out of scope for this software task.
