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
            if (Build.VERSION.SDK_INT >= 31 &&
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            ) {
                val state = ProtectionStore(context).read()
                when (state.mode) {
                    ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(
                        context, state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount
                    )
                    ProtectionMode.ALARM_TRIGGERED -> AlarmScheduler.scheduleImmediateRecovery(context)
                    else -> Unit
                }
                LocalTamperHistory.record(context, "exact_alarm_access_granted", "Pending protection deadline re-evaluated")
            }
            return
        }
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val charging = ExternalPowerState.isConnected(context)
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        val store = ProtectionStore(context)
        val next = store.update { old ->
            ProtectionEngine.restoreAfterBoot(old, charging, now, elapsed, boot)
        }
        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(
                context, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount
            )
            ProtectionMode.ALARM_TRIGGERED -> {
                if (Build.VERSION.SDK_INT >= 35) {
                    // Android 15+ blocks BOOT_COMPLETED -> mediaPlayback FGS.
                    // Exact alarm is the documented recovery path; inexact is best effort.
                    AlarmScheduler.scheduleImmediateRecovery(context)
                } else {
                    runCatching {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START)
                        )
                    }.onFailure {
                        LocalTamperHistory.record(context, "boot_alarm_start_failed", it.javaClass.simpleName + ": " + it.message)
                    }
                }
            }
            else -> {
                AlarmScheduler.cancel(context)
                if (next.mode == ProtectionMode.ARMED &&
                    context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
                        .getBoolean(ProtectionMonitorService.KEY_ENABLED, false)
                ) {
                    runCatching {
                        ContextCompat.startForegroundService(context, Intent(context, ProtectionMonitorService::class.java))
                    }.onFailure {
                        LocalTamperHistory.record(context, "boot_monitor_start_failed", it.javaClass.simpleName + ": " + it.message)
                    }
                }
            }
        }
    }
}
