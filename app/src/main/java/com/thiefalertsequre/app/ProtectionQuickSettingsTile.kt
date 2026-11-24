package com.thiefalertsequre.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Read-only Quick Settings projection; tapping always opens the app, never changes protection. */
class ProtectionQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            this, 701, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val mode = runCatching { ProtectionStore(this).read().mode }.getOrNull()
        val monitorEnabled = SafePreferences.boolean(
            getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE),
            ProtectionMonitorService.KEY_ENABLED,
            ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val activeProtectionRequiresMonitor = mode == ProtectionMode.ARMED || mode == ProtectionMode.COUNTDOWN
        val monitorActive = ProtectionMonitorService.isRunning && (monitorEnabled || activeProtectionRequiresMonitor)
        tile.state = when (mode) {
            ProtectionMode.ARMED -> if (monitorActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            ProtectionMode.COUNTDOWN, ProtectionMode.ALARM_TRIGGERED -> Tile.STATE_ACTIVE
            ProtectionMode.DISARMED -> Tile.STATE_INACTIVE
            null -> Tile.STATE_UNAVAILABLE
        }
        tile.label = when (mode) {
            ProtectionMode.ARMED -> if (monitorActive) "Protected" else "Monitor unknown"
            ProtectionMode.COUNTDOWN -> "Countdown"
            ProtectionMode.ALARM_TRIGGERED -> "Alarm active"
            ProtectionMode.DISARMED -> "Not armed"
            null -> "Status unknown"
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            tile.contentDescription = "ThiefAlertSeQure protection status: ${tile.label}. Tap to open the app."
        }
        tile.updateTile()
    }
}
