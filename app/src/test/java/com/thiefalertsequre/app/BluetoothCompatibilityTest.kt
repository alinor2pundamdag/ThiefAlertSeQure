package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothCompatibilityTest {
    @Test fun mapsAndroidEightThroughSeventeenApiLevels() {
        val expected = mapOf(
            26 to "Android 8.0", 27 to "Android 8.1", 28 to "Android 9",
            29 to "Android 10", 30 to "Android 11", 31 to "Android 12",
            32 to "Android 12L", 33 to "Android 13", 34 to "Android 14",
            35 to "Android 15", 36 to "Android 16", 37 to "Android 17"
        )
        expected.forEach { (api, label) ->
            assertEquals(label, BluetoothCompatibility.androidRelease(api))
        }
    }

    @Test fun permissionGuideSeparatesLegacyAndNearbyDevicePermissionModels() {
        assertTrue(BluetoothCompatibility.permissionGuide(30).contains("Android 8–11"))
        assertTrue(BluetoothCompatibility.permissionGuide(31).contains("Bluetooth Connect"))
        assertTrue(BluetoothCompatibility.permissionGuide(37).contains("does not need Bluetooth Scan"))
    }

    @Test fun rangingApiIsNotClaimedBeforeAndroidSixteen() {
        assertTrue(BluetoothCompatibility.versionGuide(35).contains("not available") ||
            BluetoothCompatibility.versionGuide(35).contains("Android 15"))
        assertTrue(BluetoothCompatibility.versionGuide(36).contains("capability-checked"))
        assertTrue(BluetoothCompatibility.versionGuide(37).contains("RFCOMM"))
    }
}
