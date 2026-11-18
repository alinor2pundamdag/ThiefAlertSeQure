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

    /** Camera2 JPEG rotation uses the opposite display-rotation sign for front cameras. */
    fun jpegOrientation(
        sensorOrientationDegrees: Int,
        displayRotationDegrees: Int,
        frontFacing: Boolean
    ): Int {
        val sensor = Math.floorMod(sensorOrientationDegrees, 360)
        val display = Math.floorMod(displayRotationDegrees, 360)
        return if (frontFacing) {
            (sensor + display) % 360
        } else {
            Math.floorMod(sensor - display, 360)
        }
    }
}
