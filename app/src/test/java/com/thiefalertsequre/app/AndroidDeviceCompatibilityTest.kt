package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDeviceCompatibilityTest {
    @Test fun recognizesSamsungFromManufacturerOrBrand() {
        assertEquals(AndroidDeviceCompatibility.Family.SAMSUNG, AndroidDeviceCompatibility.family("samsung", "SM"))
        assertEquals(AndroidDeviceCompatibility.Family.SAMSUNG, AndroidDeviceCompatibility.family("unknown", "Samsung"))
    }

    @Test fun recognizesHonorAndHuawei() {
        assertEquals(AndroidDeviceCompatibility.Family.HONOR_HUAWEI, AndroidDeviceCompatibility.family("HONOR", "ANY"))
        assertEquals(AndroidDeviceCompatibility.Family.HONOR_HUAWEI, AndroidDeviceCompatibility.family("HUAWEI", "ANY"))
    }

    @Test fun recognizesXiaomiRedmiAndPoco() {
        assertEquals(AndroidDeviceCompatibility.Family.XIAOMI, AndroidDeviceCompatibility.family("Xiaomi", "Redmi"))
        assertEquals(AndroidDeviceCompatibility.Family.XIAOMI, AndroidDeviceCompatibility.family("POCO", "POCO"))
    }

    @Test fun recognizesOppoRealmeAndOnePlus() {
        assertEquals(AndroidDeviceCompatibility.Family.OPPO_REALME_ONEPLUS, AndroidDeviceCompatibility.family("OPPO", "CPH"))
        assertEquals(AndroidDeviceCompatibility.Family.OPPO_REALME_ONEPLUS, AndroidDeviceCompatibility.family("realme", "RMX"))
        assertEquals(AndroidDeviceCompatibility.Family.OPPO_REALME_ONEPLUS, AndroidDeviceCompatibility.family("OnePlus", "ONEPLUS"))
    }

    @Test fun recognizesVivoIqooPixelAndMotorola() {
        assertEquals(AndroidDeviceCompatibility.Family.VIVO_IQOO, AndroidDeviceCompatibility.family("vivo", "vivo"))
        assertEquals(AndroidDeviceCompatibility.Family.VIVO_IQOO, AndroidDeviceCompatibility.family("iQOO", "iQOO"))
        assertEquals(AndroidDeviceCompatibility.Family.GOOGLE_PIXEL, AndroidDeviceCompatibility.family("Google", "Pixel"))
        assertEquals(AndroidDeviceCompatibility.Family.MOTOROLA, AndroidDeviceCompatibility.family("motorola", "moto"))
    }

    @Test fun unknownBrandsReceiveGenericGuidance() {
        assertEquals(AndroidDeviceCompatibility.Family.OTHER, AndroidDeviceCompatibility.family("ExampleOEM", "Example"))
        assertTrue(AndroidDeviceCompatibility.backgroundSettingsGuide("ExampleOEM", "Example").contains("Other Android brands"))
    }

    @Test fun allGuidesWarnThatOemControlsAreManual() {
        val brands = listOf(
            "Samsung", "HONOR", "Xiaomi", "OPPO", "vivo", "Google", "Motorola", "Unknown"
        )
        brands.forEach { brand ->
            assertTrue(
                AndroidDeviceCompatibility.backgroundSettingsGuide(brand, brand)
                    .contains("public API", ignoreCase = true)
            )
        }
    }
}
