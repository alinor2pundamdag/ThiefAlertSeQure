package com.thiefalertsequre.app

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSetupStatusPolicyTest {
    @Test
    fun enabledAppNotificationsAreVerified() {
        assertEquals(SetupState.VERIFIED, NotificationSetupStatusPolicy.appNotifications(true).state)
    }

    @Test
    fun disabledAppNotificationsRequireOwnerAction() {
        assertEquals(SetupState.ACTION_REQUIRED, NotificationSetupStatusPolicy.appNotifications(false).state)
    }

    @Test
    fun missingAlarmChannelIsUnknownRatherThanAssumedBlocked() {
        assertEquals(SetupState.UNKNOWN, NotificationSetupStatusPolicy.alarmChannel(null).state)
    }

    @Test
    fun blockedAlarmChannelRequiresOwnerAction() {
        assertEquals(
            SetupState.ACTION_REQUIRED,
            NotificationSetupStatusPolicy.alarmChannel(NotificationManager.IMPORTANCE_NONE).state
        )
    }

    @Test
    fun availableAlarmChannelIsVerified() {
        assertEquals(
            SetupState.VERIFIED,
            NotificationSetupStatusPolicy.alarmChannel(NotificationManager.IMPORTANCE_DEFAULT).state
        )
    }
}
