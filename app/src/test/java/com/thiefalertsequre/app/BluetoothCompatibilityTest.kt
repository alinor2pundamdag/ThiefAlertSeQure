package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothCompatibilityTest {
    @Test
    fun androidReleaseNamesCoverExpandedMinimumAndCurrentMatrix() {
        assertEquals("Android 6.0", BluetoothCompatibility.androidRelease(23))
        assertEquals("Android 7.0", BluetoothCompatibility.androidRelease(24))
        assertEquals("Android 7.1", BluetoothCompatibility.androidRelease(25))
        assertEquals("Android 8.0", BluetoothCompatibility.androidRelease(26))
        assertEquals("Android 17", BluetoothCompatibility.androidRelease(37))
    }

    @Test
    fun legacyBluetoothPermissionGuidanceIncludesAndroidSixThroughEleven() {
        for (api in 23..30) {
            assertTrue(
                "Missing legacy Bluetooth guidance for API $api",
                BluetoothCompatibility.permissionGuide(api).contains("Android 6–11")
            )
        }
    }

    @Test
    fun apiSpecificBluetoothGuidanceDoesNotMislabelAndroidSixAndSeven() {
        for (api in 23..25) {
            assertTrue(
                "Missing Android 6–7.1 guidance for API $api",
                BluetoothCompatibility.versionGuide(api).contains("Android 6.0–7.1")
            )
        }
        for (api in 26..27) {
            assertTrue(
                "Missing Android 8 guidance for API $api",
                BluetoothCompatibility.versionGuide(api).contains("Android 8.0–8.1")
            )
        }
    }

    @Test
    fun androidReleaseNamesCoverAndroidNineThroughSeventeen() {
        val expected = mapOf(
            28 to "Android 9",
            29 to "Android 10",
            30 to "Android 11",
            31 to "Android 12",
            32 to "Android 12L",
            33 to "Android 13",
            34 to "Android 14",
            35 to "Android 15",
            36 to "Android 16",
            37 to "Android 17"
        )
        expected.forEach { (api, label) ->
            assertEquals(label, BluetoothCompatibility.androidRelease(api))
        }
    }

    @Test
    fun permissionGuideSeparatesLegacyAndNearbyDevicePermissionModels() {
        assertTrue(BluetoothCompatibility.permissionGuide(30).contains("Android 6–11"))
        assertTrue(BluetoothCompatibility.permissionGuide(31).contains("Bluetooth Connect"))
        assertTrue(BluetoothCompatibility.permissionGuide(37).contains("does not need Bluetooth Scan"))
    }

    @Test
    fun rangingGuidanceDoesNotOverclaimBeforeAndroidSixteen() {
        assertTrue(BluetoothCompatibility.versionGuide(35).contains("Android 15"))
        assertTrue(BluetoothCompatibility.versionGuide(36).contains("capability-checked"))
        assertTrue(BluetoothCompatibility.versionGuide(37).contains("RFCOMM"))
    }

    @Test
    fun controllerProbeDistinguishesPermissionAdapterAndHardwareStates() {
        val classify = BluetoothCapabilityCatalog::classifyControllerProbe
        assertEquals(BluetoothCapabilityCatalog.State.PERMISSION_REQUIRED, classify(true, false, true, null))
        assertEquals(BluetoothCapabilityCatalog.State.ADAPTER_OFF, classify(true, true, false, null))
        assertEquals(BluetoothCapabilityCatalog.State.SUPPORTED_ON_DEVICE, classify(true, true, true, true))
        assertEquals(BluetoothCapabilityCatalog.State.NOT_SUPPORTED, classify(true, true, true, false))
        assertEquals(BluetoothCapabilityCatalog.State.UNKNOWN, classify(true, true, null, null))
    }

    @Test
    fun capabilityStatesKeepPeerPermissionAndImplementationSeparate() {
        val states = BluetoothCapabilityCatalog.State.entries.toSet()
        assertTrue(BluetoothCapabilityCatalog.State.NOT_IMPLEMENTED in states)
        assertTrue(BluetoothCapabilityCatalog.State.PEER_REQUIRED in states)
        assertTrue(BluetoothCapabilityCatalog.State.PERMISSION_REQUIRED in states)
        assertTrue(BluetoothCapabilityCatalog.State.API_PRESENT_CHECK_REQUIRED in states)
    }
}
