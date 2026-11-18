package com.thiefalertsequre.app

object A2SetupStatusPolicy {
    fun evaluate(
        a2Enabled: Boolean,
        trustedDeviceCount: Int,
        bluetoothPermissionGranted: Boolean,
        adapterEnabled: Boolean?,
        monitorEnabled: Boolean,
        monitorRunning: Boolean,
        connectedBaselineCount: Int,
        bootIdentityAvailable: Boolean = true
    ): SetupItem {
        val stateAndDetail = when {
            !a2Enabled -> SetupState.NEUTRAL to "Optional A2 is disabled."
            trustedDeviceCount <= 0 -> SetupState.ACTION_REQUIRED to "A2 is enabled but no trusted accessory is registered."
            !bluetoothPermissionGranted -> SetupState.ACTION_REQUIRED to "Bluetooth Connect permission is required."
            adapterEnabled == false -> SetupState.ACTION_REQUIRED to "Turn on Bluetooth before A2 can monitor."
            adapterEnabled == null -> SetupState.UNKNOWN to "Bluetooth adapter state could not be verified."
            !monitorEnabled -> SetupState.ACTION_REQUIRED to "A2 is enabled, but the protection monitor is disabled. Enable it in Settings."
            !monitorRunning -> SetupState.UNKNOWN to "The monitor is enabled in preferences, but Android has not confirmed that its service is running."
            connectedBaselineCount < trustedDeviceCount -> SetupState.ACTION_REQUIRED to "Reconnect every registered accessory to establish a fresh connection baseline ($connectedBaselineCount/$trustedDeviceCount confirmed)."
            !bootIdentityAvailable -> SetupState.UNKNOWN to "Android boot identity is unavailable. A2 can monitor live events, but its durable separation deadline cannot be safely verified across process restart."
            else -> SetupState.VERIFIED to "Bluetooth permission, adapter, monitor service, boot identity, and every registered-device baseline are confirmed."
        }
        return SetupItem("a2", "Bluetooth separation alert", stateAndDetail.first, stateAndDetail.second)
    }
}
