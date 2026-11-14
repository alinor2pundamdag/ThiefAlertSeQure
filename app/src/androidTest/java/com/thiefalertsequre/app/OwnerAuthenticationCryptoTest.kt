package com.thiefalertsequre.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerAuthenticationCryptoTest {
    @Test
    fun createsPromptCryptoObjectWithoutUsingKeyBeforeAuthentication() {
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
