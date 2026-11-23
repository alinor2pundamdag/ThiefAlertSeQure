package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDeadlineReceiverInstrumentationTest {
    @Test fun latchedA2DeadlineFailureUsesBoundedRecoveryPath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ProtectionStore(context)
        val originalState = store.read()
        assumeTrue(
            "Test requires no pre-existing countdown or latched alarm",
            originalState.mode != ProtectionMode.COUNTDOWN &&
                originalState.mode != ProtectionMode.ALARM_TRIGGERED
        )

        val recoveryPrefs = context.getSharedPreferences("alarm_recovery_v1", Context.MODE_PRIVATE)
        val hadRetryCount = recoveryPrefs.contains("retry_count")
        val oldRetryCount = runCatching { recoveryPrefs.getInt("retry_count", 0) }.getOrDefault(0)

        AlarmScheduler.cancel(context)
        AlarmRecoveryNotice.cancel(context)
        assertTrue(
            "Test must establish a durable alarm latch",
            store.write(
                ProtectionState(
                    ProtectionMode.ALARM_TRIGGERED,
                    0L,
                    System.currentTimeMillis(),
                    0L,
                    ProtectionClock.bootCount(context)
                )
            )
        )

        try {
            assertTrue("Recovery counter must be reset for this isolated test", recoveryPrefs.edit().clear().commit())
            AlarmDeadlineReceiver().handleProcessingFailure(
                context,
                Intent().setAction("com.thiefalertsequre.A2_DISCONNECT_DEADLINE"),
                IllegalStateException("simulated background foreground-service start restriction")
            )

            assertEquals("A latched A2 failure must enter bounded recovery", 1, recoveryPrefs.getInt("retry_count", 0))
            assertTrue(
                "A recovery retry must retain its inexact fallback when exact-alarm access is denied",
                AlarmScheduler.hasPendingIntent(context, "recovery", fallback = true)
            )
        } finally {
            AlarmScheduler.cancel(context)
            AlarmRecoveryNotice.cancel(context)
            val restoreRetry = recoveryPrefs.edit()
            if (hadRetryCount) restoreRetry.putInt("retry_count", oldRetryCount) else restoreRetry.remove("retry_count")
            assertTrue("Recovery counter must be restored", restoreRetry.commit())
            assertTrue("Original protection state must be restored", store.write(originalState))
        }
    }
}
