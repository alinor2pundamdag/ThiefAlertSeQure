package com.thiefalertsequre.app

import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.util.Base64

data class TrustedBluetoothDevice(val address: String, val name: String)

/**
 * Local A2 allow-list. Android owns pairing; this registry stores only app trust.
 * Device names, addresses and connection baselines are encrypted with Android Keystore.
 * Existing plaintext values are migrated on read without changing trust membership.
 */
object TrustedBluetoothRegistry {
    const val KEY_DEVICES = "trusted_bluetooth_devices_v1"
    private const val KEY_BASELINES = "trusted_bluetooth_baselines_v1"
    private const val KEY_REVISION = "trusted_bluetooth_registry_revision_v1"

    fun read(prefs: SharedPreferences): List<TrustedBluetoothDevice> =
        readInternal(prefs) ?: emptyList()

    private fun readInternal(prefs: SharedPreferences): List<TrustedBluetoothDevice>? {
        val raw = runCatching { prefs.getString(KEY_DEVICES, null) }.getOrElse {
            prefs.edit().remove(KEY_DEVICES).commit()
            null
        }
        if (raw == null) {
            val legacyAddress = runCatching {
                prefs.getString(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS, null)
            }.getOrNull()
            if (legacyAddress.isNullOrBlank() || !isValidAddress(legacyAddress)) {
                prefs.edit()
                    .remove(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)
                    .remove(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)
                    .commit()
                return emptyList()
            }
            val legacyName = runCatching {
                prefs.getString(ProtectionMonitorService.KEY_BT_TRUSTED_NAME, null)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Bluetooth accessory"
            val migrated = listOf(TrustedBluetoothDevice(legacyAddress, legacyName))
            return runCatching {
                val encryptedDevices = LocalPreferenceCipher.encrypt(KEY_DEVICES, encode(migrated))
                val encryptedBaselines = LocalPreferenceCipher.encrypt(KEY_BASELINES, "")
                val saved = prefs.edit()
                    .putString(KEY_DEVICES, encryptedDevices)
                    .putString(KEY_BASELINES, encryptedBaselines)
                    .remove(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)
                    .remove(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)
                    .commit()
                if (saved) migrated else null
            }.getOrNull()
        }
        val decoded = runCatching {
            decode(readOrMigratePayload(prefs, KEY_DEVICES, raw))
                .filter { isValidAddress(it.address) }
        }.getOrNull() ?: return null
        // Legacy single-device fields are removed only after the new registry is readable.
        if (prefs.contains(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS) ||
            prefs.contains(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)
        ) {
            prefs.edit()
                .remove(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)
                .remove(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)
                .commit()
        }
        return decoded
    }

    fun add(prefs: SharedPreferences, address: String, name: String): Boolean {
        if (!isValidAddress(address)) return false
        val current = readInternal(prefs) ?: return false
        return runCatching {
            val updated = (current.filterNot { it.address.equals(address, ignoreCase = true) } +
                TrustedBluetoothDevice(address, name.ifBlank { "Bluetooth accessory" }))
                .sortedBy { it.name.lowercase() }
            val baselines = baselineAddresses(prefs).filterNot { it.equals(address, ignoreCase = true) }
            prefs.edit()
                .putString(KEY_DEVICES, LocalPreferenceCipher.encrypt(KEY_DEVICES, encode(updated)))
                .putString(KEY_BASELINES, LocalPreferenceCipher.encrypt(KEY_BASELINES, baselines.joinToString("\n")))
                .putLong(deviceRevisionKey(address), deviceRevision(prefs, address) + 1L)
                .commit()
        }.getOrDefault(false)
    }

    /** Removes only app trust. If secure persistence fails, A2 is disabled fail-closed. */
    fun remove(prefs: SharedPreferences, address: String): Boolean {
        val current = readInternal(prefs) ?: return false
        val updated = current.filterNot { it.address.equals(address, ignoreCase = true) }
        if (updated.size == current.size) return false
        val saved = runCatching {
            val baselines = baselineAddresses(prefs).filterNot { it.equals(address, ignoreCase = true) }
            val edit = prefs.edit()
                .putString(KEY_DEVICES, LocalPreferenceCipher.encrypt(KEY_DEVICES, encode(updated)))
                .putString(KEY_BASELINES, LocalPreferenceCipher.encrypt(KEY_BASELINES, baselines.joinToString("\n")))
                .putLong(deviceRevisionKey(address), deviceRevision(prefs, address) + 1L)
            if (updated.isEmpty()) {
                edit.putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
                    .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
                    .remove(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS)
                    .remove(ProtectionMonitorService.KEY_BT_TRUSTED_NAME)
            }
            edit.commit()
        }.getOrDefault(false)
        if (!saved) disableA2AndInvalidateBaselines(prefs)
        return saved
    }

    fun isTrusted(prefs: SharedPreferences, address: String): Boolean =
        readInternal(prefs)?.any { it.address.equals(address, ignoreCase = true) } == true

    fun revision(prefs: SharedPreferences): Long =
        runCatching { prefs.getLong(KEY_REVISION, 0L) }.getOrDefault(0L)

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
        val saved = runCatching {
            prefs.edit()
                .putString(KEY_BASELINES, LocalPreferenceCipher.encrypt(KEY_BASELINES, updated.joinToString("\n")))
                .commit()
        }.getOrDefault(false)
        if (!saved) disableA2AndInvalidateBaselines(prefs)
        return saved && hadBaseline
    }

    fun clearBaselines(prefs: SharedPreferences) {
        // Invalidate queued 8-second callbacks when A2 is disabled, re-enabled, or restarted.
        val saved = runCatching {
            prefs.edit()
                .putString(KEY_BASELINES, LocalPreferenceCipher.encrypt(KEY_BASELINES, ""))
                .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
                .putLong(KEY_REVISION, revision(prefs) + 1L)
                .commit()
        }.getOrDefault(false)
        if (!saved) disableA2AndInvalidateBaselines(prefs)
    }

    private fun disableA2AndInvalidateBaselines(prefs: SharedPreferences) {
        // Never keep A2 active with a stale or unreadable baseline.
        prefs.edit()
            .remove(KEY_BASELINES)
            .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
            .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
            .putLong(KEY_REVISION, revision(prefs) + 1L)
            .apply()
    }

    private fun baselineAddresses(prefs: SharedPreferences): Set<String> {
        val raw = runCatching { prefs.getString(KEY_BASELINES, null) }.getOrNull() ?: return emptySet()
        val plaintext = runCatching { readOrMigratePayload(prefs, KEY_BASELINES, raw) }.getOrNull()
            ?: return emptySet()
        return plaintext.lineSequence().filter { isValidAddress(it) }.toSet()
    }

    private fun readOrMigratePayload(prefs: SharedPreferences, key: String, raw: String): String {
        if (LocalPreferenceCipher.isEncrypted(raw)) return LocalPreferenceCipher.decrypt(key, raw)
        val encrypted = LocalPreferenceCipher.encrypt(key, raw)
        check(prefs.edit().putString(key, encrypted).commit()) {
            "Could not securely migrate legacy trusted Bluetooth preferences."
        }
        return raw
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

    internal fun isValidAddress(address: String): Boolean =
        Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$").matches(address)
}
