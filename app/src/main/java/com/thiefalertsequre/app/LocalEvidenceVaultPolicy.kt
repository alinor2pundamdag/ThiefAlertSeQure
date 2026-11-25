package com.thiefalertsequre.app

/** Wall-clock retention rules that preserve evidence if the device clock moves backwards. */
object LocalEvidenceVaultPolicy {
    const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L

    fun isValidTimestampMetadata(timestampEpochMs: Long, timezoneOffsetMinutes: Int): Boolean =
        timestampEpochMs >= 0L && timezoneOffsetMinutes in -840..840

    fun isWithinRetention(nowEpochMs: Long, timestampEpochMs: Long): Boolean =
        timestampEpochMs >= nowEpochMs ||
            (timestampEpochMs >= 0L && nowEpochMs >= timestampEpochMs &&
                nowEpochMs - timestampEpochMs <= RETENTION_MS)

    fun isFutureDated(nowEpochMs: Long, timestampEpochMs: Long): Boolean =
        timestampEpochMs > nowEpochMs
}
