package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val token = intent?.data?.lastPathSegment ?: return
        val state = ProtectionStore(context).read()

        // Recovery callbacks are distinct from countdown callbacks and may only
        // restart an already-latched alarm.
        if (token == "recovery") {
            if (state.mode == ProtectionMode.ALARM_TRIGGERED) startAlarm(context, "Latched alarm recovery callback")
            return
        }

        val tokenParts = token.split(":")
        val tokenDeadline = tokenParts.getOrNull(1)?.toLongOrNull()
        val tokenElapsed = tokenParts.getOrNull(2)?.toLongOrNull()
        val tokenBoot = tokenParts.getOrNull(3)?.toIntOrNull()
        if (tokenParts.size != 4 || tokenParts[0] != "deadline" ||
            tokenDeadline == null || tokenElapsed == null || tokenBoot == null ||
            !ProtectionEngine.isCurrentDeadline(state, tokenDeadline, tokenElapsed, tokenBoot)
        ) {
            LocalTamperHistory.record(context, "stale_deadline_rejected", "Deadline callback did not match the active countdown")
            return
        }

        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        val connected = ExternalPowerState.readConnected(context)
        if (connected == null) {
            AlarmScheduler.scheduleRetry(context, 1_000L)
            return
        }

        var transitionedToAlarm = false
        var callbackStillCurrent = false
        val updated = ProtectionStore(context).update { current ->
            // Revalidate the token under the same store lock as the transition.
            // A newer countdown may have replaced it after the initial read above.
            if (!ProtectionEngine.isCurrentDeadline(
                    current, tokenDeadline, tokenElapsed, tokenBoot
                )
            ) return@update current

            callbackStillCurrent = true
            val next = ProtectionEngine.reconcileCountdown(current, connected, now, elapsed, boot)
            transitionedToAlarm = current.mode == ProtectionMode.COUNTDOWN &&
                next.mode == ProtectionMode.ALARM_TRIGGERED
            next
        }
        if (!callbackStillCurrent) {
            LocalTamperHistory.record(context, "stale_deadline_rejected", "Countdown changed while deadline callback was validating")
            return
        }

        when (updated.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(
                context, updated.deadlineEpochMs, updated.deadlineElapsedRealtimeMs, updated.bootCount
            )
            ProtectionMode.ALARM_TRIGGERED -> if (transitionedToAlarm) {
                AlarmScheduler.cancel(context)
                context.stopService(Intent(context, ProtectionMonitorService::class.java))
                LocalTamperHistory.record(context, "countdown_expired", "Validated countdown transitioned to alarm")
                startAlarm(context, "Countdown deadline validated")
            }
            ProtectionMode.ARMED, ProtectionMode.DISARMED -> AlarmScheduler.cancel(context)
            else -> Unit
        }
    }

    private fun startAlarm(context: Context, reason: String) {
        runCatching {
            ContextCompat.startForegroundService(
                context, Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START)
            )
        }.onFailure {
            LocalTamperHistory.record(context, "alarm_fgs_launch_failed", it.javaClass.simpleName + ": " + it.message)
            AlarmRecoveryNotice.show(context, "Android rejected the alarm foreground-service launch: $reason")
        }
    }
}