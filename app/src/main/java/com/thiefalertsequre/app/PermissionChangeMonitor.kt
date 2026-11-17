package com.thiefalertsequre.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Detects permission and special-access changes when the owner returns to the app.
 * Android does not provide one universal reliable callback for every OEM access toggle.
 */
internal object PermissionChangeMonitor {
    private const val PREFS = "permission_change_snapshot_v1"
    private val lock = Any()

    fun check(context: Context) = synchronized(lock) {
        val current = linkedMapOf<String, Boolean>()
        if (Build.VERSION.SDK_INT >= 33) {
            current["post_notifications"] =
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        if (Build.VERSION.SDK_INT >= 31) {
            current["bluetooth_connect"] =
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            current["exact_alarms"] =
                runCatching { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }.getOrDefault(false)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            current["full_screen_intent"] =
                runCatching { context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() }.getOrDefault(false)
        }
        current["coarse_location"] =
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        current["fine_location"] =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        current["notifications_enabled"] =
            runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(false)
        if (Build.VERSION.SDK_INT >= 23) {
            current["battery_optimization_exemption"] =
                runCatching { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
                    .getOrDefault(false)
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        current.forEach { (key, granted) ->
            if (prefs.contains(key)) {
                when (val previous = SafePreferences.readBoolean(prefs, key, granted)) {
                    is PreferenceRead.Value -> if (previous.value != granted) {
                        LocalTamperHistory.record(
                            context,
                            "android_access_changed",
                            "$key changed from ${previous.value} to $granted while the app was away"
                        )
                    }
                    is PreferenceRead.Corrupt -> LocalTamperHistory.record(
                        context, "permission_snapshot_corrupt", "Stored permission snapshot '$key' has an invalid type"
                    )
                }
            }
            editor.putBoolean(key, granted)
        }
        if (!editor.commit()) {
            LocalTamperHistory.record(context, "permission_snapshot_commit_failed", "Could not persist the latest Android access snapshot")
        }
    }
}
