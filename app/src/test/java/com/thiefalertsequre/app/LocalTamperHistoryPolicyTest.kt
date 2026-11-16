package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTamperHistoryPolicyTest {
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
}
