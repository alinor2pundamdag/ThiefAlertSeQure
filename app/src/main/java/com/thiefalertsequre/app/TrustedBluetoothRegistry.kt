package com.thiefalertsequre.app

import android.content.SharedPreferences
import android.util.Log
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
    private const val TAG = "TrustedBluetoothRegistry"

    @Synchronized
    fun read(prefs: SharedPreferences): List<TrustedBluetoothDevice> =
        when (val result = readResult(prefs)) {
            is PreferenceRead.Value -> result.value
            is PreferenceRead.Corrupt -> emptyList()
        }

    @Synchronized
    internal fun readResult(prefs: SharedPreferences): PreferenceRead<List<TrustedBluetoothDevice>> =
        readStrict(prefs)

    @Synchronized
    private fun readStrict(prefs: SharedPreferences): PreferenceRead<List<TrustedBluetoothDevice>> {
        val raw = when (val stored = SafePreferences.readString(prefs, KEY_DEVICES, null)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return PreferenceRead.Corrupt(KEY_DEVICES)
        }
        val legacyAddress = when (val stored = SafePreferences.readString(
            prefs, ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS, null
        )) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return PreferenceRead.Corrupt(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)
        }
        val legacyName = when (val stored = SafePreferences.readString(
            prefs, ProtectionMonitorService.KEY_BT_TRUSTED_NAME, null
        )) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return PreferenceRead.Corrupt(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)
        }

        val devices = if (raw == null) {
            if (legacyAddress.isNullOrBlank()) emptyList()
            else if (!isValidAddress(legacyAddress)) {
                Log.e(TAG, "Legacy trusted-device address is malformed; source retained")
                return PreferenceRead.Corrupt(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)
            } else listOf(
                TrustedBluetoothDevice(
                    legacyAddress.trim().uppercase(),
                    legacyName?.takeIf { it.isNotBlank() } ?: "Bluetooth accessory"
                )
            )
        } else {
            runCatching { decode(readOrMigratePayload(prefs, KEY_DEVICES, raw)) }
                .onFailure { Log.e(TAG, "Trusted-device payload is unreadable; encrypted source retained", it) }
                .getOrElse { return PreferenceRead.Corrupt(KEY_DEVICES) }
        }

        val editor = prefs.edit()
        var changed = false
        if (raw == null && devices.isNotEmpty()) {
            val encryptedDevices = runCatching {
                LocalPreferenceCipher.encrypt(KEY_DEVICES, encode(devices))
            }.onFailure {
                Log.e(TAG, "Legacy trusted-device encryption failed; plaintext source retained", it)
            }.getOrElse { return PreferenceRead.Corrupt(KEY_DEVICES) }
            editor.putString(KEY_DEVICES, encryptedDevices)
            if (!prefs.contains(KEY_BASELINES)) {
                val encryptedBaselines = runCatching {
                    LocalPreferenceCipher.encrypt(KEY_BASELINES, "")
                }.onFailure {
                    Log.e(TAG, "Legacy Bluetooth baseline encryption failed; source retained", it)
                }.getOrElse { return PreferenceRead.Corrupt(KEY_BASELINES) }
                editor.putString(KEY_BASELINES, encryptedBaselines)
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
        if (changed && !editor.commit()) {
            Log.e(TAG, "Trusted-device migration commit failed; source data retained where possible")
            return PreferenceRead.Corrupt(KEY_DEVICES)
        }
        return runCatching {
            migrateLegacyDeviceRevisionKeys(prefs)
            PreferenceRead.Value(devices)
        }.getOrElse {
            Log.e(TAG, "Trusted-device revision migration failed; source data retained", it)
            PreferenceRead.Corrupt(KEY_DEVICES)
        }
    }
    @Synchronized
    fun add(prefs: SharedPreferences, address: String, name: String): Boolean {
        if (!isValidAddress(address)) return false
        val normalizedAddress = address.trim().uppercase()
        val current = when (val stored = readStrict(prefs)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return false
        }.filterNot { it.address.equals(normalizedAddress, ignoreCase = true) }
        val currentRevision = deviceRevision(prefs, normalizedAddress)
        if (currentRevision == Long.MIN_VALUE) return false
        val baselines = when (val stored = readBaselineAddresses(prefs)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return false
        }.filterNot { it.equals(normalizedAddress, ignoreCase = true) }
        val updated = (current + TrustedBluetoothDevice(normalizedAddress, name.ifBlank { "Bluetooth accessory" }))
            .sortedBy { it.name.lowercase() }
        return runCatching {
            val encryptedDevices = LocalPreferenceCipher.encrypt(KEY_DEVICES, encode(updated))
            val encryptedBaselines = LocalPreferenceCipher.encrypt(KEY_BASELINES, baselines.joinToString("\n"))
            prefs.edit()
                .putString(KEY_DEVICES, encryptedDevices)
                .putString(KEY_BASELINES, encryptedBaselines)
                .putLong(deviceRevisionKey(normalizedAddress), currentRevision + 1L)
                .commit()
        }.onFailure {
            Log.e(TAG, "Could not encrypt or commit trusted-device registration; existing source retained", it)
        }.getOrDefault(false)
    }

    /** Removes only the app's trust relationship. Android pairing is never modified. */
    @Synchronized
    fun remove(prefs: SharedPreferences, address: String): Boolean {
        if (!isValidAddress(address)) return false
        val normalizedAddress = address.trim().uppercase()
        val current = when (val stored = readStrict(prefs)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return false
        }
        val updated = current.filterNot { it.address.equals(normalizedAddress, ignoreCase = true) }
        if (updated.size == current.size) return false
        val currentRevision = deviceRevision(prefs, normalizedAddress)
        if (currentRevision == Long.MIN_VALUE) return false
        val baselines = when (val stored = readBaselineAddresses(prefs)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return false
        }.filterNot { it.equals(normalizedAddress, ignoreCase = true) }
        val edit = runCatching {
            val encryptedDevices = LocalPreferenceCipher.encrypt(KEY_DEVICES, encode(updated))
            val encryptedBaselines = LocalPreferenceCipher.encrypt(KEY_BASELINES, baselines.joinToString("\n"))
            prefs.edit()
                .putString(KEY_DEVICES, encryptedDevices)
                .putString(KEY_BASELINES, encryptedBaselines)
                .putLong(deviceRevisionKey(address), currentRevision + 1L)
        }.onFailure {
            Log.e(TAG, "Could not encrypt trusted-device removal; existing source retained", it)
        }.getOrElse { return false }
        if (updated.isEmpty()) {
            edit.putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
                .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
                .remove(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)
                .remove(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)
        }
        return edit.commit()
    }

    @Synchronized
    fun isTrusted(prefs: SharedPreferences, address: String): Boolean =
        isValidAddress(address) && when (val stored = readStrict(prefs)) {
            is PreferenceRead.Value -> stored.value.any { it.address.equals(address.trim(), ignoreCase = true) }
            is PreferenceRead.Corrupt -> false
        }

    @Synchronized
    fun revision(prefs: SharedPreferences): Long =
        when (val stored = SafePreferences.readLong(prefs, KEY_REVISION, 0L)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> Long.MIN_VALUE
        }

    /** Per-address generation prevents unrelated registry edits from canceling another device's timer. */
    @Synchronized
    fun deviceRevision(prefs: SharedPreferences, address: String): Long {
        val key = deviceRevisionKey(address)
        if (prefs.contains(key)) {
            return when (val stored = SafePreferences.readLong(prefs, key, 0L)) {
                is PreferenceRead.Value -> stored.value
                is PreferenceRead.Corrupt -> Long.MIN_VALUE
            }
        }
        val legacyKey = legacyDeviceRevisionKey(address)
        if (!prefs.contains(legacyKey)) return 0L
        return when (val stored = SafePreferences.readLong(prefs, legacyKey, 0L)) {
            is PreferenceRead.Value -> {
                val migrated = runCatching {
                    prefs.edit().putLong(key, stored.value).remove(legacyKey).commit()
                }.onFailure {
                    Log.e(TAG, "Could not migrate trusted-device revision; legacy source retained", it)
                }.getOrDefault(false)
                if (migrated) stored.value else Long.MIN_VALUE
            }
            is PreferenceRead.Corrupt -> Long.MIN_VALUE // keep malformed source for diagnosis
        }
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
            when (val previous = SafePreferences.readLong(prefs, oldKey, 0L)) {
                is PreferenceRead.Value -> {
                    if (!prefs.contains(newKey)) edit.putLong(newKey, previous.value)
                    edit.remove(oldKey)
                }
                is PreferenceRead.Corrupt -> Unit // retain malformed legacy entry
            }
        }
        check(edit.commit()) { "Could not securely migrate trusted-device revision keys." }
    }

    @Synchronized
    fun isBaselineConnected(prefs: SharedPreferences, address: String): Boolean =
        isValidAddress(address) && baselineAddresses(prefs).any { it.equals(address.trim(), ignoreCase = true) }

    @Synchronized
    fun markConnected(prefs: SharedPreferences, address: String, connected: Boolean): Boolean {
        if (!isValidAddress(address)) return false
        val normalizedAddress = address.trim().uppercase()
        val old = when (val stored = readBaselineAddresses(prefs)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return false
        }
        val hadBaseline = old.any { it.equals(normalizedAddress, ignoreCase = true) }
        val updated = old.filterNot { it.equals(normalizedAddress, ignoreCase = true) }.toMutableSet()
        if (connected && isTrusted(prefs, normalizedAddress)) updated.add(normalizedAddress)
        val committed = runCatching {
            val encrypted = LocalPreferenceCipher.encrypt(KEY_BASELINES, updated.joinToString("\n"))
            prefs.edit().putString(KEY_BASELINES, encrypted).commit()
        }.onFailure {
            Log.e(TAG, "Could not encrypt or commit Bluetooth connection baseline; existing source retained", it)
        }.getOrDefault(false)
        return committed && hadBaseline
    }

    @Synchronized
    fun clearBaselines(prefs: SharedPreferences) {
        when (val baseline = readBaselineAddresses(prefs)) {
            is PreferenceRead.Value -> Unit
            is PreferenceRead.Corrupt -> throw IllegalStateException("Corrupt Bluetooth baselines retained; refusing to overwrite them.")
        }
        val nextRevision = when (val stored = SafePreferences.readLong(prefs, KEY_REVISION, 0L)) {
            is PreferenceRead.Value -> stored.value + 1L
            is PreferenceRead.Corrupt -> throw IllegalStateException("Corrupt Bluetooth revision retained; refusing to overwrite it.")
        }
        check(prefs.edit()
            .putString(KEY_BASELINES, LocalPreferenceCipher.encrypt(KEY_BASELINES, ""))
            .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
            .putLong(KEY_REVISION, nextRevision)
            .commit()) { "Could not securely clear trusted Bluetooth baselines." }
    }

    private fun baselineAddresses(prefs: SharedPreferences): Set<String> =
        when (val stored = readBaselineAddresses(prefs)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> emptySet()
        }

    @Synchronized
    private fun readBaselineAddresses(prefs: SharedPreferences): PreferenceRead<Set<String>> {
        val raw = when (val stored = SafePreferences.readString(prefs, KEY_BASELINES, null)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return PreferenceRead.Corrupt(KEY_BASELINES)
        } ?: return PreferenceRead.Value(emptySet())
        return runCatching {
            val decoded = readOrMigratePayload(prefs, KEY_BASELINES, raw)
                .lineSequence().filter { it.isNotBlank() }.toSet()
            require(decoded.all(::isValidAddress)) { "Bluetooth baseline contains an invalid address." }
            PreferenceRead.Value(decoded)
        }.onFailure { Log.e(TAG, "Bluetooth baseline payload is unreadable; encrypted source retained", it) }
            .getOrElse { PreferenceRead.Corrupt(KEY_BASELINES) }
    }

    private fun readOrMigratePayload(prefs: SharedPreferences, key: String, raw: String): String {
        if (LocalPreferenceCipher.isEncrypted(raw)) return LocalPreferenceCipher.decrypt(key, raw)
        val encrypted = LocalPreferenceCipher.encrypt(key, raw)
        check(prefs.edit().putString(key, encrypted).commit()) { "Could not securely migrate legacy trusted Bluetooth data." }
        return raw
    }

    internal fun isValidAddress(address: String): Boolean =
        Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$").matches(address.trim())

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
        .onEach { require(isValidAddress(it.address)) { "Trusted Bluetooth device address is invalid." } }
        .distinctBy { it.address.lowercase() }
        .joinToString("\n") { device ->
            val name = encodeBase64(device.name.toByteArray(StandardCharsets.UTF_8))
            "${device.address.trim().uppercase()}\t$name"
        }

    internal fun decode(raw: String): List<TrustedBluetoothDevice> =
        raw.lineSequence().filter { it.isNotBlank() }.map { line ->
            val split = line.split('\t', limit = 2)
            require(split.size == 2 && isValidAddress(split[0])) {
                "Trusted Bluetooth registry contains an invalid device entry."
            }
            val name = String(decodeBase64(split[1]), StandardCharsets.UTF_8)
            TrustedBluetoothDevice(split[0].trim().uppercase(), name.ifBlank { "Bluetooth accessory" })
        }.distinctBy { it.address.lowercase() }.toList()
}
