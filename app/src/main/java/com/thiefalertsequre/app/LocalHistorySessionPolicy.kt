package com.thiefalertsequre.app

/** Monotonic expiry policy for owner-authenticated access to private local history. */
object LocalHistorySessionPolicy {
    fun isActive(nowElapsedMs: Long, expiresAtElapsedMs: Long): Boolean =
        expiresAtElapsedMs > 0L && nowElapsedMs < expiresAtElapsedMs
}
