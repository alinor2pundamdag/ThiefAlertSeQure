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
        AlarmRecoveryNotice.show(context, reason)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val retries = when (val stored = SafePreferences.readInt(prefs, KEY_RETRIES, 0)) {
            is PreferenceRead.Value -> stored.value.coerceIn(0, AlarmRecoveryPolicy.MAX_RETRIES)
            is PreferenceRead.Corrupt -> {
                LocalTamperHistory.record(context, "alarm_recovery_counter_corrupt", "Retry counter type mismatch; automatic retries stopped and source retained")
                return
            }
        }
        val delayMs = AlarmRecoveryPolicy.nextDelayMs(retries)
        if (delayMs == null) {
            LocalTamperHistory.record(context, "alarm_recovery_exhausted", "Bounded automatic alarm recovery attempts exhausted; owner action is required")
            return
        }
        if (!prefs.edit().putInt(KEY_RETRIES, retries + 1).commit()) {
            LocalTamperHistory.record(context, "alarm_recovery_counter_persist_failed", "Retry count was not saved; no additional automatic attempt scheduled")
            return
        }
        if (AlarmScheduler.scheduleRecoveryRetry(context, delayMs)) {
            LocalTamperHistory.record(context, "alarm_recovery_retry_scheduled", "Bounded recovery retry ${retries + 1}/${AlarmRecoveryPolicy.MAX_RETRIES} scheduled")
        } else {
            LocalTamperHistory.record(context, "alarm_recovery_retry_schedule_failed", "Android did not accept the bounded recovery retry; owner-visible notice remains")
        }
    }

    /** Called only after startForeground() succeeds for the latched alarm. */
    fun onServiceStarted(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.edit().remove(KEY_RETRIES).commit()) {
            LocalTamperHistory.record(context, "alarm_recovery_counter_clear_failed", "Alarm resumed but retry counter could not be cleared")
        }
        AlarmScheduler.cancel(context)
        AlarmRecoveryNotice.cancel(context)
        LocalTamperHistory.record(context, "alarm_recovery_completed", "Foreground alarm service successfully resumed")
    }
}
