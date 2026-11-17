package com.thiefalertsequre.app

import android.app.NotificationManager

/** Pure readiness mapping for app-level notifications and the alarm channel. */
object NotificationSetupStatusPolicy {
    fun appNotifications(enabled: Boolean): SetupItem =
        SetupItem(
            "notifications",
            "App notifications",
            if (enabled) SetupState.VERIFIED else SetupState.ACTION_REQUIRED,
            if (enabled) {
                "Android reports notifications enabled."
            } else {
                "Android reports notifications disabled."
            }
        )

    fun monitorChannel(importance: Int?): SetupItem =
        when {
            importance == null -> SetupItem(
                "monitor_channel",
                "Protection notification channel",
                SetupState.UNKNOWN,
                "Android has not created the protection-monitor channel yet; it is created when the monitor service starts."
            )
            importance == NotificationManager.IMPORTANCE_NONE -> SetupItem(
                "monitor_channel",
                "Protection notification channel",
                SetupState.ACTION_REQUIRED,
                "Android reports that the persistent protection notification channel is blocked. The status-bar shield cannot appear until it is enabled."
            )
            importance == NotificationManager.IMPORTANCE_MIN -> SetupItem(
                "monitor_channel",
                "Protection notification channel",
                SetupState.ACTION_REQUIRED,
                "Android set this foreground channel to minimum importance; its notification is not shown in the status bar. Raise it to Low or Default in Android settings."
            )
            else -> SetupItem(
                "monitor_channel",
                "Protection notification channel",
                SetupState.VERIFIED,
                "The channel is available. Android may still hide silent Low-importance icons according to the device's status-bar notification preference."
            )
        }

    fun alarmChannel(importance: Int?): SetupItem =
        when {
            importance == null -> SetupItem(
                "alarm_channel",
                "Alarm notification channel",
                SetupState.UNKNOWN,
                "Android has not created the alarm channel yet; it is created when the alarm service first starts."
            )
            importance == NotificationManager.IMPORTANCE_NONE -> SetupItem(
                "alarm_channel",
                "Alarm notification channel",
                SetupState.ACTION_REQUIRED,
                "Android reports that the alarm notification channel is blocked."
            )
            else -> SetupItem(
                "alarm_channel",
                "Alarm notification channel",
                SetupState.VERIFIED,
                "Android reports that the alarm notification channel is available."
            )
        }
}
