package com.thiefalertsequre.app

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmSchedulerInstrumentationTest {
    @Test fun countdownDeadlineTokenIsCommittedAndRemovedByCancellation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("alarm_scheduler_v1", Context.MODE_PRIVATE)
        AlarmScheduler.cancel(context)
        try {
            val epoch = System.currentTimeMillis() + 60_000L
            val elapsed = SystemClock.elapsedRealtime() + 60_000L
            val boot = ProtectionClock.bootCount(context)
            assertTrue(AlarmScheduler.schedule(context, epoch, elapsed, boot))
            val token = "deadline:$epoch:$elapsed:$boot"
            assertEquals(token, prefs.getString("last_token", null))
            assertTrue("The inexact fallback must exist regardless of exact-alarm access",
                AlarmScheduler.hasPendingIntent(context, token, fallback = true))
            val exactAllowed = android.os.Build.VERSION.SDK_INT < 31 ||
                context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
            assertEquals(exactAllowed, AlarmScheduler.hasPendingIntent(context, token, fallback = false))

            AlarmScheduler.cancel(context)
            assertFalse(prefs.contains("last_token"))
            assertFalse(AlarmScheduler.hasPendingIntent(context, token, fallback = true))
            assertFalse(AlarmScheduler.hasPendingIntent(context, token, fallback = false))
        } finally {
            AlarmScheduler.cancel(context)
        }
    }

    @Test fun recoveryRetryAlwaysRetainsAnInexactFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AlarmScheduler.cancel(context)
        try {
            assertTrue(AlarmScheduler.scheduleRecoveryRetry(context, 1_000L))
            assertTrue(AlarmScheduler.hasPendingIntent(context, "recovery", fallback = true))
        } finally {
            AlarmScheduler.cancel(context)
        }
    }

    @Test fun failedAlarmSubmissionRestoresThePreviousPersistedToken() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("alarm_scheduler_v1", Context.MODE_PRIVATE)
        val existing = "previous-valid-token"
        assertTrue(prefs.edit().clear().putString("last_token", existing).commit())
        try {
            val scheduled = AlarmScheduler.replaceTokenAndSchedule(context, "new-token") {
                throw IllegalStateException("simulated AlarmManager submission failure")
            }

            assertFalse("A rejected AlarmManager submission must be reported as failure", scheduled)
            assertEquals("The previous alarm token must be restored", existing, prefs.getString("last_token", null))
        } finally {
            AlarmScheduler.cancel(context)
        }
    }

    @Test fun invalidDeadlineDoesNotReplacePreviouslyPersistedToken() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("alarm_scheduler_v1", Context.MODE_PRIVATE)
        val existing = "existing-safe-token"
        assertTrue(prefs.edit().putString("last_token", existing).commit())
        try {
            assertFalse(AlarmScheduler.schedule(context, 0L))
            assertEquals(existing, prefs.getString("last_token", null))
        } finally {
            AlarmScheduler.cancel(context)
        }
    }
}
