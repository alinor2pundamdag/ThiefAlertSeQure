package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTamperHistoryPolicyTest {
    @Test
    fun redactedDiagnosticExportKeepsOnlyEventCode() {
        assertEquals(
            "alarm_audio_open_failed",
            LocalTamperHistoryPolicy.eventCodeFromLine(
                "2026-11-17 12:00:00 | alarm_audio_open_failed | content://private.provider/user-selected-audio"
            )
        )
    }

    @Test
    fun malformedDiagnosticLineDoesNotExportFreeFormText() {
        assertEquals("unknown_event", LocalTamperHistoryPolicy.eventCodeFromLine("private path or URI"))
        assertEquals("unknown_event", LocalTamperHistoryPolicy.eventCodeFromLine("timestamp | event details without delimiter"))
    }

    @Test
    fun eventNameRemovesLineBreaksAndUnsupportedCharacters() {
        assertEquals("alarm_triggered", LocalTamperHistoryPolicy.sanitizeEvent("alarm\n_triggered!"))
    }

    @Test
    fun detailNormalizesControlAndUnicodeLineSeparators() {
        assertEquals(
            "first second third fourth",
            LocalTamperHistoryPolicy.sanitizeDetail("first\r\nsecond\tthird\u2028fourth")
        )
    }

    @Test
    fun exceptionMessagesAreRedactedButExceptionTypeRemainsUseful() {
        assertEquals(
            "IOException: [redacted]",
            LocalTamperHistoryPolicy.sanitizeDetail("IOException: Failed to open /data/user/0/app/files/private.apk")
        )
    }

    @Test
    fun credentialAssignmentsBearerTokensAndJwtValuesAreRedacted() {
        val safe = LocalTamperHistoryPolicy.sanitizeDetail(
            "api_key=secret-value password=hunter2 Authorization: Bearer abc.def.ghi"
        )
        assertEquals(false, safe.contains("secret-value"))
        assertEquals(false, safe.contains("hunter2"))
        assertEquals(false, safe.contains("abc.def.ghi"))
    }

    @Test
    fun uriPathsAndEmailAddressesAreRedacted() {
        val safe = LocalTamperHistoryPolicy.sanitizeDetail(
            "audio content://private.provider/document/secret file=/storage/emulated/0/Music/private.ogg owner=user@example.com"
        )
        assertEquals(false, safe.contains("content://"))
        assertEquals(false, safe.contains("/storage/emulated"))
        assertEquals(false, safe.contains("user@example.com"))
    }

    @Test
    fun previouslyStoredHistoryDetailsAreRedactedOnMigration() {
        val legacy = "2026-11-17 12:00:00 | alarm_audio_open_failed | IOException: /data/user/0/app/files/private.ogg"
        assertEquals(
            "2026-11-17 12:00:00 | alarm_audio_open_failed | IOException: [redacted]",
            LocalTamperHistoryPolicy.sanitizeStoredHistory(legacy)
        )
    }

    @Test
    fun safeOperationalDetailsAndEvidenceHashesRemainReadable() {
        val detail = "charger_countdown_started after 3 stable samples; sha256=" + "a".repeat(64)
        assertEquals(detail, LocalTamperHistoryPolicy.sanitizeDetail(detail))
    }

    @Test
    fun eventAndDetailLengthsAreBounded() {
        assertEquals(48, LocalTamperHistoryPolicy.sanitizeEvent("x".repeat(80)).length)
        assertEquals(180, LocalTamperHistoryPolicy.sanitizeDetail("x".repeat(240)).length)
    }

    @Test
    fun appendRetainsOnlyTheNewestOneHundredNonBlankEvents() {
        val existing = (1..101).joinToString("\n") { "event-$it" }
        val result = LocalTamperHistoryPolicy.append(existing, "event-102")
        val events = result.lines()

        assertEquals(100, events.size)
        assertEquals("event-3", events.first())
        assertEquals("event-102", events.last())
    }

    @Test
    fun readReturnsNewestEntriesFirstAndClampsLimit() {
        assertEquals(
            listOf("third", "second"),
            LocalTamperHistoryPolicy.newestFirst("first\nsecond\nthird", 2)
        )
        assertEquals(
            listOf("third"),
            LocalTamperHistoryPolicy.newestFirst("first\nsecond\nthird", 0)
        )
    }

    @Test
    fun historyCategoriesMatchExpectedEventFamilies() {
        assertEquals(true, LocalTamperHistoryPolicy.matchesCategory("charger_countdown_started", "power"))
        assertEquals(true, LocalTamperHistoryPolicy.matchesCategory("a2_bluetooth_disconnected", "bluetooth"))
        assertEquals(true, LocalTamperHistoryPolicy.matchesCategory("alarm_audio_open_failed", "alarm"))
        assertEquals(true, LocalTamperHistoryPolicy.matchesCategory("owner_biometric_failed", "authentication"))
        assertEquals(true, LocalTamperHistoryPolicy.matchesCategory("camera_evidence_deleted", "evidence"))
        assertEquals(true, LocalTamperHistoryPolicy.matchesCategory("permission_changed", "system"))
        assertEquals(false, LocalTamperHistoryPolicy.matchesCategory("owner_authenticated", "bluetooth"))
        assertEquals(true, LocalTamperHistoryPolicy.matchesCategory("owner_authenticated", "all"))
    }
}
