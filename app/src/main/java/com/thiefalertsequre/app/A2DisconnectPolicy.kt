package com.thiefalertsequre.app

/** Pure A2 gating rules; timing and alarm state transitions remain owned by the service. */
object A2DisconnectPolicy {
    fun shouldStartGracePeriod(
        alertEnabled: Boolean,
        trusted: Boolean,
        hadConnectionBaseline: Boolean,
        protectionMode: ProtectionMode
    ): Boolean = alertEnabled && trusted && hadConnectionBaseline &&
        protectionMode == ProtectionMode.ARMED

    fun shouldTriggerAlarm(
        alertEnabled: Boolean,
        trusted: Boolean,
        registryUnchanged: Boolean,
        stillDisconnected: Boolean,
        protectionMode: ProtectionMode
    ): Boolean = alertEnabled && trusted && registryUnchanged && stillDisconnected &&
        protectionMode == ProtectionMode.ARMED
}
