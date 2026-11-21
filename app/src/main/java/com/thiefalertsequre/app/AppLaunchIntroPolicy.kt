package com.thiefalertsequre.app

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build

/** Version-aware gate for the cinematic first page. */
internal object AppLaunchIntroPolicy {
    const val PREFS = "batch_three_intro_v1"
    const val KEY_COMPLETED = "completed"
    const val KEY_COMPLETED_VERSION_CODE = "completed_version_code"

    @Suppress("DEPRECATION")
    fun currentVersionCode(context: Context): Long = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(1L)

    /** Legacy installs map their old completion flag to the immediately previous release. */
    fun lastCompletedVersionCode(preferences: SharedPreferences, currentVersionCode: Long): Long {
        val stored = when (val read = SafePreferences.readLong(preferences, KEY_COMPLETED_VERSION_CODE, -1L)) {
            is PreferenceRead.Value -> read.value
            is PreferenceRead.Corrupt -> -1L
        }
        if (stored >= 0L) return stored
        val legacyCompleted = when (val read = SafePreferences.readBoolean(preferences, KEY_COMPLETED, false)) {
            is PreferenceRead.Value -> read.value
            is PreferenceRead.Corrupt -> false
        }
        return if (legacyCompleted) (currentVersionCode - 1L).coerceAtLeast(0L) else 0L
    }

    fun shouldShow(lastCompletedVersionCode: Long, currentVersionCode: Long, protectionDisarmed: Boolean): Boolean =
        protectionDisarmed && currentVersionCode > lastCompletedVersionCode
}
