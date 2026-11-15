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
}
