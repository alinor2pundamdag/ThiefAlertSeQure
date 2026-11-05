package com.thiefalertsequre.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val callbackIntent = intent ?: return
        val pending = goAsync()
        Thread {
            try {
                handleDeadline(context.applicationContext, callbackIntent)
            } catch (error: Exception) {
                LocalTamperHistory.record(
                    context, "deadline_receiver_error",
                    error.javaClass.simpleName + ": " + error.message
                )
                AlarmScheduler.scheduleRetry(context, 1_000L)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handleDeadline(context: Context, intent: Intent) {
        val token = intent.data?.lastPathSegment ?: return
        val state = ProtectionStore(context).read()

        // Recovery callbacks are distinct from countdown callbacks and may only
        // restart an already-latched alarm.
        if (token == "recovery") {
            if (state.mode == ProtectionMode.ALARM_TRIGGERED) {
                startAlarm(context, "Latched alarm recovery callback")
            }
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

        // Recheck physical power over time at the deadline. A single stale
        // BATTERY_CHANGED snapshot must not be enough to activate the alarm.
        val connected = readStableConnected(context)
        if (connected == null) {
            LocalTamperHistory.record(context, "deadline_validation_inconclusive", "Power state did not produce three consecutive disconnected samples")
            AlarmScheduler.scheduleRetry(context, 1_000L)
            return
        }

        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        var transitionedToAlarm = false
        var callbackStillCurrent = false
        val updated = ProtectionStore(context).update { current ->
            // Revalidate the token under the same store lock as the transition.
            // A newer countdown or reconnect may have replaced it during sampling.
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
                LocalTamperHistory.record(context, "countdown_expired", "Stable unplug samples and deadline validation transitioned to alarm")
                startAlarm(context, "Countdown deadline validated")
            }
            ProtectionMode.ARMED, ProtectionMode.DISARMED -> {
                AlarmScheduler.cancel(context)
                if (connected) {
                    PowerTransitionCoordinator.confirmConnectedAndArm(
                        context, "Stable deadline samples confirmed external power"
                    )
                }
            }
            else -> Unit
        }
    }

    /** true=connected, false=stably disconnected, null=unknown/unstable. */
    private fun readStableConnected(context: Context): Boolean? {
        var consecutiveDisconnected = 0
        var lastObserved: Boolean? = null
        repeat(5) { index ->
            val observed = ExternalPowerState.readConnected(context)
            lastObserved = observed
            consecutiveDisconnected = if (observed == false) consecutiveDisconnected + 1 else 0
            if (index < 4) Thread.sleep(200L)
        }
        return when {
            consecutiveDisconnected >= 3 -> false
            lastObserved == true -> true
            else -> null
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
