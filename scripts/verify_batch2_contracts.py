#!/usr/bin/env python3
"""Cross-file contract checks for the implemented Batch 2 foundations."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/thiefalertsequre/app"
monitor = (APP / "ProtectionMonitorService.kt").read_text(encoding="utf-8")
managed = (APP / "ManagedProtectionController.kt").read_text(encoding="utf-8")
flash = (APP / "AlarmFlashPatternPolicy.kt").read_text(encoding="utf-8")
flash_controller = (APP / "AlarmFlashController.kt").read_text(encoding="utf-8")
thermal = (APP / "ThermalFlashController.kt").read_text(encoding="utf-8")
alarm = (APP / "AlarmService.kt").read_text(encoding="utf-8")
vault = (APP / "LocalEvidenceVault.kt").read_text(encoding="utf-8")
location = (APP / "EvidenceLocationCapture.kt").read_text(encoding="utf-8")
diagnostics = (APP / "DeviceIntelligenceActivity.kt").read_text(encoding="utf-8")
score = (APP / "ReadinessScorePolicy.kt").read_text(encoding="utf-8")
dashboard = (APP / "MainActivity.kt").read_text(encoding="utf-8")
engine = (APP / "ProtectionEngine.kt").read_text(encoding="utf-8")
camera_capture = (APP / "CameraEvidenceCapture.kt").read_text(encoding="utf-8")
camera_vault = (APP / "CameraEvidenceVault.kt").read_text(encoding="utf-8")
camera_policy = (APP / "CameraEvidencePolicy.kt").read_text(encoding="utf-8")
settings = (APP / "SettingsActivity.kt").read_text(encoding="utf-8")

checks = {
    "orderly shutdown is observed only through the dynamically registered system receiver":
        "Intent.ACTION_SHUTDOWN" in monitor and "ContextCompat.registerReceiver(this, systemReceiver" in monitor,
    "shutdown event is diagnostic and does not claim to block system shutdown":
        "orderly_shutdown_observed" in monitor and "cannot block shutdown" in monitor,
    "managed shutdown hardening remains Device Owner gated":
        "isDeviceOwner(activity)" in managed and "setLockTaskFeatures" in managed,
    "seven named plus one custom flash pattern exist":
        all(f"{name}(" in flash for name in ("STEADY", "SLOW", "NORMAL", "FAST", "BURST", "SOS", "ALTERNATING", "CUSTOM")),
    "flash speed and custom ON/OFF cadence are persisted and applied by the torch controller":
        "speedPercent(context)" in flash_controller and "customTimings(context)" in flash_controller
        and "saveCustomTimings" in flash and "speedPercent: Int" in flash,
    "flash preview is time-bounded and hardware availability is checked":
        "previewFlashPattern" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8")
        and "postDelayed(stop, 5_000L)" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8")
        and "hasFlashHardware" in flash_controller,
    "secondary alarm loops independently with an owner-controlled toggle":
        "media.isLooping = true" in (APP / "AlarmService.kt").read_text(encoding="utf-8")
        and "syncSecondaryAlarm" in (APP / "AlarmService.kt").read_text(encoding="utf-8")
        and "isSecondaryAlarmEnabled" in (APP / "AlarmSoundStore.kt").read_text(encoding="utf-8"),
    "independent repeating haptics have selectable patterns and owner controls":
        "AlarmHapticsPolicy.waveform" in (APP / "AlarmService.kt").read_text(encoding="utf-8")
        and "Independent repeating vibration" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8")
        and "AlarmHapticPattern.entries" in (APP / "AlarmHapticsPolicy.kt").read_text(encoding="utf-8"),
    "flash pattern policy drives the torch controller":
        "AlarmFlashPatternPolicy.delayMs" in flash_controller and "AlarmFlashPatternPolicy.nextTorchState" in flash_controller,
    "torch output monitoring detects external interruptions and unavailable hardware":
        "CameraManager.TorchCallback" in flash_controller
        and "alarm_flash_external_interruption" in flash_controller
        and "alarm_flash_output_unavailable" in flash_controller,
    "optional flash failure remains independent of audio alarm":
        "Failure to access a flash unit never blocks or stops alarm audio" in alarm,
    "severe thermal status suppresses flash while preserving alarm audio":
        "THERMAL_STATUS_SEVERE" in thermal and "ThermalFlashController" in alarm and "alarm_flash_thermal_suppressed" in alarm and "audio alarm continues" in alarm,
    "built-in alarm tone preview is owner-initiated and time-bounded":
        "previewBuiltInAlarmTone" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8") and "postDelayed(stop, 5_000L)" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8"),
    "encrypted evidence vault uses Android Keystore AES-GCM and bounded retention":
        "LocalPreferenceCipher.encrypt(PURPOSE" in vault and "MAX_RECORDS = 100" in vault and "RETENTION_MS" in vault,
    "event evidence writes are bounded asynchronous work":
        "recordAsync(context, safeEvent, safeDetail)" in (APP / "LocalTamperHistory.kt").read_text(encoding="utf-8") and "ArrayBlockingQueue(64)" in vault and "generation.incrementAndGet()" in vault,
    "cached and bounded current location evidence are owner-requested":
        "getLastKnownLocation" in location and "getCurrentLocation" in location and "requestSingleUpdate" in location and "CURRENT_LOCATION_TIMEOUT_MS = 20_000L" in location,
    "five built-in alarm tone presets are selectable and integrated":
        "val builtIns = listOf(SIREN, PULSE, DOUBLE, URGENT, CONTINUOUS)" in (APP / "AlarmTonePreset.kt").read_text(encoding="utf-8")
        and "AlarmToneController" in alarm and "selectNextBuiltInPreset" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8"),
    "portable evidence backup uses password-derived AES-GCM":
        "PBKDF2WithHmacSHA256" in (APP / "EvidenceVaultBackup.kt").read_text(encoding="utf-8")
        and "AES/GCM/NoPadding" in (APP / "EvidenceVaultBackup.kt").read_text(encoding="utf-8")
        and "fun replaceAll(context" in vault,
    "diagnostics export is user initiated":
        "Export redacted diagnostics report (JSON)" in diagnostics and "CreateDocument" in diagnostics,
    "readiness score labels itself as non-guarantee":
        "not a security/integrity guarantee" in score and "PROTECTION READINESS SCORE" in diagnostics,
    "device intelligence reports OEM/background restriction, Doze, and standby bucket states":
        "OEM AND BACKGROUND EXECUTION" in diagnostics and "isBackgroundRestricted" in diagnostics
        and "isDeviceIdleMode" in diagnostics and "appStandbyBucket" in diagnostics,
    "encrypted configuration backup uses PBKDF2 AES-GCM and excludes authentication secrets":
        "PBKDF2WithHmacSHA256" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "AES/GCM/NoPadding" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "authentication secrets" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "ProtectionMode.ALARM_TRIGGERED" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "Export encrypted app configuration" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8"),
    "permission changes are detected on primary screens and recorded locally":
        "PermissionChangeMonitor.check(this)" in (APP / "MainActivity.kt").read_text(encoding="utf-8")
        and "PermissionChangeMonitor.check(this)" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8")
        and "LocalTamperHistory.record" in (APP / "PermissionChangeMonitor.kt").read_text(encoding="utf-8"),
    "alarm output diagnostics record focus and expose recent output events":
        "alarm_audio_focus_changed" in (APP / "AlarmService.kt").read_text(encoding="utf-8")
        and "ALARM OUTPUT MONITOR" in diagnostics and "recentAlarmOutputEvents" in diagnostics,
    "protection integrity score uses verified stores and evidence integrity":
        "ProtectionIntegrityPolicy.calculate" in diagnostics
        and "integritySnapshot()" in diagnostics and "verifyIntegrity(this)" in diagnostics,
    "dependency map and emergency recovery guide are available":
        "FEATURE DEPENDENCY MAP" in diagnostics and "showEmergencyRecoveryGuide" in diagnostics,
    "front-camera photo evidence runs only after successful visible owner authentication":
        "CameraEvidenceCapture.captureAfterSuccessfulAuthentication(this)" in dashboard
        and "OwnerAuthenticationCoordinator.complete(this, proof)" in dashboard
        and dashboard.index("OwnerAuthenticationCoordinator.complete(this, proof)") < dashboard.index("CameraEvidenceCapture.captureAfterSuccessfulAuthentication(this)")
        and "Lifecycle.State.STARTED" in camera_capture,
    "front-camera capture is explicitly owner-enabled and permission-gated":
        "Enable front-camera photo evidence?" in settings
        and "requestCameraEvidencePermission.launch(Manifest.permission.CAMERA)" in settings
        and "CameraEvidencePolicy.shouldCapture(true, permissionGranted, true, visible)" in camera_capture,
    "camera photo files are encrypted and integrity checked with bounded retention":
        "AES/GCM/NoPadding" in camera_vault and "AndroidKeyStore" in camera_vault
        and "sha256(plaintext) != entry.sha256" in camera_vault
        and "MAX_IMAGES = 20" in camera_policy and "RETENTION_MS = 30L" in camera_policy,
    "front-camera photos can be viewed, exported and deleted":
        "showCameraEvidenceArchive" in settings and "exportCameraEvidence.launch" in settings
        and "CameraEvidenceVault.delete(this, entry.id)" in settings and "CameraEvidenceVault.deleteAll(this)" in settings,
    "dashboard keeps the enlarged fixed-color shield above compact swipeable tabs":
        "dp(136), dp(136)" in dashboard and "val navigationScroll = HorizontalScrollView" in dashboard
        and dashboard.index("LIVE PROTECTION STATUS") < dashboard.index("val navigationScroll")
        and "private fun navigationTab" in dashboard and "minHeight = dp(48)" in dashboard
        and "contentDescription = \"Open $description\"" in dashboard
        and "Color.rgb(64, 177, 224)" in (APP / "ProtectionShieldView.kt").read_text(encoding="utf-8"),
    "all nine owner-requested dashboard destinations are wired":
        all(token in dashboard for token in (
            '"Bluetooth", "Bluetooth Settings"', '"Hardening", "Device Hardening and Reliability"',
            '"Sounds / Flash", "Sounds and Flash Alert"', '"Auth / Evidence", "Authentication Evidence"',
            '"Device Info", "Device Intelligence"', '"Appearance", "Appearance Studio"',
            '"Tamper History", "Local Tamper History"', '"More", "More Settings"',
            '"About", "About ThiefAlertSeQure"'
        ))
        and "Intent(this, AboutActivity::class.java)" in dashboard
        and 'android:name=".AboutActivity"' in (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8"),
    "authentication and tamper evidence remain reachable from the owner-authentication section":
        "Review authentication & tamper evidence" in dashboard and "Intent(this, SettingsActivity::class.java)" in dashboard,
    "charger countdown remains exactly ten seconds":
        "const val COUNTDOWN_MS = 10_000L" in engine,
}
for description, passed in checks.items():
    if not passed:
        raise SystemExit("Batch 2 contract failed: " + description)

print("Verified Batch 2 cross-file shutdown, managed-mode, flash, evidence, diagnostics, readiness, dashboard, and core-timing contracts.")
