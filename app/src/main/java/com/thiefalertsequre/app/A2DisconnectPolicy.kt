package com.thiefalertsequre.app

/** Pure A2 gating rules; timing and alarm state transitions remain owned by the service. */
object A2DisconnectPolicy {
    /** Uses the broadcast-receipt uptime so local processing never extends the grace period. */
    fun graceDeadlineUptimeMs(eventUptimeMs: Long, gracePeriodMs: Long): Long {
        require(eventUptimeMs >= 0L && gracePeriodMs >= 0L)
        return if (Long.MAX_VALUE - eventUptimeMs < gracePeriodMs) Long.MAX_VALUE
        else eventUptimeMs + gracePeriodMs
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
    ): Boolean = capturedMonitorRevision == currentMonitorRevision &&
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
