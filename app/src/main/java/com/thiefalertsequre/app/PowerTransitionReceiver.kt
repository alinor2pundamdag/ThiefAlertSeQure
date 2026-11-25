package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal object PowerTransitionQueuePolicy {
    const val MAX_PENDING_EVENTS = 4
    const val BACKPRESSURE_TIMEOUT_MS = 2_000L

    fun createExecutor(
        queueCapacity: Int = MAX_PENDING_EVENTS,
        backpressureTimeoutMs: Long = BACKPRESSURE_TIMEOUT_MS
    ): java.util.concurrent.ThreadPoolExecutor =
        BoundedSerialExecutor.create(
            queueCapacity = queueCapacity,
            backpressureTimeoutMs = backpressureTimeoutMs,
            threadName = "power-transition-events"
        )
}

class PowerTransitionReceiver : BroadcastReceiver() {
    companion object {
        // One FIFO worker preserves CONNECTED/DISCONNECTED/recovery ordering.
        // The bounded queue applies at most 2 seconds of backpressure rather than
        // allowing a broadcast burst to grow an unbounded in-memory backlog.
        private val powerEventExecutor = PowerTransitionQueuePolicy.createExecutor()

        fun enqueuePendingRecovery(context: Context) {
            try {
                powerEventExecutor.execute {
                    PowerTransitionCoordinator.recoverPendingDisconnectCandidate(context.applicationContext)
                }
            } catch (error: Exception) {
                LocalTamperHistory.record(
                    context, "pending_unplug_recovery_queue_failed",
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

        // Capture the physical event time before serialized debounce work so the
        // confirmation window is not added before the user's ten-second grace period.
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
