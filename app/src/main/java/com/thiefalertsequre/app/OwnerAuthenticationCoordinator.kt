package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * One completion path for app-authenticated and system-keyguard-authenticated owner unlocks.
 * Android verifies the credential/biometric; this class only commits the resulting local state.
 */
object OwnerAuthenticationCoordinator {
    fun complete(context: Context, source: String): ProtectionState? {
        val appContext = context.applicationContext
        val store = ProtectionStore(appContext)
        if (store.read().mode != ProtectionMode.ALARM_TRIGGERED) return null

        val now = System.currentTimeMillis()
        val charging = ExternalPowerState.isConnected(appContext)
        val next = store.update { current ->
            if (current.mode == ProtectionMode.ALARM_TRIGGERED) {
                ProtectionEngine.authenticated(current, now, charging)
            } else {
                current
            }
        }
        if (next.mode == ProtectionMode.ALARM_TRIGGERED) return null

        store.resetAuthenticationFailures()
        AlarmScheduler.cancel(appContext)
        appContext.stopService(Intent(appContext, AlarmService::class.java))
        runCatching {
            AlarmVolumeController.restore(
                appContext,
                appContext.getSystemService(AudioManager::class.java)
            )
        }
        LocalTamperHistory.record(
            appContext,
            "owner_authenticated_alarm_stopped",
            "Authentication source: $source; resulting protection state: ${next.mode.name}"
        )
        return next
    }
}
