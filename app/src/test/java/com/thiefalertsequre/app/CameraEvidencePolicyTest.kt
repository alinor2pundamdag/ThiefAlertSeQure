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

    @Test fun sourceJpegBufferAllocationIsBoundedBeforeDecode() {
        assertTrue(CameraEvidencePolicy.isValidSourceImageSize(1))
        assertTrue(CameraEvidencePolicy.isValidSourceImageSize(CameraEvidencePolicy.MAX_SOURCE_IMAGE_BYTES))
        assertFalse(CameraEvidencePolicy.isValidSourceImageSize(0))
        assertFalse(CameraEvidencePolicy.isValidSourceImageSize(CameraEvidencePolicy.MAX_SOURCE_IMAGE_BYTES + 1))
    }

    @Test fun jpegDownsamplingBoundsPortraitAndLandscapeDimensions() {
        assertEquals(1, CameraEvidencePolicy.decodeSampleSize(1280, 720, 1280, 720))
        assertEquals(4, CameraEvidencePolicy.decodeSampleSize(4000, 3000, 1280, 720))
        assertEquals(4, CameraEvidencePolicy.decodeSampleSize(1080, 2400, 1280, 720))
        assertEquals(1, CameraEvidencePolicy.decodeSampleSize(0, 720, 1280, 720))
    }

    @Test fun encryptedVaultBudgetAllowsOnlyBoundedStagingAndOrphans() {
        assertTrue(CameraEvidencePolicy.isWithinStorageBudget(20, 20L * (2L * 1024L * 1024L + 64L)))
        assertTrue(CameraEvidencePolicy.isWithinStorageBudget(21, CameraEvidencePolicy.MAX_TOTAL_STORAGE_BYTES))
        assertFalse(CameraEvidencePolicy.isWithinStorageBudget(22, CameraEvidencePolicy.MAX_TOTAL_STORAGE_BYTES))
        assertFalse(CameraEvidencePolicy.isWithinStorageBudget(1, CameraEvidencePolicy.MAX_TOTAL_STORAGE_BYTES + 1L))
        assertFalse(CameraEvidencePolicy.isWithinStorageBudget(-1, 0L))
    }
}
