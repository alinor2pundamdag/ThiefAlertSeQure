package com.thiefalertsequre.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Run migration/baseline initialization for every boot receiver entry path.
        PowerTransitionCoordinator.initializeBaseline(context)
        if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            if (Build.VERSION.SDK_INT >= 31 && context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
                val state = enforceMonitorOptOut(context)
                when (state.mode) {
                    ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount)
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
                charging == null -> old
                else -> ProtectionEngine.restoreAfterBoot(old, charging, now, elapsed, boot)
            }
        }
        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount)
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
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount)
            ProtectionMode.ALARM_TRIGGERED -> scheduleAlarmRecovery(
                        context, "Protection alarm is active after reboot or app update"
                    )
            else -> AlarmScheduler.cancel(context)
        }
        startMonitorIfNeeded(context, state, "app_update")
        LocalTamperHistory.record(context, "app_update_state_restored", "Persisted protection state re-evaluated after package replacement")
    }

    private fun scheduleAlarmRecovery(context: Context, reason: String) {
        val exactScheduled = AlarmScheduler.scheduleImmediateRecovery(context)
        if (!exactScheduled) AlarmRecoveryNotice.show(context, reason)
    }

    private fun startMonitorIfNeeded(context: Context, state: ProtectionState, source: String) {
        val enabled = monitorEnabled(context)
        if (!ProtectionMonitorPolicy.shouldRun(enabled, state.mode)) return
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, ProtectionMonitorService::class.java))
        }.onFailure {
            LocalTamperHistory.record(context, "${source}_monitor_start_failed", it.javaClass.simpleName + ": " + it.message)
            AlarmRecoveryNotice.show(context, "Background charger monitoring could not start; open the app to restore it")
        }
    }

    private fun enforceMonitorOptOut(context: Context): ProtectionState {
        if (monitorEnabled(context)) return ProtectionStore(context).read()
        return ProtectionStore(context).update { current ->
            if (current.mode == ProtectionMode.ALARM_TRIGGERED) current
            else ProtectionState(ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1)
        }
    }

    private fun monitorEnabled(context: Context): Boolean =
        context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
}