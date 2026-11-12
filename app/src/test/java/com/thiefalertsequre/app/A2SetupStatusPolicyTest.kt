package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Test

class A2SetupStatusPolicyTest {
    private fun evaluate(
        a2: Boolean = true,
        trusted: Int = 1,
        permission: Boolean = true,
        adapter: Boolean? = true,
        monitorEnabled: Boolean = true,
        monitorRunning: Boolean = true,
        baselines: Int = 1
    ) = A2SetupStatusPolicy.evaluate(a2, trusted, permission, adapter, monitorEnabled, monitorRunning, baselines)

    @Test fun disabledA2IsNeutral() {
        assertEquals(SetupState.NEUTRAL, evaluate(a2 = false).state)
    }

    @Test fun missingTrustedDeviceRequiresAction() {
        assertEquals(SetupState.ACTION_REQUIRED, evaluate(trusted = 0).state)
    }

    @Test fun missingBluetoothPermissionRequiresAction() {
        assertEquals(SetupState.ACTION_REQUIRED, evaluate(permission = false).state)
    }

    @Test fun disabledAdapterRequiresAction() {
        assertEquals(SetupState.ACTION_REQUIRED, evaluate(adapter = false).state)
    }

    @Test fun unknownAdapterNeverAppearsVerified() {
        assertEquals(SetupState.UNKNOWN, evaluate(adapter = null).state)
    }

    @Test fun disabledMonitorIsActionRequiredRatherThanUnknown() {
        val item = evaluate(monitorEnabled = false, monitorRunning = false)
        assertEquals(SetupState.ACTION_REQUIRED, item.state)
        assert(item.detail.contains("disabled"))
    }

    @Test fun unconfirmedServiceNeverAppearsVerified() {
        assertEquals(SetupState.UNKNOWN, evaluate(monitorRunning = false).state)
    }

    @Test fun missingFreshBaselineRequiresReconnect() {
        assertEquals(SetupState.ACTION_REQUIRED, evaluate(baselines = 0).state)
    }

    @Test fun partialMultiDeviceBaselineNeverAppearsVerified() {
        assertEquals(SetupState.ACTION_REQUIRED, evaluate(trusted = 2, baselines = 1).state)
    }

    @Test fun verifiedRequiresEveryPrerequisite() {
        assertEquals(SetupState.VERIFIED, evaluate().state)
    }
}
