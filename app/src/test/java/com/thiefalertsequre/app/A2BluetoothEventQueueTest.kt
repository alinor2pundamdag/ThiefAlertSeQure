package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class A2BluetoothEventQueueTest {
    private fun receipt(bootCount: Int, elapsedRealtimeNanos: Long) = A2BluetoothEventReceipt(
        eventEpochMs = 1_000L + elapsedRealtimeNanos / 1_000_000L,
        eventElapsedRealtimeMs = elapsedRealtimeNanos / 1_000_000L,
        eventUptimeMs = elapsedRealtimeNanos / 1_000_000L,
        bootCount = bootCount,
        eventElapsedRealtimeNanos = elapsedRealtimeNanos
    )

    @Test fun olderAclReceiptIsRejectedWhenItsWorkIsSubmittedAfterANewerEvent() {
        val address = "02:00:00:00:00:71"
        A2BluetoothEventQueue.clearEventWatermarks()
        try {
            val newer = receipt(bootCount = 8, elapsedRealtimeNanos = 2_000_000_000L)
            val older = receipt(bootCount = 8, elapsedRealtimeNanos = 1_000_000_000L)
            assertTrue(A2BluetoothEventQueue.acceptIfCurrent(address, newer))
            assertFalse("A stale callback must not overwrite the newer device state",
                A2BluetoothEventQueue.acceptIfCurrent(address, older))
            assertTrue(A2BluetoothEventQueue.acceptIfCurrent(
                address, receipt(bootCount = 8, elapsedRealtimeNanos = 2_100_000_000L)
            ))
        } finally {
            A2BluetoothEventQueue.clearEventWatermarks()
        }
    }

    @Test fun knownBootChangeResetsTheMonotonicEventWatermark() {
        val address = "02:00:00:00:00:72"
        A2BluetoothEventQueue.clearEventWatermarks()
        try {
            assertTrue(A2BluetoothEventQueue.acceptIfCurrent(
                address, receipt(bootCount = 8, elapsedRealtimeNanos = 2_000_000_000L)
            ))
            assertTrue("A new boot has a new elapsedRealtime clock",
                A2BluetoothEventQueue.acceptIfCurrent(
                    address, receipt(bootCount = 9, elapsedRealtimeNanos = 100_000_000L)
                ))
            assertFalse(A2BluetoothEventQueue.acceptIfCurrent(
                address, receipt(bootCount = 9, elapsedRealtimeNanos = 90_000_000L)
            ))
        } finally {
            A2BluetoothEventQueue.clearEventWatermarks()
        }
    }

    @Test fun manifestAndRuntimeAclEventsShareBroadcastSubmissionOrder() {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val completed = CountDownLatch(2)

        A2BluetoothEventQueue.execute {
            firstStarted.countDown()
            check(releaseFirst.await(5, TimeUnit.SECONDS))
            order.add("manifest-disconnect")
            completed.countDown()
        }
        assertTrue("First ACL event should enter the serial queue", firstStarted.await(5, TimeUnit.SECONDS))

        A2BluetoothEventQueue.execute {
            order.add("runtime-reconnect")
            completed.countDown()
        }
        releaseFirst.countDown()

        assertTrue("Queued ACL events should complete", completed.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("manifest-disconnect", "runtime-reconnect"), order.toList())
    }
}
