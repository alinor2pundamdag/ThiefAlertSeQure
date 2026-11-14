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
    private const val MAX_EVENTS = 100

    @Synchronized
    fun record(context: Context, event: String, detail: String = "") {
        val safeEvent = event.filter { it.isLetterOrDigit() || it in "_- ." }.take(48)
        val safeDetail = detail.replace(Regex("[\\r\\n\\t]"), " ").take(180)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = "$stamp | $safeEvent" + if (safeDetail.isBlank()) "" else " | $safeDetail"
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = runCatching { prefs.getString(KEY_EVENTS, "").orEmpty() }.getOrElse {
            prefs.edit().remove(KEY_EVENTS).commit()
            ""
        }
        val next = (existing.split('\n').filter { it.isNotBlank() } + line).takeLast(MAX_EVENTS)
        prefs.edit().putString(KEY_EVENTS, next.joinToString("\n")).commit()
    }

    fun read(context: Context, limit: Int = 100): List<String> =
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_EVENTS, "").orEmpty()
        }.getOrDefault("")
            .split('\n').filter { it.isNotBlank() }.takeLast(limit.coerceIn(1, MAX_EVENTS))
            .reversed()

    fun clear(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_EVENTS).commit()
}
