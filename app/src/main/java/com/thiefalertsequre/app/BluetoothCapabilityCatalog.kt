package com.thiefalertsequre.app

import android.annotation.SuppressLint

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Per-install Bluetooth capability inventory.
 *
 * Hardware/controller probes are used where Android exposes them. Android API
 * presence, local controller support, peer support, and a working app feature
 * are deliberately represented as different states.
 */
object BluetoothCapabilityCatalog {
    enum class State {
        SUPPORTED_ON_DEVICE,
        API_PRESENT_CHECK_REQUIRED,
        PEER_REQUIRED,
        PERMISSION_REQUIRED,
        ADAPTER_OFF,
        HARDWARE_REQUIRED,
        ROOT_ONLY,
        NOT_SUPPORTED,
        NOT_IMPLEMENTED,
        UNKNOWN
    }

    data class Item(
        val name: String,
        val state: State,
        val detail: String
    )

    data class Snapshot(
        val androidApi: Int,
        val adapterAvailable: Boolean,
        val adapterEnabled: Boolean?,
        val connectPermissionGranted: Boolean,
        val classicHardware: Boolean,
        val bleHardware: Boolean,
        val bondedDeviceCount: Int?,
        val items: List<Item>
    ) {
        val supportedCount: Int get() = items.count { it.state == State.SUPPORTED_ON_DEVICE }
        val permissionCount: Int get() = items.count { it.state == State.PERMISSION_REQUIRED }
        val unknownCount: Int get() = items.count { it.state == State.UNKNOWN }
    }

    fun inspect(
        context: Context,
        root: RootCapabilityInspector.Report = RootCapabilityInspector.inspectPassive()
    ): List<Item> = snapshot(context, root).items

    @SuppressLint("NewApi")
    fun snapshot(
        context: Context,
        root: RootCapabilityInspector.Report = RootCapabilityInspector.inspectPassive()
    ): Snapshot {
        val pm = context.packageManager
        val api = Build.VERSION.SDK_INT
        val classic = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val ble = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val connectGranted = api < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = runCatching { manager?.adapter }.getOrNull()
        val adapterEnabled = if (adapter == null || !connectGranted) null
            else runCatching { adapter.isEnabled }.getOrNull()
        val bondedCount = if (adapter == null || !connectGranted || adapterEnabled != true) null
            else runCatching { adapter.bondedDevices.size }.getOrNull()

        val items = mutableListOf<Item>()
        fun add(name: String, state: State, detail: String) {
            items += Item(name, state, detail)
        }
        fun adapterProbe(name: String, probe: (BluetoothAdapter) -> Boolean, detail: String) {
            val supported = if (adapter != null && connectGranted && adapterEnabled == true) {
                runCatching { probe(adapter) }.getOrNull()
            } else null
            add(name, classifyControllerProbe(adapter != null, connectGranted, adapterEnabled, supported), detail)
        }
        fun apiPeer(name: String, available: Boolean, detail: String) {
            add(name, when {
                !available -> State.NOT_SUPPORTED
                !connectGranted -> State.PERMISSION_REQUIRED
                adapterEnabled == false -> State.ADAPTER_OFF
                adapterEnabled == null -> State.UNKNOWN
                else -> State.PEER_REQUIRED
            }, detail)
        }
        fun apiStatus(name: String, apiAvailable: Boolean, status: () -> Int, detail: String) {
            val state = when {
                !apiAvailable -> State.NOT_SUPPORTED
                !connectGranted -> State.PERMISSION_REQUIRED
                adapterEnabled == false -> State.ADAPTER_OFF
                adapterEnabled != true -> State.UNKNOWN
                else -> runCatching { status() }.fold(
                    onSuccess = {
                        when (it) {
                            BluetoothStatusCodes.FEATURE_SUPPORTED -> State.SUPPORTED_ON_DEVICE
                            BluetoothStatusCodes.FEATURE_NOT_SUPPORTED -> State.NOT_SUPPORTED
                            else -> State.UNKNOWN
                        }
                    },
                    onFailure = { State.UNKNOWN }
                )
            }
            add(name, state, detail)
        }

        add(
            "Bluetooth Classic (BR/EDR)",
            if (classic) State.SUPPORTED_ON_DEVICE else State.NOT_SUPPORTED,
            "Android package-manager hardware feature flag; does not prove every Classic profile or role."
        )
        add(
            "Bluetooth Low Energy (BLE)",
            if (ble) State.SUPPORTED_ON_DEVICE else State.NOT_SUPPORTED,
            "Android package-manager hardware feature flag; individual PHY, advertising, GATT, and peer support are checked separately."
        )
        add(
            "Bluetooth adapter",
            when {
                adapter == null -> State.NOT_SUPPORTED
                !connectGranted -> State.PERMISSION_REQUIRED
                adapterEnabled == true -> State.SUPPORTED_ON_DEVICE
                adapterEnabled == false -> State.ADAPTER_OFF
                else -> State.UNKNOWN
            },
            "Current local adapter availability and power state."
        )
        add(
            "Paired-device inventory",
            when {
                !classic && !ble -> State.NOT_SUPPORTED
                !connectGranted -> State.PERMISSION_REQUIRED
                adapterEnabled == false -> State.ADAPTER_OFF
                bondedCount != null -> State.SUPPORTED_ON_DEVICE
                else -> State.UNKNOWN
            },
            bondedCount?.let { "$it paired device(s) detected. Device names and addresses are not copied into diagnostics." }
                ?: "Requires an enabled adapter and Bluetooth Connect permission where applicable."
        )
        add(
            "Trusted-device ACL separation alert (A2)",
            when {
                !classic && !ble -> State.NOT_SUPPORTED
                !connectGranted -> State.PERMISSION_REQUIRED
                adapterEnabled == false -> State.ADAPTER_OFF
                else -> State.API_PRESENT_CHECK_REQUIRED
            },
            "Implemented using runtime ACL connect/disconnect events while the foreground monitor is active. Accessory and OEM background behavior still require device testing."
        )

        apiPeer("GATT client", ble, "Android BLE client APIs exist; a compatible peripheral and service UUIDs are required.")
        apiPeer("GATT server", ble, "Android BLE server APIs exist; a compatible central and service definition are required.")
        apiPeer("RFCOMM / Serial Port Profile (SPP)", classic, "Classic socket transport requires a peer exposing a compatible RFCOMM service; not implemented as an app data feature.")
        apiPeer("LE L2CAP Connection-oriented Channels", ble && api >= 29, "Platform transport APIs require compatible controller, permission, peer, and channel negotiation.")

        adapterProbe("LE 2M PHY", { it.isLe2MPhySupported() }, "Direct controller capability probe (API 26+).")
        adapterProbe("LE Coded PHY", { it.isLeCodedPhySupported() }, "Direct controller capability probe (API 26+).")
        adapterProbe("LE Extended Advertising", { it.isLeExtendedAdvertisingSupported() }, "Direct controller capability probe (API 26+); app does not start advertising.")
        adapterProbe("LE Periodic Advertising", { it.isLePeriodicAdvertisingSupported() }, "Direct controller capability probe (API 26+); app does not start advertising.")
        adapterProbe("Multiple BLE advertising", { it.isMultipleAdvertisementSupported() }, "Direct controller capability probe; does not mean the app is advertising.")
        adapterProbe("BLE offloaded filtering", { it.isOffloadedFilteringSupported() }, "Direct controller capability probe; no scan is started.")
        adapterProbe("BLE offloaded scan batching", { it.isOffloadedScanBatchingSupported() }, "Direct controller capability probe; no scan is started.")

        apiStatus("LE Audio", api >= 33, { adapter!!.isLeAudioSupported() }, "Android reports local LE Audio support; compatible audio hardware, codec, role, and accessory are still required.")
        apiStatus("LE Audio Broadcast Source", api >= 33, { adapter!!.isLeAudioBroadcastSourceSupported() }, "Android reports local broadcast-source capability; no broadcast is started.")
        apiStatus("LE Audio Broadcast Assistant", api >= 33, { adapter!!.isLeAudioBroadcastAssistantSupported() }, "Android reports local broadcast-assistant capability; compatible broadcast and accessory are required.")

        apiPeer("A2DP audio profile", classic, "Profile inventory only; audio role, codec, routing, and peer negotiation are not implemented by this app.")
        apiPeer("AVRCP media controls", classic, "Profile inventory only; actual remote-control role and peer negotiation are not verified.")
        apiPeer("HFP / HSP hands-free", classic, "Profile inventory only; telephony role and connected accessory support are not verified.")
        apiPeer("HID keyboard / mouse", classic || ble, "Profile inventory only; HID host/device role and peer service are not verified.")
        apiPeer("PBAP / MAP / OPP / PAN", classic, "Profile inventory only; this app does not access contacts, messages, files, or network payloads.")

        add(
            "Bluetooth RSSI observation",
            when {
                !classic && !ble -> State.NOT_SUPPORTED
                !connectGranted -> State.PERMISSION_REQUIRED
                adapterEnabled == false -> State.ADAPTER_OFF
                else -> State.PEER_REQUIRED
            },
            "RSSI can be read only from supported connection/scan APIs and peers. Current app does not scan or calculate distance."
        )
        add(
            "Bluetooth Channel Sounding",
            when {
                api < 36 -> State.NOT_SUPPORTED
                !connectGranted -> State.PERMISSION_REQUIRED
                else -> State.API_PRESENT_CHECK_REQUIRED
            },
            "Android Ranging capability query is available on API 36+; both endpoints, controller, firmware, permission, and a ranging session must support BLE CS."
        )
        add(
            "Bluetooth RSSI ranging",
            when {
                api < 36 -> State.NOT_SUPPORTED
                !connectGranted -> State.PERMISSION_REQUIRED
                else -> State.API_PRESENT_CHECK_REQUIRED
            },
            "Ranging API family is present on API 36+; actual capability is queried separately. No distance is calculated by this inventory."
        )
        add("Bluetooth Direction Finding (AoA/AoD)", State.HARDWARE_REQUIRED,
            "Requires controller support, antenna arrays, peer support, and platform APIs. Not inferred from a phone compass.")
        add("UWB platform capability", if (api >= 31) State.API_PRESENT_CHECK_REQUIRED else State.NOT_SUPPORTED,
            "UWB is not Bluetooth. Android exposes UWB APIs on supported Android 12+ devices; Android Ranging integration is queried separately where available. This app does not start UWB sessions.")
        add("Wi-Fi NAN RTT ranging", if (api >= 36) State.API_PRESENT_CHECK_REQUIRED else State.NOT_SUPPORTED,
            "Wi-Fi NAN RTT is not Bluetooth. Android Ranging API capability must be queried; this app does not start Wi-Fi ranging sessions.")
        add("Wi-Fi Proximity Detection", if (api >= 37) State.API_PRESENT_CHECK_REQUIRED else State.NOT_SUPPORTED,
            "Android 17+ platform capability; this is Wi-Fi, not Bluetooth, and requires supported hardware, system capability, and peer context.")
        add("Root Bluetooth stack diagnostics", State.ROOT_ONLY,
            if (root.isRootAuthorized) "Root authorization was verified in an explicit owner action; read-only diagnostics can be requested."
            else "Requires an explicit owner-authorized root check. Passive root indicators are not proof.")
        add("Bluetooth firmware / controller configuration writes", State.NOT_IMPLEMENTED,
            "Intentionally not implemented: firmware flashing and undocumented controller writes risk disabling the radio or device.")
        add("Bluetooth profile data access (contacts/messages/files)", State.NOT_IMPLEMENTED,
            "Intentionally not implemented. No contacts, messages, file transfer, or network payload access is part of the anti-theft capability system.")

        return Snapshot(api, adapter != null, adapterEnabled, connectGranted, classic, ble, bondedCount, items)
    }

    internal fun classifyControllerProbe(
        adapterAvailable: Boolean,
        connectPermissionGranted: Boolean,
        adapterEnabled: Boolean?,
        supported: Boolean?
    ): State = when {
        !adapterAvailable -> State.NOT_SUPPORTED
        !connectPermissionGranted -> State.PERMISSION_REQUIRED
        adapterEnabled == false -> State.ADAPTER_OFF
        adapterEnabled == null -> State.UNKNOWN
        supported == true -> State.SUPPORTED_ON_DEVICE
        supported == false -> State.NOT_SUPPORTED
        else -> State.UNKNOWN
    }

    fun summary(context: Context): String {
        val report = snapshot(context)
        val adapterLabel = when {
            !report.adapterAvailable -> "not present"
            !report.connectPermissionGranted -> "permission required"
            report.adapterEnabled == true -> "ON"
            report.adapterEnabled == false -> "OFF"
            else -> "unknown"
        }
        return "Bluetooth adapter: $adapterLabel | Classic: ${if (report.classicHardware) "yes" else "no"} | BLE: ${if (report.bleHardware) "yes" else "no"} | Paired devices: ${report.bondedDeviceCount?.toString() ?: "not checked"} | Device-supported probes: ${report.supportedCount}"
    }

    fun display(items: List<Item>): String = items.joinToString("\n\n") { item ->
        val state = when (item.state) {
            State.SUPPORTED_ON_DEVICE -> "SUPPORTED ON THIS DEVICE"
            State.API_PRESENT_CHECK_REQUIRED -> "API PRESENT — FURTHER CHECK REQUIRED"
            State.PEER_REQUIRED -> "COMPATIBLE PEER / CONNECTION REQUIRED"
            State.PERMISSION_REQUIRED -> "PERMISSION REQUIRED"
            State.ADAPTER_OFF -> "BLUETOOTH IS OFF"
            State.HARDWARE_REQUIRED -> "SPECIAL HARDWARE REQUIRED"
            State.ROOT_ONLY -> "OWNER-AUTHORIZED ROOT DIAGNOSTICS"
            State.NOT_SUPPORTED -> "NOT SUPPORTED / NOT DETECTED"
            State.NOT_IMPLEMENTED -> "NOT IMPLEMENTED BY THIS APP"
            State.UNKNOWN -> "UNKNOWN / CHECK FAILED"
        }
        "${item.name}: $state\n${item.detail}"
    }
}
