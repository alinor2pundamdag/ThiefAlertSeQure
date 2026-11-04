package com.thiefalertsequre.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    fun schedule(
        context: Context,
        deadlineEpochMs: Long,
        deadlineElapsedRealtimeMs: Long = 0L,
        deadlineBootCount: Int = -1
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = pendingIntent(context)
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

    /**
     * Android 15+ blocks BOOT_COMPLETED receivers from directly launching a mediaPlayback
     * foreground service. A user-authorized exact alarm is an allowed background-start path.
     */
    fun scheduleImmediateRecovery(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1_000L,
                pendingIntent(context)
            )
            return false
        }
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 250L,
                pendingIntent(context)
            )
            true
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1_000L,
                pendingIntent(context)
            )
            false
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            1001,
            Intent(context, AlarmDeadlineReceiver::class.java).setAction("com.thiefalertsequre.DEADLINE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
