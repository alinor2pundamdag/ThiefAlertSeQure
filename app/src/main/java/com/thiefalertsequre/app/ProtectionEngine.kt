package com.thiefalertseq ure.app

const val COUNTDOWN_MS = 10_000L

object ProtectionEngine {
    fun chargerConnected(s: ProtectionState, now: Long): ProtectionState = when (s.mode) {
        ProtectionMode.ALARM_TRIGGERED -> s
        ProtectionMode.DISARMED, ProtectionMode.ARMED, ProtectionMode.COUNTDOWN ->
            ProtectionState(ProtectionMode.ARMED, 0L, now)
    }

    fun chargerDisconnected(s: ProtectionState, now: Long): ProtectionState = when (s.mode) {
        ProtectionMode.ARMED -> ProtectionState(ProtectionMode.COUNTDOWN, now + COUNTDOWN_MS, now)
        ProtectionMode.COUNTDOWN, ProtectionMode.ALARM_TRIGGERED, ProtectionMode.DISARMED -> s
    }

    fun evaluateDeadline(s: ProtectionState, now: Long): ProtectionState =
        if (s.mode == ProtectionMode.COUNTDOWN && now >= s.deadlineEpochMs)
            ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0L, now) else s

    fun restoreAfterBoot(s: ProtectionState, isCharging: Boolean, now: Long): ProtectionState = when (s.mode) {
        ProtectionMode.ALARM_TRIGGERED -> s
        ProtectionMode.COUNTDOWN -> evaluateDeadline(s, now)
        ProtectionMode.ARMED -> if (isCharging) s else chargerDisconnected(s, now)
        ProtectionMode.DISARMED -> if (isCharging) chargerConnected(s, now) else s
    }

    fun authenticated(s: ProtectionState, now: Long): ProtectionState =
        if (s.mode == ProtectionMode.ALARM_TRIGGERED)
            ProtectionState(ProtectionMode.DISARMED, 0L, now) else s
}
