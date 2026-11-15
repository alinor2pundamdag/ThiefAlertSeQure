package com.thiefalertsequre.app

/** A battery snapshot may retry an OS event, but must never invent a disconnect transition. */
object PowerTransitionRecoveryPolicy {
    fun shouldRetry(pendingOsDisconnect: Boolean, liveConnected: Boolean?): Boolean =
        pendingOsDisconnect && liveConnected == false

    /** A previously persisted OS disconnect may be retried after a failed state commit. */
    fun canAcceptDisconnectCandidate(
        connectedBaseline: Boolean,
        pendingOsDisconnectRetry: Boolean
    ): Boolean = connectedBaseline || pendingOsDisconnectRetry

    /** A connect event invalidates a disconnect candidate only after live confirmation and no newer event. */
    fun acceptsConnectedEvent(
        liveConnected: Boolean?,
        eventGeneration: Long,
        currentGeneration: Long
    ): Boolean = liveConnected == true && eventGeneration == currentGeneration
}
