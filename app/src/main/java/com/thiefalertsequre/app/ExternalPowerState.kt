package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Checks whether external power is physically connected, not whether the battery
 * is currently accepting charge. This keeps protection armed at 100% battery too.
 */
object ExternalPowerState {
    fun isConnected(context: Context): Boolean {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return isConnected(plugged)
    }

    /** Pure mapping kept separate so plugged-state semantics can be unit tested. */
    fun isConnected(pluggedState: Int): Boolean = pluggedState != 0
}
