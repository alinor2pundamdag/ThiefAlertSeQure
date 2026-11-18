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
                if (callbackIntent.action == A2DisconnectScheduler.ACTION_DEADLINE) {
                    handleA2DisconnectDeadline(context.applicationContext, callbackIntent)
                } else {
                    handleDeadline(context.applicationContext, callbackIntent)
                }
            } catch (error: Exception) {
                LocalTamperHistory.record(
                    context, "deadline_receiver_error",
                    error.javaClass.simpleName + ": " + error.message
                )
                if (callbackIntent.action == A2DisconnectScheduler.ACTION_DEADLINE) {
                    val token = callbackIntent.data?.lastPathSegment
                    if (token != null) A2DisconnectScheduler.find(context, token)?.let {
                        A2DisconnectScheduler.scheduleRetry(context, it, 1_000L)
                    }
                } else if (ProtectionStore(context).read().mode == ProtectionMode.ALARM_TRIGGERED) {
                    AlarmRecoveryCoordinator.onStartFailure(
                        context, "Deadline receiver failed while the alarm was latched"
                    )
                } else {
                    AlarmScheduler.scheduleRetry(context, 1_000L)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handleA2DisconnectDeadline(context: Context, intent: Intent) {
        val token = intent.data?.lastPathSegment ?: return
        val pending = A2DisconnectScheduler.find(context, token) ?: return
        if (ProtectionStore(context).read().mode == ProtectionMode.ALARM_TRIGGERED) {
            A2DisconnectScheduler.cancel(context, pending.address, pending.eventElapsedRealtimeMs)
            startAlarm(context, "Recovering an already-latched A2 alarm")
            return
        }
        val nowElapsed = ProtectionClock.elapsedRealtime()
        val sameBoot = A2DisconnectPolicy.isSameBoot(
            pending.bootCount, ProtectionClock.bootCount(context),
            pending.eventElapsedRealtimeMs, nowElapsed
        )
        if (!sameBoot) {
            A2DisconnectScheduler.cancel(context, pending.address, pending.eventElapsedRealtimeMs)
            LocalTamperHistory.record(context, "a2_deadline_old_boot_rejected", "A2 deadline belonged to a different or unverifiable boot")
            return
        }
        if (nowElapsed < pending.deadlineElapsedRealtimeMs) {
            A2DisconnectScheduler.schedule(context, pending)
            return
        }

        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val valid = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false) &&
            TrustedBluetoothRegistry.isTrusted(prefs, pending.address) &&
            A2DisconnectPolicy.isPendingDisconnectCurrent(
                pending.monitorRevision,
                TrustedBluetoothRegistry.revision(prefs),
                pending.deviceRevision,
                TrustedBluetoothRegistry.deviceRevision(prefs, pending.address)
            ) &&
            !TrustedBluetoothRegistry.isBaselineConnected(prefs, pending.address) &&
            ProtectionStore(context).read().mode == ProtectionMode.ARMED
        if (!valid) {
            A2DisconnectScheduler.cancel(context, pending.address, pending.eventElapsedRealtimeMs)
            LocalTamperHistory.record(context, "a2_deadline_invalidated", "A2 disconnect deadline failed a current trust, generation, connection, or protection-state guard")
            return
        }

        val update = ProtectionStore(context).updatePersisted { current ->
            ProtectionEngine.triggerFromArmed(
                current, System.currentTimeMillis(), ProtectionClock.bootCount(context)
            )
        }
        if (!update.persisted) {
            val retryScheduled = A2DisconnectScheduler.scheduleRetry(context, pending, 1_000L)
            LocalTamperHistory.record(
                context, "a2_alarm_state_persist_failed",
                if (retryScheduled) {
                    "A2 alarm state commit failed; the original disconnect deadline remains pending for a one-second retry"
                } else {
                    "A2 alarm state commit failed and its retry could not be scheduled; owner action may be required"
                }
            )
            return
        }
        A2DisconnectScheduler.cancel(context, pending.address, pending.eventElapsedRealtimeMs)
        if (update.state.mode == ProtectionMode.ALARM_TRIGGERED) {
            LocalTamperHistory.record(context, "trusted_bluetooth_separation_alarm", "A registered trusted Bluetooth device remained disconnected beyond grace period")
            context.stopService(Intent(context, ProtectionMonitorService::class.java))
            startAlarm(context, "Trusted Bluetooth separation deadline validated")
        }
    }

    private fun handleDeadline(context: Context, intent: Intent) {
        // Run baseline migration/reconciliation before any persisted deadline can
        // transition state, including callbacks left over from an older APK.
        PowerTransitionCoordinator.initializeBaseline(context)

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
        // A corrupted state is conservatively persisted as ALARM_TRIGGERED. A
        // stale deadline callback must still recover that latch, not discard it
        // merely because its old countdown token no longer matches.
        if (state.mode == ProtectionMode.ALARM_TRIGGERED) {
            startAlarm(context, "Latched alarm observed during deadline callback")
            return
        }

        val monitorPrefs = context.getSharedPreferences(
            ProtectionMonitorService.PREFS, Context.MODE_PRIVATE
        )
        val monitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        if (!monitorEnabled && state.mode != ProtectionMode.ALARM_TRIGGERED) {
            val disabledUpdate = ProtectionStore(context).updatePersisted { current ->
                ProtectionEngine.disableMonitoring(current, System.currentTimeMillis())
            }
            if (!disabledUpdate.persisted) {
                LocalTamperHistory.record(
                    context, "disabled_monitor_state_persist_failed",
                    "The monitor is OFF, but the protection state was not durably disarmed; the existing deadline was retained"
                )
                if (disabledUpdate.state.mode == ProtectionMode.COUNTDOWN) {
                    AlarmScheduler.scheduleRetry(context, 1_000L)
                }
                return
            }
            AlarmScheduler.cancel(context)
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

        // The physical unplug was already confirmed before the countdown began.
        // Re-read live power at the deadline without adding a second multi-sample
        // debounce that would make the ten-second grace period run long.
        val connected = ExternalPowerState.readConnected(context)
        if (connected == null) {
            LocalTamperHistory.record(context, "deadline_validation_inconclusive", "Live charger state was unavailable at the countdown deadline")
            AlarmScheduler.scheduleRetry(context, 1_000L)
            return
        }

        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(context)
        var transitionedToAlarm = false
        var callbackStillCurrent = false
        val update = ProtectionStore(context).updatePersisted { current ->
            // Revalidate the token under the same store lock as the transition.
            // A newer countdown or reconnect may have replaced it during sampling.
            if (!ProtectionEngine.isCurrentDeadline(
                    current, tokenDeadline, tokenElapsed, tokenBoot
                )
            ) return@updatePersisted current

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
        if (!update.persisted) {
            LocalTamperHistory.record(context, "deadline_state_persist_failed", "Alarm deadline transition was not committed; persisted state was retained")
            if (update.state.mode == ProtectionMode.COUNTDOWN) {
                AlarmScheduler.scheduleRetry(context, 1_000L)
            }
            return
        }

        val updated = update.state
        when (updated.mode) {
            ProtectionMode.COUNTDOWN -> {
                if (!AlarmScheduler.schedule(
                        context, updated.deadlineEpochMs, updated.deadlineElapsedRealtimeMs, updated.bootCount
                    )
                ) {
                    AlarmScheduler.scheduleRetry(context, 1_000L)
                }
            }
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

    private fun startAlarm(context: Context, reason: String) {
        runCatching {
            ContextCompat.startForegroundService(
                context, Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_START)
            )
        }.onFailure {
            LocalTamperHistory.record(context, "alarm_fgs_launch_failed", it.javaClass.simpleName + ": " + it.message)
            AlarmRecoveryCoordinator.onStartFailure(
                context, "Android rejected the alarm foreground-service launch: $reason"
            )
        }
    }
}
