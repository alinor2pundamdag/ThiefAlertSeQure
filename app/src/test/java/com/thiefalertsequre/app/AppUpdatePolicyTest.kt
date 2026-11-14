package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test fun productionPackageIdentityIsFixed() {
        AppUpdatePolicy.requireInstalledPackage("com.thiefalertsequre.app")
        AppUpdatePolicy.requireCandidatePackage("com.thiefalertsequre.app", "com.thiefalertsequre.app")
    }

    @Test fun developmentPackageCannotUseProductionUpdater() {
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdatePolicy.requireInstalledPackage("com.thiefalertsequre.app.v114test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdatePolicy.requireCandidatePackage("com.thiefalertsequre.app", "com.thiefalertsequre.app.v114test")
        }
    }

    @Test fun onlyPinnedSingleSignerIsAccepted() {
        assertTrue(AppUpdatePolicy.hasOfficialSigner(listOf(AppUpdatePolicy.EXPECTED_CERT_SHA256)))
        assertTrue(AppUpdatePolicy.hasOfficialSigner(listOf(AppUpdatePolicy.EXPECTED_CERT_SHA256.uppercase())))
        assertFalse(AppUpdatePolicy.hasOfficialSigner(emptyList()))
        assertFalse(AppUpdatePolicy.hasOfficialSigner(listOf("00".repeat(32))))
        assertFalse(AppUpdatePolicy.hasOfficialSigner(listOf(
            AppUpdatePolicy.EXPECTED_CERT_SHA256, AppUpdatePolicy.EXPECTED_CERT_SHA256
        )))
    }

    @Test fun versionCodeMustIncreaseForEveryUpdate() {
        AppUpdatePolicy.requireNewerVersionCode(5L, 6L)
        AppUpdatePolicy.requireNewerVersionCode(6L, 7L)
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdatePolicy.requireNewerVersionCode(5L, 5L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdatePolicy.requireNewerVersionCode(6L, 5L)
        }
    }
    @Test fun updateRequiresDisarmedProtectionButDoesNotRequireMonitorOptOut() {
        assertTrue(AppUpdatePolicy.canUpdateWhileProtectionDisarmed(ProtectionMode.DISARMED))
        listOf(ProtectionMode.ARMED, ProtectionMode.COUNTDOWN, ProtectionMode.ALARM_TRIGGERED)
            .forEach { mode ->
                assertFalse(AppUpdatePolicy.canUpdateWhileProtectionDisarmed(mode))
                assertThrows(IllegalArgumentException::class.java) {
                    AppUpdatePolicy.requireSafeUpdateMode(mode)
                }
            }
        AppUpdatePolicy.requireSafeUpdateMode(ProtectionMode.DISARMED)
    }

}
