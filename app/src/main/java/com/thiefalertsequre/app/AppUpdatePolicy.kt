package com.thiefalertsequre.app

import java.net.URL

/** Immutable release identity and pure validation rules shared by updater tests. */
internal object AppUpdatePolicy {
    const val EXPECTED_PACKAGE = "com.thiefalertsequre.app"
    const val EXPECTED_CERT_SHA256 =
        "9f4aa14a6c6fbca0dc10c7806858de9f050447953b93c5c99c6e6ea85f540b64"

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

    /**
     * Accept historical numeric release tags and the permanent owner-approved
     * developer suffix used by all new official releases.
     */
    fun isValidReleaseTag(tag: String): Boolean =
        tag.matches(Regex("^v[0-9]+(\\.[0-9]+){1,3}(_Developer_Alinor)?$"))

    fun isNewerVersionCode(installedVersionCode: Long, candidateVersionCode: Long): Boolean =
        candidateVersionCode > installedVersionCode

    fun requireNewerVersionCode(installedVersionCode: Long, candidateVersionCode: Long) {
        require(isNewerVersionCode(installedVersionCode, candidateVersionCode)) {
            "The APK version code is not newer than the installed version."
        }
    }

    fun isAllowedHttpsUrl(address: String): Boolean = runCatching {
        val url = URL(address)
        url.protocol.equals("https", ignoreCase = true) &&
            url.host.isNotBlank() &&
            listOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
                .any { it.equals(url.host, ignoreCase = true) } &&
            (url.port == -1 || url.port == 443) &&
            url.userInfo == null
    }.getOrDefault(false)

    fun hasOfficialSigner(signerSha256Digests: List<String>): Boolean =
        signerSha256Digests.size == 1 &&
            signerSha256Digests.single().equals(EXPECTED_CERT_SHA256, ignoreCase = true)
}
