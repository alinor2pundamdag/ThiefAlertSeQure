package com.thiefalertsequre.app

import androidx.biometric.BiometricPrompt
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** A per-use, Android Keystore-backed HMAC required to complete biometric owner authentication. */
internal object OwnerAuthenticationCrypto {
    private const val KEY_ALIAS = "com.thiefalertsequre.owner_auth_hmac_v1"
    private const val MAC_ALGORITHM = "HmacSHA256"

    fun createCryptoObject(): BiometricPrompt.CryptoObject {
        val mac = try {
            newMac(getOrCreateKey())
        } catch (error: InvalidKeyException) {
            deleteKey()
            newMac(generateKey())
        }
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        mac.update(challenge)
        return BiometricPrompt.CryptoObject(mac)
    }

    fun consume(result: BiometricPrompt.AuthenticationResult): ByteArray? {
        val authenticatedMac = result.cryptoObject?.mac ?: return null
        return runCatching { authenticatedMac.doFinal() }
            .getOrNull()
            ?.takeIf { it.size == 32 }
    }

    private fun newMac(key: SecretKey): Mac = Mac.getInstance(MAC_ALGORITHM).apply { init(key) }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return generateKey()
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setKeySize(256)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun deleteKey() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }
}
