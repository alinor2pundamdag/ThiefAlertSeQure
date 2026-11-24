package com.thiefalertsequre.app

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class A2DisconnectSchedulerInstrumentationTest {
    private val preferencesName = "a2_disconnect_scheduler_instrumentation_test"
    private val namespace = "a2-scheduler-instrumentation"
    private lateinit var context: android.content.Context
    private lateinit var historySnapshot: SharedPreferencesSnapshot

    @Before
    fun isolateTestPreferencesAndHistory() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        historySnapshot = SharedPreferencesSnapshot(context, "local_tamper_history_v1")
        assertTrue(A2DisconnectScheduler.clearAllIsolated(context, preferencesName, namespace))
    }

    @After
    fun removeOnlyTestDeadlinesAndRestoreHistory() {
        assertTrue(A2DisconnectScheduler.clearAllIsolated(context, preferencesName, namespace))
        assertTrue(context.getSharedPreferences(preferencesName, 0).edit().clear().commit())
        historySnapshot.restore()
    }

    @Test
    fun corruptRevisionAndFutureEventTimestampsAreRejectedBeforePersistence() {
        val prefs = context.getSharedPreferences(preferencesName, 0)
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)

        val corruptRevision = A2DisconnectScheduler.Pending(
            address = "02:00:00:00:00:43",
            eventEpochMs = nowEpoch,
            eventElapsedRealtimeMs = nowElapsed,
            bootCount = boot,
            monitorRevision = Long.MIN_VALUE,
            deviceRevision = 0L
        )
        assertTrue(
            "Corrupt revision sentinels must be rejected",
            !A2DisconnectScheduler.scheduleIsolated(context, corruptRevision, preferencesName, namespace)
        )

        val futureEvent = corruptRevision.copy(
            address = "02:00:00:00:00:44",
            eventElapsedRealtimeMs = SystemClock.elapsedRealtime() + 60_000L,
            monitorRevision = 0L
        )
        assertTrue(
            "Future monotonic timestamps must be rejected",
            !A2DisconnectScheduler.scheduleIsolated(context, futureEvent, preferencesName, namespace)
        )
        assertNull(prefs.getString("trusted_bluetooth_pending_disconnects_v1", null))
    }

    @Test
    fun pendingDeadlineIsEncryptedAndReconnectCancellationRemovesIt() {
        val prefs = context.getSharedPreferences(preferencesName, 0)
        val address = "02:00:00:00:00:42"
        val key = "trusted_bluetooth_pending_disconnects_v1"

        try {
            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val pending = A2DisconnectScheduler.Pending(
                address = address,
                eventEpochMs = nowEpoch,
                eventElapsedRealtimeMs = nowElapsed,
                bootCount = ProtectionClock.bootCount(context),
                monitorRevision = 17L,
                deviceRevision = 3L
            )
            assertTrue(
                "AlarmManager should accept an exact or inexact idle-aware deadline",
                A2DisconnectScheduler.scheduleIsolated(context, pending, preferencesName, namespace)
            )

            val encrypted = prefs.getString(key, null)
            assertNotNull("Pending deadline must be durably persisted", encrypted)
            assertTrue("Bluetooth address and metadata must be encrypted at rest",
                LocalPreferenceCipher.isEncrypted(encrypted!!))

            assertTrue(
                "Reconnect must remove only this test device's matching deadline",
                A2DisconnectScheduler.cancelIsolated(context, address, nowElapsed, preferencesName, namespace)
            )
            assertNull("No stale pending deadline should remain", prefs.getString(key, null))
        } finally {
            A2DisconnectScheduler.clearAllIsolated(context, preferencesName, namespace)
        }
    }
}
