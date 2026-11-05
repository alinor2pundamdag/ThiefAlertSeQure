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

        val expectedToken = "deadline:${state.deadlineEpochMs}"
        if (state.mode != ProtectionMode.COUNTDOWN || token != expectedToken) {
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
        val updated = ProtectionStore(context).update { current ->
            val next = ProtectionEngine.reconcileCountdown(current, connected, now, elapsed, boot)
            transitionedToAlarm = current.mode == ProtectionMode.COUNTDOWN &&
                next.mode == ProtectionMode.ALARM_TRIGGERED
            next
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