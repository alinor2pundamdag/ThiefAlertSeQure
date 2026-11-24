package com.thiefalertsequre.app

import android.content.Context
import android.ranging.RangingCapabilities
import android.ranging.RangingManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicBoolean

/**
 * API 36-only capability query isolated from the minSdk 26 Activity class.
 * It never discovers peers or starts a ranging session.
 */
@RequiresApi(36)
internal class RangingCapabilityQuery(
    context: Context,
    private val onResult: (String) -> Unit
) {
    private val manager = context.getSystemService(RangingManager::class.java)
    private val executor = context.mainExecutor
    private var callback: RangingManager.RangingCapabilitiesCallback? = null
    private val resultDelivered = AtomicBoolean(false)
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        if (resultDelivered.compareAndSet(false, true)) {
            unregisterCallback()
            onResult("Android did not return ranging capability information within 8 seconds. No ranging session was started.")
        }
    }

    fun start(): Boolean {
        val rangingManager = manager
        if (rangingManager == null) {
            onResult("Android exposes no RangingManager service on this device.")
            return false
        }
        val listener = object : RangingManager.RangingCapabilitiesCallback {
            override fun onRangingCapabilities(capabilities: RangingCapabilities) {
                if (!resultDelivered.compareAndSet(false, true)) return
                timeoutHandler.removeCallbacks(timeout)
                try {
                    val availability = capabilities.technologyAvailability
                    val knownTechnologies = listOf(
                        RangingManager.BLE_CS,
                        RangingManager.BLE_RSSI,
                        RangingManager.UWB,
                        RangingManager.WIFI_NAN_RTT
                    )
                    val lines = knownTechnologies.map { technology ->
                        "${RangingCapabilityStatus.technologyLabel(technology)}: ${RangingCapabilityStatus.label(availability[technology])}"
                    }.toMutableList()
                    availability.filterKeys { it !in knownTechnologies }.toSortedMap().forEach { (technology, status) ->
                        lines += "${RangingCapabilityStatus.technologyLabel(technology)}: ${RangingCapabilityStatus.label(status)}"
                    }
                    capabilities.csCapabilities?.let { cs ->
                        lines += "BLE CS security levels: ${cs.supportedSecurityLevels.sorted().joinToString()}"
                    }
                    lines += "Enabled means this phone reports local availability only. A compatible peer, firmware, permissions and session setup are still required."
                    lines += "Capability discovery only. No peer discovery, ranging session, distance measurement, or location access was performed."
                    onResult(lines.joinToString("\n"))
                } catch (error: Exception) {
                    onResult("Android returned ranging capability data that could not be read safely: ${error.javaClass.simpleName}. No ranging session was started.")
                } finally {
                    executor.execute { stop() }
                }
            }
        }
        callback = listener
        timeoutHandler.postDelayed(timeout, 8_000L)
        return runCatching {
            rangingManager.registerCapabilitiesCallback(executor, listener)
            true
        }.onFailure { error ->
            timeoutHandler.removeCallbacks(timeout)
            resultDelivered.set(true)
            unregisterCallback()
            onResult(
                if (error is SecurityException) {
                    "Android or device policy denied the local capability query. No permission was requested; this API may be restricted on this device."
                } else {
                    "Capability query failed safely: ${error.javaClass.simpleName}"
                }
            )
        }.getOrDefault(false)
    }

    fun stop() {
        resultDelivered.set(true)
        timeoutHandler.removeCallbacks(timeout)
        unregisterCallback()
    }

    private fun unregisterCallback() {
        val listener = callback ?: return
        callback = null
        runCatching { manager?.unregisterCapabilitiesCallback(listener) }
    }
}
