package com.thiefalertsequre.app

const val COUNTDOWN_MS = 10_000L

object ProtectionEngine {
    fun chargerConnected(s: ProtectionState, now: Long): ProtectionState = when (s.mode) {
        ProtectionMode.ALARM_TRIGGERED -> s
        ProtectionMode.DISARMED, ProtectionMode.ARMED, ProtectionMode.COUNTDOWN ->
            ProtectionState(ProtectionMode.ARMED, 0L, now, 0L, -1)
    }

    fun chargerDisconnected(
        s: ProtectionState,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): ProtectionState = when (s.mode) {
        ProtectionMode.ARMED -> ProtectionState(
            ProtectionMode.COUNTDOWN,
            nowEpochMs + COUNTDOWN_MS,
            nowEpochMs,
            if (nowElapsedRealtimeMs > 0L) nowElapsedRealtimeMs + COUNTDOWN_MS else 0L,
            currentBootCount
        )
        ProtectionMode.COUNTDOWN, ProtectionMode.ALARM_TRIGGERED, ProtectionMode.DISARMED -> s
    }

    fun evaluateDeadline(
        s: ProtectionState,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): ProtectionState {
        if (s.mode != ProtectionMode.COUNTDOWN) return s
        val sameBoot = s.bootCount >= 0 && currentBootCount == s.bootCount &&
            s.deadlineElapsedRealtimeMs > 0L && nowElapsedRealtimeMs > 0L
        val expired = if (sameBoot) {
            nowElapsedRealtimeMs >= s.deadlineElapsedRealtimeMs
        } else {
            nowEpochMs >= s.deadlineEpochMs
        }
        return if (expired) ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0L, nowEpochMs, 0L, currentBootCount) else s
    }

    fun remainingMs(
        s: ProtectionState,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): Long {
        if (s.mode != ProtectionMode.COUNTDOWN) return 0L
        val sameBoot = s.bootCount >= 0 && currentBootCount == s.bootCount &&
            s.deadlineElapsedRealtimeMs > 0L && nowElapsedRealtimeMs > 0L
        return if (sameBoot) {
            (s.deadlineElapsedRealtimeMs - nowElapsedRealtimeMs).coerceAtLeast(0L)
        } else {
            (s.deadlineEpochMs - nowEpochMs).coerceAtLeast(0L)
        }
    }

    fun restoreAfterBoot(
        s: ProtectionState,
        isCharging: Boolean,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): ProtectionState = when (s.mode) {
        ProtectionMode.ALARM_TRIGGERED -> s
        ProtectionMode.COUNTDOWN -> if (isCharging) {
            chargerConnected(s, nowEpochMs)
        } else {
            evaluateDeadline(s, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        }
        ProtectionMode.ARMED -> if (isCharging) s else
            chargerDisconnected(s, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        ProtectionMode.DISARMED -> if (isCharging) chargerConnected(s, nowEpochMs) else s
    }

    fun authenticated(s: ProtectionState, now: Long): ProtectionState =
        if (s.mode == ProtectionMode.ALARM_TRIGGERED)
            ProtectionState(ProtectionMode.DISARMED, 0L, now, 0L, -1) else s
}
