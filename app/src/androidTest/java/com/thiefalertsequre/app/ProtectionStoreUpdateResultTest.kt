package com.thiefalertsequre.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectionStoreUpdateResultTest {
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
    fun corruptProtectionStateIsPreservedAndOrdinaryUpdatesCannotDisarmIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)
        val backup = context.getSharedPreferences("protection_state_corrupt_backup_v1", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("mode", true).commit()
        backup.edit().clear().commit()
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
