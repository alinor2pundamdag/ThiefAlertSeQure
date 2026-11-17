package com.thiefalertsequre.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmSoundStoreCorruptionTest {
    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    private val prefs by lazy { context.getSharedPreferences("user_alarm_sounds_v1", Context.MODE_PRIVATE) }
    private lateinit var originalPreferences: SharedPreferencesSnapshot

    @Before
    fun preserveSelectedAlarmSounds() {
        originalPreferences = SharedPreferencesSnapshot(context, "user_alarm_sounds_v1")
    }

    @After
    fun restoreSelectedAlarmSounds() {
        originalPreferences.restore()
    }

    @Test fun malformedSoundUriTypeDoesNotCrashAndDoesNotEraseSource() {
        prefs.edit().clear().putInt("uri_alarm", 42).commit()
        try {
            assertNull(AlarmSoundStore.uri(context, AlarmSoundStore.ALARM))
            assertEquals(42, prefs.getInt("uri_alarm", -1))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun malformedGrantTypePreventsClearAndPreservesSoundMetadata() {
        prefs.edit().clear()
            .putString("uri_alarm", "content://example/alarm")
            .putInt("grant_uri_alarm", 7)
            .putString("grant_uri_alert", null)
            .commit()
        try {
            assertFalse(AlarmSoundStore.clear(context, AlarmSoundStore.ALARM))
            assertEquals("content://example/alarm", prefs.getString("uri_alarm", null))
            assertEquals(7, prefs.getInt("grant_uri_alarm", -1))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun malformedOtherSlotGrantAlsoPreventsClear() {
        prefs.edit().clear()
            .putString("uri_alarm", "content://example/alarm")
            .putString("grant_uri_alarm", "content://example/alarm")
            .putInt("grant_uri_alert", 9)
            .commit()
        try {
            assertFalse(AlarmSoundStore.clear(context, AlarmSoundStore.ALARM))
            assertEquals("content://example/alarm", prefs.getString("uri_alarm", null))
            assertEquals(9, prefs.getInt("grant_uri_alert", -1))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun validEmptyPreferencesUsePackagedDefaultAlarm() {
        prefs.edit().clear().commit()
        assertTrue(AlarmSoundStore.effectiveUri(context, AlarmSoundStore.ALARM).toString().startsWith("android.resource://"))
    }
}
