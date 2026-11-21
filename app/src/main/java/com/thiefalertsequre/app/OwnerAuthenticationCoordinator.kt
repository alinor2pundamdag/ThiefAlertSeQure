package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.content.ContextCompat

/**
 * One owner-authentication completion path for in-app prompts and Android lockscreen unlocks.
 * Android performs credential/biometric verification; no credential material enters this app.
 */
internal object OwnerAuthenticationCoordinator {
    fun complete(context: Context, proof: OwnerAuthenticationProof): ProtectionState? {
        val appContext = context.applicationContext
        val store = ProtectionStore(appContext)
        if (store.read().mode != ProtectionMode.ALARM_TRIGGERED) return null
        val source = proof.source
        if (!proof.isFresh(ProtectionClock.elapsedRealtime(), ProtectionClock.bootCount(appContext))) {
            LocalTamperHistory.record(appContext, "owner_auth_proof_rejected", "Owner authentication proof was invalid, expired, or from another boot")
            LocalEvidenceVault.recordAsync(appContext, "owner_auth_proof_rejected", "Owner authentication proof was invalid, expired, or from another boot")
            return null
        }

        val authenticated = store.disarmAfterOwnerAuthentication(System.currentTimeMillis()) ?: return null
        if (authenticated.mode != ProtectionMode.DISARMED) return null

        AlarmRecoveryCoordinator.onAlarmDisarmed(appContext)
        store.resetAuthenticationFailures()
        val monitorPrefs = appContext.getSharedPreferences(
            ProtectionMonitorService.PREFS, Context.MODE_PRIVATE
        )
        val a2AlertsEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false
        )
        val a2DeadlinesCleared = runCatching {
            A2DisconnectScheduler.clearAll(appContext)
        }.getOrDefault(false)
        if (!a2DeadlinesCleared && a2AlertsEnabled) {
            LocalTamperHistory.record(
                appContext, "a2_owner_recovery_deadline_cleanup_failed",
                "Old trusted-device deadlines could not be removed; the durable authenticated-disarm timestamp invalidates any pre-authentication event"
            )
            ProtectionMonitorRecoveryNotice.show(
                appContext,
                "Old trusted-device deadlines could not be removed. They are invalidated by the authenticated recovery marker; review Bluetooth monitoring status."
            )
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

        if (ConfigurationBackup.isRestoreOrRecoveryInProgress() ||
            !ConfigurationBackup.recoverPendingRestore(appContext)
        ) {
            LocalTamperHistory.record(
                appContext, "configuration_restore_recovery_pending",
                "Alarm was disarmed after valid owner authentication, but monitoring restart was deferred until interrupted settings recovery completes"
            )
            ProtectionMonitorRecoveryNotice.show(
                appContext, "Alarm disarmed. Monitoring will resume after interrupted settings recovery is completed."
            )
            appContext.stopService(Intent(appContext, ProtectionMonitorService::class.java))
            return authenticated
        }

        // Rebuild the charger baseline only after verified owner authentication.
        // A connected cable returns to ARMED; a disconnected cable remains DISARMED
        // until a new, genuine connect-then-disconnect sequence is observed.
        val monitorPrefs = appContext.getSharedPreferences(
            ProtectionMonitorService.PREFS, Context.MODE_PRIVATE
        )
        val monitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
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
                ProtectionMonitorRecoveryNotice.show(
                    appContext,
                    "Protection was re-armed after owner authentication, but Android rejected background monitoring startup. Open the app to restore monitoring."
                )
            }
        } else {
            appContext.stopService(Intent(appContext, ProtectionMonitorService::class.java))
        }

        LocalTamperHistory.record(
            appContext,
            "owner_authenticated_alarm_stopped",
            "Authentication source: $source; resulting protection state: ${next.mode.name}"
        )
        LocalEvidenceVault.recordAsync(
            appContext,
            "owner_authenticated_alarm_stopped",
            "source=$source; resultingProtectionMode=${next.mode.name}; alarmDisarmed=true"
        )
        return next
    }
}
