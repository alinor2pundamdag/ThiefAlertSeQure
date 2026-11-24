package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test fun officialReleaseTagsMapToExactAndroidVersionNames() {
        assertEquals("1.1.3", AppUpdatePolicy.versionNameFromReleaseTag("v1.1.3"))
        assertEquals(
            "1.1.4_Developer_Alinor",
            AppUpdatePolicy.versionNameFromReleaseTag("v1.1.4_Developer_Alinor")
        )
    }

    @Test fun malformedOrUnapprovedReleaseTagSuffixesAreRejected() {
        listOf("", "1.1.4", "v1.1.4-rc1", "v1.1.4_other", "v1.1.4_Developer_Alinor.evil")
            .forEach { tag ->
                assertThrows(IllegalArgumentException::class.java) {
                    AppUpdatePolicy.versionNameFromReleaseTag(tag)
                }
            }
    }

    @Test fun updaterAcceptsOnlyApprovedHttpsHostsAndStandardPorts() {
        assertTrue(AppUpdatePolicy.isAllowedHttpsUrl("https://api.github.com/repos/owner/repo/releases/latest"))
        assertTrue(AppUpdatePolicy.isAllowedHttpsUrl("https://github.com/owner/repo/releases/download/v1.0/app.apk"))
        assertTrue(AppUpdatePolicy.isAllowedHttpsUrl("https://release-assets.githubusercontent.com/assets/app.apk"))
        assertTrue(AppUpdatePolicy.isAllowedHttpsUrl("https://objects.githubusercontent.com:443/assets/app.apk"))
        assertTrue(AppUpdatePolicy.isAllowedHttpsUrl("https://GITHUB.COM/owner/repo/releases"))

        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("http://api.github.com/repos/owner/repo"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("https://github.com.evil.example/app.apk"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("https://evilgithub.com/app.apk"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("https://user@github.com/app.apk"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("https://github.com:8443/app.apk"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("not a URL"))
    }

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

    @Test fun versionCodeIsAuthoritativeEvenWhenDisplayVersionIsUnchanged() {
        assertTrue(AppUpdatePolicy.isNewerVersionCode(5L, 6L))
        assertTrue(AppUpdatePolicy.isNewerVersionCode(6L, 7L))
        assertFalse(AppUpdatePolicy.isNewerVersionCode(6L, 6L))
        assertFalse(AppUpdatePolicy.isNewerVersionCode(7L, 6L))
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
