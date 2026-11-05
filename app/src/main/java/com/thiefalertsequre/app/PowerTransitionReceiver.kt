package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_POWER_CONNECTED && action != Intent.ACTION_POWER_DISCONNECTED) return
        val monitorEnabled = context.getSharedPreferences(
            ProtectionMonitorService.PREFS, Context.MODE_PRIVATE
        ).getBoolean(
            ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        if (!monitorEnabled) return

        val pending = goAsync()
        Thread {
            try {
                when (action) {
                    Intent.ACTION_POWER_CONNECTED -> PowerTransitionCoordinator.onPowerConnected(context.applicationContext)
                    Intent.ACTION_POWER_DISCONNECTED -> PowerTransitionCoordinator.onPowerDisconnected(context.applicationContext)
                }
            } catch (error: Exception) {
                LocalTamperHistory.record(context, "power_transition_processing_failed",
                    error.javaClass.simpleName + ": " + error.message)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
