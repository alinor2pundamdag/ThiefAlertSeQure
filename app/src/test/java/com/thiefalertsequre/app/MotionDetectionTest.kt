package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionDetectionTest {
    @Test fun higherSensitivityUsesLowerMovementThreshold() {
        assertTrue(MotionSensitivity.HIGH.thresholdMetersPerSecondSquared < MotionSensitivity.MEDIUM.thresholdMetersPerSecondSquared)
        assertTrue(MotionSensitivity.MEDIUM.thresholdMetersPerSecondSquared < MotionSensitivity.LOW.thresholdMetersPerSecondSquared)
    }

    @Test fun stationaryVectorDoesNotTrigger() {
        assertFalse(MotionDetection.exceedsThreshold(0f, 0f, 0f, MotionSensitivity.HIGH))
    }

    @Test fun highSensitivityDetectsSmallerMovementThanLow() {
        assertTrue(MotionDetection.exceedsThreshold(1.5f, 0f, 0f, MotionSensitivity.HIGH))
        assertFalse(MotionDetection.exceedsThreshold(1.5f, 0f, 0f, MotionSensitivity.LOW))
    }

    @Test fun magnitudeUsesAllAxes() {
        assertEquals(5f, MotionDetection.magnitude(3f, 4f, 0f), 0.001f)
    }
}
