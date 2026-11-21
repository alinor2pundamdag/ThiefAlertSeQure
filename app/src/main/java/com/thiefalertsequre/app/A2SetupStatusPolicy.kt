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
        bootIdentityAvailable: Boolean = true,
        bootBaselineReady: Boolean = true
    ): SetupItem {
        val stateAndDetail = when {
            !a2Enabled -> SetupState.NEUTRAL to "Optional A2 is disabled."
            trustedDeviceCount <= 0 -> SetupState.ACTION_REQUIRED to "A2 is enabled but no trusted accessory is registered."
            !bluetoothPermissionGranted -> SetupState.ACTION_REQUIRED to "Bluetooth Connect permission is required."
            adapterEnabled == false -> SetupState.ACTION_REQUIRED to "Turn on Bluetooth before A2 can monitor."
            adapterEnabled == null -> SetupState.UNKNOWN to "Bluetooth adapter state could not be verified."
            !monitorEnabled -> SetupState.ACTION_REQUIRED to "A2 is enabled, but the protection monitor is disabled. Enable it in Settings."
            !monitorRunning -> SetupState.UNKNOWN to "The monitor is enabled in preferences, but Android has not confirmed that its service is running."
            !bootIdentityAvailable -> SetupState.UNKNOWN to "Android boot identity is unavailable. A2 durable separation deadlines cannot be safely verified across process restart."
            !bootBaselineReady -> SetupState.UNKNOWN to "A2 is waiting for a verified current-boot baseline reset. Its Bluetooth receiver remains disabled until that reset succeeds."
            connectedBaselineCount < trustedDeviceCount -> SetupState.ACTION_REQUIRED to "Reconnect every registered accessory to establish a fresh connection baseline ($connectedBaselineCount/$trustedDeviceCount confirmed)."
            else -> SetupState.VERIFIED to "Bluetooth permission, adapter, monitor service, current-boot baseline readiness, and every registered-device connection baseline are confirmed."
        }
        return SetupItem("a2", "Bluetooth separation alert", stateAndDetail.first, stateAndDetail.second)
    }
}
