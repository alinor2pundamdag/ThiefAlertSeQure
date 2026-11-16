package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectionStoreUpdateResultTest {
    private lateinit var context: Context
    private var originalProtection: Map<String, *> = emptyMap<String, Any?>()
    private var originalCorruptBackup: Map<String, *> = emptyMap<String, Any?>()
    private var originalMonitor: Map<String, *> = emptyMap<String, Any?>()

    @Before
    fun preserveExistingAppPreferences() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        originalProtection = preferences("protection_state_v1").all.toMap()
        originalCorruptBackup = preferences("protection_state_corrupt_backup_v1").all.toMap()
        originalMonitor = preferences(ProtectionMonitorService.PREFS).all.toMap()
    }

    @After
    fun restoreExistingAppPreferences() {
        restore("protection_state_v1", originalProtection)
        restore("protection_state_corrupt_backup_v1", originalCorruptBackup)
        restore(ProtectionMonitorService.PREFS, originalMonitor)
    }

    private fun preferences(name: String) =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun restore(name: String, values: Map<String, *>) {
        val editor = preferences(name).edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> error("Unsupported SharedPreferences value type for $name/$key")
            }
        }
        assertTrue("Original $name preferences must be restored", editor.commit())
    }

    @Test
    fun updateReportsSuccessfulDurableCommit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("protection_state_corrupt_backup_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        try {
            val now = System.currentTimeMillis()
            val result = ProtectionStore(context).updatePersisted {
                ProtectionState(ProtectionMode.ARMED, 0L, now, 0L, -1)
            }
            assertTrue("Successful SharedPreferences commit must be reported", result.persisted)
            assertEquals(ProtectionMode.ARMED, result.state.mode)
            assertEquals(ProtectionMode.ARMED, ProtectionStore(context).read().mode)
        } finally {
            context.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("protection_state_corrupt_backup_v1", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun failedPreferenceCommitDoesNotReportOrExposeAnUnpersistedTransition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)
        val backup = context.getSharedPreferences("protection_state_corrupt_backup_v1", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().commit())
        assertTrue(backup.edit().clear().commit())
        try {
            val store = ProtectionStore(context) { false }
            val result = store.updatePersisted {
                ProtectionState(ProtectionMode.ARMED, 0L, System.currentTimeMillis(), 0L, -1)
            }

            assertFalse("A rejected SharedPreferences commit must not be reported as persisted", result.persisted)
            assertEquals("The caller must receive the last durable state", ProtectionMode.DISARMED, result.state.mode)
            assertEquals("A fresh store must still observe the last durable state", ProtectionMode.DISARMED, ProtectionStore(context).read().mode)
            assertFalse("No mode may be written when commit fails", prefs.contains("mode"))
        } finally {
            prefs.edit().clear().commit()
            backup.edit().clear().commit()
        }
    }

    @Test
    fun corruptProtectionStateIsPreservedAndOrdinaryUpdatesCannotDisarmIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitorPrefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val previousMonitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val monitorWasRunning = ProtectionMonitorService.isRunning

        // This is a ProtectionStore-only test. A monitor left running by an
        // earlier Activity smoke test can receive the emulator's battery-reset
        // broadcast and legitimately re-arm DISARMED state between assertions.
        assertTrue(
            "Test must disable monitor before isolating persisted-state recovery",
            monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, false).commit()
        )
        context.stopService(Intent(context, ProtectionMonitorService::class.java))
        instrumentation.waitForIdleSync()

        val prefs = context.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)
        val backup = context.getSharedPreferences("protection_state_corrupt_backup_v1", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().clear().putBoolean("mode", true).commit())
        assertTrue(backup.edit().clear().commit())
        try {
            val store = ProtectionStore(context)
            assertEquals(ProtectionMode.ALARM_TRIGGERED, store.read().mode)
            assertTrue("Original malformed value must remain in its source preferences", prefs.getBoolean("mode", false))
            assertTrue("A private corruption snapshot must be active", backup.getBoolean("state_corruption_active", false))
            assertTrue("The original typed value must be preserved in the snapshot", backup.getBoolean("snapshot_1_mode", false))

            val ordinaryUpdate = store.updatePersisted {
                ProtectionState(ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1)
            }
            assertFalse("Non-authenticated state updates cannot clear a corruption latch", ordinaryUpdate.persisted)
            assertEquals(ProtectionMode.ALARM_TRIGGERED, ordinaryUpdate.state.mode)
            assertTrue(prefs.getBoolean("mode", false))

            val authenticated = store.disarmAfterOwnerAuthentication(System.currentTimeMillis())
            assertNotNull("Explicit owner-authentication completion may clear the latch", authenticated)
            assertEquals(ProtectionMode.DISARMED, authenticated!!.mode)
            assertEquals(ProtectionMode.DISARMED, ProtectionStore(context).read().mode)
            assertFalse(backup.getBoolean("state_corruption_active", true))
            assertTrue("Corrupt source snapshot remains available after recovery", backup.getBoolean("snapshot_1_mode", false))
        } finally {
            prefs.edit().clear().commit()
            backup.edit().clear().commit()
            monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, previousMonitorEnabled).commit()
            if (monitorWasRunning && previousMonitorEnabled) {
                runCatching {
                    ContextCompat.startForegroundService(
                        context, Intent(context, ProtectionMonitorService::class.java)
                    )
                }
            }
        }
    }

    @Test
    fun corruptAuthenticationThrottleIsPreservedWithoutOverwritingItsSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)
        val backup = context.getSharedPreferences("protection_state_corrupt_backup_v1", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("auth_failed_attempts", "corrupt").commit()
        backup.edit().clear().commit()
        try {
            val now = System.currentTimeMillis()
            val throttle = ProtectionStore(context).readAuthenticationThrottle(now, 10_000L, 3)
            assertEquals(now + AuthenticationThrottle.COOLDOWN_MS, throttle.cooldownUntilEpochMs)
            assertEquals("corrupt", prefs.getString("auth_failed_attempts", null))
            assertTrue(backup.getBoolean("auth_corruption_active", false))
            assertEquals("corrupt", backup.getString("snapshot_1_auth_failed_attempts", null))
        } finally {
            prefs.edit().clear().commit()
            backup.edit().clear().commit()
        }
    }
}
