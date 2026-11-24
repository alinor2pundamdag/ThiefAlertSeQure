package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class LocalEvidenceVaultPolicyTest {
    @Test
    fun integrityDigestIsStableAndSensitiveToMetadata() {
        fun digest(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        val original = digest("1700000000000|480|alarm_triggered|Alarm latched")
        assertEquals(original, digest("1700000000000|480|alarm_triggered|Alarm latched"))
        assertTrue(original != digest("1700000000001|480|alarm_triggered|Alarm latched"))
        assertTrue(original != digest("1700000000000|480|alarm_triggered|changed"))
    }

    @Test
    fun retentionWindowIsThirtyDaysAndIncludesBoundary() {
        assertEquals(2_592_000_000L, LocalEvidenceVaultPolicy.RETENTION_MS)
        val now = LocalEvidenceVaultPolicy.RETENTION_MS + 1_000_000L
        assertTrue(LocalEvidenceVaultPolicy.isWithinRetention(now, 1_000_000L))
    }

    @Test
    fun recordsOlderThanThirtyDaysExpire() {
        val now = LocalEvidenceVaultPolicy.RETENTION_MS + 1_000_000L
        assertFalse(LocalEvidenceVaultPolicy.isWithinRetention(now, 999_999L))
    }

    @Test
    fun futureDatedRecordsSurviveClockRollback() {
        assertTrue(LocalEvidenceVaultPolicy.isWithinRetention(900L, 1_000L))
        assertTrue(LocalEvidenceVaultPolicy.isFutureDated(900L, 1_000L))
    }

    @Test
    fun timestampAndTimezoneMetadataMustBeValid() {
        assertTrue(LocalEvidenceVaultPolicy.isValidTimestampMetadata(1_000L, 480))
        assertFalse(LocalEvidenceVaultPolicy.isValidTimestampMetadata(-1L, 480))
        assertFalse(LocalEvidenceVaultPolicy.isValidTimestampMetadata(1_000L, 900))
    }
}
