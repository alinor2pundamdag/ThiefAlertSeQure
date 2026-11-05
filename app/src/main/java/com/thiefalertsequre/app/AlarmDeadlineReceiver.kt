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
        val chargingNow = ExternalPowerState.isConnected(context)

        // Re-check the actual external-power state at expiry. A stale scheduled
        // alarm must never trigger while a charger is connected.
        val updated = ProtectionStore(context).update {
            ProtectionEngine.evaluateDeadline(it, now, elapsed, boot, chargingNow)
        }
        when (updated.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(
                context, updated.deadlineEpochMs, updated.deadlineElapsedRealtimeMs, updated.bootCount
            )
            ProtectionMode.ARMED, ProtectionMode.DISARMED -> AlarmScheduler.cancel(context)
            ProtectionMode.ALARM_TRIGGERED -> {
                LocalTamperHistory.record(context, "countdown_expired", "Charger absent at deadline; alarm latched")
                runCatching {
                    ContextCompat.startForegroundService(
                        context, Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START)
                    )
                }.onFailure {
                    LocalTamperHistory.record(context, "alarm_fgs_launch_failed", it.javaClass.simpleName + ": " + it.message)
                }
            }
        }
    }
}
