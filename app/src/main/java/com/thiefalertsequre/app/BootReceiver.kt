package com.thiefalertsequre.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            if (Build.VERSION.SDK_INT >= 31 && context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
                val state = ProtectionStore(context).read()
                when (state.mode) {
                    ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount)
                    ProtectionMode.ALARM_TRIGGERED -> scheduleAlarmRecovery(
                        context, "Protection alarm is active after reboot or app update"
                    )
                    else -> Unit
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

        val charging = ExternalPowerState.isConnected(context)
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        val next = ProtectionStore(context).update { ProtectionEngine.restoreAfterBoot(it, charging, now, elapsed, boot) }
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
        val state = ProtectionStore(context).read()
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

    private fun monitorEnabled(context: Context): Boolean =
        context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
}