package com.thiefalertsequre.app

/** Pure A2 gating rules; timing and alarm state transitions remain owned by the service. */
object A2DisconnectPolicy {
    /** Uses the broadcast-receipt uptime so local processing never extends the grace period. */
    fun graceDeadlineUptimeMs(eventUptimeMs: Long, gracePeriodMs: Long): Long {
        require(eventUptimeMs >= 0L && gracePeriodMs >= 0L)
        return if (Long.MAX_VALUE - eventUptimeMs < gracePeriodMs) Long.MAX_VALUE
        else eventUptimeMs + gracePeriodMs
    }

    /** Rejects stale timers after reboot, including OEMs where BOOT_COUNT is unreadable. */
    fun isSameBoot(
        storedBootCount: Int,
        currentBootCount: Int,
        eventElapsedRealtimeMs: Long,
        currentElapsedRealtimeMs: Long
    ): Boolean {
        // elapsedRealtime must move forward within the same boot. Boot count
        // alone must not legitimize a corrupt or future-dated persisted event.
        if (eventElapsedRealtimeMs <= 0L || currentElapsedRealtimeMs < eventElapsedRealtimeMs) return false
        // Elapsed realtime alone cannot prove boot identity: after a reboot,
        // the new uptime may already exceed the old event's uptime. Persisted
        // deadlines therefore require a matching, readable BOOT_COUNT.
        return storedBootCount >= 0 && currentBootCount >= 0 &&
            storedBootCount == currentBootCount
    }

    fun shouldStartGracePeriod(
        alertEnabled: Boolean,
        trusted: Boolean,
        hadConnectionBaseline: Boolean,
        protectionMode: ProtectionMode
    ): Boolean = alertEnabled && trusted && hadConnectionBaseline &&
        protectionMode == ProtectionMode.ARMED

    fun isPendingDisconnectCurrent(
        capturedMonitorRevision: Long,
        currentMonitorRevision: Long,
        capturedDeviceRevision: Long,
        currentDeviceRevision: Long
    ): Boolean = capturedMonitorRevision >= 0L && currentMonitorRevision >= 0L &&
        capturedDeviceRevision >= 0L && currentDeviceRevision >= 0L &&
        capturedMonitorRevision == currentMonitorRevision &&
        capturedDeviceRevision == currentDeviceRevision

    /** A durable owner-auth timestamp invalidates any older A2 grace event. */
    fun isAfterLastAuthenticatedDisarm(
        eventElapsedRealtimeMs: Long,
        eventBootCount: Int,
        lastAuthenticatedElapsedRealtimeMs: Long,
        lastAuthenticatedBootCount: Int
    ): Boolean {
        if (eventElapsedRealtimeMs <= 0L || lastAuthenticatedElapsedRealtimeMs < 0L) return false
        if (lastAuthenticatedElapsedRealtimeMs == 0L) return lastAuthenticatedBootCount == -1
        if (eventBootCount < 0 || lastAuthenticatedBootCount < 0) return false
        return when {
            eventBootCount > lastAuthenticatedBootCount -> true
            eventBootCount < lastAuthenticatedBootCount -> false
            else -> eventElapsedRealtimeMs > lastAuthenticatedElapsedRealtimeMs
        }
    }

    fun shouldTriggerAlarm(
        alertEnabled: Boolean,
        trusted: Boolean,
        pendingDisconnectCurrent: Boolean,
        stillDisconnected: Boolean,
        protectionMode: ProtectionMode
    ): Boolean = alertEnabled && trusted && pendingDisconnectCurrent && stillDisconnected &&
        protectionMode == ProtectionMode.ARMED
}
