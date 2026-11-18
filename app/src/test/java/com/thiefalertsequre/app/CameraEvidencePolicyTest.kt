package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraEvidencePolicyTest {
    @Test fun captureRequiresAllOwnerAndPlatformGates() {
        assertTrue(CameraEvidencePolicy.shouldCapture(true, true, true, true))
        assertFalse(CameraEvidencePolicy.shouldCapture(false, true, true, true))
        assertFalse(CameraEvidencePolicy.shouldCapture(true, false, true, true))
        assertFalse(CameraEvidencePolicy.shouldCapture(true, true, false, true))
        assertFalse(CameraEvidencePolicy.shouldCapture(true, true, true, false))
    }

    @Test fun frontCameraJpegOrientationUsesFrontFacingDisplayRotation() {
        assertEquals(270, CameraEvidencePolicy.jpegOrientation(270, 0, true))
        assertEquals(0, CameraEvidencePolicy.jpegOrientation(270, 90, true))
        assertEquals(180, CameraEvidencePolicy.jpegOrientation(270, 270, true))
    }

    @Test fun backCameraJpegOrientationUsesOppositeDisplayRotation() {
        assertEquals(90, CameraEvidencePolicy.jpegOrientation(90, 0, false))
        assertEquals(0, CameraEvidencePolicy.jpegOrientation(90, 90, false))
        assertEquals(180, CameraEvidencePolicy.jpegOrientation(90, 270, false))
    }

    @Test fun jpegOrientationNormalizesUnexpectedDegrees() {
        assertEquals(270, CameraEvidencePolicy.jpegOrientation(-90, 0, true))
        assertEquals(180, CameraEvidencePolicy.jpegOrientation(450, 90, true))
    }

    @Test fun retentionAndImageSizeAreBounded() {
        val now = 2_000_000_000_000L
        assertTrue(CameraEvidencePolicy.isRetained(now, now))
        assertTrue(CameraEvidencePolicy.isRetained(now - CameraEvidencePolicy.RETENTION_MS, now))
        assertFalse(CameraEvidencePolicy.isRetained(now - CameraEvidencePolicy.RETENTION_MS - 1, now))
        assertFalse(CameraEvidencePolicy.isRetained(now + 1, now))
        assertFalse(CameraEvidencePolicy.isRetained(0, now))
        assertTrue(CameraEvidencePolicy.isValidImageSize(1))
        assertTrue(CameraEvidencePolicy.isValidImageSize(CameraEvidencePolicy.MAX_IMAGE_BYTES))
        assertFalse(CameraEvidencePolicy.isValidImageSize(0))
        assertFalse(CameraEvidencePolicy.isValidImageSize(CameraEvidencePolicy.MAX_IMAGE_BYTES + 1))
    }
}
