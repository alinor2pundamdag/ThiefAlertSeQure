package com.thiefalertsequre.app

import android.content.Context

/** Bounded retry policy for an alarm that is already durably latched. */
internal object AlarmRecoveryPolicy {
    private val retryDelaysMs = longArrayOf(1_000L, 3_000L, 10_000L)
    const val MAX_RETRIES = 3

    fun nextDelayMs(completedRetries: Int): Long? =
        retryDelaysMs.getOrNull(completedRetries.coerceAtLeast(0))
}

object AlarmRecoveryCoordinator {
    private const val PREFS = "alarm_recovery_v1"
    private const val KEY_RETRIES = "retry_count"

    /** Keep the owner-visible notice while scheduling only a finite number of retries. */
    fun onStartFailure(context: Context, reason: String) {
        // Notification failure must not prevent the durable retry from being scheduled.
        runCatching { AlarmRecoveryNotice.show(context, reason) }.onFailure {
            recordSafely(context, "alarm_recovery_notice_show_failed", "${it.javaClass.simpleName}: ${it.message}")
        }
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }.getOrElse {
            recordSafely(context, "alarm_recovery_preferences_unavailable", "${it.javaClass.simpleName}: ${it.message}")
            return
        }
        val stored = runCatching { SafePreferences.readInt(prefs, KEY_RETRIES, 0) }.getOrElse {
            recordSafely(context, "alarm_recovery_counter_read_failed", "${it.javaClass.simpleName}: ${it.message}")
            return
        }
        val retries = when (stored) {
            is PreferenceRead.Value -> stored.value.coerceIn(0, AlarmRecoveryPolicy.MAX_RETRIES)
            is PreferenceRead.Corrupt -> {
                recordSafely(context, "alarm_recovery_counter_corrupt", "Retry counter type mismatch; automatic retries stopped and source retained")
                return
            }
        }
        val delayMs = AlarmRecoveryPolicy.nextDelayMs(retries)
        if (delayMs == null) {
            recordSafely(context, "alarm_recovery_exhausted", "Bounded automatic alarm recovery attempts exhausted; owner action is required")
            return
        }
        val saved = runCatching { prefs.edit().putInt(KEY_RETRIES, retries + 1).commit() }.getOrDefault(false)
        if (!saved) {
            recordSafely(context, "alarm_recovery_counter_persist_failed", "Retry count was not saved; no additional automatic attempt scheduled")
            return
        }
        val retryScheduled = runCatching {
            AlarmScheduler.scheduleRecoveryRetry(context, delayMs)
        }.getOrDefault(false)
        if (retryScheduled) {
            recordSafely(context, "alarm_recovery_retry_scheduled", "Bounded recovery retry ${retries + 1}/${AlarmRecoveryPolicy.MAX_RETRIES} scheduled")
        } else {
            recordSafely(context, "alarm_recovery_retry_schedule_failed", "Android did not accept the bounded recovery retry; owner-visible notice remains")
        }
    }

    /** Called only after startForeground() succeeds for the latched alarm. Cleanup must never block alarm audio. */
    fun onServiceStarted(context: Context) {
        clearRecoveryStateSafely(context, "alarm_recovery_cleanup_failed")
        recordSafely(context, "alarm_recovery_completed", "Foreground alarm service successfully resumed")
    }

    /** A cleanup/storage exception must never interrupt the already-persisted owner disarm. */
    fun onAlarmDisarmed(context: Context) {
        clearRecoveryStateSafely(context, "alarm_recovery_disarm_cleanup_failed")
    }

    private fun clearRecoveryStateSafely(context: Context, event: String) {
        runCatching { clearRecoveryState(context) }.onFailure {
            recordSafely(
                context,
                event,
                "Alarm recovery cleanup failed after the protection state transition: ${it.javaClass.simpleName}: ${it.message}"
            )
        }
    }

    private fun recordSafely(context: Context, event: String, detail: String) {
        runCatching { LocalTamperHistory.record(context, event, detail) }
    }

    private fun clearRecoveryState(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (SafePreferences.readInt(prefs, KEY_RETRIES, 0)) {
            is PreferenceRead.Value -> if (!prefs.edit().remove(KEY_RETRIES).commit()) {
                LocalTamperHistory.record(context, "alarm_recovery_counter_clear_failed", "Recovery counter could not be cleared")
            }
            is PreferenceRead.Corrupt -> LocalTamperHistory.record(
                context, "alarm_recovery_counter_corrupt", "Malformed recovery counter retained after alarm recovery"
            )
        }
        runCatching { AlarmScheduler.cancel(context) }.onFailure {
            LocalTamperHistory.record(context, "alarm_recovery_alarm_cancel_failed", it.javaClass.simpleName + ": " + it.message)
        }
        runCatching { AlarmRecoveryNotice.cancel(context) }.onFailure {
            LocalTamperHistory.record(context, "alarm_recovery_notice_cancel_failed", it.javaClass.simpleName + ": " + it.message)
        }
    }
}
