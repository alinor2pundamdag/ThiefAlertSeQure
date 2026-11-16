package com.thiefalertsequre.app

import android.ranging.RangingCapabilities
import android.ranging.RangingManager

/** Converts platform ranging availability into explicit, non-overstated user-facing labels. */
internal object RangingCapabilityStatus {
    fun label(status: Int?): String = when (status) {
        RangingCapabilities.ENABLED -> "Available"
        RangingCapabilities.NOT_SUPPORTED -> "Not supported by this device"
        RangingCapabilities.DISABLED_USER -> "Disabled by the user"
        RangingCapabilities.DISABLED_REGULATORY -> "Disabled by regulatory policy"
        RangingCapabilities.DISABLED_USER_RESTRICTIONS -> "Blocked by device/user restrictions"
        null -> "Not reported by Android"
        else -> "Unknown platform status ($status)"
    }

    fun technologyLabel(technology: Int): String = when (technology) {
        RangingManager.BLE_CS -> "Bluetooth Channel Sounding"
        RangingManager.BLE_RSSI -> "Bluetooth RSSI ranging"
        RangingManager.UWB -> "Ultra-wideband"
        RangingManager.WIFI_NAN_RTT -> "Wi-Fi NAN RTT"
        4 -> "Wi-Fi STA RTT (Android 16 QPR+)"
        5 -> "Wi-Fi Proximity Detection (Android 17+)"
        else -> "Unknown ranging technology ($technology)"
    }
}
