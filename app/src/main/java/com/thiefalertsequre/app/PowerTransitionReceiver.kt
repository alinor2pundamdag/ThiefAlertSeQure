package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class PowerTransitionReceiver : BroadcastReceiver() {
    companion object {
        // Preserve event order across CONNECTED, DISCONNECTED and restart recovery work.
        private val powerEventExecutor = Executors.newSingleThreadExecutor()

        fun enqueuePendingRecovery(context: Context) {
            val app = context.applicationContext
            try {
                powerEventExecutor.execute {
                    runCatching {
                        PowerTransitionCoordinator.recoverPendingDisconnectCandidate(app)
                    }.onFailure { error ->
                        LocalTamperHistory.record(
                            app, "pending_unplug_recovery_failed",
                            error.javaClass.simpleName + ": " + error.message
                        )
                    }
                    runCatching {
                        PowerTransitionCoordinator.recoverMissedDisconnectAfterServiceRestart(app)
                    }.onFailure { error ->
                        LocalTamperHistory.record(
                            app, "missed_unplug_restart_recovery_failed",
                            error.javaClass.simpleName + ": " + error.message
                        )
                    }
                }
            } catch (error: Exception) {
                LocalTamperHistory.record(
                    app, "pending_unplug_recovery_queue_failed",
                    error.javaClass.simpleName + ": " + error.message
                )
            }
        }
    }
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_POWER_CONNECTED && action != Intent.ACTION_POWER_DISCONNECTED) return
        val monitorEnabled = SafePreferences.boolean(
            context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE),
            ProtectionMonitorService.KEY_ENABLED,
            ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        if (!monitorEnabled) return

        // Capture the physical event time before serialized debounce work. Three
        // stable samples finish in about 200 ms; the five-sample cap is about 400 ms.
        // The ten-second grace starts at receipt, while Android and hardware scheduling
        // can still add delivery latency beyond this bounded confirmation window.
        val eventEpochMs = System.currentTimeMillis()
        val eventElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
        val eventBootCount = ProtectionClock.bootCount(context)
        val pending = goAsync()
        try {
            powerEventExecutor.execute {
                try {
                    when (action) {
                        Intent.ACTION_POWER_CONNECTED -> PowerTransitionCoordinator.onPowerConnected(context.applicationContext)
                        Intent.ACTION_POWER_DISCONNECTED -> PowerTransitionCoordinator.onPowerDisconnected(
                            context.applicationContext, eventEpochMs, eventElapsedRealtimeMs, eventBootCount
                        )
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
