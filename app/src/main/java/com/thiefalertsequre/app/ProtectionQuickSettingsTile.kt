package com.thiefalertsequre.app

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Read-only Quick Settings projection; tapping always opens the app, never changes protection. */
class ProtectionQuickSettingsTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivityAndCollapse(launch)
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val mode = runCatching { ProtectionStore(this).read().mode }.getOrNull()
        tile.state = when (mode) {
            ProtectionMode.ARMED, ProtectionMode.COUNTDOWN, ProtectionMode.ALARM_TRIGGERED -> Tile.STATE_ACTIVE
            ProtectionMode.DISARMED -> Tile.STATE_INACTIVE
            null -> Tile.STATE_UNAVAILABLE
        }
        tile.label = when (mode) {
            ProtectionMode.ARMED -> "Protected"
            ProtectionMode.COUNTDOWN -> "Countdown"
            ProtectionMode.ALARM_TRIGGERED -> "Alarm active"
            ProtectionMode.DISARMED -> "Not armed"
            null -> "Status unknown"
        }
        tile.contentDescription = "ThiefAlertSeQure protection status: ${tile.label}. Tap to open the app."
        tile.updateTile()
    }
}
