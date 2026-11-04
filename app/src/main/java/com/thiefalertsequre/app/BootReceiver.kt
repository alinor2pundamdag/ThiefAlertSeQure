package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val charging = context.getSystemService(BatteryManager::class.java).isCharging
        val now = System.currentTimeMillis()
        val store = ProtectionStore(context)
        val next = store.update { ProtectionEngine.restoreAfterBoot(it, charging, now) }
        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, next.deadlineEpochMs)
            ProtectionMode.ALARM_TRIGGERED -> {
                // Android 15+ restricts mediaPlayback foreground-service starts from BOOT_COMPLETED.
                // Keep the alarm latched; the owner can resume playback through the app notification/activity.
            }
            else -> AlarmScheduler.cancel(context)
        }
    }
}
