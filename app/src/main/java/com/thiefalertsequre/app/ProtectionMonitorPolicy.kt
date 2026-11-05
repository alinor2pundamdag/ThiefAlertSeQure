package com.thiefalertsequre.app

/**
 * Charger state transitions require a live, user-visible foreground monitor.
 * Owner opt-out is explicit; an active alarm is handled by AlarmService instead.
 */
object ProtectionMonitorPolicy {
    const val DEFAULT_ENABLED = true

    fun shouldRun(enabled: Boolean, mode: ProtectionMode): Boolean =
        enabled && mode != ProtectionMode.ALARM_TRIGGERED
}