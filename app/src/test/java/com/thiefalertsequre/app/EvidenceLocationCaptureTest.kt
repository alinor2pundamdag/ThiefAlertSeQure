package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceLocationCaptureTest {
    @Test fun requestsEveryEnabledLocationProviderForFallback() {
        assertEquals(
            listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER),
            EvidenceLocationCapture.enabledProviders(gpsEnabled = true, networkEnabled = true)
        )
        assertEquals(
            listOf(android.location.LocationManager.NETWORK_PROVIDER),
            EvidenceLocationCapture.enabledProviders(gpsEnabled = false, networkEnabled = true)
        )
        assertEquals(
            listOf(android.location.LocationManager.GPS_PROVIDER),
            EvidenceLocationCapture.enabledProviders(gpsEnabled = true, networkEnabled = false)
        )
        assertEquals(emptyList<String>(), EvidenceLocationCapture.enabledProviders(false, false))
    }

    @Test fun classifiesRecentAndStaleCachedFixes() {
        assertEquals("RECENT_CACHED_FIX", EvidenceLocationCapture.classifyFixAge(0L))
        assertEquals("RECENT_CACHED_FIX", EvidenceLocationCapture.classifyFixAge(120_000L))
        assertEquals("STALE_CACHED_FIX", EvidenceLocationCapture.classifyFixAge(120_001L))
    }
}
