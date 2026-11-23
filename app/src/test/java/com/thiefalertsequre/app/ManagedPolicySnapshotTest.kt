package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedPolicySnapshotTest {
    @Test fun rollbackRetainsOriginalLocalAndGlobalRestrictionValues() {
        val snapshot = ManagedPolicySnapshot(
            lockTaskPackages = arrayOf("com.example.owner", "com.example.kiosk"),
            lockTaskFeatures = 36,
            localRestrictionValues = mapOf(
                "no_factory_reset" to true,
                "no_safe_boot" to false
            ),
            globalRestrictionValues = mapOf(
                "no_factory_reset" to false,
                "no_safe_boot" to true
            )
        )

        assertTrue(snapshot.shouldKeepLocalRestrictionEnabled("no_factory_reset"))
        assertFalse(snapshot.shouldKeepGlobalRestrictionEnabled("no_factory_reset"))
        assertFalse(snapshot.shouldKeepLocalRestrictionEnabled("no_safe_boot"))
        assertTrue(snapshot.shouldKeepGlobalRestrictionEnabled("no_safe_boot"))
    }

    @Test fun unknownRestrictionIsRestoredAsPreviouslyDisabled() {
        val snapshot = ManagedPolicySnapshot(
            lockTaskPackages = emptyArray(),
            lockTaskFeatures = 1,
            localRestrictionValues = emptyMap(),
            globalRestrictionValues = emptyMap()
        )

        assertFalse(snapshot.shouldKeepLocalRestrictionEnabled("no_add_user"))
        assertFalse(snapshot.shouldKeepGlobalRestrictionEnabled("no_add_user"))
    }

    @Test fun snapshotRetainsOriginalLockTaskPackagesAndFeatureFlags() {
        val originalPackages = arrayOf("com.example.launcher", "com.example.owner")
        val snapshot = ManagedPolicySnapshot(
            lockTaskPackages = originalPackages,
            lockTaskFeatures = 37,
            localRestrictionValues = emptyMap(),
            globalRestrictionValues = emptyMap()
        )

        assertEquals(originalPackages.toList(), snapshot.lockTaskPackages.toList())
        assertEquals(37, snapshot.lockTaskFeatures)
    }
}
