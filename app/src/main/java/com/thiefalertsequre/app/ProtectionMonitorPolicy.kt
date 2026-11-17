package com.thiefalertsequre.app

/**
 * Charger state transitions require a live, user-visible foreground monitor.
 * Owner opt-out is explicit; an active alarm is handled by AlarmService instead.
 */
object ProtectionMonitorPolicy {
    const val DEFAULT_ENABLED = true
    const val DEFAULT_MOTION_ENABLED = false

    fun shouldRun(enabled: Boolean, mode: ProtectionMode): Boolean =
        enabled && mode != ProtectionMode.ALARM_TRIGGERED

    /** A stale UI stop must not overwrite a newer owner re-enable preference. */
    fun shouldApplyStopRequest(preferenceAlreadyCommitted: Boolean, monitorEnabled: Boolean): Boolean =
        !preferenceAlreadyCommitted || !monitorEnabled

    /** Motion is an independent alarm source and is opt-in, never implicit. */
    fun shouldMonitorMotion(motionEnabled: Boolean, mode: ProtectionMode): Boolean =
        motionEnabled && mode == ProtectionMode.ARMED
}