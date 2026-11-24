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

/** Separate user-visible fallback for a monitor FGS that Android refused to start. */
object ProtectionMonitorRecoveryNotice {
    private const val CHANNEL_ID = "protection_monitor_recovery"
    private const val NOTIFICATION_ID = 9043

    fun show(context: Context, reason: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            LocalTamperHistory.record(context, "monitor_recovery_notice_blocked", "Notification permission unavailable; reason=$reason")
            return
        }
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID, "Protection monitor recovery", NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Tap to restore background charger and trusted-device monitoring"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
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
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("ThiefAlertSeQure monitor needs attention")
                .setContentText("Open the app to restore background protection monitoring.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Android deferred or rejected background monitor startup. Open ThiefAlertSeQure to review permissions and restore charger and trusted-device monitoring. $reason"
                ))
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
            LocalTamperHistory.record(context, "monitor_recovery_notice_shown", reason)
        }.onFailure {
            LocalTamperHistory.record(context, "monitor_recovery_notice_failed", it.javaClass.simpleName + ": " + it.message)
        }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
            .onFailure {
                LocalTamperHistory.record(context, "monitor_recovery_notice_cancel_failed", it.javaClass.simpleName + ": " + it.message)
            }
    }
}
