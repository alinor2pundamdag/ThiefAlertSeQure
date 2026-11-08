package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedPolicySnapshotTest {
    @Test fun rollbackRetainsRestrictionsThatWereAlreadyEnabled() {
        val snapshot = ManagedPolicySnapshot(
            lockTaskPackages = arrayOf("com.example.owner", "com.example.kiosk"),
            lockTaskFeatures = 36,
            restrictionValues = mapOf(
                "no_factory_reset" to true,
                "no_safe_boot" to false
            )
        )

        assertTrue(snapshot.shouldKeepRestrictionEnabled("no_factory_reset"))
        assertFalse(snapshot.shouldKeepRestrictionEnabled("no_safe_boot"))
    }

    @Test fun unknownRestrictionIsRestoredAsPreviouslyDisabled() {
        val snapshot = ManagedPolicySnapshot(
            lockTaskPackages = emptyArray(),
            lockTaskFeatures = 1,
            restrictionValues = emptyMap()
        )

        assertFalse(snapshot.shouldKeepRestrictionEnabled("no_add_user"))
    }

    @Test fun snapshotRetainsOriginalLockTaskPackagesAndFeatureFlags() {
        val originalPackages = arrayOf("com.example.launcher", "com.example.owner")
        val snapshot = ManagedPolicySnapshot(
            lockTaskPackages = originalPackages,
            lockTaskFeatures = 37,
            restrictionValues = emptyMap()
        )

        assertEquals(originalPackages.toList(), snapshot.lockTaskPackages.toList())
        assertEquals(37, snapshot.lockTaskFeatures)
    }
}
