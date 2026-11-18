package com.thiefalertsequre.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object AlarmScheduler {
    private const val PREFS = "alarm_scheduler_v1"
    private const val KEY_LAST_TOKEN = "last_token"
    private const val REQUEST_CODE = 1001
    private const val BOOT_RECONCILIATION_RETRY_MS = 5_000L

    fun schedule(
        context: Context,
        deadlineEpochMs: Long,
        deadlineElapsedRealtimeMs: Long = 0L,
        deadlineBootCount: Int = -1
    ): Boolean = scheduleInternal(
        context, deadlineEpochMs, deadlineElapsedRealtimeMs, deadlineBootCount, PREFS, null
    )

    /** Isolated scheduler namespace for instrumentation; never touches the owner's alarm token. */
    internal fun scheduleIsolated(
        context: Context,
        deadlineEpochMs: Long,
        deadlineElapsedRealtimeMs: Long,
        deadlineBootCount: Int,
        preferencesName: String,
        namespace: String
    ): Boolean = scheduleInternal(
        context, deadlineEpochMs, deadlineElapsedRealtimeMs, deadlineBootCount, preferencesName, namespace
    )

    private fun scheduleInternal(
        context: Context,
        deadlineEpochMs: Long,
        deadlineElapsedRealtimeMs: Long,
        deadlineBootCount: Int,
        preferencesName: String,
        namespace: String?
    ): Boolean {
        if (deadlineEpochMs <= 0L) return false
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val token = deadlineToken(deadlineEpochMs, deadlineElapsedRealtimeMs, deadlineBootCount)
        val currentBootCount = ProtectionClock.bootCount(context)
        val hasMonotonicDeadline = deadlineElapsedRealtimeMs > 0L
        val sameBoot = hasMonotonicDeadline && deadlineBootCount >= 0 &&
            currentBootCount >= 0 && currentBootCount == deadlineBootCount
        val needsBootReconciliation = hasMonotonicDeadline && !sameBoot
        val clockType = if (sameBoot) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP
        val triggerAt = when {
            sameBoot -> deadlineElapsedRealtimeMs
            needsBootReconciliation -> System.currentTimeMillis() + BOOT_RECONCILIATION_RETRY_MS
            else -> deadlineEpochMs
        }
        val scheduled = replaceTokenAndScheduleInternal(context, token, preferencesName, namespace) {
            val exactAllowed = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
            if (exactAllowed) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(clockType, triggerAt, pendingIntent(context, token, namespace))
                } catch (_: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(clockType, triggerAt, pendingIntent(context, token, namespace))
                }
            } else {
                alarmManager.setAndAllowWhileIdle(clockType, triggerAt, pendingIntent(context, token, namespace))
            }
        }
        if (!scheduled) {
            LocalTamperHistory.record(context, "deadline_schedule_failed", "Alarm deadline could not be durably recorded and scheduled")
        }
        return scheduled
    }

    fun scheduleImmediateRecovery(context: Context): Boolean {
        var exactScheduled = true
        val scheduled = scheduleRecovery(context, 250L) { exactScheduled = it }
        return scheduled && exactScheduled
    }

    /** Schedules a bounded retry for an already-latched alarm recovery attempt. */
    fun scheduleRecoveryRetry(context: Context, delayMs: Long): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || runCatching {
            alarmManager.canScheduleExactAlarms()
        }.getOrDefault(false)
        if (!exactAllowed) {
            LocalTamperHistory.record(
                context, "alarm_recovery_exact_access_missing",
                "Automatic foreground-service retry was not scheduled because exact-alarm access is unavailable"
            )
            return false
        }
        val token = "recovery"
        val triggerAt = System.currentTimeMillis() + delayMs.coerceAtLeast(250L)
        return replaceTokenAndSchedule(context, token) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, token)
            )
        }
    }

    private fun scheduleRecovery(
        context: Context,
        delayMs: Long,
        onExactResult: (Boolean) -> Unit
    ): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val token = "recovery"
        var exact = Build.VERSION.SDK_INT < 31 || runCatching {
            alarmManager.canScheduleExactAlarms()
        }.getOrDefault(false)
        val scheduled = replaceTokenAndSchedule(context, token) {
            val triggerAt = System.currentTimeMillis() + delayMs.coerceAtLeast(250L)
            if (exact) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, token)
                    )
                } catch (_: SecurityException) {
                    exact = false
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, token)
                    )
                }
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, token)
                )
            }
        }
        onExactResult(exact)
        return scheduled
    }

    fun scheduleRetry(context: Context, delayMs: Long) {
        val state = ProtectionStore(context).read()
        if (state.mode != ProtectionMode.COUNTDOWN || state.deadlineEpochMs <= 0L) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val token = deadlineToken(state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount)
        val scheduled = replaceTokenAndSchedule(context, token) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delayMs.coerceAtLeast(250L),
                pendingIntent(context, token)
            )
        }
        if (!scheduled) {
            LocalTamperHistory.record(context, "deadline_retry_schedule_failed", "Could not persist or schedule the countdown retry")
        }
    }

    /** Detects only the pre-token legacy alarm so UI smoke tests can avoid cancelling it. */
    internal fun hasLegacyPendingIntent(context: Context): Boolean =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, AlarmDeadlineReceiver::class.java).setAction("com.thiefalertsequre.DEADLINE"),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null

    fun cancel(context: Context) = cancelInternal(context, PREFS, null, includeLegacy = true)

    internal fun cancelIsolated(context: Context, preferencesName: String, namespace: String) =
        cancelInternal(context, preferencesName, namespace, includeLegacy = false)

    private fun cancelInternal(
        context: Context,
        preferencesName: String,
        namespace: String?,
        includeLegacy: Boolean
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        when (val stored = SafePreferences.readString(prefs, KEY_LAST_TOKEN, null)) {
            is PreferenceRead.Value -> {
                stored.value?.let { alarmManager.cancel(pendingIntent(context, it, namespace)) }
                var cleared = prefs.edit().remove(KEY_LAST_TOKEN).commit()
                if (!cleared) cleared = prefs.edit().remove(KEY_LAST_TOKEN).commit()
                if (!cleared) {
                    LocalTamperHistory.record(context, "deadline_token_clear_failed", "Scheduled alarm token could not be removed from preferences after one retry")
                }
            }
            is PreferenceRead.Corrupt -> LocalTamperHistory.record(
                context, "deadline_token_corrupt", "Malformed alarm token retained; pending alarm could not be identified safely"
            )
        }
        if (includeLegacy) {
            alarmManager.cancel(
                PendingIntent.getBroadcast(
                    context, REQUEST_CODE,
                    Intent(context, AlarmDeadlineReceiver::class.java).setAction("com.thiefalertsequre.DEADLINE"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
    }

    /**
     * Persist the new token before scheduling; cancel the previous PendingIntent
     * only after the new alarm is accepted. On failure, restore the prior token.
     */
    internal fun replaceTokenAndSchedule(
        context: Context,
        token: String,
        schedule: () -> Unit
    ): Boolean = replaceTokenAndScheduleInternal(context, token, PREFS, null, schedule)

    internal fun replaceTokenAndScheduleIsolated(
        context: Context,
        token: String,
        preferencesName: String,
        namespace: String,
        schedule: () -> Unit
    ): Boolean = replaceTokenAndScheduleInternal(context, token, preferencesName, namespace, schedule)

    private fun replaceTokenAndScheduleInternal(
        context: Context,
        token: String,
        preferencesName: String,
        namespace: String?,
        schedule: () -> Unit
    ): Boolean {
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val previous = when (val stored = SafePreferences.readString(prefs, KEY_LAST_TOKEN, null)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> {
                LocalTamperHistory.record(context, "deadline_token_corrupt", "Malformed alarm token retained; new schedule rejected safely")
                return false
            }
        }
        var tokenPersisted = prefs.edit().putString(KEY_LAST_TOKEN, token).commit()
        if (!tokenPersisted) tokenPersisted = prefs.edit().putString(KEY_LAST_TOKEN, token).commit()
        if (!tokenPersisted) {
            LocalTamperHistory.record(context, "deadline_token_persist_failed", "Alarm token was not saved after one retry; previous alarm retained")
            return false
        }
        return try {
            schedule()
            if (previous != null && previous != token) {
                context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, previous, namespace))
            }
            true
        } catch (error: Exception) {
            fun restorePreviousToken(): Boolean {
                val restore = prefs.edit()
                if (previous == null) restore.remove(KEY_LAST_TOKEN) else restore.putString(KEY_LAST_TOKEN, previous)
                return restore.commit()
            }
            var restored = restorePreviousToken()
            if (!restored) restored = restorePreviousToken()
            if (!restored) {
                LocalTamperHistory.record(context, "deadline_token_restore_failed", "Failed scheduling left the alarm token in an uncertain state after one retry")
            }
            LocalTamperHistory.record(context, "deadline_alarm_schedule_failed", error.javaClass.simpleName + ": " + error.message)
            false
        }
    }

    private fun deadlineToken(epochMs: Long, elapsedRealtimeMs: Long, bootCount: Int): String =
        "deadline:$epochMs:$elapsedRealtimeMs:$bootCount"

    private fun pendingIntent(context: Context, token: String, namespace: String? = null): PendingIntent {
        val uri = if (namespace == null) "thiefalertsequre://alarm/$token"
        else "thiefalertsequre://alarm-test/" + Uri.encode(namespace) + "/" + token
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, AlarmDeadlineReceiver::class.java)
                .setAction("com.thiefalertsequre.DEADLINE")
                .setData(Uri.parse(uri)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
