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
