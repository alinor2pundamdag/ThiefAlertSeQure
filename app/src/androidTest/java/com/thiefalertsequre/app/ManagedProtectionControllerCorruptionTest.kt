package com.thiefalertsequre.app

import android.content.Context
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagedProtectionControllerCorruptionTest {
    private var originalPreferences: Map<String, *> = emptyMap<String, Any?>()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun rollbackPreferences() =
        context.getSharedPreferences("managed_protection_rollback", Context.MODE_PRIVATE)

    @Before
    fun preserveExistingRollbackMetadata() {
        originalPreferences = rollbackPreferences().all.toMap()
    }

    @After
    fun restoreExistingRollbackMetadata() {
        val editor = rollbackPreferences().edit().clear()
        originalPreferences.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> error("Unsupported SharedPreferences value type for $key")
            }
        }
        assertTrue("Original managed-policy rollback metadata must be restored", editor.commit())
    }

    private fun seedValidSnapshot() {
        val prefs = rollbackPreferences()
        val editor = prefs.edit()
            .clear()
            .putInt("version", 1)
            .putStringSet("lock_task_packages", emptySet())
            .putInt("lock_task_features", 0)
            .putBoolean("recovery_pending", false)
        listOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_USB_FILE_TRANSFER
        ).forEach { key ->
            editor.putBoolean("local_restriction:$key", false)
            editor.putBoolean("global_restriction:$key", false)
        }
        check(editor.commit())
    }

    @Test
    fun validRollbackSnapshotDoesNotExposeEmergencyRecovery() {
        seedValidSnapshot()
        assertFalse(ManagedProtectionController.hasPendingRollback(context))
    }

    @Test
    fun wrongTypedVersionExposesEmergencyRecovery() {
        rollbackPreferences().edit().clear()
            .putString("version", "not-an-integer")
            .commit()

        assertTrue(ManagedProtectionController.hasPendingRollback(context))
    }

    @Test
    fun wrongTypedRestrictionExposesEmergencyRecovery() {
        seedValidSnapshot()
        rollbackPreferences().edit()
            .putString("local_restriction:${UserManager.DISALLOW_FACTORY_RESET}", "not-a-boolean")
            .commit()

        assertTrue(ManagedProtectionController.hasPendingRollback(context))
    }

    @Test
    fun wrongTypedRecoveryMarkerFailsClosed() {
        rollbackPreferences().edit().clear()
            .putString("recovery_pending", "not-a-boolean")
            .commit()

        assertTrue(ManagedProtectionController.hasPendingRollback(context))
    }
}
