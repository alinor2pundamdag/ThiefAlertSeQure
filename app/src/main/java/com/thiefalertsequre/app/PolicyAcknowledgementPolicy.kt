package com.thiefalertsequre.app

/** Versioned local owner acknowledgements for the in-app privacy notice and terms. */
object PolicyAcknowledgementPolicy {
    const val PRIVACY_PREFS = "privacy_notice_v5"
    const val PRIVACY_VERSION = 5
    const val TERMS_PREFS = "terms_notice_v1"
    const val TERMS_VERSION = 1

    fun isCurrent(decision: String?, storedVersion: Int, requiredVersion: Int): Boolean =
        decision == ACKNOWLEDGED && storedVersion == requiredVersion

    private const val ACKNOWLEDGED = "acknowledged"
}
