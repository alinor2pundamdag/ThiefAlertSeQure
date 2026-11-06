package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class PowerTransitionReceiver : BroadcastReceiver() {
    companion object {
        // Preserve event order across CONNECTED and DISCONNECTED work.
        private val powerEventExecutor = Executors.newSingleThreadExecutor()
    }
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
        try {
            powerEventExecutor.execute {
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
            }
        } catch (error: Exception) {
            pending.finish()
            LocalTamperHistory.record(context, "power_transition_queue_failed",
                error.javaClass.simpleName + ": " + error.message)
        }
    }
}
