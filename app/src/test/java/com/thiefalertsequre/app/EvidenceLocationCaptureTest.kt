package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceLocationCaptureTest {
    @Test fun classifiesRecentAndStaleCachedFixes() {
        assertEquals("RECENT_CACHED_FIX", EvidenceLocationCapture.classifyFixAge(0L))
        assertEquals("RECENT_CACHED_FIX", EvidenceLocationCapture.classifyFixAge(120_000L))
        assertEquals("STALE_CACHED_FIX", EvidenceLocationCapture.classifyFixAge(120_001L))
    }
}
