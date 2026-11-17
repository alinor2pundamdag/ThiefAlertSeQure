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

    /** Bluetooth ACL monitoring requires CONNECT permission from API 31 onward. */
    fun hasBluetoothConnectPermission(apiLevel: Int, permissionGranted: Boolean): Boolean =
        apiLevel < 31 || permissionGranted

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
        if (storedBootCount >= 0 || currentBootCount >= 0) {
            return storedBootCount >= 0 && currentBootCount >= 0 && storedBootCount == currentBootCount
        }
        return true
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
