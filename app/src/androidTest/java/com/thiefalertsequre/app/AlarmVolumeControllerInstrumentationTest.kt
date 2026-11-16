package com.thiefalertsequre.app

import android.content.Context
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmVolumeControllerInstrumentationTest {
    @Test fun alarmVolumeSnapshotIsRestoredAndDurablyCleared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(AudioManager::class.java)
        val prefs = context.getSharedPreferences("alarm_volume_restore_v1", Context.MODE_PRIVATE)
        val original = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        try {
            assertTrue(prefs.edit().remove("original_alarm_volume").commit())
            assertTrue(AlarmVolumeController.raiseToMaximum(context, audio))
            assertEquals(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                audio.getStreamVolume(AudioManager.STREAM_ALARM))
            assertTrue(AlarmVolumeController.restore(context, audio))
            assertEquals(original, audio.getStreamVolume(AudioManager.STREAM_ALARM))
            assertFalse(prefs.contains("original_alarm_volume"))
        } finally {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, original, 0) }
            prefs.edit().remove("original_alarm_volume").commit()
        }
    }

    @Test fun corruptVolumeSnapshotIsNotTreatedAsAValidRestoreValue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = context.getSystemService(AudioManager::class.java)
        val prefs = context.getSharedPreferences("alarm_volume_restore_v1", Context.MODE_PRIVATE)
        try {
            assertTrue(prefs.edit().putString("original_alarm_volume", "corrupt").commit())
            assertFalse(AlarmVolumeController.restore(context, audio))
            assertFalse(prefs.contains("original_alarm_volume"))
        } finally {
            prefs.edit().remove("original_alarm_volume").commit()
        }
    }
}
