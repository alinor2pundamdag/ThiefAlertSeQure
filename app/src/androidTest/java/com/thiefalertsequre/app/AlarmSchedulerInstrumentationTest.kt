package com.thiefalertsequre.app

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmSchedulerInstrumentationTest {
    private val preferencesName = "alarm_scheduler_instrumentation_test"
    private val namespace = "alarm-scheduler-instrumentation"
    private lateinit var appContext: Context
    private lateinit var historySnapshot: SharedPreferencesSnapshot

    @Before
    fun isolateAlarmTokenAndPendingIntentNamespace() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        historySnapshot = SharedPreferencesSnapshot(appContext, "local_tamper_history_v1")
        AlarmScheduler.cancelIsolated(appContext, preferencesName, namespace)
    }

    @After
    fun removeOnlyTestAlarmAndPreferences() {
        AlarmScheduler.cancelIsolated(appContext, preferencesName, namespace)
        assertTrue(appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit())
        historySnapshot.restore()
    }

    @Test
    fun countdownDeadlineTokenIsCommittedAndRemovedByCancellation() {
        val prefs = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val epoch = System.currentTimeMillis() + 60_000L
        val elapsed = SystemClock.elapsedRealtime() + 60_000L
        val boot = ProtectionClock.bootCount(appContext)

        assertTrue(AlarmScheduler.scheduleIsolated(appContext, epoch, elapsed, boot, preferencesName, namespace))
        assertEquals("deadline:$epoch:$elapsed:$boot", prefs.getString("last_token", null))

        AlarmScheduler.cancelIsolated(appContext, preferencesName, namespace)
        assertFalse(prefs.contains("last_token"))
    }

    @Test
    fun failedAlarmSubmissionRestoresThePreviousPersistedToken() {
        val prefs = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val existing = "previous-valid-token"
        assertTrue(prefs.edit().clear().putString("last_token", existing).commit())

        val scheduled = AlarmScheduler.replaceTokenAndScheduleIsolated(
            appContext, "new-token", preferencesName, namespace
        ) {
            throw IllegalStateException("simulated AlarmManager submission failure")
        }

        assertFalse("A rejected AlarmManager submission must be reported as failure", scheduled)
        assertEquals("The previous alarm token must be restored", existing, prefs.getString("last_token", null))
    }

    @Test
    fun invalidDeadlineDoesNotReplacePreviouslyPersistedToken() {
        val prefs = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val existing = "existing-safe-token"
        assertTrue(prefs.edit().putString("last_token", existing).commit())

        assertFalse(AlarmScheduler.scheduleIsolated(appContext, 0L, 0L, -1, preferencesName, namespace))
        assertEquals(existing, prefs.getString("last_token", null))
    }
}
