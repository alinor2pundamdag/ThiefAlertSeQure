package com.thiefalertsequre.app

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

object ProtectionClock {
    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    fun bootCount(context: Context): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    }.getOrDefault(-1)
}
