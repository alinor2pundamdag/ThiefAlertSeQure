package com.thiefalertseq ure.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = ProtectionStore(context)
        val updated = ProtectionEngine.evaluateDeadline(store.read(), System.currentTimeMillis())
        if (updated != store.read()) store.write(updated)
        when (updated.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, updated.deadlineEpochMs)
            ProtectionMode.ALARM_TRIGGERED -> runCatching {
                ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START))
            }
            else -> Unit
        }
    }
}
