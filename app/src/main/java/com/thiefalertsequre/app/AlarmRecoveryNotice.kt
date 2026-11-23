package com.thiefalertsequre.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Policy-compliant user-visible fallback when Android defers alarm foreground playback. */
object AlarmRecoveryNotice {
    private const val CHANNEL_ID = "alarm_recovery_required"
    private const val NOTIFICATION_ID = 9042

    fun cancel(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }.onFailure {
            LocalTamperHistory.record(context, "alarm_recovery_notice_cancel_failed", it.javaClass.simpleName + ": " + it.message)
        }
    }

    fun show(context: Context, reason: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            LocalTamperHistory.record(context, "alarm_recovery_notice_blocked", "Notification permission unavailable; reason=$reason")
            return
        }
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, "Alarm recovery", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Tap to reopen ThiefAlertSeQure when Android defers alarm recovery"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                })
            }
            val open = PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("ThiefAlertSeQure needs attention")
                .setContentText("Open the app to resume the active protection alarm.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Protection remains latched. Android deferred background alarm playback. " +
                        "Open ThiefAlertSeQure to resume the alarm and authenticate as the owner."
                ))
                .setContentIntent(open)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
            LocalTamperHistory.record(context, "alarm_recovery_notice_shown", reason)
        }.onFailure {
            LocalTamperHistory.record(context, "alarm_recovery_notice_failed", it.javaClass.simpleName + ": " + it.message)
        }
    }
}