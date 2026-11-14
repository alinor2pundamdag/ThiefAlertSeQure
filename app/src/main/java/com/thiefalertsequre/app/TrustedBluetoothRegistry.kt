package com.thiefalertsequre.app

import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.util.Base64

data class TrustedBluetoothDevice(val address: String, val name: String)

/**
 * Local allow-list for A2. This registry never pairs/unpairs devices; Android owns bonding.
 * Baselines are per-device and are intentionally cleared when the monitor service restarts.
 */
object TrustedBluetoothRegistry {
    const val KEY_DEVICES = "trusted_bluetooth_devices_v1"
    private const val KEY_BASELINES = "trusted_bluetooth_baselines_v1"
    private const val KEY_REVISION = "trusted_bluetooth_registry_revision_v1"

    fun read(prefs: SharedPreferences): List<TrustedBluetoothDevice> {
        val raw = runCatching { prefs.getString(KEY_DEVICES, null) }.getOrElse {
            prefs.edit().remove(KEY_DEVICES).apply()
            null
        }
        if (raw == null) {
            val legacyAddress = runCatching {
                prefs.getString(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS, null)
            }.getOrNull()
            if (!legacyAddress.isNullOrBlank() && isValidAddress(legacyAddress)) {
                val legacyName = runCatching {
                    prefs.getString(ProtectionMonitorService.KEY_BT_TRUSTED_NAME, null)
                }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "Bluetooth accessory"
                val migrated = listOf(TrustedBluetoothDevice(legacyAddress, legacyName))
                prefs.edit().putString(KEY_DEVICES, encode(migrated)).putString(KEY_BASELINES, "").apply()
                return migrated
            }
            return emptyList()
        }
        return decode(raw).filter { isValidAddress(it.address) }
    }

    fun add(prefs: SharedPreferences, address: String, name: String): Boolean {
        if (!isValidAddress(address)) return false
        val current = read(prefs).filterNot { it.address.equals(address, ignoreCase = true) }
        val updated = (current + TrustedBluetoothDevice(address, name.ifBlank { "Bluetooth accessory" }))
            .sortedBy { it.name.lowercase() }
        val baselines = baselineAddresses(prefs).filterNot { it.equals(address, ignoreCase = true) }
        prefs.edit()
            .putString(KEY_DEVICES, encode(updated))
            .putString(KEY_BASELINES, baselines.joinToString("\n"))
            .putLong(deviceRevisionKey(address), deviceRevision(prefs, address) + 1L)
            .apply()
        return true
    }

    /** Removes only the app's trust relationship. Android pairing is never modified. */
    fun remove(prefs: SharedPreferences, address: String): Boolean {
        val current = read(prefs)
        val updated = current.filterNot { it.address.equals(address, ignoreCase = true) }
        if (updated.size == current.size) return false
        val baselines = baselineAddresses(prefs).filterNot { it.equals(address, ignoreCase = true) }
        val edit = prefs.edit()
            .putString(KEY_DEVICES, encode(updated))
            .putString(KEY_BASELINES, baselines.joinToString("\n"))
            .putLong(deviceRevisionKey(address), deviceRevision(prefs, address) + 1L)
        if (updated.isEmpty()) {
            edit.putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
                .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
                .remove(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)
                .remove(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)
        }
        edit.apply()
        return true
    }

    fun isTrusted(prefs: SharedPreferences, address: String): Boolean =
        read(prefs).any { it.address.equals(address, ignoreCase = true) }

    fun revision(prefs: SharedPreferences): Long = runCatching { prefs.getLong(KEY_REVISION, 0L) }.getOrDefault(0L)

    /** Per-address generation prevents unrelated registry edits from canceling another device's timer. */
    fun deviceRevision(prefs: SharedPreferences, address: String): Long =
        runCatching { prefs.getLong(deviceRevisionKey(address), 0L) }.getOrDefault(0L)

    private fun deviceRevisionKey(address: String): String =
        "trusted_bluetooth_device_revision_v1_" + address.uppercase().replace(":", "_")

    fun isBaselineConnected(prefs: SharedPreferences, address: String): Boolean =
        baselineAddresses(prefs).any { it.equals(address, ignoreCase = true) }

    fun markConnected(prefs: SharedPreferences, address: String, connected: Boolean): Boolean {
        val old = baselineAddresses(prefs)
        val hadBaseline = old.any { it.equals(address, ignoreCase = true) }
        val updated = old.filterNot { it.equals(address, ignoreCase = true) }.toMutableSet()
        if (connected && isTrusted(prefs, address)) updated.add(address)
        prefs.edit().putString(KEY_BASELINES, updated.joinToString("\n")).apply()
        return hadBaseline
    }

    fun clearBaselines(prefs: SharedPreferences) {
        // Invalidate queued 8-second callbacks when A2 is disabled, re-enabled, or restarted.
        prefs.edit()
            .putString(KEY_BASELINES, "")
            .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
            .putLong(KEY_REVISION, revision(prefs) + 1L)
            .apply()
    }

    internal fun encode(devices: List<TrustedBluetoothDevice>): String = devices
        .distinctBy { it.address.lowercase() }
        .joinToString("\n") { device ->
            val name = Base64.getEncoder().encodeToString(device.name.toByteArray(StandardCharsets.UTF_8))
            "${device.address}\t$name"
        }

    internal fun decode(raw: String): List<TrustedBluetoothDevice> = raw.lineSequence()
        .mapNotNull { line ->
            val split = line.split('\t', limit = 2)
            if (split.size != 2 || split[0].isBlank()) null else runCatching {
                val name = String(Base64.getDecoder().decode(split[1]), StandardCharsets.UTF_8)
                TrustedBluetoothDevice(split[0], name.ifBlank { "Bluetooth accessory" })
            }.getOrNull()
        }
        .distinctBy { it.address.lowercase() }
        .toList()

    private fun baselineAddresses(prefs: SharedPreferences): Set<String> =
        runCatching { prefs.getString(KEY_BASELINES, "").orEmpty() }.getOrDefault("")
            .lineSequence().filter { isValidAddress(it) }.toSet()

    internal fun isValidAddress(address: String): Boolean =
        Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$").matches(address)
}
