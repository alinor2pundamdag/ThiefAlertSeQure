package com.thiefalertsequre.app

import androidx.biometric.BiometricManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerAuthenticationCryptoTest {
    @Test
    fun createsPromptCryptoObjectWithoutUsingKeyBeforeAuthentication() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val strongBiometricStatus = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        // A per-use biometric-bound Keystore key cannot be provisioned on an
        // emulator/device with no enrolled strong biometric. Device-credential
        // fallback is tested through the separate verified-proof path.
        assumeTrue(
            "Requires enrolled BIOMETRIC_STRONG hardware; no biometric enrollment is available",
            strongBiometricStatus == BiometricManager.BIOMETRIC_SUCCESS
        )

        val prepared = OwnerAuthenticationCrypto.createCryptoObject()
        val mac = prepared.cryptoObject.mac
        assertNotNull("Biometric prompt must receive a Keystore-backed MAC", mac)

        // Android 7 reports ProviderException; newer providers may report a
        // more specific authentication exception. Both are expected denial outcomes.
        try {
            mac!!.update(ByteArray(32) { 1 })
            mac.doFinal()
            fail("The owner-authentication key must reject use before system authentication")
        } catch (_: Exception) {
            // Expected: unauthenticated use of this key must fail closed.
        }
    }
}
