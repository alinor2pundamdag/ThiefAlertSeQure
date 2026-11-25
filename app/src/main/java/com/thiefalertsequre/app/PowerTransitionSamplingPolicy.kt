package com.thiefalertsequre.app

/**
 * Bounded, low-latency confirmation for OS power-transition broadcasts.
 *
 * Three consecutive matching live snapshots reject transient/stale values while
 * allowing the common stable case to finish after three reads instead of always
 * waiting through the full five-read window. Unknown values reset the sequence.
 */
internal object PowerTransitionSamplingPolicy {
    const val REQUIRED_CONSECUTIVE_SAMPLES = 3
    const val MAX_SAMPLES = 5
    const val SAMPLE_INTERVAL_MS = 100L

    data class Confirmation(
        val confirmed: Boolean,
        val lastObserved: Boolean?,
        val sampleCount: Int
    )

    @Throws(InterruptedException::class)
    fun confirm(
        expectedConnected: Boolean,
        readConnected: () -> Boolean?,
        pause: (Long) -> Unit = { Thread.sleep(it) }
    ): Confirmation {
        var consecutiveMatches = 0
        var lastObserved: Boolean? = null
        var sampleCount = 0

        while (sampleCount < MAX_SAMPLES) {
            lastObserved = readConnected()
            sampleCount += 1
            consecutiveMatches = if (lastObserved == expectedConnected) {
                consecutiveMatches + 1
            } else {
                0
            }

            if (consecutiveMatches >= REQUIRED_CONSECUTIVE_SAMPLES) {
                return Confirmation(true, lastObserved, sampleCount)
            }

            if (sampleCount < MAX_SAMPLES) pause(SAMPLE_INTERVAL_MS)
        }

        return Confirmation(false, lastObserved, sampleCount)
    }
}
