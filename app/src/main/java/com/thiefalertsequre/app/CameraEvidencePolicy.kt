package com.thiefalertsequre.app

/** Pure consent, retention and size rules for optional post-authentication photo evidence. */
internal object CameraEvidencePolicy {
    const val MAX_IMAGES = 20
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    /** Reject oversized camera buffers before allocating a second full byte array or bitmap. */
    const val MAX_SOURCE_IMAGE_BYTES = 16 * 1024 * 1024
    const val MAX_ENCRYPTED_FILE_OVERHEAD_BYTES = 64
    const val MAX_STAGING_FILES = MAX_IMAGES + 1
    const val MAX_TOTAL_STORAGE_BYTES = MAX_STAGING_FILES.toLong() *
        (MAX_IMAGE_BYTES + MAX_ENCRYPTED_FILE_OVERHEAD_BYTES).toLong()
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

    fun isValidSourceImageSize(sizeBytes: Int): Boolean =
        sizeBytes in 1..MAX_SOURCE_IMAGE_BYTES

    /** Power-of-two sampling keeps decoded bitmap dimensions within the target bounds. */
    fun decodeSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        if (width <= 0 || height <= 0 || maxWidth <= 0 || maxHeight <= 0) return 1
        val shrinkRatio = maxOf(width.toDouble() / maxWidth, height.toDouble() / maxHeight)
        var sampleSize = 1
        while (sampleSize < (1 shl 15) && sampleSize * 2 <= shrinkRatio) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /** Bounds indexed images plus one atomic-write staging/orphan slot. */
    fun isWithinStorageBudget(fileCount: Int, totalBytes: Long): Boolean =
        fileCount in 0..MAX_STAGING_FILES && totalBytes in 0L..MAX_TOTAL_STORAGE_BYTES
}
