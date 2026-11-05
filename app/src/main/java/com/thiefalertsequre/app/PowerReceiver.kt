package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED &&
            intent.action != Intent.ACTION_POWER_DISCONNECTED
        ) return

        // Re-read the sticky battery state after a short debounce. This prevents
        // cable bounce or stale queued broadcasts from starting a false countdown.
        val pending = goAsync()
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                reconcileStablePowerState(context)
            } finally {
                pending.finish()
            }
        }, POWER_DEBOUNCE_MS)
    }

    private fun reconcileStablePowerState(context: Context) {
        val connected = ExternalPowerState.isConnected(context)
        val alarmReady = AlarmSoundStore.hasAlarmSound(context)
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        val store = ProtectionStore(context)
        val before = store.read()
        val next = store.update { old ->
            when {
                old.mode == ProtectionMode.ALARM_TRIGGERED -> old
                connected && !alarmReady -> ProtectionState(ProtectionMode.DISARMED, 0L, now, 0L, -1)
                connected && (old.mode == ProtectionMode.DISARMED || old.mode == ProtectionMode.COUNTDOWN) ->
                    ProtectionEngine.chargerConnected(old, now)
                !connected && old.mode == ProtectionMode.ARMED ->
                    ProtectionEngine.chargerDisconnected(old, now, elapsed, boot)
                else -> old
            }
        }

        if (connected && before.mode != ProtectionMode.ARMED && next.mode == ProtectionMode.ARMED) {
            LocalTamperHistory.record(context, "charger_connected_stable")
        } else if (!connected && before.mode == ProtectionMode.ARMED && next.mode == ProtectionMode.COUNTDOWN) {
            LocalTamperHistory.record(context, "charger_disconnected_stable")
        } else if (connected && !alarmReady && before.mode != ProtectionMode.ALARM_TRIGGERED) {
            LocalTamperHistory.record(context, "arming_blocked_no_alarm_sound")
        }

        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(
                context, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount
            )
            ProtectionMode.ARMED, ProtectionMode.DISARMED -> AlarmScheduler.cancel(context)
            ProtectionMode.ALARM_TRIGGERED -> AlarmScheduler.scheduleImmediateRecovery(context)
        }
    }

    companion object {
        private const val POWER_DEBOUNCE_MS = 300L
    }
}
