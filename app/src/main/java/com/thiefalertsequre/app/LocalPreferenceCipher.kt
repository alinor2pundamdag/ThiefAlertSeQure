package com.thiefalertsequre.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encryption-key lookup rules shared by the Android Keystore adapter and JVM tests. */
internal object LocalPreferenceCipherKeyPolicy {
    fun requireExistingKey(key: SecretKey?): SecretKey =
        key ?: throw IllegalStateException(
            "Private preference encryption key is missing; encrypted data was preserved and cannot be decrypted."
        )
}

/** Encrypts local Bluetooth identifiers before they enter SharedPreferences. */
internal object LocalPreferenceCipher {
    private const val KEY_ALIAS = "com.thiefalertsequre.private_preferences_v1"
    private const val PREFIX = "tas-aesgcm:v1:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val lock = Any()

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    fun encrypt(purpose: String, plaintext: String): String {
        val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(purpose.toByteArray(StandardCharsets.UTF_8))
            val encrypted = cipher.doFinal(plaintextBytes)
            val iv = cipher.iv
            try {
                require(iv.size == IV_BYTES) { "Unexpected Android Keystore AES-GCM IV length." }
                val payload = ByteArray(1 + iv.size + encrypted.size)
                try {
                    payload[0] = iv.size.toByte()
                    System.arraycopy(iv, 0, payload, 1, iv.size)
                    System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)
                    return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
                } finally {
                    payload.fill(0)
                }
            } finally {
                encrypted.fill(0)
                iv.fill(0)
            }
        } finally {
            plaintextBytes.fill(0)
        }
    }

    fun decrypt(purpose: String, encoded: String): String {
        require(isEncrypted(encoded)) { "Private preference payload is not encrypted." }
        val payload = Base64.decode(encoded.removePrefix(PREFIX), Base64.NO_WRAP)
        var iv: ByteArray? = null
        var ciphertext: ByteArray? = null
        var plaintextBytes: ByteArray? = null
        try {
            require(payload.isNotEmpty()) { "Private preference payload is empty." }
            val ivSize = payload[0].toInt() and 0xff
            require(ivSize == IV_BYTES && payload.size >= 1 + ivSize + 16) {
                "Private preference payload has an invalid AES-GCM structure."
            }
            iv = payload.copyOfRange(1, 1 + ivSize)
            ciphertext = payload.copyOfRange(1 + ivSize, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            // Decryption must never create a replacement key: doing so would hide
            // a missing original key and make every existing ciphertext unreadable.
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(purpose.toByteArray(StandardCharsets.UTF_8))
            plaintextBytes = cipher.doFinal(ciphertext)
            return String(plaintextBytes, StandardCharsets.UTF_8)
        } finally {
            payload.fill(0)
            iv?.fill(0)
            ciphertext?.fill(0)
            plaintextBytes?.fill(0)
        }
    }

    private fun getOrCreateKey(): SecretKey = synchronized(lock) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            generator.init(spec)
            generator.generateKey()
        }
    }

    private fun getExistingKey(): SecretKey = synchronized(lock) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        LocalPreferenceCipherKeyPolicy.requireExistingKey(
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        )
    }
}
