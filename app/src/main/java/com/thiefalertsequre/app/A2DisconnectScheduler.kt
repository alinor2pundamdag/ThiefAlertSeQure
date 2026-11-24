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
    private val confirmedConnections = java.util.concurrent.ConcurrentHashMap<String, Long>()

    data class Pending(
        val address: String,
        val eventEpochMs: Long,
        val eventElapsedRealtimeMs: Long,
        val bootCount: Int,
        val monitorRevision: Long,
        val deviceRevision: Long
    ) {
        val deadlineElapsedRealtimeMs: Long
            get() = A2DisconnectPolicy.graceDeadlineUptimeMs(
                eventElapsedRealtimeMs, ProtectionMonitorService.BLUETOOTH_DISCONNECT_GRACE_MS
            )
    }

    fun schedule(context: Context, pending: Pending): Boolean =
        scheduleInternal(context, pending, ProtectionMonitorService.PREFS, null)

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
            pending.bootCount < 0 || pending.monitorRevision < 0L || pending.deviceRevision < 0L
        ) {
            LocalTamperHistory.record(context, "a2_deadline_metadata_rejected", "Invalid trusted-device deadline metadata was rejected")
            return false
        }
        if (!ProtectionStore(context).isA2EventAfterLastInvalidation(
                pending.eventElapsedRealtimeMs, pending.bootCount
            )
        ) {
            LocalTamperHistory.record(
                context, "a2_deadline_predates_invalidation",
                "A2 deadline was rejected because it predates the last A2 deadline invalidation"
            )
            return@synchronized false
        }
        val addressKey = pending.address.uppercase()
        val confirmedAt = confirmedConnections[addressKey]
        if (confirmedAt != null && confirmedAt >= pending.eventElapsedRealtimeMs) {
            // A later CONNECTED event already superseded this queued DISCONNECTED event.
            return@synchronized false
        }
        confirmedConnections.computeIfPresent(addressKey) { _, connectedAt ->
            if (pending.eventElapsedRealtimeMs > connectedAt) null else connectedAt
        }
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_store_unreadable", "Existing encrypted A2 deadlines are unreadable; source retained")
            return false
        }
        val updated = mergePending(records, pending)
        val effectivePending = updated.first { it.address.equals(addressKey, ignoreCase = true) }
        var persisted = write(prefs, updated)
        if (!persisted) persisted = write(prefs, updated)
        if (!persisted) {
            LocalTamperHistory.record(context, "a2_deadline_persist_failed", "Encrypted A2 disconnect deadline could not be committed after one retry")
            return false
        }
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = pendingIntent(context, effectivePending.address, namespace)
        val deadline = effectivePending.deadlineElapsedRealtimeMs.coerceAtLeast(android.os.SystemClock.elapsedRealtime())
        val exactAllowed = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        if (!exactAllowed) {
            LocalTamperHistory.record(context, "a2_exact_alarm_access_missing", "Using the inexact idle-aware A2 deadline fallback; Android may delay delivery")
        }
        var lastFailure: Throwable? = null
        for (attempt in 1..2) {
            val scheduled = runCatching {
                if (exactAllowed) {
                    try {
                        manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, operation)
                    } catch (_: SecurityException) {
                        manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, operation)
                    }
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, operation)
                }
            }
            if (scheduled.isSuccess) return@synchronized true
            lastFailure = scheduled.exceptionOrNull()
        }
        LocalTamperHistory.record(
            context, "a2_deadline_schedule_failed",
            "AlarmManager rejected the A2 grace deadline after one retry: ${lastFailure?.javaClass?.simpleName}: ${lastFailure?.message}; encrypted pending record retained for retry"
        )
        // Keep the durable source. A caller can retry immediately, and monitor
        // startup can re-arm this deadline after a process restart.
        false
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

    /**
     * A confirmed ACL_CONNECTED event must suppress an older disconnect deadline
     * even when encrypted registry persistence fails. The in-process timestamp
     * also closes the race with a callback already executing in this process.
     */
    fun cancelAfterConfirmedConnection(
        context: Context,
        address: String,
        connectedAtElapsedRealtimeMs: Long
    ): Boolean = synchronized(lock) {
        if (!TrustedBluetoothRegistry.isValidAddress(address) || connectedAtElapsedRealtimeMs <= 0L) return false
        val key = address.uppercase()
        confirmedConnections.merge(key, connectedAtElapsedRealtimeMs) { old, next -> maxOf(old, next) }
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: return false
        val matching = records.firstOrNull { it.address.equals(address, ignoreCase = true) }
            ?: return true
        // An out-of-order older CONNECTED broadcast must not cancel a newer DISCONNECTED event.
        if (matching.eventElapsedRealtimeMs > connectedAtElapsedRealtimeMs) return true
        val remaining = records.filterNot { it.address.equals(address, ignoreCase = true) }
        var removed = write(prefs, remaining)
        if (!removed) removed = write(prefs, remaining)
        val manager = context.getSystemService(AlarmManager::class.java)
        if (!removed) {
            // The observed reconnect is authoritative for this older deadline.
            // Cancel its OS callback rather than risk a false separation alarm;
            // the stale encrypted record is inert until a later disconnect replaces it.
            manager.cancel(pendingIntent(context, address))
            LocalTamperHistory.record(context, "a2_reconnect_cancel_persist_failed", "Reconnect was observed but encrypted deadline removal failed; its older OS callback was cancelled to prevent a false alarm")
            return false
        }
        manager.cancel(pendingIntent(context, address))
        true
    }

    fun wasConfirmedConnectedAfter(pending: Pending): Boolean =
        wasConfirmedConnectedAfter(pending.address, pending.eventElapsedRealtimeMs)

    fun wasConfirmedConnectedAfter(address: String, disconnectEventElapsedRealtimeMs: Long): Boolean =
        (confirmedConnections[address.uppercase()] ?: Long.MIN_VALUE) >= disconnectEventElapsedRealtimeMs

    /**
     * Re-arm encrypted pending deadlines after a same-boot monitor/process restart.
     * Invalid or obsolete records are removed only after their state is revalidated.
     */
    fun reschedulePending(context: Context): Int = synchronized(lock) {
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_recovery_unreadable", "Pending A2 deadlines could not be read during monitor recovery; encrypted source retained")
            return 0
        }
        if (records.isEmpty()) return 0
        val nowElapsed = ProtectionClock.elapsedRealtime()
        val bootCount = ProtectionClock.bootCount(context)
        val state = ProtectionStore(context).read()
        val enabled = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
        var recovered = 0
        records.forEach { pending ->
            val valid = enabled &&
                state.mode == ProtectionMode.ARMED &&
                ProtectionStore(context).isA2EventAfterLastInvalidation(
                    pending.eventElapsedRealtimeMs, pending.bootCount
                ) &&
                A2DisconnectPolicy.isSameBoot(pending.bootCount, bootCount, pending.eventElapsedRealtimeMs, nowElapsed) &&
                TrustedBluetoothRegistry.isTrusted(prefs, pending.address) &&
                !TrustedBluetoothRegistry.isBaselineConnected(prefs, pending.address) &&
                A2DisconnectPolicy.isPendingDisconnectCurrent(
                    pending.monitorRevision,
                    TrustedBluetoothRegistry.revision(prefs),
                    pending.deviceRevision,
                    TrustedBluetoothRegistry.deviceRevision(prefs, pending.address)
                )
            if (!valid) {
                cancelInternal(context, pending.address, pending.eventElapsedRealtimeMs, ProtectionMonitorService.PREFS, null)
            } else {
                val scheduled = scheduleInternal(context, pending, ProtectionMonitorService.PREFS, null)
                val retried = scheduled || scheduleRetry(context, pending, 1_000L)
                if (retried) recovered += 1
                else LocalTamperHistory.record(
                    context, "a2_deadline_recovery_failed",
                    "A pending trusted-device deadline remains encrypted but AlarmManager rejected recovery for accessory " + pending.address
                )
            }
        }
        recovered
    }

    /** Retry a consumed one-shot deadline without extending the original grace period. */
    fun scheduleRetry(context: Context, pending: Pending, delayMs: Long = 1_000L): Boolean =
        synchronized(lock) {
            if (delayMs !in 250L..60_000L) return false
            val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
            val records = read(prefs) ?: return false
            val current = records.firstOrNull {
                it.address.equals(pending.address, ignoreCase = true) &&
                    it.eventElapsedRealtimeMs == pending.eventElapsedRealtimeMs
            } ?: return false
            if (current != pending) return false
            return runCatching {
                val manager = context.getSystemService(AlarmManager::class.java)
                val operation = pendingIntent(context, pending.address)
                val triggerAt = android.os.SystemClock.elapsedRealtime() + delayMs
                if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
                    try {
                        manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
                    } catch (_: SecurityException) {
                        manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
                    }
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, operation)
                }
                true
            }.onFailure {
                LocalTamperHistory.record(context, "a2_deadline_retry_failed", it.javaClass.simpleName + ": " + it.message)
            }.getOrDefault(false)
        }

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
        // Commit removal before cancelling the OS callback. If persistence fails,
        // leave the callback scheduled: it will revalidate trust, connection and
        // protection state instead of becoming an orphaned durable deadline.
        val remaining = records.filterNot { it.address.equals(address, true) }
        var removed = write(prefs, remaining)
        if (!removed) removed = write(prefs, remaining)
        if (!removed) {
            LocalTamperHistory.record(context, "a2_deadline_cancel_persist_failed", "Reconnect cancellation could not commit the encrypted deadline removal; the scheduled callback was retained for safe state revalidation")
            return false
        }
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, address, namespace))
        return true
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
        // Remove the durable source first. If that commit fails, preserve all
        // AlarmManager callbacks so they can still revalidate against the source.
        var cleared = prefs.edit().remove(KEY_PENDING).commit()
        if (!cleared) cleared = prefs.edit().remove(KEY_PENDING).commit()
        if (!cleared) {
            LocalTamperHistory.record(context, "a2_pending_clear_failed", "Pending trusted-device deadlines could not be cleared after one retry; scheduled callbacks were retained")
            return false
        }
        val manager = context.getSystemService(AlarmManager::class.java)
        records.forEach { manager.cancel(pendingIntent(context, it.address, namespace)) }
        return true
    }

    /** Replace only this accessory's timer while preserving every other device's record. */
    internal fun mergePending(records: List<Pending>, pending: Pending): List<Pending> {
        val current = records.firstOrNull { it.address.equals(pending.address, ignoreCase = true) }
        val selected = if (current != null &&
            current.eventElapsedRealtimeMs > pending.eventElapsedRealtimeMs
        ) current else pending
        return records.filterNot { it.address.equals(pending.address, ignoreCase = true) } + selected
    }

    private fun read(prefs: android.content.SharedPreferences): List<Pending>? {
        val raw = when (val value = SafePreferences.readString(prefs, KEY_PENDING, null)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return null
        } ?: return emptyList()
        return runCatching {
            val decoded = if (LocalPreferenceCipher.isEncrypted(raw)) {
                LocalPreferenceCipher.decrypt(KEY_PENDING, raw)
            } else {
                val encrypted = LocalPreferenceCipher.encrypt(KEY_PENDING, raw)
                check(commitWithOneRetry { prefs.edit().putString(KEY_PENDING, encrypted) })
                raw
            }
            val records = decoded.lineSequence().filter { it.isNotBlank() }.map { line ->
                val fields = line.split('|')
                require(fields.size == 6 && TrustedBluetoothRegistry.isValidAddress(fields[0]))
                val record = Pending(
                    fields[0], fields[1].toLong(), fields[2].toLong(),
                    fields[3].toInt(), fields[4].toLong(), fields[5].toLong()
                )
                require(
                    record.eventEpochMs > 0L && record.eventElapsedRealtimeMs > 0L &&
                        record.bootCount >= -1 && record.monitorRevision >= 0L && record.deviceRevision >= 0L
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
        if (records.isEmpty()) return commitWithOneRetry { prefs.edit().remove(KEY_PENDING) }
        val raw = records.distinctBy { it.address.uppercase() }.joinToString("\n") {
            "${it.address.uppercase()}|${it.eventEpochMs}|${it.eventElapsedRealtimeMs}|${it.bootCount}|${it.monitorRevision}|${it.deviceRevision}"
        }
        val encrypted = runCatching { LocalPreferenceCipher.encrypt(KEY_PENDING, raw) }.getOrNull() ?: return false
        return commitWithOneRetry { prefs.edit().putString(KEY_PENDING, encrypted) }
    }

    private fun commitWithOneRetry(editorFactory: () -> android.content.SharedPreferences.Editor): Boolean {
        val first = runCatching { editorFactory().commit() }.getOrDefault(false)
        if (first) return true
        return runCatching { editorFactory().commit() }.getOrDefault(false)
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
