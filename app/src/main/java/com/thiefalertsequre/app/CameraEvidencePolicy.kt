package com.thiefalertsequre.app

/** Pure consent, retention and size rules for optional post-authentication photo evidence. */
internal object CameraEvidencePolicy {
    const val MAX_IMAGES = 20
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L

    fun shouldCapture(
        ownerEnabled: Boolean,
        cameraPermissionGranted: Boolean,
        authenticationSucceeded: Boolean,
        activityVisible: Boolean
    ): Boolean = ownerEnabled && cameraPermissionGranted &&
        authenticationSucceeded && activityVisible

    fun isRetained(timestampEpochMs: Long, nowEpochMs: Long): Boolean =
        timestampEpochMs > 0L && nowEpochMs >= timestampEpochMs &&
            nowEpochMs - timestampEpochMs <= RETENTION_MS

    fun isValidImageSize(sizeBytes: Int): Boolean =
        sizeBytes in 1..MAX_IMAGE_BYTES
}
