package com.thiefalertsequre.app

/**
 * Full-screen alarm presentation is an explicit local opt-in and remains subject
 * to Android's separate USE_FULL_SCREEN_INTENT access on API 34+.
 */
object AlarmPresentationPolicy {
    fun shouldUseFullScreenIntent(
        ownerEnabledDashboard: Boolean,
        sdkInt: Int,
        systemAccessGranted: Boolean
    ): Boolean = ownerEnabledDashboard && (sdkInt < 34 || systemAccessGranted)
}
