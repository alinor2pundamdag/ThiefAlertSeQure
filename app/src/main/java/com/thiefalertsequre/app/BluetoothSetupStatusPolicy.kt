package com.thiefalertsequre.app

/** Bluetooth permissions and radio state are optional unless the owner enables A2. */
object BluetoothSetupStatusPolicy {
    fun permission(a2Enabled: Boolean, permissionGranted: Boolean): SetupItem {
        val stateAndDetail = when {
            !a2Enabled -> SetupState.NEUTRAL to "Optional A2 is disabled; Bluetooth Connect permission is not needed."
            permissionGranted -> SetupState.VERIFIED to "Bluetooth Connect permission is granted."
            else -> SetupState.ACTION_REQUIRED to "A2 is enabled and requires Bluetooth Connect permission."
        }
        return SetupItem("bt_permission", "Bluetooth Connect permission", stateAndDetail.first, stateAndDetail.second)
    }

    fun adapter(a2Enabled: Boolean, adapterEnabled: Boolean?): SetupItem {
        val stateAndDetail = when {
            !a2Enabled -> SetupState.NEUTRAL to "Optional A2 is disabled; Bluetooth radio state does not affect charger protection."
            adapterEnabled == true -> SetupState.VERIFIED to "Android reports Bluetooth enabled."
            adapterEnabled == false -> SetupState.ACTION_REQUIRED to "A2 is enabled; turn on Bluetooth before trusted-device separation monitoring can work."
            else -> SetupState.UNKNOWN to "A2 is enabled, but Bluetooth adapter state could not be verified."
        }
        return SetupItem("bt", "Bluetooth adapter", stateAndDetail.first, stateAndDetail.second)
    }
}
