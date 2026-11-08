package com.thiefalertsequre.app

const val COUNTDOWN_MS = 10_000L

object ProtectionEngine {
    fun shouldScheduleDeadline(mode: ProtectionMode): Boolean = mode == ProtectionMode.COUNTDOWN

    /** Rejects callbacks belonging to a superseded or already-cancelled countdown. */
    fun isCurrentDeadline(
        s: ProtectionState,
        deadlineEpochMs: Long,
        deadlineElapsedRealtimeMs: Long,
        deadlineBootCount: Int
    ): Boolean = s.mode == ProtectionMode.COUNTDOWN &&
        deadlineEpochMs > 0L &&
        s.deadlineEpochMs == deadlineEpochMs &&
        s.deadlineElapsedRealtimeMs == deadlineElapsedRealtimeMs &&
        s.bootCount == deadlineBootCount

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
            if (nowElapsedRealtimeMs > 0L && currentBootCount >= 0) nowElapsedRealtimeMs + COUNTDOWN_MS else 0L,
            currentBootCount
        )
        ProtectionMode.COUNTDOWN, ProtectionMode.ALARM_TRIGGERED, ProtectionMode.DISARMED -> s
    }

    /** Re-check live power before allowing an expired countdown to latch the alarm. */
    fun reconcileCountdown(
        s: ProtectionState,
        chargerConnected: Boolean?,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): ProtectionState {
        if (s.mode != ProtectionMode.COUNTDOWN) return s

        // elapsedRealtime resets on reboot. Never compare an old-boot deadline
        // against the new boot's clock or wall time; require a live power sample
        // and restart the full grace period when still unplugged.
        if (chargerConnected == true) return chargerConnected(s, nowEpochMs)
        if (chargerConnected == null) return s

        // Boot completed but Android could not provide a reliable charger sample.
        // Keep a short retry token until live power is known. The first confirmed
        // disconnected sample starts a fresh full grace period; never let an old
        // boot's monotonic deadline expire or hang forever on devices without BOOT_COUNT.
        if (s.bootCount == BOOT_RECONCILIATION_PENDING && s.deadlineElapsedRealtimeMs == 0L) {
            return chargerDisconnected(
                ProtectionState(ProtectionMode.ARMED, 0L, nowEpochMs),
                nowEpochMs, nowElapsedRealtimeMs, currentBootCount
            )
        }

        val hasMonotonicDeadline = s.deadlineElapsedRealtimeMs > 0L
        // No valid boot identity means elapsedRealtime cannot safely be compared
        // with a persisted deadline. Stay pending rather than inventing expiry.
        if (hasMonotonicDeadline && currentBootCount < 0) return s

        // A countdown created before a reliable boot identity was available is
        // untrusted once Android later supplies one. Rebase it with a fresh grace
        // period rather than comparing its old wall-clock deadline.
        val bootChanged = hasMonotonicDeadline &&
            (s.bootCount < 0 || currentBootCount != s.bootCount)
        if (bootChanged) {
            return restoreAfterBoot(
                s, false, nowEpochMs, nowElapsedRealtimeMs, currentBootCount
            )
        }

        return evaluateDeadline(s, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
    }

    fun evaluateDeadline(
        s: ProtectionState,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): ProtectionState {
        if (s.mode != ProtectionMode.COUNTDOWN) return s
        val hasMonotonicDeadline = s.deadlineElapsedRealtimeMs > 0L
        val sameBoot = s.bootCount >= 0 && currentBootCount >= 0 &&
            currentBootCount == s.bootCount && nowElapsedRealtimeMs > 0L
        // Never fall back to mutable wall time for a monotonic deadline when
        // boot identity is missing or differs. That can turn reboot into a false alarm.
        if (hasMonotonicDeadline && !sameBoot) return s
        val expired = if (hasMonotonicDeadline) {
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
        val hasMonotonicDeadline = s.deadlineElapsedRealtimeMs > 0L
        val sameBoot = s.bootCount >= 0 && currentBootCount >= 0 &&
            currentBootCount == s.bootCount && nowElapsedRealtimeMs > 0L
        return if (hasMonotonicDeadline && sameBoot) {
            (s.deadlineElapsedRealtimeMs - nowElapsedRealtimeMs).coerceAtLeast(0L)
        } else if (hasMonotonicDeadline) {
            // Until boot identity can be verified, do not display an expired
            // wall-clock deadline as zero remaining.
            COUNTDOWN_MS
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
            // Reboot resets elapsedRealtime. Revalidate the existing countdown and
            // grant a fresh full grace period; never fire immediately from a snapshot.
            ProtectionState(
                ProtectionMode.COUNTDOWN, nowEpochMs + COUNTDOWN_MS, nowEpochMs,
                if (nowElapsedRealtimeMs > 0L && currentBootCount >= 0) nowElapsedRealtimeMs + COUNTDOWN_MS else 0L,
                currentBootCount
            )
        }
        // A disconnected boot snapshot is not evidence of a new unplug event.
        ProtectionMode.ARMED -> s
        ProtectionMode.DISARMED -> if (isCharging) chargerConnected(s, nowEpochMs) else s
    }

    fun authenticated(s: ProtectionState, now: Long): ProtectionState =
        if (s.mode == ProtectionMode.ALARM_TRIGGERED)
            ProtectionState(ProtectionMode.DISARMED, 0L, now, 0L, -1) else s
}
