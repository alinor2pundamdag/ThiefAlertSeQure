package com.thiefalertsequre.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.ranging.RangingCapabilities
import android.ranging.RangingManager
import androidx.annotation.RequiresApi

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

    fun start(): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RANGING) != PackageManager.PERMISSION_GRANTED) {
            onResult("Ranging permission is not granted. No capability query was performed.")
            return false
        }
        val rangingManager = manager
        if (rangingManager == null) {
            onResult("Android exposes no RangingManager service on this device.")
            return false
        }
        val listener = object : RangingManager.RangingCapabilitiesCallback {
            override fun onRangingCapabilities(capabilities: RangingCapabilities) {
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
                stop()
            }
        }
        callback = listener
        return runCatching {
            rangingManager.registerCapabilitiesCallback(executor, listener)
            true
        }.onFailure { error ->
            callback = null
            onResult(
                if (error is SecurityException) {
                    "Android denied the capability query. Confirm the Ranging permission in app settings."
                } else {
                    "Capability query failed safely: ${error.javaClass.simpleName}"
                }
            )
        }.getOrDefault(false)
    }

    fun stop() {
        val listener = callback ?: return
        callback = null
        runCatching { manager?.unregisterCapabilitiesCallback(listener) }
    }
}
