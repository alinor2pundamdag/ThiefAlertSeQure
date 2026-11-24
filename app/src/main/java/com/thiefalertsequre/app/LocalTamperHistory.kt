package com.thiefalertsequre.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bounded, encrypted, on-device protection event history with safe v1 migration. */
object LocalTamperHistory {
    private const val PREFS = "local_tamper_history_v2"
    private const val KEY_EVENTS = "encrypted_events"
    private const val LEGACY_PREFS = "local_tamper_history_v1"
    private const val LEGACY_KEY_EVENTS = "events"
    private const val PURPOSE = "thiefalertsequre:local-tamper-history:v2"

    private data class HistoryRead(val value: String, val healthy: Boolean)

    @Synchronized
    fun record(context: Context, event: String, detail: String = "", writeEvidenceArchive: Boolean = true): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = readStored(app, prefs)
        if (!current.healthy) return false
        val safeEvent = LocalTamperHistoryPolicy.sanitizeEvent(event)
        val safeDetail = LocalTamperHistoryPolicy.sanitizeDetail(detail)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val line = "$stamp | $safeEvent" + if (safeDetail.isBlank()) "" else " | $safeDetail"
        val next = LocalTamperHistoryPolicy.append(current.value, line)
        val encrypted = runCatching { LocalPreferenceCipher.encrypt(PURPOSE, next) }.getOrNull() ?: return false
        if (!prefs.edit().putString(KEY_EVENTS, encrypted).commit()) return false
        // Remove legacy plaintext only after the encrypted replacement is durable.
        app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().remove(LEGACY_KEY_EVENTS).commit()
        if (writeEvidenceArchive) LocalEvidenceVault.recordAsync(context, safeEvent, safeDetail)
        return true
    }

    @Synchronized
    fun read(context: Context, limit: Int = 100): List<String> {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val result = readStored(app, prefs)
        if (!result.healthy) return emptyList()
        val safe = LocalTamperHistoryPolicy.sanitizeStoredHistory(result.value)
        if (safe != result.value) {
            val encrypted = runCatching { LocalPreferenceCipher.encrypt(PURPOSE, safe) }.getOrNull()
            if (encrypted != null) prefs.edit().putString(KEY_EVENTS, encrypted).commit()
        }
        return LocalTamperHistoryPolicy.newestFirst(safe, limit)
    }

    /** Verifies the encrypted event log without overwriting corrupt data. */
    @Synchronized
    fun verifyIntegrity(context: Context): Boolean {
        val app = context.applicationContext
        return readStored(app, app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).healthy
    }

    /** Clears only the event timeline; the separate encrypted evidence archive is retained. */
    @Synchronized
    fun clear(context: Context): Boolean {
        val app = context.applicationContext
        val currentCleared = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_EVENTS).commit()
        val legacyCleared = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().remove(LEGACY_KEY_EVENTS).commit()
        return currentCleared && legacyCleared
    }

    private fun readStored(context: Context, prefs: SharedPreferences): HistoryRead {
        when (val stored = SafePreferences.readString(prefs, KEY_EVENTS, null)) {
            is PreferenceRead.Corrupt -> return HistoryRead("", false)
            is PreferenceRead.Value -> {
                val raw = stored.value
                if (raw != null) {
                    val plaintext = runCatching { LocalPreferenceCipher.decrypt(PURPOSE, raw) }.getOrNull()
                        ?: return HistoryRead("", false)
                    return HistoryRead(LocalTamperHistoryPolicy.sanitizeStoredHistory(plaintext), true)
                }
            }
        }
        // First-use migration from v1 plaintext SharedPreferences.
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacy = when (val old = SafePreferences.readString(legacyPrefs, LEGACY_KEY_EVENTS, "")) {
            is PreferenceRead.Value -> old.value.orEmpty()
            is PreferenceRead.Corrupt -> return HistoryRead("", false)
        }
        val safeLegacy = LocalTamperHistoryPolicy.sanitizeStoredHistory(legacy)
        if (safeLegacy.isNotBlank()) {
            val encrypted = runCatching { LocalPreferenceCipher.encrypt(PURPOSE, safeLegacy) }.getOrNull()
                ?: return HistoryRead("", false)
            if (!prefs.edit().putString(KEY_EVENTS, encrypted).commit()) return HistoryRead("", false)
        }
        legacyPrefs.edit().remove(LEGACY_KEY_EVENTS).commit()
        return HistoryRead(safeLegacy, true)
    }
}
