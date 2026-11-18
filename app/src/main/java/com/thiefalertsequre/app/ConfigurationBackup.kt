package com.thiefalertsequre.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable encrypted allowlist of non-secret owner preferences.
 *
 * Deliberately excludes authentication secrets, protection/alarm state, Device
 * Owner policy snapshots, Bluetooth identities, permission grants, custom audio
 * content URIs, and the separate evidence vault.
 */
internal object ConfigurationBackup {
    private val MAGIC = byteArrayOf(0x54, 0x41, 0x53, 0x43, 0x46, 0x31) // TASCF1
    private val AAD = "ThiefAlertSeQure configuration backup v1".toByteArray(Charsets.UTF_8)
    private const val ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 150_000
    private const val MAX_ITERATIONS = 500_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MAX_BACKUP_BYTES = 128_000

    private data class Definition(
        val file: String,
        val key: String,
        val type: String,
        val defaultValue: Any,
        val allowedStrings: Set<String>? = null,
        val minimum: Long? = null,
        val maximum: Long? = null
    )

    private data class Entry(val definition: Definition, val value: Any)

    private val definitions = listOf(
        Definition("protection_monitor_v1", "monitor_enabled", "boolean", true),
        Definition("protection_monitor_v1", "motion_enabled", "boolean", false),
        Definition("protection_monitor_v1", "motion_sensitivity", "string", MotionSensitivity.MEDIUM.name,
            MotionSensitivity.entries.map { it.name }.toSet()),
        Definition("protection_monitor_v1", "battery_alert_enabled", "boolean", false),
        Definition("protection_monitor_v1", "battery_threshold", "int", 80, minimum = 50, maximum = 100),
        Definition("protection_monitor_v1", "trusted_bluetooth_alert_enabled", "boolean", false),

        Definition("alarm_flash_settings_v1", "enabled", "boolean", true),
        Definition("alarm_flash_settings_v1", "pattern", "string", AlarmFlashPattern.SLOW.name,
            AlarmFlashPattern.entries.map { it.name }.toSet()),
        Definition("alarm_flash_settings_v1", "speed_percent", "int", 100, minimum = 50, maximum = 200),
        Definition("alarm_flash_settings_v1", "custom_on_ms", "long", 700L, minimum = 100, maximum = 2_000),
        Definition("alarm_flash_settings_v1", "custom_off_ms", "long", 700L, minimum = 100, maximum = 2_000),

        Definition("alarm_haptics_v1", "enabled", "boolean", true),
        Definition("alarm_haptics_v1", "pattern", "string", AlarmHapticPattern.PULSE.name,
            AlarmHapticPattern.entries.map { it.name }.toSet()),

        Definition("user_alarm_sounds_v1", "built_in_alarm_preset", "string", AlarmTonePreset.DEFAULT_AUDIO.name,
            (listOf(AlarmTonePreset.DEFAULT_AUDIO) + AlarmTonePreset.builtIns).map { it.name }.toSet()),
        Definition("tas_theme_preferences_v1", "selected_theme_id", "string", ThemeStore.DEFAULT_ID,
            ThemeStore.all.map { it.id }.toSet()),
        Definition("alarm_display_settings_v1", "show_alarm_screen_over_lockscreen", "boolean", false),
        Definition("camera_evidence_settings", "encrypt_new_photos", "boolean", false)
    )

    fun encrypt(context: Context, password: CharArray): ByteArray {
        try {
            require(password.size >= 12) { "Configuration backup password must contain at least 12 characters." }
            val payload = snapshot(context).toString().toByteArray(Charsets.UTF_8)
            return encryptPayload(payload, password)
        } finally {
            password.fill('\u0000')
        }
    }

    fun decryptAndRestore(context: Context, bytes: ByteArray, password: CharArray): Int {
        try {
            require(password.size >= 12) { "Configuration backup password must contain at least 12 characters." }
            require(ProtectionStore(context).read().mode != ProtectionMode.ALARM_TRIGGERED) {
                "Configuration restore is blocked while protection is latched. Complete fresh owner authentication first."
            }
            val payload = decryptPayload(bytes, password)
            val imported = parse(payload)
            // Validate current values before changing any settings and retain a rollback snapshot.
            val previous = parse(snapshot(context).toString().toByteArray(Charsets.UTF_8))
            if (!apply(context, imported)) {
                if (!apply(context, previous)) {
                    throw IllegalStateException("Configuration restore failed and automatic rollback was incomplete. Do not restart the app until settings are reviewed.")
                }
                throw IllegalStateException("Android did not confirm every configuration write. Previous settings were restored.")
            }
            return imported.size
        } finally {
            password.fill('\u0000')
        }
    }

    private fun snapshot(context: Context): JSONObject {
        val array = JSONArray()
        definitions.forEach { definition ->
            val prefs = context.getSharedPreferences(definition.file, Context.MODE_PRIVATE)
            val value = readValue(prefs, definition)
            validate(definition, value)
            array.put(JSONObject().apply {
                put("file", definition.file)
                put("key", definition.key)
                put("type", definition.type)
                put("value", value)
            })
        }
        return JSONObject().put("schema", 2).put("settings", array)
    }

    private fun readValue(prefs: SharedPreferences, definition: Definition): Any =
        when (definition.type) {
            "boolean" -> when (val read = SafePreferences.readBoolean(prefs, definition.key, definition.defaultValue as Boolean)) {
                is PreferenceRead.Value -> read.value
                is PreferenceRead.Corrupt -> throw IllegalStateException("Cannot back up corrupted setting '\${definition.key}'.")
            }
            "int" -> when (val read = SafePreferences.readInt(prefs, definition.key, definition.defaultValue as Int)) {
                is PreferenceRead.Value -> read.value
                is PreferenceRead.Corrupt -> throw IllegalStateException("Cannot back up corrupted setting '\${definition.key}'.")
            }
            "long" -> when (val read = SafePreferences.readLong(prefs, definition.key, definition.defaultValue as Long)) {
                is PreferenceRead.Value -> read.value
                is PreferenceRead.Corrupt -> throw IllegalStateException("Cannot back up corrupted setting '\${definition.key}'.")
            }
            "string" -> when (val read = SafePreferences.readString(prefs, definition.key, definition.defaultValue as String)) {
                is PreferenceRead.Value -> read.value ?: definition.defaultValue
                is PreferenceRead.Corrupt -> throw IllegalStateException("Cannot back up corrupted setting '\${definition.key}'.")
            }
            else -> throw IllegalStateException("Unsupported configuration type.")
        }

    private fun parse(payload: ByteArray): List<Entry> {
        val root = JSONObject(String(payload, Charsets.UTF_8))
        val schema = root.getInt("schema")
        require(schema == 1 || schema == 2) { "Configuration backup version is not supported." }
        // Schema 1 predates the camera-evidence storage preference. Preserve the
        // current value when restoring an older backup instead of silently changing
        // the owner's photo privacy mode.
        val expectedDefinitions = if (schema == 1) {
            definitions.filterNot { it.file == "camera_evidence_settings" && it.key == "encrypt_new_photos" }
        } else definitions
        val array = root.getJSONArray("settings")
        require(array.length() == expectedDefinitions.size) { "Configuration backup is incomplete or contains unexpected settings." }
        val definitionMap = expectedDefinitions.associateBy { it.file to it.key }
        val seen = mutableSetOf<Pair<String, String>>()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val file = item.getString("file")
                val key = item.getString("key")
                val type = item.getString("type")
                val identity = file to key
                require(seen.add(identity)) { "Configuration backup contains a duplicate setting." }
                val definition = definitionMap[identity]
                    ?: throw IllegalArgumentException("Configuration backup contains an unsupported setting.")
                require(type == definition.type) { "Configuration setting type mismatch." }
                val value: Any = when (type) {
                    "boolean" -> item.getBoolean("value")
                    "int" -> item.getInt("value")
                    "long" -> item.getLong("value")
                    "string" -> item.getString("value")
                    else -> throw IllegalArgumentException("Unsupported configuration value type.")
                }
                validate(definition, value)
                add(Entry(definition, value))
            }
        }.also { require(it.size == expectedDefinitions.size) { "Configuration backup is missing settings." } }
    }

    private fun validate(definition: Definition, value: Any) {
        when (definition.type) {
            "boolean" -> require(value is Boolean) { "Invalid boolean setting." }
            "int" -> {
                require(value is Int) { "Invalid integer setting." }
                require(value.toLong() in (definition.minimum ?: Int.MIN_VALUE.toLong())..(definition.maximum ?: Int.MAX_VALUE.toLong())) {
                    "Configuration value '\${definition.key}' is outside its supported range."
                }
            }
            "long" -> {
                require(value is Long) { "Invalid long setting." }
                require(value in (definition.minimum ?: Long.MIN_VALUE)..(definition.maximum ?: Long.MAX_VALUE)) {
                    "Configuration value '\${definition.key}' is outside its supported range."
                }
            }
            "string" -> {
                require(value is String && value in definition.allowedStrings.orEmpty()) {
                    "Configuration value '\${definition.key}' is not recognized."
                }
            }
        }
    }

    private fun apply(context: Context, entries: List<Entry>): Boolean {
        entries.groupBy { it.definition.file }.forEach { (file, values) ->
            val editor = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            values.forEach { entry ->
                when (entry.definition.type) {
                    "boolean" -> editor.putBoolean(entry.definition.key, entry.value as Boolean)
                    "int" -> editor.putInt(entry.definition.key, entry.value as Int)
                    "long" -> editor.putLong(entry.definition.key, entry.value as Long)
                    "string" -> editor.putString(entry.definition.key, entry.value as String)
                }
            }
            if (!editor.commit()) return false
        }
        return true
    }

    internal fun encryptPayload(payload: ByteArray, password: CharArray): ByteArray {
        require(payload.size <= MAX_BACKUP_BYTES) { "Configuration backup is too large." }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(payload)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeInt(ITERATIONS)
            data.write(salt)
            data.write(iv)
            data.writeInt(encrypted.size)
            data.write(encrypted)
        }
        require(output.size() <= MAX_BACKUP_BYTES) { "Encrypted configuration backup is too large." }
        return output.toByteArray()
    }

    internal fun decryptPayload(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size in (MAGIC.size + 4 + SALT_BYTES + IV_BYTES + 4 + 16)..MAX_BACKUP_BYTES) {
            "Configuration backup file size is invalid."
        }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val magic = ByteArray(MAGIC.size).also(input::readFully)
        require(magic.contentEquals(MAGIC)) { "This is not a ThiefAlertSeQure configuration backup." }
        val iterations = input.readInt()
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Backup key-derivation parameters are invalid." }
        val salt = ByteArray(SALT_BYTES).also(input::readFully)
        val iv = ByteArray(IV_BYTES).also(input::readFully)
        val encryptedSize = input.readInt()
        require(encryptedSize in 16..MAX_BACKUP_BYTES && encryptedSize == input.available()) {
            "Configuration backup payload length is invalid."
        }
        val encrypted = ByteArray(encryptedSize).also(input::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(AAD)
        return cipher.doFinal(encrypted)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = javax.crypto.spec.PBEKeySpec(password, salt, iterations, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
