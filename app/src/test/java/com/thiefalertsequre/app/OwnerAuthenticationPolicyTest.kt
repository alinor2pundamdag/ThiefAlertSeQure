package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerAuthenticationPolicyTest {
    @Test
    fun prefersEnrolledStrongBiometricWhenAvailable() {
        assertEquals(
            OwnerAuthenticationMethod.BIOMETRIC,
            OwnerAuthenticationPolicy.select(
                strongBiometricAvailable = true,
                deviceCredentialAvailable = true
            )
        )
    }

    @Test
    fun usesDeviceCredentialWhenNoStrongBiometricIsEnrolled() {
        assertEquals(
            OwnerAuthenticationMethod.DEVICE_CREDENTIAL,
            OwnerAuthenticationPolicy.select(
                strongBiometricAvailable = false,
                deviceCredentialAvailable = true
            )
        )
    }

    @Test
    fun rejectsAuthenticationWhenNeitherMethodIsConfigured() {
        assertEquals(
            OwnerAuthenticationMethod.UNAVAILABLE,
            OwnerAuthenticationPolicy.select(
                strongBiometricAvailable = false,
                deviceCredentialAvailable = false
            )
        )
    }
}
