package com.thiefalertsequre.app

/** Immutable release identity and pure validation rules shared by updater tests. */
internal object AppUpdatePolicy {
    const val EXPECTED_PACKAGE = "com.thiefalertsequre.app"
    const val EXPECTED_CERT_SHA256 =
        "9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64"

    private val officialReleaseTagPattern =
        Regex("^v([0-9]+(?:\\.[0-9]+){1,3}(?:_Developer_Alinor)?)$")

    /** Maps an official tag to the exact Android versionName carried by its APK. */
    fun versionNameFromReleaseTag(tag: String): String {
        val match = officialReleaseTagPattern.matchEntire(tag)
        requireNotNull(match) { "The official release tag has an invalid version format." }
        return match.groupValues[1]
    }

    fun requireInstalledPackage(installedPackage: String) {
        require(installedPackage == EXPECTED_PACKAGE) {
            "In-app updates are supported only for the official production package."
        }
    }

    fun requireCandidatePackage(installedPackage: String, candidatePackage: String) {
        requireInstalledPackage(installedPackage)
        require(candidatePackage == EXPECTED_PACKAGE && candidatePackage == installedPackage) {
            "The update APK application ID does not match the installed production application."
        }
    }

    fun canUpdateWhileProtectionDisarmed(mode: ProtectionMode): Boolean =
        mode == ProtectionMode.DISARMED

    fun requireSafeUpdateMode(mode: ProtectionMode) {
        require(canUpdateWhileProtectionDisarmed(mode)) {
            "Disable protection and clear any active countdown or alarm before updating."
        }
    }

    fun isNewerVersionCode(installedVersionCode: Long, candidateVersionCode: Long): Boolean =
        candidateVersionCode > installedVersionCode

    fun requireNewerVersionCode(installedVersionCode: Long, candidateVersionCode: Long) {
        require(isNewerVersionCode(installedVersionCode, candidateVersionCode)) {
            "The APK version code is not newer than the installed version."
        }
    }

    fun hasOfficialSigner(signerSha256Digests: List<String>): Boolean =
        signerSha256Digests.size == 1 &&
            signerSha256Digests.single().equals(EXPECTED_CERT_SHA256, ignoreCase = true)
}
