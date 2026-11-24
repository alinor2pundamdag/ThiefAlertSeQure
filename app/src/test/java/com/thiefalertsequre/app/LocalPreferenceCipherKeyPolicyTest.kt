package com.thiefalertsequre.app

import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class LocalPreferenceCipherKeyPolicyTest {
    @Test fun existingKeyIsReturnedWithoutReplacement() {
        val existing = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        assertSame(existing, LocalPreferenceCipherKeyPolicy.requireExistingKey(existing))
    }

    @Test fun missingKeyFailsClosedInsteadOfCreatingReplacement() {
        assertThrows(IllegalStateException::class.java) {
            LocalPreferenceCipherKeyPolicy.requireExistingKey(null)
        }
    }
}
