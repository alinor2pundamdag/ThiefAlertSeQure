package com.thiefalertsequre.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.TimeZone

/**
 * Encrypted local evidence metadata archive.
 *
 * It stores only app-observed protection/authentication event metadata. It does
 * not capture camera, microphone, credentials, biometrics, or location. Those
 * sources require separate visible owner consent and are intentionally not
 * inferred from an alarm event.
 */
object LocalEvidenceVault {
    private const val PREFS = "local_evidence_vault_v1"
    private const val KEY_PAYLOAD = "encrypted_records"
    private const val PURPOSE = "thiefalertsequre:evidence-vault:v1"
    private const val MAX_RECORDS = 100
    private const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L

    data class Entry(
        val timestampEpochMs: Long,
        val timezoneOffsetMinutes: Int,
        val event: String,
        val detail: String,
        val integritySha256: String
    )

    @Synchronized
    fun record(context: Context, event: String, detail: String = ""): Boolean {
        val safeEvent = LocalTamperHistoryPolicy.sanitizeEvent(event)
        val safeDetail = LocalTamperHistoryPolicy.sanitizeDetail(detail).take(240)
        val now = System.currentTimeMillis()
        val offset = TimeZone.getDefault().getOffset(now) / 60_000
        val entry = Entry(now, offset, safeEvent, safeDetail, digest(now, offset, safeEvent, safeDetail))
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = readEntries(prefs.getString(KEY_PAYLOAD, null)) ?: return false
        val retained = (existing.filter { now - it.timestampEpochMs in 0..RETENTION_MS } + entry)
            .takeLast(MAX_RECORDS)
        val encoded = JSONArray().apply {
            retained.forEach { item ->
                put(JSONObject().apply {
                    put("at", item.timestampEpochMs)
                    put("tz", item.timezoneOffsetMinutes)
                    put("event", item.event)
                    put("detail", item.detail)
                    put("sha256", item.integritySha256)
                })
            }
        }.toString()
        return runCatching {
            val encrypted = LocalPreferenceCipher.encrypt(PURPOSE, encoded)
            prefs.edit().putString(KEY_PAYLOAD, encrypted).commit()
        }.getOrDefault(false)
    }

    fun read(context: Context, limit: Int = MAX_RECORDS): List<Entry> {
        require(limit in 1..MAX_RECORDS)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = when (val value = SafePreferences.readString(prefs, KEY_PAYLOAD, null)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return emptyList()
        } ?: return emptyList()
        val entries = readEntries(raw) ?: return emptyList()
        val now = System.currentTimeMillis()
        return entries.filter { now - it.timestampEpochMs in 0..RETENTION_MS }
            .takeLast(limit).asReversed()
    }

    fun deleteAll(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_PAYLOAD).commit()

    private fun readEntries(raw: String?): List<Entry>? {
        if (raw == null) return emptyList()
        return runCatching {
            val plaintext = LocalPreferenceCipher.decrypt(PURPOSE, raw)
            val array = JSONArray(plaintext)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val at = item.getLong("at")
                    val tz = item.getInt("tz")
                    val event = LocalTamperHistoryPolicy.sanitizeEvent(item.getString("event"))
                    val detail = LocalTamperHistoryPolicy.sanitizeDetail(item.optString("detail")).take(240)
                    val hash = item.getString("sha256")
                    require(hash == digest(at, tz, event, detail)) { "Evidence record integrity check failed." }
                    add(Entry(at, tz, event, detail, hash))
                }
            }
        }.getOrNull()
    }

    private fun digest(at: Long, tz: Int, event: String, detail: String): String {
        val bytes = "$at|$tz|$event|$detail".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
