package com.thiefalertsequre.app

import android.security.keystore.UserNotAuthenticatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnerAuthenticationCryptoTest {
    @Test
    fun biometricMacCannotCompleteBeforeSystemAuthentication() {
        val crypto = OwnerAuthenticationCrypto.createCryptoObject()
        val mac = crypto.mac
        assertNotNull("Biometric prompt must receive a Keystore-backed MAC", mac)
        try {
            mac!!.doFinal()
            fail("The owner-authentication key must reject use before system authentication")
        } catch (_: UserNotAuthenticatedException) {
            // Expected: only a successful system prompt can authorize the MAC operation.
        }
    }
}
