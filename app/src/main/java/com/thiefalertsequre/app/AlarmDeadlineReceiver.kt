package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        val connected = ExternalPowerState.readConnected(context)
        if (connected == null) {
            AlarmScheduler.scheduleRetry(context, 1_000L)
            return
        }
        val updated = ProtectionStore(context).update {
            ProtectionEngine.reconcileCountdown(it, connected, now, elapsed, boot)
        }
        when (updated.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, updated.deadlineEpochMs, updated.deadlineElapsedRealtimeMs, updated.bootCount)
            ProtectionMode.ALARM_TRIGGERED -> {
                AlarmScheduler.cancel(context)
                context.stopService(Intent(context, ProtectionMonitorService::class.java))
                LocalTamperHistory.record(context, "countdown_expired", "Alarm latched")
                runCatching {
                    ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START))
                }.onFailure {
                    LocalTamperHistory.record(context, "alarm_fgs_launch_failed", it.javaClass.simpleName + ": " + it.message)
                    AlarmRecoveryNotice.show(context, "Android rejected the alarm foreground-service launch")
                }
            }
            else -> Unit
        }
    }
}