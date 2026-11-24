package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmRecoveryPolicyTest {
    @Test fun retryBackoffIsBoundedAndOrdered() {
        assertEquals(1_000L, AlarmRecoveryPolicy.nextDelayMs(0))
        assertEquals(3_000L, AlarmRecoveryPolicy.nextDelayMs(1))
        assertEquals(10_000L, AlarmRecoveryPolicy.nextDelayMs(2))
    }

    @Test fun retriesStopAfterConfiguredMaximum() {
        assertNull(AlarmRecoveryPolicy.nextDelayMs(3))
        assertNull(AlarmRecoveryPolicy.nextDelayMs(4))
    }
}
