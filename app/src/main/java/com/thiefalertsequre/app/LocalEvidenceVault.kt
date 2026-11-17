package com.thiefalertsequre.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.TimeZone
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Encrypted local evidence metadata archive.
 *
 * It stores app-observed protection/authentication event metadata and optional
 * cached location snapshots only after an explicit owner action and permission.
 * Optional front-camera photos live separately in CameraEvidenceVault and are
 * captured only after successful owner authentication and explicit opt-in.
 * No microphone content or credentials/biometrics are stored.
 */
object LocalEvidenceVault {
    private const val PREFS = "local_evidence_vault_v1"
    private const val KEY_PAYLOAD = "encrypted_records"
    private const val PURPOSE = "thiefalertsequre:evidence-vault:v1"
    private const val MAX_RECORDS = 100
    private const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
    private val generation = AtomicLong(0L)
    private val writer = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64),
        { task -> Thread(task, "tas-evidence-vault").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy()
    )

    /** Best-effort bounded background write; never blocks an alarm or receiver callback. */
    fun recordAsync(context: Context, event: String, detail: String = "") {
        val submittedGeneration = generation.get()
        runCatching {
            writer.execute {
                synchronized(this) {
                    if (generation.get() == submittedGeneration) {
                        runCatching { record(context, event, detail) }
                    }
                }
            }
        }
    }

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
        val raw = when (val stored = SafePreferences.readString(prefs, KEY_PAYLOAD, null)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> return false
        }
        val existing = readEntries(raw) ?: return false
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

    /** Replace the archive only after a fully decrypted and integrity-checked owner restore. */
    @Synchronized
    fun replaceAll(context: Context, imported: List<Entry>): Boolean {
        if (imported.size > MAX_RECORDS) return false
        val now = System.currentTimeMillis()
        val validated = imported.mapNotNull { item ->
            val event = LocalTamperHistoryPolicy.sanitizeEvent(item.event)
            val detail = LocalTamperHistoryPolicy.sanitizeDetail(item.detail).take(240)
            if (item.integritySha256 != digest(item.timestampEpochMs, item.timezoneOffsetMinutes, event, detail)) {
                return false
            }
            if (now - item.timestampEpochMs !in 0..RETENTION_MS) null
            else Entry(item.timestampEpochMs, item.timezoneOffsetMinutes, event, detail, item.integritySha256)
        }.takeLast(MAX_RECORDS)
        val encoded = JSONArray().apply {
            validated.forEach { item ->
                put(JSONObject().apply {
                    put("at", item.timestampEpochMs)
                    put("tz", item.timezoneOffsetMinutes)
                    put("event", item.event)
                    put("detail", item.detail)
                    put("sha256", item.integritySha256)
                })
            }
        }.toString()
        generation.incrementAndGet()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return runCatching {
            val encrypted = LocalPreferenceCipher.encrypt(PURPOSE, encoded)
            prefs.edit().putString(KEY_PAYLOAD, encrypted).commit()
        }.getOrDefault(false)
    }

    /** Read-only integrity check that distinguishes an empty vault from a corrupt one. */
    fun verifyIntegrity(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = when (val value = SafePreferences.readString(prefs, KEY_PAYLOAD, null)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return false
        }
        return raw == null || readEntries(raw) != null
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

    @Synchronized
    fun deleteAll(context: Context): Boolean {
        // Invalidate queued writes before clearing; older queued tasks cannot repopulate the vault.
        generation.incrementAndGet()
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_PAYLOAD).commit()
    }

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
