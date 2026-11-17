package com.thiefalertsequre.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-encrypted, portable backup format. The Android Keystore key is never exported. */
internal object EvidenceVaultBackup {
    private val MAGIC = byteArrayOf(0x54, 0x41, 0x53, 0x45, 0x56, 0x31) // TASEV1
    private val AAD = "ThiefAlertSeQure evidence backup v1".toByteArray(Charsets.UTF_8)
    private const val ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 150_000
    private const val MAX_ITERATIONS = 500_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MAX_BACKUP_BYTES = 1_000_000
    private const val MAX_RECORDS = 100

    fun encrypt(entries: List<LocalEvidenceVault.Entry>, password: CharArray): ByteArray {
        try {
            require(password.size >= 12) { "Backup password must contain at least 12 characters." }
            require(entries.size <= MAX_RECORDS) { "Evidence archive exceeds the record limit." }
            val json = JSONArray().apply {
                entries.forEach { entry ->
                    put(JSONObject().apply {
                        put("at", entry.timestampEpochMs)
                        put("tz", entry.timezoneOffsetMinutes)
                        put("event", entry.event)
                        put("detail", entry.detail)
                        put("sha256", entry.integritySha256)
                    })
                }
            }.toString().toByteArray(Charsets.UTF_8)
            val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
            val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
            val key = deriveKey(password, salt, ITERATIONS)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD)
            val encrypted = cipher.doFinal(json)
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use { data ->
                data.write(MAGIC)
                data.writeInt(ITERATIONS)
                data.write(salt)
                data.write(iv)
                data.writeInt(encrypted.size)
                data.write(encrypted)
            }
            require(output.size() <= MAX_BACKUP_BYTES) { "Encrypted backup is too large." }
            return output.toByteArray()
        } finally {
            password.fill('\u0000')
        }
    }

    fun decrypt(bytes: ByteArray, password: CharArray): List<LocalEvidenceVault.Entry> {
        try {
            require(password.size >= 12) { "Backup password must contain at least 12 characters." }
            require(bytes.size in (MAGIC.size + 4 + SALT_BYTES + IV_BYTES + 4 + 16)..MAX_BACKUP_BYTES) {
                "Backup file size is invalid."
            }
            val input = DataInputStream(ByteArrayInputStream(bytes))
            val magic = ByteArray(MAGIC.size).also(input::readFully)
            require(magic.contentEquals(MAGIC)) { "This is not a ThiefAlertSeQure evidence backup." }
            val iterations = input.readInt()
            require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Backup key-derivation parameters are invalid." }
            val salt = ByteArray(SALT_BYTES).also(input::readFully)
            val iv = ByteArray(IV_BYTES).also(input::readFully)
            val encryptedSize = input.readInt()
            require(encryptedSize in 16..MAX_BACKUP_BYTES && encryptedSize == input.available()) {
                "Backup payload length is invalid."
            }
            val encrypted = ByteArray(encryptedSize).also(input::readFully)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD)
            val array = JSONArray(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            require(array.length() <= MAX_RECORDS) { "Backup contains too many records." }
            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val at = item.getLong("at")
                    val tz = item.getInt("tz")
                    val event = LocalTamperHistoryPolicy.sanitizeEvent(item.getString("event"))
                    val detail = LocalTamperHistoryPolicy.sanitizeDetail(item.optString("detail")).take(240)
                    val hash = item.getString("sha256")
                    require(hash == digest(at, tz, event, detail)) { "Backup record integrity check failed." }
                    add(LocalEvidenceVault.Entry(at, tz, event, detail, hash))
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun digest(at: Long, tz: Int, event: String, detail: String): String {
        val bytes = "$at|$tz|$event|$detail".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
