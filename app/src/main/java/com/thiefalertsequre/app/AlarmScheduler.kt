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
    private const val ACTION_DEADLINE = "com.thiefalertsequre.DEADLINE"
    private const val ACTION_DEADLINE_FALLBACK = "com.thiefalertsequre.DEADLINE_FALLBACK"
    private const val URI_PREFIX = "thiefalertsequre://alarm/"
    private const val BOOT_RECONCILIATION_RETRY_MS = 5_000L

    fun schedule(
        context: Context,
        deadlineEpochMs: Long,
        deadlineElapsedRealtimeMs: Long = 0L,
        deadlineBootCount: Int = -1
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
        val scheduled = replaceTokenAndSchedule(context, token) {
            scheduleCallbacks(context, token, alarmManager, clockType, triggerAt)
        }
        if (!scheduled) {
            LocalTamperHistory.record(context, "deadline_schedule_failed", "Alarm deadline could not be durably recorded and scheduled")
        }
        return scheduled
    }

    /** A bounded inexact fallback is retained even when the exact callback is available. */
    fun scheduleImmediateRecovery(context: Context): Boolean = scheduleRecovery(context, 250L)

    /** Recovery still has a delayed path when exact-alarm access is denied or revoked. */
    fun scheduleRecoveryRetry(context: Context, delayMs: Long): Boolean =
        scheduleRecovery(context, delayMs)

    private fun scheduleRecovery(context: Context, delayMs: Long): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val token = "recovery"
        val triggerAt = System.currentTimeMillis() + delayMs.coerceAtLeast(250L)
        return replaceTokenAndSchedule(context, token) {
            scheduleCallbacks(
                context, token, alarmManager, AlarmManager.RTC_WAKEUP, triggerAt
            )
        }
    }

    fun scheduleRetry(context: Context, delayMs: Long) {
        val state = ProtectionStore(context).read()
        if (state.mode != ProtectionMode.COUNTDOWN || state.deadlineEpochMs <= 0L) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val token = deadlineToken(state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount)
        val triggerAt = System.currentTimeMillis() + delayMs.coerceAtLeast(250L)
        val scheduled = replaceTokenAndSchedule(context, token) {
            scheduleCallbacks(
                context, token, alarmManager, AlarmManager.RTC_WAKEUP, triggerAt
            )
        }
        if (!scheduled) {
            LocalTamperHistory.record(context, "deadline_retry_schedule_failed", "Could not persist or schedule the countdown retry")
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (val stored = SafePreferences.readString(prefs, KEY_LAST_TOKEN, null)) {
            is PreferenceRead.Value -> {
                stored.value?.let { cancelCallbacks(context, it) }
                if (!prefs.edit().remove(KEY_LAST_TOKEN).commit()) {
                    LocalTamperHistory.record(context, "deadline_token_clear_failed", "Scheduled alarm token could not be removed from preferences")
                }
            }
            is PreferenceRead.Corrupt -> LocalTamperHistory.record(
                context, "deadline_token_corrupt", "Malformed alarm token retained; pending alarm could not be identified safely"
            )
        }
        cancelPendingIntent(
            alarmManager,
            PendingIntent.getBroadcast(
                context, REQUEST_CODE,
                Intent(context, AlarmDeadlineReceiver::class.java).setAction(ACTION_DEADLINE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        cancelPendingIntent(
            alarmManager,
            PendingIntent.getBroadcast(
                context, REQUEST_CODE,
                Intent(context, AlarmDeadlineReceiver::class.java).setAction(ACTION_DEADLINE_FALLBACK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    /**
     * Persist the new token before scheduling; cancel the previous PendingIntent
     * pair only after the new callbacks are accepted. On failure, restore the prior token.
     */
    internal fun replaceTokenAndSchedule(
        context: Context,
        token: String,
        schedule: () -> Unit
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = when (val stored = SafePreferences.readString(prefs, KEY_LAST_TOKEN, null)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> {
                LocalTamperHistory.record(context, "deadline_token_corrupt", "Malformed alarm token retained; new schedule rejected safely")
                return false
            }
        }
        if (!prefs.edit().putString(KEY_LAST_TOKEN, token).commit()) {
            LocalTamperHistory.record(context, "deadline_token_persist_failed", "Alarm token was not saved; previous alarm retained")
            return false
        }
        return try {
            schedule()
            if (previous != null && previous != token) {
                cancelCallbacks(context, previous)
            }
            true
        } catch (error: Exception) {
            if (previous != token) runCatching { cancelCallbacks(context, token) }
            val restore = prefs.edit()
            if (previous == null) restore.remove(KEY_LAST_TOKEN) else restore.putString(KEY_LAST_TOKEN, previous)
            if (!restore.commit()) {
                LocalTamperHistory.record(context, "deadline_token_restore_failed", "Failed scheduling left the alarm token in an uncertain state")
            }
            LocalTamperHistory.record(context, "deadline_alarm_schedule_failed", error.javaClass.simpleName + ": " + error.message)
            false
        }
    }

    private fun scheduleCallbacks(
        context: Context,
        token: String,
        alarmManager: AlarmManager,
        clockType: Int,
        triggerAt: Long
    ) {
        val hadExactOperation = hasPendingIntent(context, token, fallback = false)
        val hadFallbackOperation = hasPendingIntent(context, token, fallback = true)
        val exactOperation = pendingIntent(context, token, fallback = false)
        val fallbackOperation = pendingIntent(context, token, fallback = true)
        val exactPermissionState: Boolean? = if (Build.VERSION.SDK_INT < 31) {
            true
        } else {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrNull()
        }
        val exactAllowed = Build.VERSION.SDK_INT < 31 || exactPermissionState == true

        var exactScheduled = false
        if (exactAllowed) {
            try {
                alarmManager.setExactAndAllowWhileIdle(clockType, triggerAt, exactOperation)
                exactScheduled = true
            } catch (error: Exception) {
                val permissionAfterFailure: Boolean? = if (Build.VERSION.SDK_INT < 31) {
                    true
                } else {
                    runCatching { alarmManager.canScheduleExactAlarms() }.getOrNull()
                }
                if (AlarmCallbackPolicy.shouldCancelRejectedExactCallback(
                        permissionAfterFailure, hadExactOperation
                    )
                ) {
                    runCatching { cancelPendingIntent(alarmManager, exactOperation) }
                }
                LocalTamperHistory.record(
                    context, "deadline_exact_schedule_failed",
                    error.javaClass.simpleName + ": " + error.message + "; trying the inexact fallback"
                )
            }
        } else {
            if (AlarmCallbackPolicy.shouldCancelRejectedExactCallback(
                    exactPermissionState, hadExactOperation
                )
            ) {
                runCatching { cancelPendingIntent(alarmManager, exactOperation) }
            }
            LocalTamperHistory.record(
                context, "deadline_exact_access_missing",
                "The inexact idle-aware countdown/recovery fallback is scheduled; Android may delay delivery"
            )
        }

        var fallbackScheduled = false
        try {
            alarmManager.setAndAllowWhileIdle(clockType, triggerAt, fallbackOperation)
            fallbackScheduled = true
        } catch (error: Exception) {
            if (!hadFallbackOperation) {
                runCatching { cancelPendingIntent(alarmManager, fallbackOperation) }
            }
            LocalTamperHistory.record(
                context, "deadline_inexact_fallback_schedule_failed",
                error.javaClass.simpleName + ": " + error.message
            )
        }

        // A failed replacement must not discard a callback that was already accepted.
        if (!AlarmCallbackPolicy.hasUsableDeadlineCallback(
                exactScheduled, fallbackScheduled
            )
        ) {
            throw IllegalStateException("Android rejected both exact and inexact alarm callbacks")
        }
    }

    private fun cancelCallbacks(context: Context, token: String) {
        val manager = context.getSystemService(AlarmManager::class.java)
        cancelPendingIntent(manager, pendingIntent(context, token, fallback = false))
        cancelPendingIntent(manager, pendingIntent(context, token, fallback = true))
    }

    private fun cancelPendingIntent(manager: AlarmManager, operation: PendingIntent) {
        manager.cancel(operation)
        operation.cancel()
    }

    private fun pendingIntent(context: Context, token: String, fallback: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, AlarmDeadlineReceiver::class.java)
                .setAction(if (fallback) ACTION_DEADLINE_FALLBACK else ACTION_DEADLINE)
                .setData(Uri.parse(URI_PREFIX + token)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    internal fun hasPendingIntent(context: Context, token: String, fallback: Boolean): Boolean {
        val intent = Intent(context, AlarmDeadlineReceiver::class.java)
            .setAction(if (fallback) ACTION_DEADLINE_FALLBACK else ACTION_DEADLINE)
            .setData(Uri.parse(URI_PREFIX + token))
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null
    }

    private fun deadlineToken(epochMs: Long, elapsedRealtimeMs: Long, bootCount: Int): String =
        "deadline:" + epochMs + ":" + elapsedRealtimeMs + ":" + bootCount
}
