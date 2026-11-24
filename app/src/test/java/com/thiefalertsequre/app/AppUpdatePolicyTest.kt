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

    @Test fun updaterAcceptsOnlyExactGithubHttpsHostsAndStandardPorts() {
        assertTrue(AppUpdatePolicy.isAllowedHttpsUrl("https://api.github.com/repos/owner/repo/releases/latest"))
        assertTrue(AppUpdatePolicy.isAllowedHttpsUrl("https://github.com/owner/repo/releases/download/v1.0/app.apk"))
        assertTrue(AppUpdatePolicy.isAllowedHttpsUrl("https://release-assets.githubusercontent.com/assets/app.apk"))
        assertTrue(AppUpdatePolicy.isAllowedHttpsUrl("https://objects.githubusercontent.com/assets/app.apk:443"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("http://api.github.com/repos/owner/repo"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("https://github.com.evil.example/app.apk"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("https://evilgithub.com/app.apk"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("https://user@github.com/app.apk"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("https://github.com:8443/app.apk"))
        assertFalse(AppUpdatePolicy.isAllowedHttpsUrl("not a URL"))
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

    @Test fun releaseTagsAcceptPermanentDeveloperSuffixAndHistoricalTags() {
        assertTrue(AppUpdatePolicy.isValidReleaseTag("v1.1.3"))
        assertTrue(AppUpdatePolicy.isValidReleaseTag("v1.1.4_Developer_Alinor"))
        assertTrue(AppUpdatePolicy.isValidReleaseTag("v1.1.5_Developer_Alinor"))
        assertFalse(AppUpdatePolicy.isValidReleaseTag("1.1.5_Developer_Alinor"))
        assertFalse(AppUpdatePolicy.isValidReleaseTag("v1.1.5-developer-alinor"))
        assertFalse(AppUpdatePolicy.isValidReleaseTag("v1.1.5_Developer_Alinor-extra"))
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
