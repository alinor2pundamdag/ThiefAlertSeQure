package com.thiefalertsequre.app

/**
 * Pure formatting and retention rules for the bounded local protection history.
 * Kept separate from Android storage so the safety rules have JVM unit coverage.
 */
internal object LocalTamperHistoryPolicy {
    const val MAX_EVENTS = 100

    fun sanitizeEvent(event: String): String =
        event.filter { it.isLetterOrDigit() || it in "_- ." }.take(48)

    fun sanitizeDetail(detail: String): String =
        detail.replace(Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]"), " ").take(180)

    fun append(existing: String, entry: String, maxEvents: Int = MAX_EVENTS): String {
        require(maxEvents > 0) { "maxEvents must be positive" }
        val prior = existing.lineSequence().filter { it.isNotBlank() }.toList()
        return (prior + entry).takeLast(maxEvents).joinToString("\n")
    }

    fun newestFirst(existing: String, limit: Int = MAX_EVENTS): List<String> {
        val boundedLimit = limit.coerceIn(1, MAX_EVENTS)
        return existing.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(boundedLimit)
            .asReversed()
    }
}
