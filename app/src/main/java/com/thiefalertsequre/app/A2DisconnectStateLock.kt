package com.thiefalertsequre.app

/**
 * Serializes A2 registry, baseline, deadline, and callback state transitions.
 *
 * Manifest ACL broadcasts, runtime ACL broadcasts, and AlarmManager callbacks
 * can execute on different threads. SharedPreferences commits are atomic
 * individually, but their read-modify-write sequences are not.
 */
internal object A2DisconnectStateLock {
    @PublishedApi
    internal val monitor = Any()

    inline fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
