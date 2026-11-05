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
                    ProtectionMode.ALARM_TRIGGERED -> AlarmScheduler.scheduleImmediateRecovery(context)
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
            ProtectionMode.ALARM_TRIGGERED -> {
                if (Build.VERSION.SDK_INT >= 35) {
                    // Android 15+ forbids BOOT_COMPLETED -> mediaPlayback foreground service.
                    // Use the exact-alarm exemption when granted; inexact recovery is best effort.
                    AlarmScheduler.scheduleImmediateRecovery(context)
                } else {
                    runCatching {
                        ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START))
                    }.onFailure {
                        LocalTamperHistory.record(context, "boot_alarm_start_failed", it.javaClass.simpleName + ": " + it.message)
                        AlarmRecoveryNotice.show(context, "Boot recovery could not start the alarm foreground service")
                    }
                }
            }
            else -> {
                AlarmScheduler.cancel(context)
                if (next.mode == ProtectionMode.ARMED && monitorEnabled(context)) {
                    runCatching {
                        ContextCompat.startForegroundService(context, Intent(context, ProtectionMonitorService::class.java))
                    }.onFailure {
                        LocalTamperHistory.record(context, "boot_monitor_start_failed", it.javaClass.simpleName + ": " + it.message)
                    }
                }
            }
        }
    }

    /** Package replacement preserves the current boot's monotonic countdown deadline. */
    private fun restoreAfterAppUpdate(context: Context) {
        val state = ProtectionStore(context).read()
        when (state.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount)
            ProtectionMode.ALARM_TRIGGERED -> {
                AlarmScheduler.scheduleImmediateRecovery(context)
                LocalTamperHistory.record(context, "app_update_alarm_recovery_scheduled")
            }
            ProtectionMode.ARMED -> if (monitorEnabled(context)) {
                runCatching {
                    ContextCompat.startForegroundService(context, Intent(context, ProtectionMonitorService::class.java))
                }.onFailure {
                    LocalTamperHistory.record(context, "update_monitor_start_failed", it.javaClass.simpleName + ": " + it.message)
                }
            }
            ProtectionMode.DISARMED -> AlarmScheduler.cancel(context)
        }
        LocalTamperHistory.record(context, "app_update_state_restored", "Persisted protection state re-evaluated after package replacement")
    }

    private fun monitorEnabled(context: Context): Boolean =
        context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
            .getBoolean(ProtectionMonitorService.KEY_ENABLED, false)
}