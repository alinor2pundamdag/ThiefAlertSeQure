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
        val pendingIntent = pendingIntent(context)
        val sameBoot = deadlineBootCount >= 0 &&
            ProtectionClock.bootCount(context) == deadlineBootCount &&
            deadlineElapsedRealtimeMs > 0L
        val clockType = if (sameBoot) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP
        val triggerAt = if (sameBoot) deadlineElapsedRealtimeMs else deadlineEpochMs
        if (Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(clockType, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(clockType, triggerAt, pendingIntent)
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
