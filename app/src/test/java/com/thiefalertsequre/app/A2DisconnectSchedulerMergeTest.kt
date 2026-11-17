package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2DisconnectSchedulerMergeTest {
    @Test fun updatingOneAccessoryPreservesOtherAccessoriesDeadlines() {
        val watch = A2DisconnectScheduler.Pending(
            "AA:BB:CC:DD:EE:01", 1_000L, 2_000L, 4, 7L, 3L
        )
        val earbuds = A2DisconnectScheduler.Pending(
            "AA:BB:CC:DD:EE:02", 1_100L, 2_100L, 4, 7L, 5L
        )
        val replacementWatch = watch.copy(
            eventEpochMs = 3_000L,
            eventElapsedRealtimeMs = 4_000L,
            deviceRevision = 4L
        )
        val merged = A2DisconnectScheduler.mergePending(listOf(watch, earbuds), replacementWatch)
        assertEquals(2, merged.size)
        assertEquals(earbuds, merged.single { it.address == earbuds.address })
        assertEquals(replacementWatch, merged.single { it.address == watch.address })
    }

    @Test fun addressMatchingIsCaseInsensitive() {
        val existing = A2DisconnectScheduler.Pending(
            "AA:BB:CC:DD:EE:01", 1_000L, 2_000L, 4, 7L, 3L
        )
        val replacement = existing.copy(
            address = "aa:bb:cc:dd:ee:01",
            eventEpochMs = 5_000L,
            eventElapsedRealtimeMs = 6_000L
        )
        val merged = A2DisconnectScheduler.mergePending(listOf(existing), replacement)
        assertEquals(1, merged.size)
        assertEquals(replacement, merged.single())
    }

    @Test fun bluetoothConnectPermissionIsRequiredOnlyFromApi31() {
        assertTrue(A2DisconnectPolicy.hasBluetoothConnectPermission(30, false))
        assertFalse(A2DisconnectPolicy.hasBluetoothConnectPermission(31, false))
        assertTrue(A2DisconnectPolicy.hasBluetoothConnectPermission(31, true))
    }
}
