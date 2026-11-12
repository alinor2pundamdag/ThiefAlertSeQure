package com.thiefalertsequre.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * A capability inventory that separates platform API presence from proven
 * local hardware and peer support. Never report a profile as usable merely
 * because its API exists in the Android release.
 */
object BluetoothCapabilityCatalog {
    enum class State { AVAILABLE, API_PRESENT_CHECK_REQUIRED, HARDWARE_REQUIRED, PEER_REQUIRED, ROOT_ONLY, NOT_AVAILABLE, UNKNOWN }

    data class Item(
        val name: String,
        val state: State,
        val detail: String
    )

    fun inspect(context: Context, root: RootCapabilityInspector.Report = RootCapabilityInspector.inspectPassive()): List<Item> {
        val pm = context.packageManager
        val classic = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val ble = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val api = Build.VERSION.SDK_INT
        val ranging = api >= 36

        return listOf(
            Item("Bluetooth Classic (BR/EDR)", if (classic) State.AVAILABLE else State.NOT_AVAILABLE,
                "Local hardware feature flag. Adapter must also be powered on."),
            Item("Bluetooth Low Energy (BLE)", if (ble) State.AVAILABLE else State.NOT_AVAILABLE,
                "Local hardware feature flag. Peripheral roles and controller limits vary by chipset."),
            Item("Paired-device access / ACL link state", if (classic || ble) State.PEER_REQUIRED else State.NOT_AVAILABLE,
                "Requires Bluetooth Connect on Android 12+ and a paired/connected peer. A2 listens for ACL connect/disconnect events."),
            Item("GATT client and server", if (ble) State.PEER_REQUIRED else State.NOT_AVAILABLE,
                "BLE platform support exists; actual GATT operations require compatible services, permissions, and peer behavior."),
            Item("RFCOMM / Serial Port Profile (SPP)", if (classic) State.PEER_REQUIRED else State.NOT_AVAILABLE,
                "Classic Bluetooth data sockets require compatible peer profile/service and connection handling."),
            Item("A2DP audio streaming", if (classic) State.API_PRESENT_CHECK_REQUIRED else State.NOT_AVAILABLE,
                "Audio profile availability and codec negotiation depend on Android Bluetooth stack, audio policy, and accessory."),
            Item("AVRCP media controls", if (classic) State.API_PRESENT_CHECK_REQUIRED else State.NOT_AVAILABLE,
                "Remote-control role and supported AVRCP version are negotiated with the peer."),
            Item("HFP / HSP hands-free audio", if (classic) State.API_PRESENT_CHECK_REQUIRED else State.NOT_AVAILABLE,
                "Telephony/audio profile roles depend on stack implementation and connected accessory."),
            Item("HID keyboard / mouse", if (classic || ble) State.API_PRESENT_CHECK_REQUIRED else State.NOT_AVAILABLE,
                "Host/device role support and HID service compatibility vary by device."),
            Item("PBAP / MAP / OPP / PAN", if (classic) State.API_PRESENT_CHECK_REQUIRED else State.NOT_AVAILABLE,
                "Profile support is vendor/role dependent; this app does not read contacts, messages, files, or network traffic."),
            Item("LE Audio / LC3", if (api >= 33 && ble) State.API_PRESENT_CHECK_REQUIRED else State.HARDWARE_REQUIRED,
                "Android 13+ platform generation can support LE Audio; controller, stack, audio framework, and peer must support it."),
            Item("LE Audio Broadcast / Auracast", if (api >= 33 && ble) State.API_PRESENT_CHECK_REQUIRED else State.HARDWARE_REQUIRED,
                "Requires supported broadcast source/assistant roles and compatible accessories; not inferred from BLE alone."),
            Item("Bluetooth RSSI signal level", if (classic || ble) State.PEER_REQUIRED else State.NOT_AVAILABLE,
                "Can be available for a connected peer or scan result. RSSI is not an accurate distance or compass bearing."),
            Item("Bluetooth Channel Sounding", if (ranging) State.API_PRESENT_CHECK_REQUIRED else State.HARDWARE_REQUIRED,
                "Android Ranging API is available from API 36. Both endpoints, controller, firmware, ranging service, and session setup must support BLE CS."),
            Item("Bluetooth RSSI ranging", if (ranging) State.API_PRESENT_CHECK_REQUIRED else State.HARDWARE_REQUIRED,
                "Android Ranging API is available from API 36; actual ranging availability and peer support must be queried."),
            Item("Bluetooth Direction Finding (AoA/AoD)", State.HARDWARE_REQUIRED,
                "Requires compatible direction-finding controller/antenna-array hardware and supported platform APIs. A phone compass alone cannot locate the peer."),
            Item("Bluetooth controller / stack diagnostics", if (root.isRootAuthorized) State.ROOT_ONLY else State.ROOT_ONLY,
                "Read-only dumpsys, getprop, and sysfs inspection can be attempted only after owner-authorized root verification."),
            Item("Bluetooth firmware / vendor configuration writes", State.NOT_AVAILABLE,
                "Not implemented. The app does not flash firmware, change undocumented controller parameters, or modify system Bluetooth configuration.")
        )
    }

    fun display(items: List<Item>): String = items.joinToString("\\n\\n") { item ->
        val state = when (item.state) {
            State.AVAILABLE -> "AVAILABLE"
            State.API_PRESENT_CHECK_REQUIRED -> "API PRESENT — VERIFY DEVICE / PEER"
            State.HARDWARE_REQUIRED -> "HARDWARE-DEPENDENT"
            State.PEER_REQUIRED -> "PEER / CONNECTION REQUIRED"
            State.ROOT_ONLY -> "ROOT-ONLY DIAGNOSTICS"
            State.NOT_AVAILABLE -> "NOT DETECTED / NOT IMPLEMENTED"
            State.UNKNOWN -> "UNKNOWN"
        }
        "${item.name}: $state\\n${item.detail}"
    }
}
