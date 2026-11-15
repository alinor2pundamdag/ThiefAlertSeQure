package com.thiefalertsequre.app

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppScreenSmokeTest {
    @Test
    fun allInternalScreensCreateAndDestroyWithoutCrashing() {
        launch(MainActivity::class.java)
        launch(SettingsActivity::class.java)
        launch(ThemeActivity::class.java)
        launch(SetupStatusActivity::class.java)
        launch(BluetoothCenterActivity::class.java)
        launch(DeviceIntelligenceActivity::class.java)
    }

    @Test
    fun setupSnapshotReportsEveryCriticalSystemConnection() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val snapshot = SetupStatusSnapshot.read(context)
        val ids = snapshot.items.map { it.id }.toSet()

        listOf(
            "exact",
            "notifications",
            "alarm_channel",
            "fullscreen",
            "battery",
            "lock",
            "monitor",
            "bt",
            "a2"
        ).forEach { id -> assertTrue("Missing setup status: $id", id in ids) }
    }

    private fun <T : Activity> launch(activityClass: Class<T>) {
        ActivityScenario.launch(activityClass).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull("Activity window should be created: ${activityClass.simpleName}", activity.window)
            }
        }
    }
}
