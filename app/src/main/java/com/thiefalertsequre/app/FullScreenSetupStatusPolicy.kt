package com.thiefalertsequre.app

/** Full-screen alarm dashboard access is optional and only required after explicit owner opt-in. */
object FullScreenSetupStatusPolicy {
    fun evaluate(ownerEnabledDashboard: Boolean, sdkInt: Int, systemAccessGranted: Boolean): SetupItem {
        val stateAndDetail = when {
            sdkInt < 34 -> SetupState.UNSUPPORTED to
                "Android does not expose a separate full-screen intent access gate on this version."
            !ownerEnabledDashboard -> SetupState.NEUTRAL to
                "Optional full-screen alarm dashboard is OFF. No full-screen access is required for the current configuration."
            systemAccessGranted -> SetupState.VERIFIED to
                "The owner-enabled full-screen dashboard has the required Android access."
            else -> SetupState.ACTION_REQUIRED to
                "The owner enabled the full-screen dashboard, but Android has not granted full-screen intent access. The normal alarm notification and sound remain available."
        }
        return SetupItem("fullscreen", "Full-screen alarm dashboard access", stateAndDetail.first, stateAndDetail.second)
    }
}
