package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class PowerTransitionSamplingPolicyTest {
    @Test fun confirmsConnectAfterThreeConsecutiveSamplesWithoutWaitingForUnusedReads() {
        val samples = listOf(true, true, true, false, false)
        val delays = mutableListOf<Long>()
        var index = 0

        val result = PowerTransitionSamplingPolicy.confirm(
            expectedConnected = true,
            readConnected = { samples[index++] },
            pause = { delays += it }
        )

        assertTrue(result.confirmed)
        assertEquals(3, result.sampleCount)
        assertEquals(3, index)
        assertEquals(listOf(100L, 100L), delays)
    }

    @Test fun confirmsDisconnectAfterUnknownAndOppositeSamplesResetTheSequence() {
        val samples = listOf(false, null, false, false, false)
        val delays = mutableListOf<Long>()
        var index = 0

        val result = PowerTransitionSamplingPolicy.confirm(
            expectedConnected = false,
            readConnected = { samples[index++] },
            pause = { delays += it }
        )

        assertTrue(result.confirmed)
        assertEquals(5, result.sampleCount)
        assertEquals(4, delays.size)
    }

    @Test fun rejectsUnstablePowerStateAfterBoundedFiveReadWindow() {
        val samples = listOf(false, true, false, true, false)
        var index = 0

        val result = PowerTransitionSamplingPolicy.confirm(
            expectedConnected = false,
            readConnected = { samples[index++] },
            pause = {}
        )

        assertFalse(result.confirmed)
        assertEquals(5, result.sampleCount)
        assertEquals(false, result.lastObserved)
    }

    @Test fun powerTransitionExecutorUsesBoundedQueueAndExplicitTimedRejection() {
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val executor = PowerTransitionQueuePolicy.createExecutor(
            queueCapacity = 1,
            backpressureTimeoutMs = 50L
        )

        try {
            assertTrue(executor.queue is ArrayBlockingQueue<*>)
            assertEquals(1, executor.queue.remainingCapacity() + executor.queue.size)
            executor.execute {
                workerStarted.countDown()
                releaseWorker.await(1, TimeUnit.SECONDS)
            }
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS))
            executor.execute { }
            assertEquals(1, executor.queue.size)

            var rejected = false
            try {
                executor.execute { }
            } catch (_: RejectedExecutionException) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            releaseWorker.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun a2ManifestReceiverQueueIsBoundedAndSingleWorker() {
        val executor = A2BluetoothReceiverQueuePolicy.createExecutor(
            queueCapacity = 2,
            backpressureTimeoutMs = 50L
        )
        try {
            assertTrue(executor.queue is ArrayBlockingQueue<*>)
            assertEquals(2, executor.queue.remainingCapacity() + executor.queue.size)
            assertEquals(1, executor.corePoolSize)
            assertEquals(1, executor.maximumPoolSize)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun unknownSamplesNeverCountAsConnectedOrDisconnectedConfirmation() {
        val result = PowerTransitionSamplingPolicy.confirm(
            expectedConnected = true,
            readConnected = { null },
            pause = {}
        )

        assertFalse(result.confirmed)
        assertEquals(5, result.sampleCount)
        assertEquals(null, result.lastObserved)
    }
}
