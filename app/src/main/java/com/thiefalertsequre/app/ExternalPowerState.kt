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
        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, Int.MIN_VALUE)
        return readPluggedValue(
            hasExtra = batteryStatus.hasExtra(BatteryManager.EXTRA_PLUGGED),
            plugged = plugged
        )
    }

    fun isConnected(context: Context): Boolean = readConnected(context) == true

    internal fun readPluggedValue(hasExtra: Boolean, plugged: Int): Boolean? {
        if (!hasExtra || plugged == Int.MIN_VALUE) return null
        return isPluggedIn(plugged)
    }

    internal fun isPluggedIn(plugged: Int): Boolean = plugged != 0
}
