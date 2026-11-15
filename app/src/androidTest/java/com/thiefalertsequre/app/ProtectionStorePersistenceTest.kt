package com.thiefalertsequre.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectionStorePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun failedStateCommitIsReportedAndDoesNotClaimNewState() {
        val durableStore = ProtectionStore(context)
        val before = durableStore.read()
        val failingStore = ProtectionStore(context) { false }

        val result = failingStore.updatePersisted {
            ProtectionState(
                mode = ProtectionMode.ALARM_TRIGGERED,
                changedAtEpochMs = System.currentTimeMillis()
            )
        }

        assertFalse(result.persisted)
        assertEquals(before, result.state)
        assertEquals(before, durableStore.read())
    }

    @Test
    fun successfulStateCommitIsVisibleToFreshStoreInstance() {
        val store = ProtectionStore(context)
        val before = store.read()
        try {
            val expected = ProtectionState(
                mode = ProtectionMode.ARMED,
                changedAtEpochMs = System.currentTimeMillis()
            )
            val result = store.updatePersisted { expected }

            assertTrue(result.persisted)
            assertEquals(expected, result.state)
            assertEquals(expected, ProtectionStore(context).read())
        } finally {
            assertTrue(store.write(before))
        }
    }
}
