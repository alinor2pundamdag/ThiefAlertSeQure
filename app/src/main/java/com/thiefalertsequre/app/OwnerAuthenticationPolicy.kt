package com.thiefalertsequre.app

/**
 * Chooses the Android-owned authentication surface without assuming that every
 * device has enrolled biometrics. Biometrics take priority when available;
 * otherwise use the device's existing secure screen-lock credential.
 */
internal enum class OwnerAuthenticationMethod {
    BIOMETRIC,
    DEVICE_CREDENTIAL,
    UNAVAILABLE
}

internal object OwnerAuthenticationPolicy {
    fun select(
        strongBiometricAvailable: Boolean,
        deviceCredentialAvailable: Boolean
    ): OwnerAuthenticationMethod = when {
        strongBiometricAvailable -> OwnerAuthenticationMethod.BIOMETRIC
        deviceCredentialAvailable -> OwnerAuthenticationMethod.DEVICE_CREDENTIAL
        else -> OwnerAuthenticationMethod.UNAVAILABLE
    }
}
