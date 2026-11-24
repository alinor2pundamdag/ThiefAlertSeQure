package com.thiefalertsequre.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Confirms the local event log remains bounded and sanitizes multi-line detail. */
@RunWith(AndroidJUnit4::class)
class LocalTamperHistoryInstrumentationTest {
    private lateinit var context: Context
    private lateinit var originalHistory: SharedPreferencesSnapshot

    @Before
    fun preserveAndIsolateExistingHistory() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        originalHistory = SharedPreferencesSnapshot(context, "local_tamper_history_v1")
        assertTrue(LocalTamperHistory.clear(context))
    }

    @After
    fun restoreExistingHistory() {
        originalHistory.restore()
    }

    @Test
    fun eventHistoryRetainsOnlyTheMostRecentOneHundredEntries() {
        repeat(110) { index ->
            LocalTamperHistory.record(context, "event_$index")
        }

        val events = LocalTamperHistory.read(context)
        assertEquals(100, events.size)
        assertTrue(events.first().endsWith("| event_109"))
        assertTrue(events.last().endsWith("| event_10"))
    }

    @Test
    fun eventDetailsCannotInjectAdditionalHistoryRows() {
        LocalTamperHistory.record(context, "privacy_test", "first\nsecond\tthird")

        val events = LocalTamperHistory.read(context)
        assertEquals(1, events.size)
        assertTrue(events.single().endsWith("| first second third"))
    }
}
