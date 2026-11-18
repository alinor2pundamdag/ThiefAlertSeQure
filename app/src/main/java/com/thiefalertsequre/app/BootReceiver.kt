package com.thiefalertsequre.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // A reboot invalidates every previous Bluetooth connection baseline even
        // when settings recovery is pending. Do this before any early return.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            !A2BluetoothReceiverController.resetBaselineForBoot(context)
        ) {
            LocalTamperHistory.record(
                context, "a2_boot_baseline_reset_failed",
                "A2 manifest receiver remains disabled until stale Bluetooth baselines are safely cleared"
            )
        }
        if (ConfigurationBackup.isRestoreOrRecoveryInProgress()) {
            LocalTamperHistory.record(
                context, "configuration_restore_recovery_pending",
                "Boot/package/exact-alarm callback was deferred while an in-process settings restore is active"
            )
            return
        }
        if (!ConfigurationBackup.recoverPendingRestore(context)) {
            LocalTamperHistory.record(
                context, "configuration_restore_recovery_pending",
                "Boot/package/exact-alarm reconciliation was deferred until the interrupted settings restore can be recovered"
            )
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                A2BluetoothReceiverController.deferBaselineResetForBoot(context)
            }
            val state = ProtectionStore(context).read()
            when (state.mode) {
                ProtectionMode.ALARM_TRIGGERED -> {
                    scheduleAlarmRecovery(context, "Latched alarm recovery takes priority over interrupted settings recovery")
                }
                ProtectionMode.COUNTDOWN -> {
                    // Preserve/recreate the consumed countdown callback. On a new
                    // boot AlarmScheduler detects the stale monotonic clock and
                    // schedules a reconciliation callback instead of firing early.
                    scheduleCountdownWithRetry(
                        context, state, "interrupted_settings_recovery"
                    )
                }
                ProtectionMode.ARMED, ProtectionMode.DISARMED -> Unit
            }
            ProtectionMonitorRecoveryNotice.show(
                context, "An interrupted settings restore must be recovered before background monitoring can safely resume. Existing alarm/countdown state is preserved and deadlines retry automatically."
            )
            return
        }
        // BOOT_COMPLETED is authoritative even on devices where Settings.Global.BOOT_COUNT
        // is unreadable. Invalidate the previous boot's charger baseline before processing
        // any later power event; package-update and exact-alarm callbacks preserve it.
        PowerTransitionCoordinator.initializeBaseline(
            context,
            forceBootReconciliation = intent.action == Intent.ACTION_BOOT_COMPLETED
        )
        if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            if (Build.VERSION.SDK_INT >= 31 && context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
                val state = enforceMonitorOptOut(context)
                when (state.mode) {
                    ProtectionMode.COUNTDOWN -> scheduleCountdownWithRetry(context, state, "exact_alarm_access")
                    ProtectionMode.ALARM_TRIGGERED -> scheduleAlarmRecovery(
                        context, "Protection alarm is active after reboot or app update"
                    )
                    else -> AlarmScheduler.cancel(context)
                }
                LocalTamperHistory.record(context, "exact_alarm_access_granted", "Pending protection deadline re-evaluated")
            }
            return
        }
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            restoreAfterAppUpdate(context)
            return
        }
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val stateBeforeRestore = enforceMonitorOptOut(context)
        val charging = ExternalPowerState.readConnected(context)
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        val next = ProtectionStore(context).update { old ->
            when {
                old.mode == ProtectionMode.ALARM_TRIGGERED -> old
                !monitorEnabled(context) -> stateBeforeRestore
                charging == null && old.mode == ProtectionMode.COUNTDOWN ->
                    ProtectionEngine.awaitPostBootPowerSample(old, now)
                charging == null -> old
                else -> ProtectionEngine.restoreAfterBoot(old, charging, now, elapsed, boot)
            }
        }
        when (next.mode) {
            ProtectionMode.COUNTDOWN -> scheduleCountdownWithRetry(context, next, "boot")
            ProtectionMode.ALARM_TRIGGERED -> scheduleAlarmRecovery(
                        context, "Protection alarm is active after reboot or app update"
                    )
            else -> AlarmScheduler.cancel(context)
        }
        startMonitorIfNeeded(context, next, "boot")
    }

    /** Package replacement preserves the current boot's monotonic countdown deadline. */
    private fun restoreAfterAppUpdate(context: Context) {
        val state = enforceMonitorOptOut(context)
        when (state.mode) {
            ProtectionMode.COUNTDOWN -> scheduleCountdownWithRetry(context, state, "app_update")
            ProtectionMode.ALARM_TRIGGERED -> scheduleAlarmRecovery(
                        context, "Protection alarm is active after reboot or app update"
                    )
            else -> AlarmScheduler.cancel(context)
        }
        startMonitorIfNeeded(context, state, "app_update")
        LocalTamperHistory.record(context, "app_update_state_restored", "Persisted protection state re-evaluated after package replacement")
    }

    private fun scheduleCountdownWithRetry(
        context: Context,
        state: ProtectionState,
        source: String
    ) {
        if (AlarmScheduler.schedule(
                context, state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount
            )
        ) return
        AlarmScheduler.scheduleRetry(context, 1_000L)
        LocalTamperHistory.record(
            context, "countdown_recovery_schedule_failed",
            "$source could not schedule the preserved countdown immediately; a one-second retry was requested"
        )
    }

    private fun scheduleAlarmRecovery(context: Context, reason: String) {
        AlarmRecoveryCoordinator.onStartFailure(context, reason)
    }

    private fun startMonitorIfNeeded(context: Context, state: ProtectionState, source: String) {
        if (!ConfigurationBackup.recoverPendingRestore(context)) {
            LocalTamperHistory.record(context, "configuration_restore_recovery_pending", "Background monitor startup blocked because an interrupted configuration restore could not be recovered")
            ProtectionMonitorRecoveryNotice.show(context, "An interrupted settings restore needs recovery before background monitoring can safely resume.")
            return
        }
        val enabled = monitorEnabled(context)
        if (!ProtectionMonitorPolicy.shouldRun(enabled, state.mode)) return
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, ProtectionMonitorService::class.java))
        }.onFailure {
            LocalTamperHistory.record(context, "${source}_monitor_start_failed", it.javaClass.simpleName + ": " + it.message)
            ProtectionMonitorRecoveryNotice.show(context, "Background charger monitoring could not start; open the app to restore it")
        }
    }

    private fun enforceMonitorOptOut(context: Context): ProtectionState {
        val store = ProtectionStore(context)
        val monitorPrefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val activeRepair = store.runIfProtectionActive {
            if (monitorEnabled(context)) true
            else monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, true).commit()
        }
        if (activeRepair != null) {
            if (!activeRepair) {
                LocalTamperHistory.record(
                    context, "active_monitor_preference_repair_failed",
                    "Boot preserved active protection, but the monitor-enabled preference could not be restored"
                )
            }
            A2BluetoothReceiverController.sync(context)
            return store.read()
        }
        val current = store.read()
        if (current.mode == ProtectionMode.ALARM_TRIGGERED) return current
        if (monitorEnabled(context)) return current

        // Owner opt-out may clear optional monitoring state only while the
        // authoritative protection state is already DISARMED.
        val deadlinesCleared = A2DisconnectScheduler.clearAll(context)
        val baselinesCleared = runCatching { TrustedBluetoothRegistry.clearBaselines(monitorPrefs); true }.getOrDefault(false)
        A2BluetoothReceiverController.sync(context)
        if (!deadlinesCleared || !baselinesCleared) {
            LocalTamperHistory.record(
                context, "boot_monitor_opt_out_cleanup_incomplete",
                "Monitor is disabled while DISARMED, but trusted-device cleanup could not be fully confirmed"
            )
        }
        PowerTransitionCoordinator.resetBaselineToCurrentPower(context, "Protection monitor is disabled")
        return store.read()
    }

    private fun monitorEnabled(context: Context): Boolean =
        SafePreferences.boolean(
            context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE),
            ProtectionMonitorService.KEY_ENABLED,
            ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
}