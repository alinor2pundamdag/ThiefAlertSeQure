package com.thiefalertsequre.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bounded, on-device protection event history. Never records credentials,
 * biometric material, notification contents, or arbitrary user data.
 */
object LocalTamperHistory {
    private const val PREFS = "local_tamper_history_v1"
    private const val KEY_EVENTS = "events"

    @Synchronized
    fun record(context: Context, event: String, detail: String = "") {
        val safeEvent = LocalTamperHistoryPolicy.sanitizeEvent(event)
        val safeDetail = LocalTamperHistoryPolicy.sanitizeDetail(detail)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = "$stamp | $safeEvent" + if (safeDetail.isBlank()) "" else " | $safeDetail"
        val prefs = runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }.getOrElse {
            // Keep the independent encrypted archive writable even if the
            // convenience history preference cannot be opened.
            LocalEvidenceVault.recordAsync(context, safeEvent, safeDetail)
            return
        }
        val existing = when (val stored = SafePreferences.readString(prefs, KEY_EVENTS, "")) {
            is PreferenceRead.Value -> stored.value.orEmpty()
            is PreferenceRead.Corrupt -> {
                LocalEvidenceVault.recordAsync(context, safeEvent, safeDetail)
                return
            }
        }
        val next = LocalTamperHistoryPolicy.append(existing, line)
        val historySaved = runCatching { prefs.edit().putString(KEY_EVENTS, next).commit() }.getOrDefault(false)
        if (!historySaved) {
            android.util.Log.w("LocalTamperHistory", "Readable event history commit failed; encrypted evidence write will still be attempted")
        }
        // The encrypted archive is independent of the convenience history log.
        // A plaintext-history failure must not suppress secondary evidence.
        LocalEvidenceVault.recordAsync(context, safeEvent, safeDetail)
    }

    fun read(context: Context, limit: Int = 100): List<String> =
        run {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = SafePreferences.readString(prefs, KEY_EVENTS, "")
            val value = when (stored) {
                is PreferenceRead.Value -> stored.value.orEmpty()
                is PreferenceRead.Corrupt -> ""
            }
            LocalTamperHistoryPolicy.newestFirst(value, limit)
        }

    fun clear(context: Context): Boolean =
        ProtectionStore(context).runIfDisarmed {
            val historyPrefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val historyCleared = historyPrefs.edit().remove(KEY_EVENTS).commit()
            if (!historyCleared) return@runIfDisarmed false
            val evidenceCleared = LocalEvidenceVault.deleteAll(context)
            historyCleared && evidenceCleared
        } ?: false
}
