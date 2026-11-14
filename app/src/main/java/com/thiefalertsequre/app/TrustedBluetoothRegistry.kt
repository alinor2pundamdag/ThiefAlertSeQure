package com.thiefalertsequre.app

import android.content.SharedPreferences
import java.nio.charset.StandardCharsets

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
        val raw = prefs.getString(KEY_DEVICES, null)
        if (raw == null) {
            val legacyAddress = prefs.getString(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS, null)
            if (!legacyAddress.isNullOrBlank()) {
                val legacyName = prefs.getString(ProtectionMonitorService.KEY_BT_TRUSTED_NAME, null)
                    ?.takeIf { it.isNotBlank() } ?: "Bluetooth accessory"
                val migrated = listOf(TrustedBluetoothDevice(legacyAddress, legacyName))
                prefs.edit().putString(KEY_DEVICES, encode(migrated)).putString(KEY_BASELINES, "").apply()
                return migrated
            }
            return emptyList()
        }
        return decode(raw)
    }

    fun add(prefs: SharedPreferences, address: String, name: String): Boolean {
        if (address.isBlank()) return false
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

    fun revision(prefs: SharedPreferences): Long = prefs.getLong(KEY_REVISION, 0L)

    /** Per-address generation prevents unrelated registry edits from canceling another device's timer. */
    fun deviceRevision(prefs: SharedPreferences, address: String): Long =
        prefs.getLong(deviceRevisionKey(address), 0L)

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

    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun encodeBase64(input: ByteArray): String {
        val output = StringBuilder(((input.size + 2) / 3) * 4)
        var index = 0
        while (index < input.size) {
            val first = input[index].toInt() and 0xff
            val hasSecond = index + 1 < input.size
            val hasThird = index + 2 < input.size
            val second = if (hasSecond) input[index + 1].toInt() and 0xff else 0
            val third = if (hasThird) input[index + 2].toInt() and 0xff else 0
            output.append(BASE64_ALPHABET[first ushr 2])
            output.append(BASE64_ALPHABET[((first and 3) shl 4) or (second ushr 4)])
            output.append(if (hasSecond) BASE64_ALPHABET[((second and 15) shl 2) or (third ushr 6)] else '=')
            output.append(if (hasThird) BASE64_ALPHABET[third and 63] else '=')
            index += 3
        }
        return output.toString()
    }

    private fun decodeBase64(value: String): ByteArray {
        require(value.length % 4 == 0) { "Invalid Base64 length" }
        val output = java.io.ByteArrayOutputStream((value.length / 4) * 3)
        var index = 0
        while (index < value.length) {
            val c0 = BASE64_ALPHABET.indexOf(value[index])
            val c1 = BASE64_ALPHABET.indexOf(value[index + 1])
            val c2 = if (value[index + 2] == '=') -2 else BASE64_ALPHABET.indexOf(value[index + 2])
            val c3 = if (value[index + 3] == '=') -2 else BASE64_ALPHABET.indexOf(value[index + 3])
            require(c0 >= 0 && c1 >= 0 && c2 != -1 && c3 != -1) { "Invalid Base64 character" }
            require(c2 != -2 || c3 == -2) { "Invalid Base64 padding" }
            require((c2 == -2 || c3 == -2).not() || index + 4 == value.length) { "Invalid Base64 padding position" }
            output.write((c0 shl 2) or (c1 ushr 4))
            if (c2 >= 0) {
                output.write(((c1 and 15) shl 4) or (c2 ushr 2))
                if (c3 >= 0) output.write(((c2 and 3) shl 6) or c3)
            }
            index += 4
        }
        return output.toByteArray()
    }

    internal fun encode(devices: List<TrustedBluetoothDevice>): String = devices
        .distinctBy { it.address.lowercase() }
        .joinToString("\n") { device ->
            val name = encodeBase64(device.name.toByteArray(StandardCharsets.UTF_8))
            "${device.address}\t$name"
        }

    internal fun decode(raw: String): List<TrustedBluetoothDevice> = raw.lineSequence()
        .mapNotNull { line ->
            val split = line.split('\t', limit = 2)
            if (split.size != 2 || split[0].isBlank()) null else runCatching {
                val name = String(decodeBase64(split[1]), StandardCharsets.UTF_8)
                TrustedBluetoothDevice(split[0], name.ifBlank { "Bluetooth accessory" })
            }.getOrNull()
        }
        .distinctBy { it.address.lowercase() }
        .toList()

    private fun baselineAddresses(prefs: SharedPreferences): Set<String> =
        prefs.getString(KEY_BASELINES, "").orEmpty().lineSequence()
            .filter { it.isNotBlank() }.toSet()
}
