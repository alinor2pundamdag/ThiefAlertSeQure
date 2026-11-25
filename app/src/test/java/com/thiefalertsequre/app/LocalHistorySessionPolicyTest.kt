package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHistorySessionPolicyTest {
    @Test
    fun sessionIsActiveBeforeDeadline() {
        assertTrue(LocalHistorySessionPolicy.isActive(9_999L, 10_000L))
    }

    @Test
    fun sessionExpiresAtExactDeadline() {
        assertFalse(LocalHistorySessionPolicy.isActive(10_000L, 10_000L))
    }

    @Test
    fun sessionIsExpiredAfterDeadline() {
        assertFalse(LocalHistorySessionPolicy.isActive(10_001L, 10_000L))
    }

    @Test
    fun missingOrInvalidDeadlineIsNotAuthenticated() {
        assertFalse(LocalHistorySessionPolicy.isActive(1L, 0L))
        assertFalse(LocalHistorySessionPolicy.isActive(1L, -1L))
    }
}
