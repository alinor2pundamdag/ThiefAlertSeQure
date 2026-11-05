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
    ) {
        if (deadlineEpochMs <= 0L) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val token = deadlineToken(deadlineEpochMs, deadlineElapsedRealtimeMs, deadlineBootCount)
        val operation = pendingIntent(context, token)
        rememberToken(context, token)
        val currentBootCount = ProtectionClock.bootCount(context)
        val hasMonotonicDeadline = deadlineElapsedRealtimeMs > 0L
        val sameBoot = hasMonotonicDeadline && deadlineBootCount >= 0 &&
            currentBootCount >= 0 && currentBootCount == deadlineBootCount
        val needsBootReconciliation = hasMonotonicDeadline && !sameBoot
        val clockType = if (sameBoot) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP
        // Never schedule an old monotonic deadline against wall time when boot
        // identity is unknown/different; wake only to revalidate and rebase it.
        val triggerAt = when {
            sameBoot -> deadlineElapsedRealtimeMs
            needsBootReconciliation -> System.currentTimeMillis() + BOOT_RECONCILIATION_RETRY_MS
            else -> deadlineEpochMs
        }
        val exactAllowed = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            try {
                alarmManager.setExactAndAllowWhileIdle(clockType, triggerAt, operation)
                return
            } catch (_: SecurityException) {
                // Permission can be revoked between the capability check and scheduling.
            }
        }
        alarmManager.setAndAllowWhileIdle(clockType, triggerAt, operation)
    }

    fun scheduleImmediateRecovery(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val token = "recovery"
        val operation = pendingIntent(context, token)
        rememberToken(context, token)
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1_000L, operation)
            return false
        }
        return try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 250L, operation)
            true
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1_000L, operation)
            false
        }
    }

    fun scheduleRetry(context: Context, delayMs: Long) {
        val state = ProtectionStore(context).read()
        if (state.mode != ProtectionMode.COUNTDOWN || state.deadlineEpochMs <= 0L) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val token = deadlineToken(state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount)
        val operation = pendingIntent(context, token)
        rememberToken(context, token)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delayMs.coerceAtLeast(250L),
            operation
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_LAST_TOKEN, null)?.let { alarmManager.cancel(pendingIntent(context, it)) }
        prefs.edit().remove(KEY_LAST_TOKEN).commit()
        // Cancel the pre-tokenized PendingIntent used by older app versions.
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context, REQUEST_CODE,
                Intent(context, AlarmDeadlineReceiver::class.java).setAction("com.thiefalertsequre.DEADLINE"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun rememberToken(context: Context, token: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString(KEY_LAST_TOKEN, null)
        if (previous != null && previous != token) {
            alarmManager.cancel(pendingIntent(context, previous))
        }
        prefs.edit().putString(KEY_LAST_TOKEN, token).commit()
    }

    private fun deadlineToken(epochMs: Long, elapsedRealtimeMs: Long, bootCount: Int): String =
        "deadline:$epochMs:$elapsedRealtimeMs:$bootCount"

    private fun pendingIntent(context: Context, token: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, AlarmDeadlineReceiver::class.java)
                .setAction("com.thiefalertsequre.DEADLINE")
                .setData(Uri.parse("thiefalertsequre://alarm/$token")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
