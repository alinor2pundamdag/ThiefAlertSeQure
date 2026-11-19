package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmCallbackPolicyTest {
    @Test fun eitherAcceptedAlarmChannelRetainsTheDeadline() {
        assertTrue(AlarmCallbackPolicy.hasUsableDeadlineCallback(exactScheduled = true, fallbackScheduled = false))
        assertTrue(AlarmCallbackPolicy.hasUsableDeadlineCallback(exactScheduled = false, fallbackScheduled = true))
        assertTrue(AlarmCallbackPolicy.hasUsableDeadlineCallback(exactScheduled = true, fallbackScheduled = true))
        assertFalse(AlarmCallbackPolicy.hasUsableDeadlineCallback(exactScheduled = false, fallbackScheduled = false))
    }
}
