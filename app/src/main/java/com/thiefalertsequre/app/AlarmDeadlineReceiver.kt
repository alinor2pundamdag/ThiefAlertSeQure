package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val now = System.currentTimeMillis()
        val store = ProtectionStore(context)
        val updated = store.update { ProtectionEngine.evaluateDeadline(it, now) }
        when (updated.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, updated.deadlineEpochMs)
            ProtectionMode.ALARM_TRIGGERED -> runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START)
                )
            }
            else -> Unit
        }
    }
}
