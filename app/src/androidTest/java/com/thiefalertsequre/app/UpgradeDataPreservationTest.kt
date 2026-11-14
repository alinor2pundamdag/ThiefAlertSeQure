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
        val markerKey = "pre_upgrade_private_data_marker"
        val versionKey = "pre_upgrade_version_code"
        val markerValue = "preserve-me-across-in-place-upgrade"
        val markerFile = java.io.File(context.filesDir, "upgrade_compatibility_regression.marker")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val monitorPrefs = context.getSharedPreferences(
            "protection_monitor_v1", android.content.Context.MODE_PRIVATE
        )

        when (InstrumentationRegistry.getArguments().getString("upgradePhase")) {
            "seed" -> {
                assertTrue(
                    "The pre-upgrade preference marker must be durably committed",
                    preferences.edit()
                        .putString(markerKey, markerValue)
                        .putLong(versionKey, currentVersionCode)
                        .commit()
                )
                markerFile.writeText(markerValue)
                assertTrue(
                    "The prior release monitor preference must be seeded",
                    monitorPrefs.edit().putBoolean("monitor_enabled", true).commit()
                )
            }
            "verify" -> {
                assertEquals(
                    "Private SharedPreferences must survive an in-place APK update",
                    markerValue,
                    preferences.getString(markerKey, null)
                )
                assertTrue("Private files directory must survive an in-place APK update", markerFile.isFile)
                assertEquals(markerValue, markerFile.readText())
                assertTrue(
                    "The candidate versionCode must increase over the installed prior release",
                    currentVersionCode > preferences.getLong(versionKey, Long.MAX_VALUE)
                )
                assertTrue(
                    "The monitor enabled preference must survive the in-place update",
                    monitorPrefs.getBoolean("monitor_enabled", false)
                )
            }
            else -> {
                assertTrue(preferences.edit()
                    .putString(markerKey, markerValue)
                    .putLong(versionKey, currentVersionCode)
                    .commit())
                markerFile.writeText(markerValue)
                assertEquals(markerValue, preferences.getString(markerKey, null))
                assertEquals(markerValue, markerFile.readText())
            }
        }
    }
}
