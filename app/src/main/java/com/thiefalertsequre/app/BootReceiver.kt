package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val charging = ExternalPowerState.isConnected(context)
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        val store = ProtectionStore(context)
        val next = store.update { ProtectionEngine.restoreAfterBoot(it, charging, now, elapsed, boot) }

        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(
                context, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount
            )
            ProtectionMode.ALARM_TRIGGERED -> {
                if (Build.VERSION.SDK_INT >= 35) {
                    // Exact alarms are exempt from the Android 15 BOOT_COMPLETED mediaPlayback-FGS restriction.
                    // If the user has not granted exact-alarm access, a best-effort inexact recovery is scheduled.
                    AlarmScheduler.scheduleImmediateRecovery(context)
                } else {
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
