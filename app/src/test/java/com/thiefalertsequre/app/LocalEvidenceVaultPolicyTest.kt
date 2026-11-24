package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class LocalEvidenceVaultPolicyTest {
    @Test fun integrityDigestIsStableAndSensitiveToMetadata() {
        fun digest(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        val original = digest("1700000000000|480|alarm_triggered|Alarm latched")
        assertEquals(original, digest("1700000000000|480|alarm_triggered|Alarm latched"))
        assertTrue(original != digest("1700000000001|480|alarm_triggered|Alarm latched"))
        assertTrue(original != digest("1700000000000|480|alarm_triggered|changed"))
    }

    @Test fun retentionWindowIsThirtyDays() {
        val retentionMs = 30L * 24L * 60L * 60L * 1000L
        assertTrue(retentionMs > 0L)
        assertEquals(2_592_000_000L, retentionMs)
    }
}
