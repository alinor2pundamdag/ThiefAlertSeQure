package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class A2DisconnectSchedulerMergeTest {
    @Test fun updatingOneAccessoryPreservesOtherAccessoriesDeadlines() {
        val watch = A2DisconnectScheduler.Pending(
            "AA:BB:CC:DD:EE:01", 1_000L, 2_000L, 4, 7L, 3L
        )
        val earbuds = A2DisconnectScheduler.Pending(
            "AA:BB:CC:DD:EE:02", 1_100L, 2_100L, 4, 7L, 5L
        )
        val replacementWatch = watch.copy(
            eventEpochMs = 3_000L,
            eventElapsedRealtimeMs = 4_000L,
            deviceRevision = 4L
        )
        val merged = A2DisconnectScheduler.mergePending(listOf(watch, earbuds), replacementWatch)
        assertEquals(2, merged.size)
        assertEquals(earbuds, merged.single { it.address == earbuds.address })
        assertEquals(replacementWatch, merged.single { it.address == watch.address })
    }

    @Test fun addressMatchingIsCaseInsensitive() {
        val existing = A2DisconnectScheduler.Pending(
            "AA:BB:CC:DD:EE:01", 1_000L, 2_000L, 4, 7L, 3L
        )
        val replacement = existing.copy(
            address = "aa:bb:cc:dd:ee:01",
            eventEpochMs = 5_000L,
            eventElapsedRealtimeMs = 6_000L
        )
        val merged = A2DisconnectScheduler.mergePending(listOf(existing), replacement)
        assertEquals(1, merged.size)
        assertEquals(replacement, merged.single())
    }

    @Test fun bluetoothConnectPermissionIsRequiredOnlyFromApi31() {
        assertTrue(A2DisconnectPolicy.hasBluetoothConnectPermission(30, false))
        assertFalse(A2DisconnectPolicy.hasBluetoothConnectPermission(31, false))
        assertTrue(A2DisconnectPolicy.hasBluetoothConnectPermission(31, true))
    }
    @Test fun concurrentAccessoryUpdatesRemainIndependentUnderTheSharedStateLock() {
        val addresses = (0 until 8).map { index ->
            "02:00:00:00:01:${"%02X".format(index)}"
        }
        val ready = CountDownLatch(addresses.size)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(addresses.size)
        val records = mutableListOf<A2DisconnectScheduler.Pending>()
        try {
            val futures = addresses.mapIndexed { index, address ->
                executor.submit {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    val pending = A2DisconnectScheduler.Pending(
                        address = address,
                        eventEpochMs = 1_000L + index,
                        eventElapsedRealtimeMs = 2_000L + index,
                        bootCount = 4,
                        monitorRevision = 7L,
                        deviceRevision = index.toLong() + 1L
                    )
                    A2DisconnectStateLock.withLock {
                        val merged = A2DisconnectScheduler.mergePending(records, pending)
                        records.clear()
                        records.addAll(merged)
                    }
                }
            }
            assertTrue("All accessory updates should reach the concurrency barrier",
                ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }

            assertEquals("Concurrent updates must preserve every accessory", addresses.size, records.size)
            addresses.forEach { address ->
                assertTrue("Missing concurrent accessory update for $address",
                    records.any { it.address.equals(address, ignoreCase = true) })
            }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

}
