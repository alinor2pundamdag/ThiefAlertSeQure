package com.thiefalertseq ure.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val battery = context.getSystemService(BatteryManager::class.java)
        val charging = battery.isCharging
        val store = ProtectionStore(context)
        val next = ProtectionEngine.restoreAfterBoot(store.read(), charging, System.currentTimeMillis())
        store.write(next)
        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(context, next.deadlineEpochMs)
            ProtectionMode.ALARM_TRIGGERED -> {
                // Android 15+ prohibits starting a mediaPlayback FGS from BOOT_COMPLETED.
                // The alarm state is retained; the user-visible app/notification entry can resume playback.
            }
            else -> Unit
        }
    }
}
