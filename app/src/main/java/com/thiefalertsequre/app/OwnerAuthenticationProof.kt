package com.thiefalertsequre.app

import android.app.Activity
import android.content.Context
import androidx.biometric.BiometricPrompt

/**
 * Ephemeral proof emitted only after an Android credential callback or a
 * successful Keystore-backed BiometricPrompt operation. It is never persisted.
 */
internal class OwnerAuthenticationProof private constructor(
    internal val source: String,
    private val issuedElapsedRealtimeMs: Long,
    private val issuedBootCount: Int,
    private val biometricMacProof: ByteArray?
) {
    internal fun isFresh(nowElapsedRealtimeMs: Long, currentBootCount: Int): Boolean {
        if (!OwnerAuthenticationProofPolicy.isFresh(
                issuedElapsedRealtimeMs, nowElapsedRealtimeMs, issuedBootCount, currentBootCount
            )
        ) return false
        return when (source) {
            SOURCE_BIOMETRIC -> biometricMacProof?.size == 32
            SOURCE_DEVICE_CREDENTIAL -> biometricMacProof == null
            else -> false
        }
    }

    companion object {
        private const val SOURCE_BIOMETRIC = "biometric_keystore"
        private const val SOURCE_DEVICE_CREDENTIAL = "android_device_credential"

        fun fromBiometricKeystoreProof(context: Context, proof: ByteArray): OwnerAuthenticationProof? {
            if (proof.size != 32) return null
            return OwnerAuthenticationProof(
                SOURCE_BIOMETRIC,
                ProtectionClock.elapsedRealtime(),
                ProtectionClock.bootCount(context),
                proof.copyOf()
            )
        }

        fun fromBiometricPromptCredentialResult(
            context: Context,
            authenticationType: Int
        ): OwnerAuthenticationProof? {
            if (authenticationType != BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL) return null
            return deviceCredential(context)
        }

        fun fromKeyguardResult(context: Context, resultCode: Int): OwnerAuthenticationProof? {
            if (resultCode != Activity.RESULT_OK) return null
            return deviceCredential(context)
        }

        private fun deviceCredential(context: Context): OwnerAuthenticationProof =
            OwnerAuthenticationProof(
                SOURCE_DEVICE_CREDENTIAL,
                ProtectionClock.elapsedRealtime(),
                ProtectionClock.bootCount(context),
                null
            )
    }
}

internal object OwnerAuthenticationProofPolicy {
    const val MAX_AGE_MS = 30_000L

    fun isFresh(
        issuedElapsedRealtimeMs: Long,
        nowElapsedRealtimeMs: Long,
        issuedBootCount: Int,
        currentBootCount: Int
    ): Boolean {
        if (issuedElapsedRealtimeMs <= 0L || nowElapsedRealtimeMs < issuedElapsedRealtimeMs) return false
        if (nowElapsedRealtimeMs - issuedElapsedRealtimeMs > MAX_AGE_MS) return false
        if (issuedBootCount >= 0 && currentBootCount >= 0 && issuedBootCount != currentBootCount) return false
        return true
    }
}
