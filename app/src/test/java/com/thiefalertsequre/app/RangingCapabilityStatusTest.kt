package com.thiefalertsequre.app

import android.ranging.RangingCapabilities
import android.ranging.RangingManager
import org.junit.Assert.assertEquals
import org.junit.Test

class RangingCapabilityStatusTest {
    @Test
    fun availabilityStatusesAreExplicitAndNonOverstated() {
        assertEquals("Available", RangingCapabilityStatus.label(RangingCapabilities.ENABLED))
        assertEquals("Not supported by this device", RangingCapabilityStatus.label(RangingCapabilities.NOT_SUPPORTED))
        assertEquals("Disabled by the user", RangingCapabilityStatus.label(RangingCapabilities.DISABLED_USER))
        assertEquals("Disabled by regulatory policy", RangingCapabilityStatus.label(RangingCapabilities.DISABLED_REGULATORY))
        assertEquals("Blocked by device/user restrictions", RangingCapabilityStatus.label(RangingCapabilities.DISABLED_USER_RESTRICTIONS))
        assertEquals("Not reported by Android", RangingCapabilityStatus.label(null))
    }

    @Test
    fun technologyIdentifiersMapToKnownApi36RangingTypes() {
        assertEquals("Bluetooth Channel Sounding", RangingCapabilityStatus.technologyLabel(RangingManager.BLE_CS))
        assertEquals("Bluetooth RSSI ranging", RangingCapabilityStatus.technologyLabel(RangingManager.BLE_RSSI))
        assertEquals("Ultra-wideband", RangingCapabilityStatus.technologyLabel(RangingManager.UWB))
        assertEquals("Wi-Fi NAN RTT", RangingCapabilityStatus.technologyLabel(RangingManager.WIFI_NAN_RTT))
    }
}
