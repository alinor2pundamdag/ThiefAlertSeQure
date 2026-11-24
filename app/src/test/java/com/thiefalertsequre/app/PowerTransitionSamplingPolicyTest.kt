package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTransitionSamplingPolicyTest {
    @Test fun confirmsConnectAfterThreeConsecutiveSamplesWithoutWaitingForUnusedReads() {
        val samples = listOf(true, true, true, false, false)
        val delays = mutableListOf<Long>()
        var index = 0

        val result = PowerTransitionSamplingPolicy.confirm(
            expectedConnected = true,
            readConnected = { samples[index++] },
            pause = { delays += it }
        )

        assertTrue(result.confirmed)
        assertEquals(3, result.sampleCount)
        assertEquals(3, index)
        assertEquals(listOf(100L, 100L), delays)
    }

    @Test fun confirmsDisconnectAfterUnknownAndOppositeSamplesResetTheSequence() {
        val samples = listOf(false, null, false, false, false)
        val delays = mutableListOf<Long>()
        var index = 0

        val result = PowerTransitionSamplingPolicy.confirm(
            expectedConnected = false,
            readConnected = { samples[index++] },
            pause = { delays += it }
        )

        assertTrue(result.confirmed)
        assertEquals(5, result.sampleCount)
        assertEquals(4, delays.size)
    }

    @Test fun rejectsUnstablePowerStateAfterBoundedFiveReadWindow() {
        val samples = listOf(false, true, false, true, false)
        var index = 0

        val result = PowerTransitionSamplingPolicy.confirm(
            expectedConnected = false,
            readConnected = { samples[index++] },
            pause = {}
        )

        assertFalse(result.confirmed)
        assertEquals(5, result.sampleCount)
        assertEquals(false, result.lastObserved)
    }

    @Test fun unknownSamplesNeverCountAsConnectedOrDisconnectedConfirmation() {
        val result = PowerTransitionSamplingPolicy.confirm(
            expectedConnected = true,
            readConnected = { null },
            pause = {}
        )

        assertFalse(result.confirmed)
        assertEquals(5, result.sampleCount)
        assertEquals(null, result.lastObserved)
    }
}
