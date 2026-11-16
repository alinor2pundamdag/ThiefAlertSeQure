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
            assertTrue("AlarmManager should accept an exact or inexact idle-aware deadline",
                A2DisconnectScheduler.schedule(context, pending))

            val encrypted = prefs.getString(key, null)
            assertNotNull("Pending deadline must be durably persisted", encrypted)
            assertTrue("Bluetooth address and metadata must be encrypted at rest",
                LocalPreferenceCipher.isEncrypted(encrypted!!))

            assertTrue("Reconnect must remove only this device's matching deadline",
                A2DisconnectScheduler.cancel(context, address, nowElapsed))
            assertNull("No stale pending deadline should remain", prefs.getString(key, null))
        } finally {
            A2DisconnectScheduler.clearAll(context)
        }
    }
}
