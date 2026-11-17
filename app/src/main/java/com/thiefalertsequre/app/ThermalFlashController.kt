package com.thiefalertsequre.app

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi

/** API 29+ thermal listener kept in an isolated class for minSdk 26 compatibility. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class ThermalFlashController(
    context: Context,
    private val onSuppressionChanged: (Boolean) -> Unit
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var registered = false

    var isSuppressed: Boolean = runCatching {
        powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }.getOrDefault(false)
        private set

    private val listener = PowerManager.OnThermalStatusChangedListener { status ->
        val suppressed = status >= PowerManager.THERMAL_STATUS_SEVERE
        if (suppressed != isSuppressed) {
            isSuppressed = suppressed
            onSuppressionChanged(suppressed)
        }
    }

    fun start(): Boolean {
        runCatching {
            powerManager.addThermalStatusListener(contextExecutor, listener)
            registered = true
        }
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
