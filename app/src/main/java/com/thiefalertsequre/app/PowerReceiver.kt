package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ProtectionStore(context)
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        val next = store.update { old ->
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> ProtectionEngine.chargerConnected(old, now)
                Intent.ACTION_POWER_DISCONNECTED -> ProtectionEngine.chargerDisconnected(old, now, elapsed, boot)
                else -> old
            }
        }
        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(
                context, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount
            )
            ProtectionMode.ARMED, ProtectionMode.DISARMED -> AlarmScheduler.cancel(context)
            ProtectionMode.ALARM_TRIGGERED -> AlarmScheduler.scheduleImmediateRecovery(context)
        }
    }
}
