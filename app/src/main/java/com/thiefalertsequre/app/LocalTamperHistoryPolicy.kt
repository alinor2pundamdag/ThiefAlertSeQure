package com.thiefalertsequre.app

import java.util.Locale

/**
 * Pure formatting and retention rules for the bounded local protection history.
 * Kept separate from Android storage so the safety rules have JVM unit coverage.
 */
internal object LocalTamperHistoryPolicy {
    const val MAX_EVENTS = 100

    fun sanitizeEvent(event: String): String =
        event.filter { it.isLetterOrDigit() || it in "_- ." }.take(48)

    private val exceptionMessage = Regex("""\b([A-Za-z][A-Za-z0-9_$]*(?:Exception|Error)):\s*.*$""")
    private val credentialAssignment = Regex(
        """(?i)\b(api[_-]?key|access[_-]?token|refresh[_-]?token|password|passwd|secret|authorization|bearer|token|private[_-]?key)\b\s*[:=]\s*(?:Bearer\s+)?(?:"[^"]*"|'[^']*'|[^\s,;]+)"""
    )
    private val bearerCredential = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/-]+=*""")
    private val knownToken = Regex("""\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,})\b""")
    private val jwt = Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b""")
    private val uri = Regex("""(?i)\b(?:https?|content|file)://[^\s,;]+""")
    private val filesystemPath = Regex("""(?<![A-Za-z0-9])(?:/data/(?:user|user_de|data|app)/|/storage/|/sdcard/|/mnt/)[^\s,;]+""")
    private val email = Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""")

    fun sanitizeDetail(detail: String): String {
        var safe = detail
            .replace(Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]"), " ")
            .replace(Regex(" +"), " ")
            .trim()
        safe = exceptionMessage.replace(safe) { it.groupValues[1] + ": [redacted]" }
        safe = bearerCredential.replace(safe, "Bearer [redacted]")
        safe = credentialAssignment.replace(safe) { it.groupValues[1] + "=[redacted]" }
        safe = knownToken.replace(safe, "[redacted]")
        safe = jwt.replace(safe, "[redacted]")
        safe = uri.replace(safe, "[redacted-uri]")
        safe = filesystemPath.replace(safe, "[redacted-path]")
        safe = email.replace(safe, "[redacted-email]")
        return safe.trim().take(180)
    }

    /** Redacts previously persisted detail fields when older local history is read or appended. */
    fun sanitizeStoredHistory(existing: String): String =
        existing.lineSequence().joinToString("\n") { line ->
            val firstSeparator = line.indexOf(" | ")
            val detailSeparator = if (firstSeparator >= 0) line.indexOf(" | ", firstSeparator + 3) else -1
            if (detailSeparator < 0) {
                line
            } else {
                line.substring(0, detailSeparator + 3) + sanitizeDetail(line.substring(detailSeparator + 3))
            }
        }

    fun append(existing: String, entry: String, maxEvents: Int = MAX_EVENTS): String {
        require(maxEvents > 0) { "maxEvents must be positive" }
        val prior = existing.lineSequence().filter { it.isNotBlank() }.toList()
        return (prior + entry).takeLast(maxEvents).joinToString("\n")
    }

    /** Export only the event code; never include free-form details in redacted diagnostics. */
    fun eventCodeFromLine(line: String): String {
        val candidate = line.substringAfter("|", "").trim().substringBefore("|").trim()
        return candidate.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,48}")) } ?: "unknown_event"
    }

    fun matchesCategory(eventCode: String, category: String): Boolean {
        val code = eventCode.lowercase(Locale.ROOT)
        val terms = when (category) {
            "power" -> listOf("charger", "power", "shutdown", "boot", "monitor", "motion")
            "bluetooth" -> listOf("bluetooth", "a2")
            "alarm" -> listOf("alarm", "flash", "haptic", "sound", "audio")
            "authentication" -> listOf("auth", "credential", "biometric", "unlock")
            "evidence" -> listOf("evidence", "camera", "location", "history", "backup", "export", "vault")
            "system" -> listOf("permission", "notification", "update", "device", "bypass", "diagnostic", "root", "settings")
            "all" -> return true
            else -> return false
        }
        return terms.any(code::contains)
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
