package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_POWER_CONNECTED && action != Intent.ACTION_POWER_DISCONNECTED) return

        LocalTamperHistory.record(
            context,
            if (action == Intent.ACTION_POWER_CONNECTED) "charger_connected" else "charger_disconnected"
        )
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)

        // Ignore stale disconnect broadcasts if the cable has already been reconnected.
        val chargingNow = action == Intent.ACTION_POWER_CONNECTED || ExternalPowerState.isConnected(context)
        val next = ProtectionStore(context).update { old ->
            ProtectionEngine.reconcilePowerState(old, chargingNow, now, elapsed, boot)
        }

        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(
                context, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount
            )
            ProtectionMode.ARMED, ProtectionMode.DISARMED -> AlarmScheduler.cancel(context)
            ProtectionMode.ALARM_TRIGGERED -> {
                LocalTamperHistory.record(context, "charger_event_alarm_latched")
                AlarmScheduler.scheduleImmediateRecovery(context)
            }
        }
    }
}
