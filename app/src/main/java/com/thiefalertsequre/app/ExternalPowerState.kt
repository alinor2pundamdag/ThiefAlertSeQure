package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Detects external power presence, not battery charging activity.
 * A plugged-in device may temporarily stop charging because of battery
 * protection, thermal limits, or a full battery.
 */
object ExternalPowerState {
    /** Null means Android has not supplied a reliable sticky battery snapshot yet. */
    fun readConnected(context: Context): Boolean? {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return isPluggedIn(plugged)
    }

    fun isConnected(context: Context): Boolean = readConnected(context) == true

    internal fun isPluggedIn(plugged: Int): Boolean = plugged != 0
}
