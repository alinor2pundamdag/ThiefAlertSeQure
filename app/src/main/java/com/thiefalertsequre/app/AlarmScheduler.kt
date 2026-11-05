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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_TOKEN, token).commit()
        val sameBoot = deadlineBootCount >= 0 &&
            ProtectionClock.bootCount(context) == deadlineBootCount &&
            deadlineElapsedRealtimeMs > 0L
        val clockType = if (sameBoot) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP
        val triggerAt = if (sameBoot) deadlineElapsedRealtimeMs else deadlineEpochMs
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_TOKEN, token).commit()
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_TOKEN, token).commit()
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
