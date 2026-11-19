package com.thiefalertsequre.app

/** Pure A2 gating rules; timing and alarm state transitions remain owned by the service. */
object A2DisconnectPolicy {
    /** Uses the broadcast-receipt uptime so local processing never extends the grace period. */
    fun graceDeadlineUptimeMs(eventUptimeMs: Long, gracePeriodMs: Long): Long {
        require(eventUptimeMs >= 0L && gracePeriodMs >= 0L)
        return if (Long.MAX_VALUE - eventUptimeMs < gracePeriodMs) Long.MAX_VALUE
        else eventUptimeMs + gracePeriodMs
    }

    /** Exact access enables the precise path; callers must still retain an inexact fallback. */
    fun shouldScheduleExactAlarm(apiLevel: Int, canScheduleExactAlarms: Boolean): Boolean =
        apiLevel < 31 || canScheduleExactAlarms

    /** At least one independently scheduled callback must remain available. */
    fun hasUsableDeadlineCallback(exactScheduled: Boolean, fallbackScheduled: Boolean): Boolean =
        exactScheduled || fallbackScheduled

    /** Bluetooth ACL monitoring requires CONNECT permission from API 31 onward. */
    fun hasBluetoothConnectPermission(apiLevel: Int, permissionGranted: Boolean): Boolean =
        apiLevel < 31 || permissionGranted

    /** A durable callback requires a verifiable boot identity; elapsedRealtime alone is insufficient. */
    fun isSameBoot(
        storedBootCount: Int,
        currentBootCount: Int,
        eventElapsedRealtimeMs: Long,
        currentElapsedRealtimeMs: Long
    ): Boolean {
        if (storedBootCount < 0 || currentBootCount < 0 || storedBootCount != currentBootCount) return false
        if (eventElapsedRealtimeMs <= 0L || currentElapsedRealtimeMs < eventElapsedRealtimeMs) return false
        return true
    }

    /** Retry callbacks never move before the original grace deadline. */
    fun retryDeadlineElapsedRealtimeMs(
        originalDeadlineElapsedRealtimeMs: Long,
        nowElapsedRealtimeMs: Long,
        retryDelayMs: Long
    ): Long {
        require(originalDeadlineElapsedRealtimeMs >= 0L && nowElapsedRealtimeMs >= 0L && retryDelayMs >= 0L)
        val retryAt = if (Long.MAX_VALUE - nowElapsedRealtimeMs < retryDelayMs) Long.MAX_VALUE
            else nowElapsedRealtimeMs + retryDelayMs
        return maxOf(originalDeadlineElapsedRealtimeMs, retryAt)
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

    fun shouldTriggerAlarm(
        alertEnabled: Boolean,
        trusted: Boolean,
        pendingDisconnectCurrent: Boolean,
        stillDisconnected: Boolean,
        protectionMode: ProtectionMode
    ): Boolean = alertEnabled && trusted && pendingDisconnectCurrent && stillDisconnected &&
        protectionMode == ProtectionMode.ARMED
}
