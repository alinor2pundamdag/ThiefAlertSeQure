package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class A2BluetoothEventQueueTest {
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
