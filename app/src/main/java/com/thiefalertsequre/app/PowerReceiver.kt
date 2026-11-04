package com.thiefalertseq ure.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ProtectionStore(context)
        val now = System.currentTimeMillis()
        val old = store.read()
        val next = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> ProtectionEngine.chargerConnected(old, now)
            Intent.ACTION_POWER_DISCONNECTED -> ProtectionEngine.chargerDisconnected(old, now)
            else -> old
        }
        store.write(next)
        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, next.deadlineEpochMs)
            ProtectionMode.ARMED, ProtectionMode.DISARMED -> AlarmScheduler.cancel(context)
            ProtectionMode.ALARM_TRIGGERED -> runCatching {
                ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START))
            }
        }
    }
}
