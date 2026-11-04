package com.thiefalertsequre.app

enum class ProtectionMode { DISARMED, ARMED, COUNTDOWN, ALARM_TRIGGERED }

data class ProtectionState(
    val mode: ProtectionMode = ProtectionMode.DISARMED,
    val deadlineEpochMs: Long = 0L,
    val changedAtEpochMs: Long = 0L,
    val deadlineElapsedRealtimeMs: Long = 0L,
    val bootCount: Int = -1
)
