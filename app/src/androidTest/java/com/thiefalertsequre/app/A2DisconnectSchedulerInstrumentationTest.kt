package com.thiefalertsequre.app

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class A2DisconnectSchedulerInstrumentationTest {

    @Test fun corruptRevisionAndFutureEventTimestampsAreRejectedBeforePersistence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val key = "trusted_bluetooth_pending_disconnects_v1"
        assertTrue(A2DisconnectScheduler.clearAll(context))
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
        assertTrue("Corrupt revision sentinels must be rejected", !A2DisconnectScheduler.schedule(context, corruptRevision))

        val futureEvent = corruptRevision.copy(
            address = "02:00:00:00:00:44",
            eventElapsedRealtimeMs = SystemClock.elapsedRealtime() + 60_000L,
            monitorRevision = 0L
        )
        assertTrue("Future monotonic timestamps must be rejected", !A2DisconnectScheduler.schedule(context, futureEvent))
        assertNull(prefs.getString(key, null))
    }

    @Test fun pendingDeadlineIsEncryptedAndReconnectCancellationRemovesIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val address = "02:00:00:00:00:42"
        val key = "trusted_bluetooth_pending_disconnects_v1"
        A2DisconnectScheduler.clearAll(context)

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
            assertTrue("AlarmManager should accept an exact callback or the inexact idle-aware fallback",
                A2DisconnectScheduler.schedule(context, pending))
            assertTrue("The inexact fallback must remain independent of exact-alarm permission",
                A2DisconnectScheduler.hasPendingIntent(context, address, fallback = true))
            val exactAllowed = android.os.Build.VERSION.SDK_INT < 31 ||
                context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
            assertEquals(exactAllowed,
                A2DisconnectScheduler.hasPendingIntent(context, address, fallback = false))

            val encrypted = prefs.getString(key, null)
            assertNotNull("Pending deadline must be durably persisted", encrypted)
            assertTrue("Bluetooth address and metadata must be encrypted at rest",
                LocalPreferenceCipher.isEncrypted(encrypted!!))

            assertTrue("Reconnect must remove only this device's matching deadline",
                A2DisconnectScheduler.cancel(context, address, nowElapsed))
            assertNull("No stale pending deadline should remain", prefs.getString(key, null))
            assertTrue("Reconnect must cancel both exact and fallback PendingIntent identities",
                !A2DisconnectScheduler.hasPendingIntent(context, address, fallback = true) &&
                    !A2DisconnectScheduler.hasPendingIntent(context, address, fallback = false))
        } finally {
            A2DisconnectScheduler.clearAll(context)
        }
    }

    @Test fun multipleDevicesKeepIndependentDeadlinesAndReconnectIsScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val key = "trusted_bluetooth_pending_disconnects_v1"
        val firstAddress = "02:00:00:00:00:51"
        val secondAddress = "02:00:00:00:00:52"
        A2DisconnectScheduler.clearAll(context)
        try {
            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val boot = ProtectionClock.bootCount(context)
            val first = A2DisconnectScheduler.Pending(firstAddress, nowEpoch, nowElapsed - 100L, boot, 21L, 4L)
            val second = A2DisconnectScheduler.Pending(secondAddress, nowEpoch, nowElapsed - 50L, boot, 21L, 8L)
            assertTrue(A2DisconnectScheduler.schedule(context, first))
            assertTrue(A2DisconnectScheduler.schedule(context, second))

            val stored = LocalPreferenceCipher.decrypt(key, prefs.getString(key, null)!!)
            assertTrue(stored.contains(firstAddress))
            assertTrue(stored.contains(secondAddress))
            assertTrue(A2DisconnectScheduler.hasPendingIntent(context, firstAddress, fallback = true))
            assertTrue(A2DisconnectScheduler.hasPendingIntent(context, secondAddress, fallback = true))

            assertTrue(A2DisconnectScheduler.cancel(context, firstAddress, first.eventElapsedRealtimeMs))
            val remaining = LocalPreferenceCipher.decrypt(key, prefs.getString(key, null)!!)
            assertTrue(remaining.contains(secondAddress))
            assertTrue(!remaining.contains(firstAddress))
            assertTrue(A2DisconnectScheduler.hasPendingIntent(context, secondAddress, fallback = true))
            assertTrue(!A2DisconnectScheduler.hasPendingIntent(context, firstAddress, fallback = true))
        } finally {
            A2DisconnectScheduler.clearAll(context)
        }
    }

    @Test fun staleReconnectCannotCancelANewerDeadlineForTheSameDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val key = "trusted_bluetooth_pending_disconnects_v1"
        val address = "02:00:00:00:00:53"
        A2DisconnectScheduler.clearAll(context)
        try {
            val nowEpoch = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val boot = ProtectionClock.bootCount(context)
            val old = A2DisconnectScheduler.Pending(address, nowEpoch - 500L, nowElapsed - 500L, boot, 22L, 9L)
            val newer = old.copy(eventEpochMs = nowEpoch, eventElapsedRealtimeMs = nowElapsed)
            assertTrue(A2DisconnectScheduler.schedule(context, old))
            assertTrue(A2DisconnectScheduler.schedule(context, newer))

            assertTrue(A2DisconnectScheduler.cancel(context, address, old.eventElapsedRealtimeMs))
            val stored = LocalPreferenceCipher.decrypt(key, prefs.getString(key, null)!!)
            assertTrue("The newer event must remain authoritative",
                stored.contains("|" + newer.eventElapsedRealtimeMs + "|"))
            assertTrue(A2DisconnectScheduler.hasPendingIntent(context, address, fallback = true))
        } finally {
            A2DisconnectScheduler.clearAll(context)
        }
    }
}
