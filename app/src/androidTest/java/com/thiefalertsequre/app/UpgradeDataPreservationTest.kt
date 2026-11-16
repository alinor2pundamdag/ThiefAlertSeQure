package com.thiefalertsequre.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies in-place upgrade data continuity against a minified production APK.
 * The test persists a typed snapshot before seeding and restores all app-owned
 * test state after verification so it cannot leave the owner's monitor settings,
 * private preferences, or marker file modified.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeDataPreservationTest {
    @Test
    @Suppress("NewApi", "DEPRECATION")
    fun upgradeDataCanBeSeededOrVerifiedAcrossPackageReplacement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("com.thiefalertsequre.app", context.packageName)

        val preferences = context.getSharedPreferences(
            "upgrade_compatibility_regression",
            Context.MODE_PRIVATE
        )
        val monitorPrefs = context.getSharedPreferences(
            "protection_monitor_v1",
            Context.MODE_PRIVATE
        )
        val backupPrefs = context.getSharedPreferences(
            "upgrade_compatibility_regression_backup_v1",
            Context.MODE_PRIVATE
        )
        val markerKey = "pre_upgrade_private_data_marker"
        val versionKey = "pre_upgrade_version_code"
        val markerValue = "preserve-me-across-in-place-upgrade"
        val markerFile = File(context.filesDir, "upgrade_compatibility_regression.marker")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        when (InstrumentationRegistry.getArguments().getString("upgradePhase")) {
            "seed" -> {
                preserveOriginalState(context, preferences, monitorPrefs, backupPrefs, markerFile)
                try {
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
                        monitorPrefs.edit()
                            .putBoolean("monitor_enabled", true)
                            // A stale true value must not make the post-update restart assertion pass.
                            .putBoolean("monitor_running", false)
                            .commit()
                    )
                } catch (failure: Throwable) {
                    runCatching {
                        restoreOriginalState(context, preferences, monitorPrefs, backupPrefs, markerFile)
                    }
                    throw failure
                }
            }

            "verify" -> {
                check(backupPrefs.contains(KEY_MONITOR_SNAPSHOT)) {
                    "No pre-upgrade snapshot exists; run the seed phase before verify"
                }
                try {
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
                    val restartDeadline = SystemClock.elapsedRealtime() + 15_000L
                    while (!monitorPrefs.getBoolean("monitor_running", false) &&
                        SystemClock.elapsedRealtime() < restartDeadline
                    ) {
                        SystemClock.sleep(250L)
                    }
                    assertTrue(
                        "The protection monitor must be restarted after package replacement",
                        monitorPrefs.getBoolean("monitor_running", false)
                    )
                } finally {
                    restoreOriginalState(context, preferences, monitorPrefs, backupPrefs, markerFile)
                }
            }

            else -> throw IllegalArgumentException(
                "Pass the explicit instrumentation argument -e upgradePhase seed or verify"
            )
        }
    }

    private fun preserveOriginalState(
        context: Context,
        preferences: SharedPreferences,
        monitorPrefs: SharedPreferences,
        backupPrefs: SharedPreferences,
        markerFile: File
    ) {
        // A leftover snapshot means a prior interrupted upgrade test still needs cleanup.
        if (backupPrefs.contains(KEY_MONITOR_SNAPSHOT)) return

        val markerExisted = markerFile.isFile
        val markerContents = if (markerExisted) {
            Base64.encodeToString(markerFile.readBytes(), Base64.NO_WRAP)
        } else {
            ""
        }
        val monitorWasRunning = runCatching {
            context.getSystemService(ActivityManager::class.java)
                .getRunningServices(100)
                .any { it.service.className == MONITOR_SERVICE_CLASS }
        }.getOrDefault(false)

        check(
            backupPrefs.edit()
                .putString(KEY_MONITOR_SNAPSHOT, encodePreferences(monitorPrefs))
                .putString(KEY_UPGRADE_SNAPSHOT, encodePreferences(preferences))
                .putString(
                    KEY_HISTORY_SNAPSHOT,
                    encodePreferences(context.getSharedPreferences("local_tamper_history_v1", Context.MODE_PRIVATE))
                )
                .putBoolean(KEY_MARKER_EXISTED, markerExisted)
                .putString(KEY_MARKER_CONTENTS, markerContents)
                .putBoolean(KEY_MONITOR_WAS_RUNNING, monitorWasRunning)
                .commit()
        ) { "The original app-owned state snapshot must be durably saved before seeding" }
    }

    private fun restoreOriginalState(
        context: Context,
        preferences: SharedPreferences,
        monitorPrefs: SharedPreferences,
        backupPrefs: SharedPreferences,
        markerFile: File
    ) {
        val monitorSnapshot = backupPrefs.getString(KEY_MONITOR_SNAPSHOT, null)
            ?: error("Original monitor preferences snapshot is missing")
        val upgradeSnapshot = backupPrefs.getString(KEY_UPGRADE_SNAPSHOT, null)
            ?: error("Original upgrade-test preferences snapshot is missing")
        val markerExisted = backupPrefs.getBoolean(KEY_MARKER_EXISTED, false)
        val markerContents = backupPrefs.getString(KEY_MARKER_CONTENTS, "").orEmpty()
        val monitorWasRunning = backupPrefs.getBoolean(KEY_MONITOR_WAS_RUNNING, false)

        restorePreferences(preferences, upgradeSnapshot)
        restorePreferences(monitorPrefs, monitorSnapshot)
        if (markerExisted) {
            markerFile.writeBytes(Base64.decode(markerContents, Base64.NO_WRAP))
        } else {
            markerFile.delete()
        }
        check(backupPrefs.edit().clear().commit()) {
            "The temporary upgrade-test snapshot must be removed after restoration"
        }

        val monitorEnabled = monitorPrefs.getBoolean("monitor_enabled", true)
        val monitorIntent = Intent().setClassName(context.packageName, MONITOR_SERVICE_CLASS)
        if (monitorEnabled && monitorWasRunning) {
            runCatching { ContextCompat.startForegroundService(context, monitorIntent) }
        } else {
            context.stopService(monitorIntent)
        }
        // Package-replacement recovery can append events; restore the owner's
        // original bounded history after the test monitor state has been settled.
        restorePreferences(
            context.getSharedPreferences("local_tamper_history_v1", Context.MODE_PRIVATE),
            historySnapshot
        )
    }

    private fun encodePreferences(preferences: SharedPreferences): String {
        val root = JSONObject()
        preferences.all.forEach { (key, value) ->
            val entry = JSONObject()
            when (value) {
                is String -> entry.put("type", "string").put("value", value)
                is Boolean -> entry.put("type", "boolean").put("value", value)
                is Int -> entry.put("type", "int").put("value", value)
                is Long -> entry.put("type", "long").put("value", value)
                is Float -> entry.put("type", "float").put("value", value.toDouble())
                is Set<*> -> {
                    val strings = value.filterIsInstance<String>()
                    require(strings.size == value.size) {
                        "Unsupported SharedPreferences string-set value for $key"
                    }
                    entry.put("type", "string_set").put("value", JSONArray(strings))
                }
                else -> error("Unsupported SharedPreferences value type for $key")
            }
            root.put(key, entry)
        }
        return root.toString()
    }

    private fun restorePreferences(preferences: SharedPreferences, snapshot: String) {
        val root = JSONObject(snapshot)
        val editor = preferences.edit().clear()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = root.getJSONObject(key)
            when (entry.getString("type")) {
                "string" -> editor.putString(key, entry.getString("value"))
                "boolean" -> editor.putBoolean(key, entry.getBoolean("value"))
                "int" -> editor.putInt(key, entry.getInt("value"))
                "long" -> editor.putLong(key, entry.getLong("value"))
                "float" -> editor.putFloat(key, entry.getDouble("value").toFloat())
                "string_set" -> {
                    val array = entry.getJSONArray("value")
                    val values = linkedSetOf<String>()
                    for (index in 0 until array.length()) values += array.getString(index)
                    editor.putStringSet(key, values)
                }
                else -> error("Unknown SharedPreferences snapshot type for $key")
            }
        }
        check(editor.commit()) { "Original SharedPreferences values could not be restored" }
    }

    private companion object {
        const val KEY_MONITOR_SNAPSHOT = "monitor_snapshot"
        const val KEY_UPGRADE_SNAPSHOT = "upgrade_snapshot"
        const val KEY_HISTORY_SNAPSHOT = "history_snapshot"
        const val KEY_MARKER_EXISTED = "marker_existed"
        const val KEY_MARKER_CONTENTS = "marker_contents"
        const val KEY_MONITOR_WAS_RUNNING = "monitor_was_running"
        const val MONITOR_SERVICE_CLASS = "com.thiefalertsequre.app.ProtectionMonitorService"
    }
}
