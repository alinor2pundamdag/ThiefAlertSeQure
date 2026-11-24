package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyAcknowledgementPolicyTest {
    @Test
    fun currentAcknowledgementIsAccepted() {
        assertTrue(
            PolicyAcknowledgementPolicy.isCurrent(
                "acknowledged",
                PolicyAcknowledgementPolicy.PRIVACY_VERSION,
                PolicyAcknowledgementPolicy.PRIVACY_VERSION
            )
        )
    }

    @Test
    fun oldAcknowledgementIsRejectedAfterPolicyVersionChanges() {
        assertFalse(
            PolicyAcknowledgementPolicy.isCurrent(
                "acknowledged",
                PolicyAcknowledgementPolicy.PRIVACY_VERSION,
                PolicyAcknowledgementPolicy.PRIVACY_VERSION + 1
            )
        )
    }

    @Test
    fun missingVersionIsRejectedEvenWhenDecisionExists() {
        assertFalse(PolicyAcknowledgementPolicy.isCurrent("acknowledged", -1, PolicyAcknowledgementPolicy.TERMS_VERSION))
    }

    @Test
    fun missingOrDifferentDecisionIsRejected() {
        assertFalse(PolicyAcknowledgementPolicy.isCurrent(null, PolicyAcknowledgementPolicy.TERMS_VERSION, PolicyAcknowledgementPolicy.TERMS_VERSION))
        assertFalse(PolicyAcknowledgementPolicy.isCurrent("declined", PolicyAcknowledgementPolicy.TERMS_VERSION, PolicyAcknowledgementPolicy.TERMS_VERSION))
    }
}
