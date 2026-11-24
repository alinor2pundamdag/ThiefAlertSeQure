package com.thiefalertsequre.app

/** Shared result rule for independent exact and inexact AlarmManager callbacks. */
internal object AlarmCallbackPolicy {
    fun hasUsableDeadlineCallback(exactScheduled: Boolean, fallbackScheduled: Boolean): Boolean =
        exactScheduled || fallbackScheduled

    /**
     * Preserve a previously accepted exact callback when a replacement attempt fails
     * but permission is still available (or cannot be re-checked). A revoked permission
     * or a newly-created unscheduled PendingIntent must be cancelled.
     */
    fun shouldCancelRejectedExactCallback(
        permissionStillGranted: Boolean?,
        existedBeforeAttempt: Boolean
    ): Boolean = permissionStillGranted == false || !existedBeforeAttempt
}
