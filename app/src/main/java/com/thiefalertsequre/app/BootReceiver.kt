package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val charging = context.getSystemService(BatteryManager::class.java).isCharging
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        val store = ProtectionStore(context)
        val next = store.update {
            ProtectionEngine.restoreAfterBoot(it, charging, now, elapsed, boot)
        }
        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(
                context, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount
            )
            ProtectionMode.ALARM_TRIGGERED -> {
                // Android 15+ restricts mediaPlayback foreground-service starts from BOOT_COMPLETED.
                // On earlier releases, attempt recovery; on Android 15+, keep the alarm latched
                // and resume playback when the owner opens the app.
                if (Build.VERSION.SDK_INT < 35) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START)
                        )
                    }
                }
            }
            else -> AlarmScheduler.cancel(context)
        }
    }
}
