package com.thiefalertsequre.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Per-operation Android Keystore proof for opening private local history.
 *
 * The key is separate from data-encryption keys and cannot encrypt/decrypt until
 * the system authorizes the strong-biometric CryptoObject operation.
 */
internal object LocalHistoryBiometricCrypto {
    private const val KEY_ALIAS = "com.thiefalertsequre.local_history_auth_v1"
    private const val AAD = "thiefalertsequre:local-history-auth:v1"
    private const val CHALLENGE_BYTES = 32
    private const val GCM_TAG_BITS = 128

    fun newChallenge(): ByteArray = ByteArray(CHALLENGE_BYTES).also {
        SecureRandom().nextBytes(it)
    }

    fun createCipher(): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(AAD.toByteArray(Charsets.UTF_8))
        }
    }

    /** Returns true only after the authenticated Keystore operation completes. */
    fun verify(cipher: Cipher?, challenge: ByteArray): Boolean {
        if (cipher == null || challenge.size != CHALLENGE_BYTES) return false
        val proof = cipher.doFinal(challenge)
        return proof.size == challenge.size + GCM_TAG_BITS / 8
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
