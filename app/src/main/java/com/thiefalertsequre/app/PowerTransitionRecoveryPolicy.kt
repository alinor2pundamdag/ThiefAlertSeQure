package com.thiefalertsequre.app

/** A battery snapshot may retry an OS event, but must never invent a disconnect transition. */
object PowerTransitionRecoveryPolicy {
    fun shouldRetry(pendingOsDisconnect: Boolean, liveConnected: Boolean?): Boolean =
        pendingOsDisconnect && liveConnected == false
}
