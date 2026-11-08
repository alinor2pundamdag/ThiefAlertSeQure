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



    @Test fun recognizesAdditionalAndroidManufacturersWithSafeGenericGuidance() {
        val cases = listOf(
            Triple("ASUS", "ASUS", "ASUS"),
            Triple("Sony", "Xperia", "Sony Xperia"),
            Triple("Nothing", "Nothing", "Nothing OS"),
            Triple("TECNO", "TECNO", "TECNO / Infinix / itel"),
            Triple("Infinix", "Infinix", "TECNO / Infinix / itel"),
            Triple("ZTE", "nubia", "ZTE / nubia"),
            Triple("Lenovo", "Lenovo", "Lenovo Android")
        )
        cases.forEach { (manufacturer, brand, expectedLabel) ->
            assertEquals(AndroidDeviceCompatibility.Family.valueOf(expectedLabelFamily(expectedLabel)),
                AndroidDeviceCompatibility.family(manufacturer, brand))
            assertTrue(AndroidDeviceCompatibility.familyLabel(manufacturer, brand).contains(expectedLabel))
            assertTrue(AndroidDeviceCompatibility.backgroundSettingsGuide(manufacturer, brand)
                .contains("model- and region-specific", ignoreCase = true))
            AndroidDeviceCompatibility.LaunchControl.values().forEach { control ->
                assertTrue(AndroidDeviceCompatibility.launchControlGuide(manufacturer, brand, control).isNotBlank())
                assertTrue(AndroidDeviceCompatibility.settingsSearchHint(manufacturer, brand, control).isNotBlank())
            }
        }
    }

    private fun expectedLabelFamily(label: String): String = when (label) {
        "ASUS" -> "ASUS"
        "Sony Xperia" -> "SONY"
        "Nothing OS" -> "NOTHING"
        "TECNO / Infinix / itel" -> "TRANSSION"
        "ZTE / nubia" -> "ZTE"
        "Lenovo Android" -> "LENOVO"
        else -> error("Unexpected label: $label")
    }

    @Test fun honorManualGuidesNameAllThreeRequiredControls() {
        val guides = AndroidDeviceCompatibility.LaunchControl.values().map {
            AndroidDeviceCompatibility.launchControlGuide("HONOR", "HONOR", it)
        }
        listOf("Manage automatically OFF", "Auto-launch", "Secondary launch", "Run in background")
            .forEach { expected -> assertTrue(guides.any { it.contains(expected, ignoreCase = true) }) }
    }

    @Test fun everyRecognizedFamilyHasGuidanceForAllThreeControls() {
        val devices = listOf("Samsung", "HONOR", "Huawei", "Xiaomi", "OPPO", "realme",
            "OnePlus", "vivo", "iQOO", "Google", "Motorola", "Unknown")
        devices.forEach { brand ->
            AndroidDeviceCompatibility.LaunchControl.values().forEach { control ->
                assertTrue("$brand / " + control.title + " should have guidance",
                    AndroidDeviceCompatibility.launchControlGuide(brand, brand, control).isNotBlank())
            }
        }
    }

    @Test fun settingsSearchHintsMatchDetectedDeviceFamily() {
        val auto = AndroidDeviceCompatibility.LaunchControl.AUTO_LAUNCH
        assertEquals("App launch", AndroidDeviceCompatibility.settingsSearchHint("HONOR", "HONOR", auto))
        assertEquals("Never sleeping apps", AndroidDeviceCompatibility.settingsSearchHint("Samsung", "Samsung", auto))
        assertEquals("Background autostart", AndroidDeviceCompatibility.settingsSearchHint("Xiaomi", "Redmi", auto))
        assertEquals("Auto launch", AndroidDeviceCompatibility.settingsSearchHint("OPPO", "OPPO", auto))
        assertEquals("Autostart", AndroidDeviceCompatibility.settingsSearchHint("vivo", "vivo", auto))
        assertEquals("App battery usage", AndroidDeviceCompatibility.settingsSearchHint("Google", "Pixel", auto))
    }

    @Test fun deviceSpecificGuideIncludesDetectedModelAndAndroidVersion() {
        val guide = AndroidDeviceCompatibility.launchControlGuide(
            "HONOR", "HONOR", AndroidDeviceCompatibility.LaunchControl.RUN_IN_BACKGROUND,
            "HONOR 200 Pro", "15", 35
        )
        assertTrue(guide.contains("HONOR 200 Pro"))
        assertTrue(guide.contains("Android 15"))
        assertTrue(guide.contains("API 35"))
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
