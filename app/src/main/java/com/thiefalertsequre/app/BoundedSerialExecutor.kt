package com.thiefalertsequre

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Creates a single-worker FIFO executor with a bounded queue and timed backpressure.
 * A rejected task is surfaced to the receiver so its pending broadcast can be finished
 * and the missed event can be recorded rather than silently spawning another thread.
 */
internal object BoundedSerialExecutor {
    fun create(
        queueCapacity: Int,
        backpressureTimeoutMs: Long,
        threadName: String
    ): ThreadPoolExecutor {
        require(queueCapacity > 0) { "queueCapacity must be positive" }
        require(backpressureTimeoutMs > 0) { "backpressureTimeoutMs must be positive" }
        require(threadName.isNotBlank()) { "threadName must not be blank" }

        val queue = ArrayBlockingQueue<Runnable>(queueCapacity)
        val rejectionHandler = RejectedExecutionHandler { task, executor ->
            if (executor.isShutdown) {
                throw RejectedExecutionException("$threadName executor is shut down")
            }
            try {
                if (!executor.queue.offer(task, backpressureTimeoutMs, TimeUnit.MILLISECONDS)) {
                    throw RejectedExecutionException("$threadName queue remained full")
                }
                if (executor.isShutdown && executor.remove(task)) {
                    throw RejectedExecutionException("$threadName executor shut down while accepting a task")
                }
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RejectedExecutionException("Interrupted while waiting for $threadName queue", interrupted)
            }
        }
        val threadFactory = ThreadFactory { task ->
            Thread(task, threadName).apply { isDaemon = true }
        }
        return ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            queue,
            threadFactory,
            rejectionHandler
        )
    }
}
