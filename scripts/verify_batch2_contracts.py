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

trusted_registry = (APP / "TrustedBluetoothRegistry.kt").read_text(encoding="utf-8")
device_intelligence = (APP / "DeviceIntelligenceActivity.kt").read_text(encoding="utf-8")
device_render_start = device_intelligence.index("private fun render()")
device_render_end = device_intelligence.index("private fun ", device_render_start + 20)
device_render = device_intelligence[device_render_start:device_render_end]

checks = {
    "Device Intelligence performs evidence integrity scans and export on a background executor":
        "integrityExecutor.execute {" in device_intelligence
        and "LocalEvidenceVault.verifyIntegrity(applicationContext)" in device_intelligence
        and "CameraEvidenceVault.verifyIntegrity(applicationContext)" in device_intelligence
        and "CameraEvidenceVault.entries(applicationContext)" in device_intelligence
        and "LocalEvidenceVault.verifyIntegrity(this)" not in device_render
        and "CameraEvidenceVault.verifyIntegrity(this)" not in device_render
        and "private fun writeDiagnosticsReport(uri: android.net.Uri)" in device_intelligence,
    "trusted Bluetooth registry serializes compound reads, migrations, and mutations":
        all(f"@Synchronized\n    {signature}" in trusted_registry for signature in (
            "fun add(prefs: SharedPreferences, address: String, name: String): Boolean {",
            "fun remove(prefs: SharedPreferences, address: String): Boolean {",
            "fun markConnected(\n        prefs: SharedPreferences,\n        address: String,\n        connected: Boolean\n    ): BluetoothBaselineUpdateResult {",
            "fun clearBaselines(prefs: SharedPreferences) {",
            "private fun readStrict(prefs: SharedPreferences): PreferenceRead<List<TrustedBluetoothDevice>> {"
        )),
    "trusted Bluetooth baseline commits expose storage status separately from prior connection state":
        "enum class BluetoothBaselineUpdateStatus" in trusted_registry
        and "val persisted: Boolean get() = status == BluetoothBaselineUpdateStatus.COMMITTED" in trusted_registry
        and "if (!update.persisted)" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "if (!update.persisted)" in monitor,
    "A2 manifest and dynamic receivers require a verified boot-scoped baseline":
        "KEY_A2_BOOT_BASELINE_READY" in monitor
        and "fun ensureBaselineReady(context: Context): Boolean" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "fun resetBaselineForBoot(context: Context): Boolean" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "baselineReady" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "KEY_A2_BOOT_BASELINE_READY" in (APP / "ProtectionMonitorService.kt").read_text(encoding="utf-8")
        and "resetBaselineForBoot(context)" in (APP / "BootReceiver.kt").read_text(encoding="utf-8")
        and "resetBaselineForBoot(this)" in (APP / "BluetoothCenterActivity.kt").read_text(encoding="utf-8"),
    "manifest A2 receiver remains enabled while ARMED or COUNTDOWN requires the monitor":
        "activeProtectionRequiresMonitor" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "monitorAvailable = monitorEnabled || activeProtectionRequiresMonitor" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "monitorRequired=enabled||protectionMode==ProtectionMode.ARMED||protectionMode==ProtectionMode.COUNTDOWN" in (APP / "SetupStatusSnapshot.kt").read_text(encoding="utf-8"),
    "A2 boot reset marks the baseline unavailable before disabling its manifest receiver":
        (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8").index(
            "val markedNotReady = commitWithOneRetry"
        ) < (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8").index(
            "val receiverDisabled = setComponentEnabled(app, false)"
        )
        and "if (!markedNotReady || !receiverDisabled)" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8"),
    "A2 deadline callback retries when the protection-state commit fails":
        "fun scheduleRetry(context: Context, pending: Pending" in (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8")
        and "A2DisconnectScheduler.scheduleRetry(context, pending, 1_000L)" in (APP / "AlarmDeadlineReceiver.kt").read_text(encoding="utf-8"),
    "confirmed A2 reconnect suppresses stale deadlines even when registry persistence fails":
        "fun cancelAfterConfirmedConnection(" in (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8")
        and "confirmedConnections.merge(key, connectedAtElapsedRealtimeMs)" in (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8")
        and "manager.cancel(pendingIntent(context, address))" in (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8")
        and "wasConfirmedConnectedAfter(pending)" in (APP / "AlarmDeadlineReceiver.kt").read_text(encoding="utf-8")
        and "wasConfirmedConnectedAfter(" in monitor
        and "cancelAfterConfirmedConnection(context, address, eventElapsedRealtimeMs)" in monitor
        and "cancelAfterConfirmedConnection(app, address, eventElapsedRealtimeMs)" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8"),
    "A2 cancellation commits durable removal before cancelling OS callbacks":
        (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8").index(
            "var removed = write(prefs, remaining)"
        ) < (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8").index(
            "context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, address, namespace))"
        )
        and (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8").index(
            "var cleared = prefs.edit().remove(KEY_PENDING).commit()"
        ) < (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8").index(
            "records.forEach { manager.cancel(pendingIntent(context, it.address, namespace)) }"
        )
        and "if (!removed) {" in (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8")
        and "if (!cleared) {" in (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8")
        and "scheduled callback was retained" in (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8"),
    "countdown cancellation retains the OS callback if its durable token cannot be cleared":
        (APP / "AlarmScheduler.kt").read_text(encoding="utf-8").index(
            "var cleared = prefs.edit().remove(KEY_LAST_TOKEN).commit()"
        ) < (APP / "AlarmScheduler.kt").read_text(encoding="utf-8").index(
            "alarmManager.cancel(pendingIntent(context, it, namespace))"
        )
        and "else {\n                    stored.value?.let { alarmManager.cancel(pendingIntent(context, it, namespace)) }" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8"),
    "countdown scheduling is serialized and stale workers cannot replace the current deadline":
        "private val lock = Any()" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8")
        and "): Boolean = synchronized(lock) {" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8")
        and "ProtectionEngine.isCurrentDeadline(" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8")
        and "fun scheduleRetry(context: Context, delayMs: Long) = synchronized(lock)" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8")
        and "private fun cancelInternal(" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8")
        and "private fun replaceTokenAndScheduleInternal(" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8"),
    "latched-alarm recovery retries fall back to inexact allow-while-idle scheduling":
        "fun scheduleRecoveryRetry(context: Context, delayMs: Long): Boolean {" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8")
        and "val scheduled = scheduleRecovery(context, delayMs)" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8")
        and "alarm_recovery_inexact_retry" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8")
        and "setAndAllowWhileIdle(" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8"),
    "orderly shutdown is observed only through the dynamically registered system receiver":
        "Intent.ACTION_SHUTDOWN" in monitor and "ContextCompat.registerReceiver(this, systemReceiver" in monitor,
    "shutdown event is diagnostic and does not claim to block system shutdown":
        "orderly_shutdown_observed" in monitor and "cannot block shutdown" in monitor,
    "managed shutdown hardening remains Device Owner gated":
        "isDeviceOwner(activity)" in managed and "setLockTaskFeatures" in managed,
    "managed restriction API 34 scope excludes unsupported global DISALLOW_ADD_USER":
        "ManagedRestrictionPolicy.useGlobalRestriction" in managed
        and "key != UserManager.DISALLOW_ADD_USER" in managed
        and "ManagedRestrictionPolicyTest" in (ROOT / "app/src/test/java/com/thiefalertsequre/app/ManagedRestrictionPolicyTest.kt").read_text(encoding="utf-8"),
    "managed lockdown reads back all restrictions before reporting configured":
        "managedRestrictions.all { key -> isRestrictionApplied(dpm, admin, key) }" in managed
        and "Android did not confirm every managed user restriction" in managed,
    "managed rollback verifies exact saved policies and emergency navigation by readback":
        "snapshotReadbackMatches" in managed and "emergencyReadbackMatches" in managed,
    "managed lockdown preserves Keyguard and verifies full emergency navigation restoration":
        "getLockTaskFeatures(admin) == DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD" in managed
        and "dpm.getLockTaskFeatures(admin) == emergencyFeatures" in managed,
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
    "primary alarm audio starts immediately after foreground promotion before optional lock/UI work":
        alarm.index("startForeground(NOTIFICATION_ID, notification())")
        < alarm.index("if (player == null && toneController == null) startAlarmAudio()")
        < alarm.index("BatchThreeUiBridge.refresh(this)")
        < alarm.index("lockDeviceIfAdminEnabled(alarmStartedAt)"),
    "optional flash failure remains independent of audio alarm":
        "Failure to access a flash unit never blocks or stops alarm audio" in alarm,
    "severe or unknown thermal status suppresses flash while preserving alarm audio":
        "THERMAL_STATUS_SEVERE" in thermal and "ThermalFlashController" in alarm
        and "private var thermalFlashSuppressed = Build.VERSION.SDK_INT >= 29" in alarm
        and "alarm_flash_thermal_suppressed" in alarm and "audio alarm continues" in alarm,
    "flash fails closed when thermal monitoring or initial status is unavailable":
        "ThermalFlashPolicy.shouldSuppress" in thermal
        and "!listenerRegistered || thermalStatus == null || thermalStatus >= severeThreshold" in thermal
        and "ThermalFlashPolicyTest" in (ROOT / "app/src/test/java/com/thiefalertsequre/app/ThermalFlashPolicyTest.kt").read_text(encoding="utf-8"),
    "built-in alarm tone preview is owner-initiated and time-bounded":
        "previewBuiltInAlarmTone" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8") and "postDelayed(stop, 5_000L)" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8"),
    "encrypted evidence vault uses Android Keystore AES-GCM and bounded retention":
        "LocalPreferenceCipher.encrypt(PURPOSE" in vault and "MAX_RECORDS = 100" in vault and "RETENTION_MS" in vault,
    "encrypted event evidence is recorded even when readable history storage fails":
        "is PreferenceRead.Corrupt -> {\n                LocalEvidenceVault.recordAsync(context, safeEvent, safeDetail)" in (APP / "LocalTamperHistory.kt").read_text(encoding="utf-8")
        and "if (!historySaved)" in (APP / "LocalTamperHistory.kt").read_text(encoding="utf-8")
        and (APP / "LocalTamperHistory.kt").read_text(encoding="utf-8").index("if (!historySaved)")
            < (APP / "LocalTamperHistory.kt").read_text(encoding="utf-8").index("LocalEvidenceVault.recordAsync(context, safeEvent, safeDetail)", (APP / "LocalTamperHistory.kt").read_text(encoding="utf-8").index("if (!historySaved)")),
    "event evidence writes are bounded asynchronous work":
        "recordAsync(context, safeEvent, safeDetail)" in (APP / "LocalTamperHistory.kt").read_text(encoding="utf-8") and "ArrayBlockingQueue(64)" in vault and "generation.incrementAndGet()" in vault,
    "evidence queue rejection and failed async commits are durably observable without blocking callbacks":
        "ThreadPoolExecutor.AbortPolicy()" in vault
        and "private fun noteAsyncWriteFailure" in vault
        and "if (!stored) noteAsyncWriteFailure(context" in vault
        and "catch (_: RejectedExecutionException)" in vault
        and "KEY_ASYNC_WRITE_FAILURES" in vault
        and "fun asyncWriteFailureCount(context: Context)" in vault
        and vault.index("val committed", vault.index("fun replaceAll")) <
            vault.index("if (committed) generation.incrementAndGet()", vault.index("fun replaceAll"))
        and vault.index("val committed", vault.index("fun deleteAll")) <
            vault.index("if (committed) generation.incrementAndGet()", vault.index("fun deleteAll")),
    "cached and bounded current location evidence are owner-requested":
        "getLastKnownLocation" in location and "getCurrentLocation" in location and "requestSingleUpdate" in location and "CURRENT_LOCATION_TIMEOUT_MS = 20_000L" in location,
    "current location tries every enabled GPS/network provider with bounded completion":
        "enabledProviders(" in location and "pendingProviders" in location
        and "EvidenceLocationCapture.enabledProviders" in (ROOT / "app/src/test/java/com/thiefalertsequre/app/EvidenceLocationCaptureTest.kt").read_text(encoding="utf-8"),
    "five built-in alarm tone presets are selectable and integrated":
        "val builtIns = listOf(SIREN, PULSE, DOUBLE, URGENT, CONTINUOUS)" in (APP / "AlarmTonePreset.kt").read_text(encoding="utf-8")
        and "AlarmToneController" in alarm and "selectNextBuiltInPreset" in (APP / "SettingsActivity.kt").read_text(encoding="utf-8"),
    "portable evidence backup uses password-derived AES-GCM":
        "PBKDF2WithHmacSHA256" in (APP / "EvidenceVaultBackup.kt").read_text(encoding="utf-8")
        and "AES/GCM/NoPadding" in (APP / "EvidenceVaultBackup.kt").read_text(encoding="utf-8")
        and "fun replaceAll(context" in vault,
    "diagnostics export is user initiated":
        "Export redacted diagnostics report (JSON)" in diagnostics and "CreateDocument" in diagnostics,
    "redacted diagnostics export only event codes, never free-form event details":
        "eventCodeFromLine" in diagnostics
        and "eventCodeFromLine" in (APP / "LocalTamperHistoryPolicy.kt").read_text(encoding="utf-8"),
    "readiness score labels itself as non-guarantee":
        "not a security/integrity guarantee" in score and "PROTECTION READINESS SCORE" in diagnostics,
    "device intelligence reports OEM/background restriction, Doze, and standby bucket states":
        "OEM AND BACKGROUND EXECUTION" in diagnostics and "isBackgroundRestricted" in diagnostics
        and "isDeviceIdleMode" in diagnostics and "appStandbyBucket" in diagnostics,
    "confirmed charger reconnect cannot turn a stale OFF preference into an unauthenticated disarm":
        "shouldArmOnConfirmedConnection(monitorEnabled, current.mode)" in (APP / "PowerTransitionCoordinator.kt").read_text(encoding="utf-8")
        and "val activeProtection = current.mode == ProtectionMode.ARMED" in (APP / "PowerTransitionCoordinator.kt").read_text(encoding="utf-8")
        and "monitorRequired = monitorEnabled || activeProtection" in (APP / "PowerTransitionCoordinator.kt").read_text(encoding="utf-8")
        and "fun shouldArmOnConfirmedConnection(" in (APP / "ProtectionMonitorPolicy.kt").read_text(encoding="utf-8")
        and "staleMonitorOptOutNeverDisarmsActiveProtectionOnReconnect" in (ROOT / "app/src/test/java/com/thiefalertsequre/app/ProtectionMonitorPolicyTest.kt").read_text(encoding="utf-8"),
    "dashboard and boot reconciliation never disarm active protection because of an OFF preference":
        "ProtectionEngine.disableMonitoring(" not in (APP / "MainActivity.kt").read_text(encoding="utf-8")
        and "ProtectionEngine.disableMonitoring(" not in (APP / "BootReceiver.kt").read_text(encoding="utf-8")
        and "store.runIfProtectionActive" in (APP / "BootReceiver.kt").read_text(encoding="utf-8")
        and "store.runIfProtectionActive" in (APP / "MainActivity.kt").read_text(encoding="utf-8")
        and "A2BluetoothReceiverController.sync(context)" in (APP / "BootReceiver.kt").read_text(encoding="utf-8")
        and "runIfProtectionActive" in (APP / "AlarmDeadlineReceiver.kt").read_text(encoding="utf-8"),
    "monitor notification cannot disarm ARMED or COUNTDOWN protection":
        "fun canStop(mode: ProtectionMode): Boolean = mode == ProtectionMode.DISARMED" in (APP / "ProtectionMonitorPolicy.kt").read_text(encoding="utf-8")
        and "private fun handleStopRequest(" in monitor
        and "ProtectionStore(this).runIfDisarmed" in monitor
        and "ProtectionStore(this).runIfProtectionActive" in monitor
        and "monitor_stop_rejected_active_protection" in monitor
        and "if (ProtectionMonitorPolicy.canStop(mode))" in monitor
        and "if (!monitorEnabled && state.mode == ProtectionMode.DISARMED)" in (APP / "AlarmDeadlineReceiver.kt").read_text(encoding="utf-8")
        and "ProtectionEngine.disableMonitoring(current" not in (APP / "AlarmDeadlineReceiver.kt").read_text(encoding="utf-8")
        and "notificationStopIsAllowedOnlyWhileDisarmed" in (ROOT / "app/src/test/java/com/thiefalertsequre/app/ProtectionMonitorPolicyTest.kt").read_text(encoding="utf-8"),
    "security-sensitive settings mutations are transactionally blocked outside DISARMED":
        "requireProtectionDisarmed()" in settings
        and "ProtectionStore(this).runIfDisarmed" in settings
        and "ProtectionStore(context).runIfDisarmed" in (APP / "AlarmSoundStore.kt").read_text(encoding="utf-8")
        and "ProtectionStore(context).runIfDisarmed" in flash
        and "ProtectionStore(context).runIfDisarmed" in flash_controller
        and "ProtectionStore(context).runIfDisarmed" in (APP / "AlarmHapticsPolicy.kt").read_text(encoding="utf-8")
        and "ProtectionStore(context).runIfDisarmed" in camera_capture,
    "multi-file configuration restore has durable process-death recovery":
        "JOURNAL_APPLYING" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "JOURNAL_COMMITTED" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "fun recoverPendingRestore(context: Context): Boolean" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "ConfigurationBackup.recoverPendingRestore(this)" in (APP / "ThiefAlertSeQureApplication.kt").read_text(encoding="utf-8")
        and 'android:name=".ThiefAlertSeQureApplication"' in (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8"),
    "state transitions notify dependent settings previews to release alarm outputs":
        "ACTION_STATE_CHANGED" in (APP / "ProtectionStore.kt").read_text(encoding="utf-8")
        and "sendBroadcast(" in (APP / "ProtectionStore.kt").read_text(encoding="utf-8")
        and "protectionStateReceiver" in settings
        and "stopPreview()" in settings and "stopFlashPreview()" in settings,
    "Filament cleanup attempts every scene resource even after an individual destroy failure":
        "fun destroyMesh(mesh: Mesh)" in (APP / "GenesisFilamentRendererView.kt").read_text(encoding="utf-8")
        and 'attempt("Filament engine")' in (APP / "GenesisFilamentRendererView.kt").read_text(encoding="utf-8")
        and "catch (error: Throwable)" in (APP / "GenesisFilamentRendererView.kt").read_text(encoding="utf-8"),
    "Seismic Genesis includes an articulated PBR intruder that fractures into the shared debris simulation":
        "private data class RigBone" in (APP / "GenesisFilamentRendererView.kt").read_text(encoding="utf-8")
        and "private fun createIntruderRig()" in (APP / "GenesisFilamentRendererView.kt").read_text(encoding="utf-8")
        and "private fun updateIntruderRig(" in (APP / "GenesisFilamentRendererView.kt").read_text(encoding="utf-8")
        and "bodies += body" in (APP / "GenesisFilamentRendererView.kt").read_text(encoding="utf-8")
        and "intruderRig.toList().forEach { destroyMesh(it.body.mesh) }" in (APP / "GenesisFilamentRendererView.kt").read_text(encoding="utf-8")
        and "intruderAccentMaterial" in (APP / "GenesisFilamentRendererView.kt").read_text(encoding="utf-8"),
    "trusted Bluetooth and Device Owner configuration are locked outside DISARMED":
        "requireProtectionDisarmed()" in (APP / "BluetoothCenterActivity.kt").read_text(encoding="utf-8")
        and "toggleA2WhileDisarmed()" in (APP / "BluetoothCenterActivity.kt").read_text(encoding="utf-8")
        and "enableA2WhileDisarmed()" in (APP / "BluetoothCenterActivity.kt").read_text(encoding="utf-8")
        and "ProtectionStore(this).runIfDisarmed" in (APP / "BluetoothCenterActivity.kt").read_text(encoding="utf-8")
        and "isEnabled = canEditConfiguration" in (APP / "BluetoothCenterActivity.kt").read_text(encoding="utf-8")
        and "Full-screen app alarm dashboard" in settings
        and "if (!requireProtectionDisarmed()) return@action" in settings
        and "ProtectionStore(activity).runIfDisarmed" in managed
        and "Managed lockdown can be enabled only while protection is DISARMED" in managed
        and "Managed lockdown can be disabled only while protection is DISARMED" in managed,
    "critical protection, authentication, and deadline preference writes retry once":
        "private fun commitWithOneRetry" in (APP / "ProtectionStore.kt").read_text(encoding="utf-8")
        and "val saved = commitWithOneRetry" in (APP / "ProtectionStore.kt").read_text(encoding="utf-8")
        and "var persisted = write(prefs, updated)" in (APP / "A2DisconnectScheduler.kt").read_text(encoding="utf-8")
        and "var tokenPersisted = prefs.edit().putString(KEY_LAST_TOKEN, token).commit()" in (APP / "AlarmScheduler.kt").read_text(encoding="utf-8")
        and "for (attempt in 1..2)" in (APP / "TrustedBluetoothRegistry.kt").read_text(encoding="utf-8"),
    "sensitive evidence review and location capture require DISARMED protection":
        "Event details are hidden while protection is active" in settings
        and "private fun showCameraEvidenceArchive() {\n        if (!requireProtectionDisarmed()) return" in settings
        and "private fun exportHistory(uri: Uri) {\n        if (!requireProtectionDisarmed()) return" in settings
        and "LocalEvidenceVault.record(this, \"owner_requested_current_location\"" in settings
        and "LocalEvidenceVault.record(this, \"owner_requested_location_snapshot\"" in settings,
    "both Bluetooth ACL receiver paths honor active protection over a stale monitor opt-out":
        "ProtectionMonitorPolicy.shouldRun(monitorEnabled, protectionState.mode)" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "ProtectionMonitorPolicy.shouldRun(monitorEnabled, protectionMode)" in monitor
        and "ProtectionMonitorPolicy.shouldRun(monitorEnabled, state.mode) &&" in monitor
        and monitor.index("val state = ProtectionStore(this).read()", monitor.index("override fun onStartCommand")) < monitor.index("A2BluetoothReceiverController.ensureBaselineReady(this)", monitor.index("override fun onStartCommand")),
    "alarm output preempts battery-alert audio and ACL work is bounded":
        "protectionStateReceiver" in (APP / "ProtectionMonitorService.kt").read_text(encoding="utf-8")
        and "releaseAlertPlayer()" in (APP / "ProtectionMonitorService.kt").read_text(encoding="utf-8")
        and "stopService(Intent(this, ProtectionMonitorService::class.java))" in (APP / "AlarmService.kt").read_text(encoding="utf-8")
        and "ArrayBlockingQueue(64)" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "RejectedExecutionException" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8"),
    "manifest ACL receiver uses valid Runnable labels and encrypted queue-overflow evidence":
        "return@Runnable" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "return@Thread" not in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "LocalEvidenceVault.recordAsync(" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8"),
    "root diagnostics have bounded execution and forceful timeout cleanup":
        "timeoutSeconds.coerceIn(1L, 30L)" in (APP / "RootCapabilityInspector.kt").read_text(encoding="utf-8")
        and "destroyForcibly()" in (APP / "RootCapabilityInspector.kt").read_text(encoding="utf-8")
        and "Root Bluetooth diagnostics could not start" in (APP / "RootCapabilityInspector.kt").read_text(encoding="utf-8"),
    "location evidence rejects invalid coordinates":
        "latitude.isFinite()" in (APP / "EvidenceLocationCapture.kt").read_text(encoding="utf-8")
        and "longitude.isFinite()" in (APP / "EvidenceLocationCapture.kt").read_text(encoding="utf-8")
        and "location.accuracy.isFinite()" in (APP / "EvidenceLocationCapture.kt").read_text(encoding="utf-8"),
    "encrypted photo previews are bounded against decompression memory exhaustion":
        "width.toLong() * height.toLong() > 200_000_000L" in settings
        and "inSampleSize = sampleSize" in settings
        and "inPreferredConfig = android.graphics.Bitmap.Config.RGB_565" in settings,
    "intro reduced-motion preference disables animated developer-name spectrum":
        "effectiveSpectrumPosition = if (reducedMotion) 0f else spectrumPosition" in (APP / "SeismicGenesisIntroActivity.kt").read_text(encoding="utf-8"),
    "configuration preferences commit only while disarmed and monitor stop is guarded against active state":
        "runIfDisarmed" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "apply(context, imported)" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "internal fun <T> runIfDisarmed" in (APP / "ProtectionStore.kt").read_text(encoding="utf-8")
        and "protectionMode != ProtectionMode.DISARMED" in settings
        and "configuration_restore_monitor_sync_deferred" in settings
        and "configuration_restore_monitor_stop_deferred" in settings
        and "ProtectionStore(this).runIfDisarmed" in settings,
    "configuration restore journal operations are serialized and every live protection consumer defers safely":
        "@Synchronized\n    fun decryptAndRestore" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "@Synchronized\n    fun recoverPendingRestore" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "fun isRestoreOrRecoveryInProgress(): Boolean" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "ConfigurationBackup.isRestoreOrRecoveryInProgress()" in dashboard
        and "ConfigurationBackup.isRestoreOrRecoveryInProgress()" in settings
        and "ConfigurationBackup.isRestoreOrRecoveryInProgress()" in managed
        and "ConfigurationBackup.isRestoreOrRecoveryInProgress()" in (APP / "BluetoothCenterActivity.kt").read_text(encoding="utf-8")
        and "ConfigurationBackup.isRestoreOrRecoveryInProgress()" in (APP / "OwnerAuthenticationCoordinator.kt").read_text(encoding="utf-8")
        and "ConfigurationBackup.isRestoreOrRecoveryInProgress()" in (APP / "PowerTransitionCoordinator.kt").read_text(encoding="utf-8")
        and "ConfigurationBackup.isRestoreOrRecoveryInProgress()" in monitor
        and "ConfigurationBackup.recoverPendingRestore(app)" in (APP / "PowerTransitionReceiver.kt").read_text(encoding="utf-8")
        and "ConfigurationBackup.recoverPendingRestore(app)" in (APP / "A2BluetoothAclReceiver.kt").read_text(encoding="utf-8")
        and "ConfigurationBackup.recoverPendingRestore(app)" in (APP / "AlarmDeadlineReceiver.kt").read_text(encoding="utf-8")
        and "ConfigurationBackup.isRestoreOrRecoveryInProgress()" in (APP / "BootReceiver.kt").read_text(encoding="utf-8"),
    "encrypted configuration backup includes the photo-storage preference and accepts old backups":
        "PBKDF2WithHmacSHA256" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "AES/GCM/NoPadding" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "authentication secrets" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "ConfigurationBackupPolicy.requireSafeRestoreMode" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "mode == ProtectionMode.DISARMED" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and '"camera_evidence_settings", "encrypt_new_photos"' in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and 'put("schema", 2)' in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
        and "schema == 1 || schema == 2" in (APP / "ConfigurationBackup.kt").read_text(encoding="utf-8")
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
        and "integritySnapshot()" in diagnostics and "verifyIntegrity(applicationContext)" in diagnostics,
    "dependency map and emergency recovery guide are available":
        "FEATURE DEPENDENCY MAP" in diagnostics and "showEmergencyRecoveryGuide" in diagnostics,
    "front-camera photo evidence runs only after successful visible owner authentication":
        "CameraEvidenceCapture.captureAfterSuccessfulAuthentication(this)" in dashboard
        and "OwnerAuthenticationCoordinator.complete(this, proof)" in dashboard
        and dashboard.index("OwnerAuthenticationCoordinator.complete(this, proof)") < dashboard.index("CameraEvidenceCapture.captureAfterSuccessfulAuthentication(this)")
        and "Lifecycle.State.STARTED" in camera_capture
        and "LifecycleEventObserver" in camera_capture and "Lifecycle.Event.ON_STOP" in camera_capture
        and "activityVisible.get()" in camera_capture,
    "successful owner authentication and biometric failures enter encrypted evidence":
        "LocalEvidenceVault.recordAsync" in (APP / "OwnerAuthenticationCoordinator.kt").read_text(encoding="utf-8")
        and "owner_authenticated_alarm_stopped" in (APP / "OwnerAuthenticationCoordinator.kt").read_text(encoding="utf-8")
        and "owner_biometric_failed" in dashboard
        and "AuthenticationThrottle.remainingMs" in dashboard,
    "front-camera capture is explicitly owner-enabled and permission-gated":
        "Enable front-camera photo evidence?" in settings
        and "requestCameraEvidencePermissions.launch(permissions.toTypedArray())" in settings
        and "CameraEvidencePolicy.shouldCapture(true, permissionGranted, true, visible)" in camera_capture,
    "camera photos route to Gallery by default or the optional AES-GCM private vault":
        "KEY_ENCRYPT_NEW_PHOTOS" in camera_capture
        and "CameraEvidenceMediaStore.save" in camera_capture and "CameraEvidenceVault.save" in camera_capture
        and "Encrypt new camera photos:" in settings
        and "VOLUME_EXTERNAL_PRIMARY" in (APP / "CameraEvidenceMediaStore.kt").read_text(encoding="utf-8")
        and "IS_PENDING" in (APP / "CameraEvidenceMediaStore.kt").read_text(encoding="utf-8")
        and "AES/GCM/NoPadding" in camera_vault and "AndroidKeyStore" in camera_vault
        and "sha256(plaintext) != entry.sha256" in camera_vault
        and "MAX_IMAGES = 20" in camera_policy and "RETENTION_MS = 30L" in camera_policy,
    "front-camera photos can be viewed, exported, deleted and integrity-checked":
        "showCameraEvidenceArchive" in settings and "exportCameraEvidence.launch" in settings
        and "CameraEvidenceVault.delete(applicationContext, entry.id)" in settings
        and "CameraEvidenceVault.deleteAll(applicationContext)" in settings
        and "CameraEvidenceMediaStore.deleteAll(applicationContext)" in settings
        and "var allDeleted = true" in (APP / "CameraEvidenceMediaStore.kt").read_text(encoding="utf-8")
        and "CameraEvidenceVault.verifyIntegrity(this@SettingsActivity)" in settings,
    "emergency recovery guide explains camera evidence failure boundaries":
        "front-camera evidence is not saved" in diagnostics
        and "camera failure never reverses alarm disarm" in diagnostics,
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
    "dashboard tabs deep-link to sounds, tamper history, and more settings":
        "SECTION_SOUNDS_FLASH" in dashboard and "SECTION_TAMPER_HISTORY" in dashboard
        and "SECTION_MORE" in dashboard and "sectionAnchors[section]" in settings
        and "scroll.smoothScrollTo(0, target.top)" in settings,
    "About screen exposes app identity and privacy destinations":
        "class AboutActivity" in (APP / "AboutActivity.kt").read_text(encoding="utf-8")
        and "Privacy & Data" in (APP / "AboutActivity.kt").read_text(encoding="utf-8")
        and "SECTION_PRIVACY" in (APP / "AboutActivity.kt").read_text(encoding="utf-8"),
    "authentication and tamper evidence remain reachable from the owner-authentication section":
        "Review authentication & tamper evidence" in dashboard and "Intent(this, SettingsActivity::class.java)" in dashboard,
    "baseline reset and monitor preference reconciliation are atomic with protection state":
        "store.runIfDisarmed" in (APP / "PowerTransitionCoordinator.kt").read_text(encoding="utf-8")
        and "canResetBaseline(store.read().mode)" in (APP / "PowerTransitionCoordinator.kt").read_text(encoding="utf-8")
        and "monitorEnabled = SafePreferences.boolean(" in (APP / "PowerTransitionCoordinator.kt").read_text(encoding="utf-8")
        and "monitorPrefs, ProtectionMonitorService.KEY_ENABLED" in (APP / "PowerTransitionCoordinator.kt").read_text(encoding="utf-8")
        and "baselineResetIsAllowedOnlyWhenProtectionIsDisarmed" in (ROOT / "app/src/test/java/com/thiefalertsequre/app/PowerTransitionRecoveryPolicyTest.kt").read_text(encoding="utf-8"),
    "active armed/countdown protection retains the foreground monitor despite a stale OFF preference":
        "mode == ProtectionMode.ARMED || mode == ProtectionMode.COUNTDOWN" in (APP / "ProtectionMonitorPolicy.kt").read_text(encoding="utf-8")
        and "shouldRun(false, ProtectionMode.ARMED)" in (ROOT / "app/src/test/java/com/thiefalertsequre/app/ProtectionMonitorPolicyTest.kt").read_text(encoding="utf-8")
        and "shouldRun(false, ProtectionMode.COUNTDOWN)" in (ROOT / "app/src/test/java/com/thiefalertsequre/app/ProtectionMonitorPolicyTest.kt").read_text(encoding="utf-8"),
    "charger countdown remains exactly ten seconds":
        "const val COUNTDOWN_MS = 10_000L" in engine,
}
for description, passed in checks.items():
    if not passed:
        raise SystemExit("Batch 2 contract failed: " + description)

print("Verified Batch 2 cross-file shutdown, managed-mode, flash, evidence, diagnostics, readiness, dashboard, and core-timing contracts.")
