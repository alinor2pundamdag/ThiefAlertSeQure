package com.thiefalertsequre.app

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi

/** Pure fail-closed decision for optional flash thermal monitoring. */
internal object ThermalFlashPolicy {
    fun shouldSuppress(
        thermalStatus: Int?,
        listenerRegistered: Boolean,
        severeThreshold: Int
    ): Boolean = !listenerRegistered || thermalStatus == null || thermalStatus >= severeThreshold
}

/** API 29+ thermal listener kept in an isolated class for minSdk 26 compatibility. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class ThermalFlashController(
    context: Context,
    private val onSuppressionChanged: (Boolean) -> Unit
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var registered = false

    // Unknown thermal state is unsafe for optional torch output. Stay suppressed
    // until both the initial status and ongoing listener are available.
    var isSuppressed: Boolean = true
        private set

    private val listener = PowerManager.OnThermalStatusChangedListener { status ->
        val suppressed = status >= PowerManager.THERMAL_STATUS_SEVERE
        if (suppressed != isSuppressed) {
            isSuppressed = suppressed
            onSuppressionChanged(suppressed)
        }
    }

    fun start(): Boolean {
        if (registered) return isSuppressed

        registered = runCatching {
            powerManager.addThermalStatusListener(contextExecutor, listener)
        }.isSuccess

        val currentStatus = runCatching { powerManager.currentThermalStatus }.getOrNull()
        isSuppressed = ThermalFlashPolicy.shouldSuppress(
            currentStatus,
            registered,
            PowerManager.THERMAL_STATUS_SEVERE
        )

        // Report fail-closed startup so AlarmService records the condition and
        // never starts the torch without a working thermal safety boundary.
        if (isSuppressed) onSuppressionChanged(true)
        return isSuppressed
    }

    fun stop() {
        if (registered) {
            runCatching { powerManager.removeThermalStatusListener(listener) }
            registered = false
        }
    }

    private val contextExecutor = context.mainExecutor
}
