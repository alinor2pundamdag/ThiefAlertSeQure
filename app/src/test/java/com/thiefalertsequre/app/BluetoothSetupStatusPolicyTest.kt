package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothSetupStatusPolicyTest {
    @Test fun missingPermissionIsNeutralWhenA2IsOff() {
        assertEquals(SetupState.NEUTRAL, BluetoothSetupStatusPolicy.permission(false, false).state)
    }

    @Test fun missingPermissionRequiresActionWhenA2IsOn() {
        assertEquals(SetupState.ACTION_REQUIRED, BluetoothSetupStatusPolicy.permission(true, false).state)
    }

    @Test fun grantedPermissionIsVerifiedWhenA2IsOn() {
        assertEquals(SetupState.VERIFIED, BluetoothSetupStatusPolicy.permission(true, true).state)
    }

    @Test fun bluetoothOffIsNeutralWhenA2IsOff() {
        assertEquals(SetupState.NEUTRAL, BluetoothSetupStatusPolicy.adapter(false, false).state)
    }

    @Test fun bluetoothOffRequiresActionWhenA2IsOn() {
        assertEquals(SetupState.ACTION_REQUIRED, BluetoothSetupStatusPolicy.adapter(true, false).state)
    }

    @Test fun unknownAdapterNeverAppearsVerifiedWhenA2IsOn() {
        assertEquals(SetupState.UNKNOWN, BluetoothSetupStatusPolicy.adapter(true, null).state)
    }
}
