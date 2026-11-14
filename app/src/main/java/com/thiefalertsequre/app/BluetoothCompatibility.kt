package com.thiefalertsequre.app

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Runtime Bluetooth compatibility checks and truthful version-specific guidance.
 *
 * Android version alone never proves that a Bluetooth feature is supported:
 * adapter hardware, firmware, permissions, and the remote accessory matter too.
 */
object BluetoothCompatibility {
    enum class AdapterStatus { AVAILABLE_ON, AVAILABLE_OFF, NEEDS_PERMISSION, UNSUPPORTED, UNKNOWN }

    data class Report(
        val androidVersion: String,
        val apiLevel: Int,
        val classicSupported: Boolean,
        val bleSupported: Boolean,
        val connectPermissionGranted: Boolean,
        val adapterStatus: AdapterStatus,
        val rangingApiAvailable: Boolean
    )

    fun inspect(context: Context): Report {
        val sdk = Build.VERSION.SDK_INT
        val classic = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val ble = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val permissionGranted = sdk < 31 ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        val adapterStatus = when {
            !classic && !ble -> AdapterStatus.UNSUPPORTED
            !permissionGranted -> AdapterStatus.NEEDS_PERMISSION
            else -> runCatching {
                val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                when {
                    adapter == null -> AdapterStatus.UNSUPPORTED
                    adapter.isEnabled -> AdapterStatus.AVAILABLE_ON
                    else -> AdapterStatus.AVAILABLE_OFF
                }
            }.getOrDefault(AdapterStatus.UNKNOWN)
        }

        return Report(
            androidVersion = androidRelease(sdk),
            apiLevel = sdk,
            classicSupported = classic,
            bleSupported = ble,
            connectPermissionGranted = permissionGranted,
            adapterStatus = adapterStatus,
            rangingApiAvailable = sdk >= 36
        )
    }

    fun androidRelease(api: Int): String = when (api) {
        26 -> "Android 8.0"
        27 -> "Android 8.1"
        28 -> "Android 9"
        29 -> "Android 10"
        30 -> "Android 11"
        31 -> "Android 12"
        32 -> "Android 12L"
        33 -> "Android 13"
        34 -> "Android 14"
        35 -> "Android 15"
        36 -> "Android 16"
        37 -> "Android 17"
        else -> "Android (API $api)"
    }

    fun permissionGuide(api: Int): String = if (api >= 31) {
        "Android 12–17: allow Nearby devices / Bluetooth Connect only when selecting or using your paired trusted device. This app does not need Bluetooth Scan, Bluetooth Advertise, or location permission for its current paired-device separation alert."
    } else {
        "Android 8–11: Bluetooth permissions are granted through the install-time legacy permission model. The current paired-device alert does not request location permission or perform nearby-device scanning."
    }

    fun versionGuide(api: Int): String = when {
        api <= 27 -> "Android 8.0–8.1 (API 26–27): use legacy BLUETOOTH / BLUETOOTH_ADMIN permissions. Background monitoring must run through the user-visible foreground service; implicit broadcasts are registered at runtime."
        api == 28 -> "Android 9 (API 28): legacy Bluetooth permissions remain in use. Review battery optimization and keep the user-visible monitoring service enabled."
        api == 29 -> "Android 10 (API 29): legacy Bluetooth permissions remain in use. Background activity launches are restricted; do not depend on opening screens from a background event."
        api == 30 -> "Android 11 (API 30): legacy Bluetooth permissions remain in use. Settings navigation must use public actions and safe fallbacks."
        api in 31..32 -> "Android 12 / 12L (API 31–32): request BLUETOOTH_CONNECT at runtime for paired-device access. Legacy BLUETOOTH permissions are capped at API 30. Background foreground-service starts are restricted."
        api == 33 -> "Android 13 (API 33): use the Nearby devices permission flow for Bluetooth Connect. Notification permission is separate and must be checked independently."
        api == 34 -> "Android 14 (API 34): Bluetooth Connect remains a runtime permission. Foreground-service type and background-start rules must also be satisfied."
        api == 35 -> "Android 15 (API 35): Bluetooth Connect remains a runtime permission. Target-35 edge-to-edge and foreground-service boot restrictions are handled separately from Bluetooth capability."
        api == 36 -> "Android 16 (API 36): Android Ranging APIs are available at the platform level. Precise BLE Channel Sounding or RSSI ranging must still be capability-checked; API availability alone is not hardware support."
        api >= 37 -> "Android 17 (API 37+): Android Ranging APIs remain capability-dependent. Apps targeting API 37 must also handle the updated Bluetooth RFCOMM socket read behavior; the current separation alert does not use RFCOMM data sockets."
        else -> "This Android API level is outside the documented Android 8–17 support range. Re-check platform behavior before enabling optional Bluetooth features."
    }

    fun reportText(report: Report): String {
        val adapter = when (report.adapterStatus) {
            AdapterStatus.AVAILABLE_ON -> "Bluetooth adapter: ON"
            AdapterStatus.AVAILABLE_OFF -> "Bluetooth adapter: OFF — turn it on in Android Settings"
            AdapterStatus.NEEDS_PERMISSION -> "Bluetooth adapter: permission required — choose the trusted device to request Connect access"
            AdapterStatus.UNSUPPORTED -> "Bluetooth adapter: no supported Bluetooth hardware detected"
            AdapterStatus.UNKNOWN -> "Bluetooth adapter: status could not be verified"
        }
        return buildString {
            append("${report.androidVersion} (API ${report.apiLevel})\n")
            append("Bluetooth Classic: ${if (report.classicSupported) "available" else "not detected"}\n")
            append("Bluetooth LE: ${if (report.bleSupported) "available" else "not detected"}\n")
            append(adapter)
            append("\n")
            append(if (report.rangingApiAvailable) {
                "Android Ranging API: present (API 36+); actual ranging technologies must be queried and supported by both devices."
            } else {
                "Android Ranging API: not available on this OS version."
            })
        }
    }
}
