package com.thiefalertsequre.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Durable per-accessory A2 grace alarms, independent of the monitor process lifetime. */
internal object A2DisconnectScheduler {
    private const val KEY_PENDING = "trusted_bluetooth_pending_disconnects_v1"
    private const val ACTION = "com.thiefalertsequre.A2_DISCONNECT_DEADLINE"
    private const val URI_PREFIX = "thiefalertsequre://a2-disconnect/"
    private const val REQUEST_BASE = 20_000
    // ACL broadcasts can arrive concurrently for different accessories. Serialize
    // every encrypted read-modify-write so one device cannot overwrite another.
    private val lock = Any()

    data class Pending(
        val address: String,
        val eventEpochMs: Long,
        val eventElapsedRealtimeMs: Long,
        val bootCount: Int,
        val monitorRevision: Long,
        val deviceRevision: Long,
        val retryCount: Int = 0
    ) {
        val deadlineElapsedRealtimeMs: Long
            get() = A2DisconnectPolicy.graceDeadlineUptimeMs(
                eventElapsedRealtimeMs, ProtectionMonitorService.BLUETOOTH_DISCONNECT_GRACE_MS
            )
    }

    fun schedule(context: Context, pending: Pending): Boolean =
        scheduleInternal(context, pending, ProtectionMonitorService.PREFS, null)

    /**
     * Re-arm a consumed A2 callback after a transient receiver/state-write failure.
     * The original grace deadline is preserved; retries use a separate bounded delay.
     */
    fun scheduleRetry(context: Context, pending: Pending): Boolean =
        scheduleRetryInternal(context, pending, ProtectionMonitorService.PREFS, null)

    internal fun scheduleRetryIsolated(
        context: Context,
        pending: Pending,
        preferencesName: String,
        namespace: String
    ): Boolean = scheduleRetryInternal(context, pending, preferencesName, namespace)

    private fun scheduleRetryInternal(
        context: Context,
        pending: Pending,
        preferencesName: String,
        namespace: String?
    ): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_retry_store_unreadable", "A2 deadline retry could not read the encrypted schedule; source retained")
            ProtectionMonitorRecoveryNotice.show(context, "Trusted-device deadline retry storage is unreadable. Open the app to review protection monitoring.")
            return false
        }
        val current = records.firstOrNull {
            it.address.equals(pending.address, ignoreCase = true) &&
                it.eventEpochMs == pending.eventEpochMs &&
                it.eventElapsedRealtimeMs == pending.eventElapsedRealtimeMs &&
                it.bootCount == pending.bootCount &&
                it.monitorRevision == pending.monitorRevision &&
                it.deviceRevision == pending.deviceRevision
        } ?: return false
        if (current.retryCount != pending.retryCount) return false

        val delayMs = A2DisconnectPolicy.nextRetryDelayMs(current.retryCount)
        if (delayMs == null) {
            val cleared = runCatching {
                cancelInternal(context, current.address, current.eventElapsedRealtimeMs, preferencesName, namespace)
            }.getOrDefault(false)
            LocalTamperHistory.record(
                context,
                "a2_deadline_retry_exhausted",
                "A2 deadline validation could not be durably completed after ${A2DisconnectPolicy.MAX_RETRIES} bounded retries; pending record cleared=$cleared"
            )
            ProtectionMonitorRecoveryNotice.show(context, "A trusted-device separation deadline could not be verified. Open the app to review protection monitoring.")
            return false
        }

        val updated = current.copy(retryCount = current.retryCount + 1)
        if (!write(prefs, mergePending(records, updated))) {
            LocalTamperHistory.record(context, "a2_deadline_retry_persist_failed", "A2 deadline retry count could not be committed")
            ProtectionMonitorRecoveryNotice.show(context, "A trusted-device deadline retry could not be saved. Open the app to review protection monitoring.")
            return false
        }

        runCatching {
            val manager = context.getSystemService(AlarmManager::class.java)
            val operation = pendingIntent(context, current.address, namespace)
            val retryAt = android.os.SystemClock.elapsedRealtime() + delayMs
            val exactAllowed = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
            if (!exactAllowed) {
                LocalTamperHistory.record(context, "a2_retry_exact_alarm_access_missing", "Using the inexact idle-aware A2 retry; Android may delay delivery")
            }
            if (exactAllowed) {
                try {
                    manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, retryAt, operation)
                } catch (_: SecurityException) {
                    manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, retryAt, operation)
                }
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, retryAt, operation)
            }
            LocalTamperHistory.record(context, "a2_deadline_retry_scheduled", "A2 deadline retry ${updated.retryCount}/${A2DisconnectPolicy.MAX_RETRIES} scheduled after ${delayMs} ms")
            true
        }.getOrElse {
            LocalTamperHistory.record(context, "a2_deadline_retry_schedule_failed", it.javaClass.simpleName + ": " + it.message)
            ProtectionMonitorRecoveryNotice.show(context, "Android could not schedule a trusted-device deadline retry. Open the app to review protection monitoring.")
            false
        }
    }

    /** Isolated preference and PendingIntent namespace for non-destructive instrumentation. */
    internal fun scheduleIsolated(
        context: Context,
        pending: Pending,
        preferencesName: String,
        namespace: String
    ): Boolean = scheduleInternal(context, pending, preferencesName, namespace)

    private fun scheduleInternal(
        context: Context,
        pending: Pending,
        preferencesName: String,
        namespace: String?
    ): Boolean = synchronized(lock) {
        if (!TrustedBluetoothRegistry.isValidAddress(pending.address) ||
            pending.eventEpochMs <= 0L || pending.eventElapsedRealtimeMs <= 0L ||
            pending.eventElapsedRealtimeMs > android.os.SystemClock.elapsedRealtime() ||
            pending.bootCount < -1 || pending.monitorRevision < 0L || pending.deviceRevision < 0L ||
            pending.retryCount !in 0..A2DisconnectPolicy.MAX_RETRIES
        ) {
            LocalTamperHistory.record(context, "a2_deadline_metadata_rejected", "Invalid trusted-device deadline metadata was rejected")
            return false
        }
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_store_unreadable", "Existing encrypted A2 deadlines are unreadable; source retained")
            return false
        }
        val updated = mergePending(records, pending)
        if (!write(prefs, updated)) {
            LocalTamperHistory.record(context, "a2_deadline_persist_failed", "Encrypted A2 disconnect deadline could not be committed")
            return false
        }
        return runCatching {
            val manager = context.getSystemService(AlarmManager::class.java)
            val operation = pendingIntent(context, pending.address, namespace)
            val deadline = pending.deadlineElapsedRealtimeMs.coerceAtLeast(android.os.SystemClock.elapsedRealtime())
            val exactAllowed = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
            if (!exactAllowed) {
                LocalTamperHistory.record(context, "a2_exact_alarm_access_missing", "Using the inexact idle-aware A2 deadline fallback; Android may delay delivery")
            }
            if (exactAllowed) {
                try {
                    manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, operation)
                } catch (_: SecurityException) {
                    manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, operation)
                }
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, operation)
            }
            true
        }.getOrElse {
            LocalTamperHistory.record(context, "a2_deadline_schedule_failed", it.javaClass.simpleName + ": " + it.message)
            runCatching {
                cancelInternal(context, pending.address, pending.eventElapsedRealtimeMs, preferencesName, namespace)
            }
            false
        }
    }

    fun find(context: Context, token: String): Pending? = synchronized(lock) {
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_read_failed", "A2 deadline callback could not read the encrypted schedule; source retained")
            return null
        }
        val pending = records.firstOrNull { tokenFor(it.address) == token } ?: return null
        if (pending.eventElapsedRealtimeMs > android.os.SystemClock.elapsedRealtime()) {
            LocalTamperHistory.record(context, "a2_future_deadline_rejected", "Future-dated A2 deadline metadata was rejected")
            return null
        }
        return pending
    }

    fun cancel(context: Context, address: String, expectedEventElapsedMs: Long? = null): Boolean =
        cancelInternal(context, address, expectedEventElapsedMs, ProtectionMonitorService.PREFS, null)

    internal fun cancelIsolated(
        context: Context,
        address: String,
        expectedEventElapsedMs: Long?,
        preferencesName: String,
        namespace: String
    ): Boolean = cancelInternal(context, address, expectedEventElapsedMs, preferencesName, namespace)

    private fun cancelInternal(
        context: Context,
        address: String,
        expectedEventElapsedMs: Long?,
        preferencesName: String,
        namespace: String?
    ): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_cancel_store_unreadable", "A2 deadline cancellation could not read the encrypted schedule; source retained")
            return false
        }
        val matching = records.firstOrNull { it.address.equals(address, true) }
        if (matching == null || (expectedEventElapsedMs != null &&
                matching.eventElapsedRealtimeMs != expectedEventElapsedMs)
        ) return true
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, address, namespace))
        val removed = write(prefs, records.filterNot { it.address.equals(address, true) })
        if (!removed) {
            LocalTamperHistory.record(context, "a2_deadline_cancel_persist_failed", "Reconnect cancellation removed the AlarmManager callback but could not commit the updated encrypted deadline list")
        }
        return removed
    }

    fun clearAll(context: Context): Boolean =
        clearAllInternal(context, ProtectionMonitorService.PREFS, null)

    internal fun clearAllIsolated(
        context: Context,
        preferencesName: String,
        namespace: String
    ): Boolean = clearAllInternal(context, preferencesName, namespace)

    private fun clearAllInternal(
        context: Context,
        preferencesName: String,
        namespace: String?
    ): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_pending_clear_store_unreadable", "A2 deadline cleanup could not read the encrypted schedule; source retained")
            return false
        }
        val manager = context.getSystemService(AlarmManager::class.java)
        records.forEach { manager.cancel(pendingIntent(context, it.address, namespace)) }
        val cleared = prefs.edit().remove(KEY_PENDING).commit()
        if (!cleared) {
            LocalTamperHistory.record(context, "a2_pending_clear_failed", "Pending trusted-device deadlines could not be cleared")
        }
        return cleared
    }

    /** Replace only this accessory's timer while preserving every other device's record. */
    internal fun mergePending(records: List<Pending>, pending: Pending): List<Pending> =
        records.filterNot { it.address.equals(pending.address, ignoreCase = true) } + pending

    private fun read(prefs: android.content.SharedPreferences): List<Pending>? {
        val raw = when (val value = SafePreferences.readString(prefs, KEY_PENDING, null)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return null
        } ?: return emptyList()
        return runCatching {
            val decoded = if (LocalPreferenceCipher.isEncrypted(raw)) {
                LocalPreferenceCipher.decrypt(KEY_PENDING, raw)
            } else {
                check(prefs.edit().putString(KEY_PENDING, LocalPreferenceCipher.encrypt(KEY_PENDING, raw)).commit())
                raw
            }
            val records = decoded.lineSequence().filter { it.isNotBlank() }.map { line ->
                val fields = line.split('|')
                require(fields.size in 6..7 && TrustedBluetoothRegistry.isValidAddress(fields[0]))
                val record = Pending(
                    fields[0], fields[1].toLong(), fields[2].toLong(),
                    fields[3].toInt(), fields[4].toLong(), fields[5].toLong(),
                    if (fields.size == 7) fields[6].toInt() else 0
                )
                require(
                    record.eventEpochMs > 0L && record.eventElapsedRealtimeMs > 0L &&
                        record.bootCount >= -1 && record.monitorRevision >= 0L && record.deviceRevision >= 0L &&
                        record.retryCount in 0..A2DisconnectPolicy.MAX_RETRIES
                ) { "A2 deadline record contains invalid time or revision metadata." }
                record
            }.toList()
            require(records.map { it.address.uppercase() }.distinct().size == records.size) {
                "A2 deadline store contains duplicate accessory records."
            }
            records
        }.onFailure {
            android.util.Log.e("A2DisconnectScheduler", "Encrypted A2 deadline data is unreadable; source retained", it)
        }.getOrNull()
    }

    private fun write(prefs: android.content.SharedPreferences, records: List<Pending>): Boolean {
        if (records.isEmpty()) return prefs.edit().remove(KEY_PENDING).commit()
        val raw = records.distinctBy { it.address.uppercase() }.joinToString("\n") {
            "${it.address.uppercase()}|${it.eventEpochMs}|${it.eventElapsedRealtimeMs}|${it.bootCount}|${it.monitorRevision}|${it.deviceRevision}|${it.retryCount}"
        }
        return runCatching {
            prefs.edit().putString(KEY_PENDING, LocalPreferenceCipher.encrypt(KEY_PENDING, raw)).commit()
        }.getOrDefault(false)
    }

    private fun tokenFor(address: String): String = MessageDigest.getInstance("SHA-256")
        .digest(address.trim().uppercase().toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun pendingIntent(context: Context, address: String, namespace: String? = null): PendingIntent {
        val token = tokenFor(address)
        val uri = if (namespace == null) URI_PREFIX + token
        else URI_PREFIX + "test/" + Uri.encode(namespace) + "/" + token
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + (token.take(7).toLong(16) % 1_000_000L).toInt(),
            Intent(context, AlarmDeadlineReceiver::class.java)
                .setAction(ACTION)
                .setData(Uri.parse(uri)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val ACTION_DEADLINE = ACTION
}
