package com.thiefalertsequre.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
        try {
            val now = System.currentTimeMillis()
            val result = ProtectionStore(context).updatePersisted {
                ProtectionState(ProtectionMode.ARMED, 0L, now, 0L, -1)
            }
            assertTrue("Successful SharedPreferences commit must be reported", result.persisted)
            assertEquals(ProtectionMode.ARMED, result.state.mode)
            assertEquals(ProtectionMode.ARMED, ProtectionStore(context).read().mode)
        } finally {
            ProtectionStore(context).write(
                ProtectionState(ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1)
            )
        }
    }
}
