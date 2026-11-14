package com.thiefalertsequre.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kept independent of app implementation classes so it can inspect a minified
 * production APK after an in-place Package Installer update.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeDataPreservationTest {
    @Test
    fun upgradeDataCanBeSeededOrVerifiedAcrossPackageReplacement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("com.thiefalertsequre.app", context.packageName)

        val preferences = context.getSharedPreferences(
            "upgrade_compatibility_regression",
            android.content.Context.MODE_PRIVATE
        )
        val markerKey = "v113_private_data_marker"
        val markerValue = "preserve-me-across-in-place-upgrade"
        val markerFile = java.io.File(context.filesDir, "upgrade_compatibility_regression.marker")

        when (InstrumentationRegistry.getArguments().getString("upgradePhase")) {
            "seed" -> {
                assertTrue(
                    "The pre-upgrade preference marker must be durably committed",
                    preferences.edit().putString(markerKey, markerValue).commit()
                )
                markerFile.writeText(markerValue)
            }
            "verify" -> {
                assertEquals(
                    "Private SharedPreferences must survive an in-place APK update",
                    markerValue,
                    preferences.getString(markerKey, null)
                )
                assertTrue("Private files directory must survive an in-place APK update", markerFile.isFile)
                assertEquals(markerValue, markerFile.readText())
            }
            else -> {
                assertTrue(preferences.edit().putString(markerKey, markerValue).commit())
                markerFile.writeText(markerValue)
                assertEquals(markerValue, preferences.getString(markerKey, null))
                assertEquals(markerValue, markerFile.readText())
            }
        }
    }
}
