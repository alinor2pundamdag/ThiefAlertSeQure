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
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = when (val stored = SafePreferences.readString(prefs, KEY_EVENTS, "")) {
            is PreferenceRead.Value -> stored.value.orEmpty()
            is PreferenceRead.Corrupt -> return
        }
        val next = LocalTamperHistoryPolicy.append(existing, line)
        if (prefs.edit().putString(KEY_EVENTS, next).commit()) {
            // The encrypted archive is secondary evidence; failure never changes
            // the authoritative protection state or blocks alarm delivery.
            runCatching { LocalEvidenceVault.record(context, safeEvent, safeDetail) }
        }
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

    fun clear(context: Context): Boolean {
        val historyCleared = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_EVENTS).commit()
        val evidenceCleared = LocalEvidenceVault.deleteAll(context)
        return historyCleared && evidenceCleared
    }
}
