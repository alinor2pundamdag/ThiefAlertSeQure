package com.thiefalertsequre.app

import android.content.Context

class ProtectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)

    @Synchronized fun read(): ProtectionState = ProtectionState(
        mode = runCatching { ProtectionMode.valueOf(prefs.getString("mode", "DISARMED")!!) }.getOrDefault(ProtectionMode.DISARMED),
        deadlineEpochMs = prefs.getLong("deadline", 0L),
        changedAtEpochMs = prefs.getLong("changed", 0L)
    )

    @Synchronized fun write(state: ProtectionState): Boolean = prefs.edit()
        .putString("mode", state.mode.name)
        .putLong("deadline", state.deadlineEpochMs)
        .putLong("changed", state.changedAtEpochMs)
        .commit()
}
