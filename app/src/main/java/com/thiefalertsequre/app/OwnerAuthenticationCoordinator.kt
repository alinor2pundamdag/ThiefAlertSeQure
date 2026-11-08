package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.content.ContextCompat

/**
 * One owner-authentication completion path for in-app prompts and Android lockscreen unlocks.
 * Android performs credential/biometric verification; no credential material enters this app.
 */
object OwnerAuthenticationCoordinator {
    fun complete(context: Context, source: String): ProtectionState? {
        val appContext = context.applicationContext
        val store = ProtectionStore(appContext)
        if (store.read().mode != ProtectionMode.ALARM_TRIGGERED) return null

        val authenticated = store.update {
            ProtectionEngine.authenticated(it, System.currentTimeMillis())
        }
        if (authenticated.mode != ProtectionMode.DISARMED) return null

        store.resetAuthenticationFailures()
        runCatching { AlarmScheduler.cancel(appContext) }.onFailure {
            LocalTamperHistory.record(appContext, "deadline_cancel_after_auth_failed", it.javaClass.simpleName + ": " + it.message)
        }
        appContext.stopService(Intent(appContext, AlarmService::class.java))
        val volumeRestored = runCatching {
            AlarmVolumeController.restore(
                appContext,
                appContext.getSystemService(AudioManager::class.java)
            )
        }.getOrDefault(false)
        if (!volumeRestored) {
            LocalTamperHistory.record(
                appContext, "alarm_volume_restore_incomplete",
                "The saved alarm-volume value was unavailable or Android rejected restoration; review alarm volume in system settings"
            )
        }

        // Rebuild the charger baseline only after verified owner authentication.
        // A connected cable returns to ARMED; a disconnected cable remains DISARMED
        // until a new, genuine connect-then-disconnect sequence is observed.
        val monitorPrefs = appContext.getSharedPreferences(
            ProtectionMonitorService.PREFS, Context.MODE_PRIVATE
        )
        val monitorEnabled = monitorPrefs.getBoolean(
            ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val next = if (monitorEnabled) {
            PowerTransitionCoordinator.resetBaselineToCurrentPower(
                appContext,
                "Owner authentication completed via $source; current charger state re-baselined"
            )
            store.read()
        } else {
            authenticated
        }

        if (ProtectionMonitorPolicy.shouldRun(monitorEnabled, next.mode)) {
            runCatching {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, ProtectionMonitorService::class.java)
                )
            }.onFailure {
                LocalTamperHistory.record(
                    appContext, "monitor_restart_after_auth_failed",
                    it.javaClass.simpleName + ": " + it.message
                )
                monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_RUNNING, false).apply()
            }
        } else {
            appContext.stopService(Intent(appContext, ProtectionMonitorService::class.java))
        }

        LocalTamperHistory.record(
            appContext,
            "owner_authenticated_alarm_stopped",
            "Authentication source: $source; resulting protection state: ${next.mode.name}"
        )
        return next
    }
}
