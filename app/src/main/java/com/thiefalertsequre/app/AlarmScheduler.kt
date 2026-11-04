package com.thiefalertsequre.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    fun schedule(context: Context, deadline: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 1001, Intent(context, AlarmDeadlineReceiver::class.java).setAction("com.thiefalertseq ure.DEADLINE".replace(" ", "")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pi)
        }
    }

    fun cancel(context: Context) {
        val pi = PendingIntent.getBroadcast(context, 1001,
            Intent(context, AlarmDeadlineReceiver::class.java).setAction("com.thiefalertseq ure.DEADLINE".replace(" ", "")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java).cancel(pi)
    }
}
