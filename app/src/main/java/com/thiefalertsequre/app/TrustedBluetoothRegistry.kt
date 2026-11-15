package com.thiefalertsequre.app

import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class TrustedBluetoothDevice(val address: String, val name: String)

/**
 * Local allow-list for A2. Android owns pairing; this registry only stores app trust.
 * MAC addresses, names and connection baselines are encrypted at rest with Android Keystore.
 * Plaintext v1 data is migrated on first read without changing trusted-device membership.
 */
object TrustedBluetoothRegistry {
    const val KEY_DEVICES = "trusted_bluetooth_devices_v1"
    internal const val KEY_BASELINES = "trusted_bluetooth_baselines_v1"
    private const val KEY_REVISION = "trusted_bluetooth_registry_revision_v1"
    private const val DEVICE_REVISION_PREFIX = "trusted_bluetooth_device_revision_v1_"

    fun read(prefs: SharedPreferences): List<TrustedBluetoothDevice> {
        val raw = prefs.getString(KEY_DEVICES, null)
        val legacyAddress = prefs.getString(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS, null)
        val legacyName = prefs.getString(ProtectionMonitorService.KEY_BT_TRUSTED_NAME, null)
        val devices = if (raw == null) {
            if (legacyAddress.isNullOrBlank()) emptyList()
            else listOf(TrustedBluetoothDevice(legacyAddress, legacyName?.takeIf { it.isNotBlank() } ?: "Bluetooth accessory"))
        } else {
            decode(readOrMigratePayload(prefs, KEY_DEVICES, raw))
        }

        val editor = prefs.edit()
        var changed = false
        if (raw == null && devices.isNotEmpty()) {
            editor.putString(KEY_DEVICES, LocalPreferenceCipher.encrypt(KEY_DEVICES, encode(devices)))
            if (!prefs.contains(KEY_BASELINES)) {
                editor.putString(KEY_BASELINES, LocalPreferenceCipher.encrypt(KEY_BASELINES, ""))
            }
            changed = true
        }
        if (prefs.contains(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)) {
            editor.remove(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)
            changed = true
        }
        if (prefs.contains(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)) {
            editor.remove(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)
            changed = true
        }
        if (changed) check(editor.commit()) { "Could not securely migrate trusted Bluetooth preferences." }
        migrateLegacyDeviceRevisionKeys(prefs)
        return devices
    }

    fun add(prefs: SharedPreferences, address: String, name: String): Boolean {
        if (address.isBlank()) return false
        val current = read(prefs).filterNot { it.address.equals(address, ignoreCase = true) }
        val currentRevision = deviceRevision(prefs, address)
        val baselines = baselineAddresses(prefs).filterNot { it.equals(address, ignoreCase = true) }
        val updated = (current + TrustedBluetoothDevice(address, name.ifBlank { "Bluetooth accessory" }))
            .sortedBy { it.name.lowercase() }
        return prefs.edit()
            .putString(KEY_DEVICES, LocalPreferenceCipher.encrypt(KEY_DEVICES, encode(updated)))
            .putString(KEY_BASELINES, LocalPreferenceCipher.encrypt(KEY_BASELINES, baselines.joinToString("\n")))
            .putLong(deviceRevisionKey(address), currentRevision + 1L)
            .commit()
    }

    /** Removes only the app's trust relationship. Android pairing is never modified. */
    fun remove(prefs: SharedPreferences, address: String): Boolean {
        val current = read(prefs)
        val updated = current.filterNot { it.address.equals(address, ignoreCase = true) }
        if (updated.size == current.size) return false
        val currentRevision = deviceRevision(prefs, address)
        val baselines = baselineAddresses(prefs).filterNot { it.equals(address, ignoreCase = true) }
        val edit = prefs.edit()
            .putString(KEY_DEVICES, LocalPreferenceCipher.encrypt(KEY_DEVICES, encode(updated)))
            .putString(KEY_BASELINES, LocalPreferenceCipher.encrypt(KEY_BASELINES, baselines.joinToString("\n")))
            .putLong(deviceRevisionKey(address), currentRevision + 1L)
        if (updated.isEmpty()) {
            edit.putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
                .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
                .remove(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)
                .remove(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)
        }
        return edit.commit()
    }

    fun isTrusted(prefs: SharedPreferences, address: String): Boolean =
        read(prefs).any { it.address.equals(address, ignoreCase = true) }

    fun revision(prefs: SharedPreferences): Long = prefs.getLong(KEY_REVISION, 0L)

    /** Per-address generation prevents unrelated registry edits from canceling another device's timer. */
    fun deviceRevision(prefs: SharedPreferences, address: String): Long {
        val key = deviceRevisionKey(address)
        if (prefs.contains(key)) return prefs.getLong(key, 0L)
        val legacyKey = legacyDeviceRevisionKey(address)
        if (!prefs.contains(legacyKey)) return 0L
        val previous = prefs.getLong(legacyKey, 0L)
        check(prefs.edit().putLong(key, previous).remove(legacyKey).commit()) {
            "Could not securely migrate a trusted-device revision."
        }
        return previous
    }

    private fun deviceRevisionKey(address: String): String =
        DEVICE_REVISION_PREFIX + sha256(address.trim().uppercase())

    private fun legacyDeviceRevisionKey(address: String): String =
        DEVICE_REVISION_PREFIX + address.trim().uppercase().replace(":", "_")

    private fun migrateLegacyDeviceRevisionKeys(prefs: SharedPreferences) {
        val legacyKeys = prefs.all.keys.filter { key ->
            if (!key.startsWith(DEVICE_REVISION_PREFIX)) return@filter false
            key.removePrefix(DEVICE_REVISION_PREFIX).matches(Regex("(?i)([0-9a-f]{2}_){5}[0-9a-f]{2}"))
        }
        if (legacyKeys.isEmpty()) return
        val edit = prefs.edit()
        legacyKeys.forEach { oldKey ->
            val address = oldKey.removePrefix(DEVICE_REVISION_PREFIX).replace("_", ":")
            val newKey = deviceRevisionKey(address)
            if (!prefs.contains(newKey)) edit.putLong(newKey, prefs.getLong(oldKey, 0L))
            edit.remove(oldKey)
        }
        check(edit.commit()) { "Could not securely migrate trusted-device revision keys." }
    }

    fun isBaselineConnected(prefs: SharedPreferences, address: String): Boolean =
        baselineAddresses(prefs).any { it.equals(address, ignoreCase = true) }

    fun markConnected(prefs: SharedPreferences, address: String, connected: Boolean): Boolean {
        val old = baselineAddresses(prefs)
        val hadBaseline = old.any { it.equals(address, ignoreCase = true) }
        val updated = old.filterNot { it.equals(address, ignoreCase = true) }.toMutableSet()
        if (connected && isTrusted(prefs, address)) updated.add(address)
        val committed = prefs.edit()
            .putString(KEY_BASELINES, LocalPreferenceCipher.encrypt(KEY_BASELINES, updated.joinToString("\n")))
            .commit()
        return committed && hadBaseline
    }

    fun clearBaselines(prefs: SharedPreferences) {
        check(prefs.edit()
            .putString(KEY_BASELINES, LocalPreferenceCipher.encrypt(KEY_BASELINES, ""))
            .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
            .putLong(KEY_REVISION, revision(prefs) + 1L)
            .commit()) { "Could not securely clear trusted Bluetooth baselines." }
    }

    private fun baselineAddresses(prefs: SharedPreferences): Set<String> {
        val raw = prefs.getString(KEY_BASELINES, null) ?: return emptySet()
        return readOrMigratePayload(prefs, KEY_BASELINES, raw).lineSequence()
            .filter { it.isNotBlank() }.toSet()
    }

    private fun readOrMigratePayload(prefs: SharedPreferences, key: String, raw: String): String {
        if (LocalPreferenceCipher.isEncrypted(raw)) return LocalPreferenceCipher.decrypt(key, raw)
        val encrypted = LocalPreferenceCipher.encrypt(key, raw)
        check(prefs.edit().putString(key, encrypted).commit()) { "Could not securely migrate legacy trusted Bluetooth data." }
        return raw
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

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
}
