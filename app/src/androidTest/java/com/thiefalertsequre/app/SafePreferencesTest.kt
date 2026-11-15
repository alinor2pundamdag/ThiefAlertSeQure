package com.thiefalertsequre.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafePreferencesTest {
    @Test
    fun wrongTypedValuesAreReportedAndRetained() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("safe_preferences_test", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString("boolean_value", "not-a-boolean")
            .putBoolean("string_value", true)
            .putString("long_value", "not-a-long")
            .commit()

        assertTrue(SafePreferences.readBoolean(prefs, "boolean_value", false) is PreferenceRead.Corrupt)
        assertTrue(SafePreferences.readString(prefs, "string_value") is PreferenceRead.Corrupt)
        assertTrue(SafePreferences.readLong(prefs, "long_value", 0L) is PreferenceRead.Corrupt)

        assertTrue("Corrupt values must remain available for diagnosis", prefs.contains("boolean_value"))
        assertTrue(prefs.contains("string_value"))
        assertTrue(prefs.contains("long_value"))
        assertEquals("not-a-boolean", prefs.getString("boolean_value", null))
    }

    @Test
    fun validValuesAndMissingDefaultsRemainReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("safe_preferences_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("enabled", true).putLong("revision", 7L).commit()

        assertEquals(true, SafePreferences.boolean(prefs, "enabled", false))
        assertEquals(7L, SafePreferences.long(prefs, "revision", 0L))
        assertEquals(false, SafePreferences.boolean(prefs, "missing", false))
    }
}
