package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LocalTamperHistory.record(context, if (intent.action == Intent.ACTION_POWER_CONNECTED) "charger_connected" else "charger_disconnected")
        val store = ProtectionStore(context)
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        val alarmReady = AlarmSoundStore.hasAlarmSound(context)
        val next = store.update { old ->
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> if (alarmReady) ProtectionEngine.chargerConnected(old, now) else {
                    LocalTamperHistory.record(context, "arming_blocked_no_alarm_sound")
                    ProtectionState(ProtectionMode.DISARMED, 0L, now, 0L, -1)
                }
                Intent.ACTION_POWER_DISCONNECTED -> ProtectionEngine.chargerDisconnected(old, now, elapsed, boot)
                else -> old
            }
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
