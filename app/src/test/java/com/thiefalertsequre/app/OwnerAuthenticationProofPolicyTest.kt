package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerAuthenticationProofPolicyTest {
    @Test fun acceptsFreshProofOnSameKnownBoot() {
        assertTrue(OwnerAuthenticationProofPolicy.isFresh(10_000L, 20_000L, 4, 4))
    }

    @Test fun rejectsExpiredProof() {
        assertFalse(OwnerAuthenticationProofPolicy.isFresh(10_000L, 50_001L, 4, 4))
    }

    @Test fun rejectsElapsedClockReset() {
        assertFalse(OwnerAuthenticationProofPolicy.isFresh(10_000L, 500L, -1, -1))
    }

    @Test fun rejectsKnownBootChange() {
        assertFalse(OwnerAuthenticationProofPolicy.isFresh(10_000L, 20_000L, 4, 5))
    }
}
