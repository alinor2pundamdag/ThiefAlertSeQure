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
    private const val ACTION_FALLBACK = "com.thiefalertsequre.A2_DISCONNECT_DEADLINE_FALLBACK"
    private const val URI_PREFIX = "thiefalertsequre://a2-disconnect/"
    private const val REQUEST_BASE = 20_000

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

    fun schedule(context: Context, pending: Pending): Boolean = A2DisconnectStateLock.withLock {
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val currentBootCount = ProtectionClock.bootCount(context)
        if (!A2DisconnectPolicy.isSameBoot(
                pending.bootCount, currentBootCount, pending.eventElapsedRealtimeMs, nowElapsed
            )
        ) {
            LocalTamperHistory.record(
                context, "a2_deadline_boot_identity_unavailable",
                "A durable A2 deadline was not scheduled because its event could not be verified against the current boot; the in-process timer remains available while the service is alive"
            )
            return@withLock false
        }
        if (!TrustedBluetoothRegistry.isValidAddress(pending.address) ||
            pending.eventEpochMs <= 0L || pending.eventElapsedRealtimeMs <= 0L ||
            pending.monitorRevision < 0L || pending.deviceRevision < 0L
        ) {
            LocalTamperHistory.record(context, "a2_deadline_metadata_rejected", "Invalid trusted-device deadline metadata was rejected")
            return@withLock false
        }
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_store_unreadable", "Existing encrypted A2 deadlines are unreadable; source retained")
            return@withLock false
        }
        val updated = mergePending(records, pending)
        if (!write(prefs, updated)) {
            LocalTamperHistory.record(context, "a2_deadline_persist_failed", "Encrypted A2 disconnect deadline could not be committed")
            return@withLock false
        }
        val scheduled = scheduleCallbacks(context, pending)
        if (!scheduled) {
            // Keep the encrypted pending record. A later service/app start can retry
            // AlarmManager registration; deleting it here would lose process-death recovery.
            LocalTamperHistory.record(
                context, "a2_deadline_callbacks_unavailable",
                "The encrypted deadline remains persisted for a later reschedule; Android has not accepted a process-absent callback yet"
            )
        }
        scheduled
    }

    /** Reinstall only deadlines whose monotonic event belongs to the current boot. */
    fun rescheduleAll(context: Context): Boolean =
        rescheduleAll(context, ProtectionClock.bootCount(context), android.os.SystemClock.elapsedRealtime())

    /**
     * Explicit clock inputs keep reboot reconciliation deterministic in instrumentation tests.
     * A stale-boot record is cancelled and removed rather than reinstalled against a reset clock.
     */
    internal fun rescheduleAll(
        context: Context,
        currentBootCount: Int,
        currentElapsedRealtimeMs: Long
    ): Boolean = A2DisconnectStateLock.withLock {
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_reschedule_store_unreadable", "Persisted A2 deadlines could not be read for rescheduling; source retained")
            return@withLock false
        }
        if (records.isEmpty()) return@withLock true
        val retained = mutableListOf<Pending>()
        var allScheduled = true
        var removedStale = false

        records.forEach { pending ->
            when {
                pending.bootCount < 0 -> {
                    cancelCallbacks(context, pending.address)
                    removedStale = true
                    LocalTamperHistory.record(
                        context, "a2_deadline_invalid_boot_removed",
                        "An A2 deadline without a verifiable boot identity was removed without triggering an alarm"
                    )
                }
                currentBootCount < 0 -> {
                    retained += pending
                    allScheduled = false
                    LocalTamperHistory.record(
                        context, "a2_deadline_boot_identity_unavailable",
                        "A persisted A2 deadline was retained but not rescheduled because the current boot identity is unavailable"
                    )
                }
                !A2DisconnectPolicy.isSameBoot(
                    pending.bootCount, currentBootCount,
                    pending.eventElapsedRealtimeMs, currentElapsedRealtimeMs
                ) -> {
                    cancelCallbacks(context, pending.address)
                    removedStale = true
                    LocalTamperHistory.record(
                        context, "a2_deadline_stale_boot_removed",
                        "A persisted A2 deadline from another boot or a future monotonic timestamp was discarded"
                    )
                }
                else -> {
                    retained += pending
                    if (!scheduleCallbacks(context, pending)) allScheduled = false
                }
            }
        }

        if (removedStale && !write(prefs, retained)) {
            allScheduled = false
            LocalTamperHistory.record(
                context, "a2_deadline_stale_boot_cleanup_failed",
                "Stale A2 callbacks were cancelled, but the encrypted schedule could not be rewritten"
            )
        }
        allScheduled
    }

    /** Re-schedule a still-current expired deadline after a transient state-store commit failure. */
    fun scheduleRetry(context: Context, pending: Pending, delayMs: Long): Boolean = A2DisconnectStateLock.withLock {
        if (delayMs < 0L) return@withLock false
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val currentBootCount = ProtectionClock.bootCount(context)
        if (!A2DisconnectPolicy.isSameBoot(
                pending.bootCount, currentBootCount, pending.eventElapsedRealtimeMs, nowElapsed
            )
        ) return@withLock false
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_retry_store_unreadable", "A2 retry could not read the encrypted deadline; source retained")
            return@withLock false
        }
        val current = records.firstOrNull {
            it.address.equals(pending.address, true) &&
                it.eventElapsedRealtimeMs == pending.eventElapsedRealtimeMs &&
                it.bootCount == pending.bootCount &&
                it.monitorRevision == pending.monitorRevision &&
                it.deviceRevision == pending.deviceRevision
        } ?: return@withLock false
        val retryAt = A2DisconnectPolicy.retryDeadlineElapsedRealtimeMs(
            current.deadlineElapsedRealtimeMs, nowElapsed, delayMs.coerceAtLeast(250L)
        )
        val scheduled = scheduleCallbacks(context, current, retryAt)
        if (!scheduled) {
            LocalTamperHistory.record(
                context, "a2_deadline_retry_schedule_failed",
                "The current A2 deadline remains persisted, but Android did not accept its retry callback"
            )
        }
        scheduled
    }

    fun find(context: Context, token: String): Pending? = A2DisconnectStateLock.withLock {
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_read_failed", "A2 deadline callback could not read the encrypted schedule; source retained")
            return@withLock null
        }
        val pending = records.firstOrNull { tokenFor(it.address) == token } ?: return@withLock null
        if (pending.eventElapsedRealtimeMs > android.os.SystemClock.elapsedRealtime()) {
            LocalTamperHistory.record(context, "a2_future_deadline_rejected", "Future-dated A2 deadline metadata was rejected")
            return@withLock null
        }
        pending
    }

    fun cancel(context: Context, address: String, expectedEventElapsedMs: Long? = null): Boolean = A2DisconnectStateLock.withLock {
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_deadline_cancel_store_unreadable", "A2 deadline cancellation could not read the encrypted schedule; source retained")
            return@withLock false
        }
        val matching = records.firstOrNull { it.address.equals(address, true) }
        if (matching == null) {
            cancelCallbacks(context, address)
            return@withLock true
        }
        if (expectedEventElapsedMs != null && matching.eventElapsedRealtimeMs != expectedEventElapsedMs) {
            // A stale callback must not cancel a newer disconnect for the same accessory.
            return@withLock true
        }
        cancelCallbacks(context, address)
        val removed = write(prefs, records.filterNot { it.address.equals(address, true) })
        if (!removed) {
            LocalTamperHistory.record(context, "a2_deadline_cancel_persist_failed", "Reconnect cancellation removed the AlarmManager callback but could not commit the updated encrypted deadline list")
        }
        removed
    }

    fun clearAll(context: Context): Boolean = A2DisconnectStateLock.withLock {
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: run {
            LocalTamperHistory.record(context, "a2_pending_clear_store_unreadable", "A2 deadline cleanup could not read the encrypted schedule; source retained")
            return@withLock false
        }
        records.forEach { cancelCallbacks(context, it.address) }
        val cleared = prefs.edit().remove(KEY_PENDING).commit()
        if (!cleared) {
            LocalTamperHistory.record(context, "a2_pending_clear_failed", "Pending trusted-device deadlines could not be cleared")
        }
        cleared
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
                check(prefs.edit().putString(KEY_PENDING, LocalPreferenceCipher.encrypt(KEY_PENDING, raw)).commit())
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
        if (records.isEmpty()) return prefs.edit().remove(KEY_PENDING).commit()
        val raw = records.distinctBy { it.address.uppercase() }.joinToString("\n") {
            "${it.address.uppercase()}|${it.eventEpochMs}|${it.eventElapsedRealtimeMs}|${it.bootCount}|${it.monitorRevision}|${it.deviceRevision}"
        }
        return runCatching {
            prefs.edit().putString(KEY_PENDING, LocalPreferenceCipher.encrypt(KEY_PENDING, raw)).commit()
        }.getOrDefault(false)
    }

    internal fun mergePending(records: List<Pending>, pending: Pending): List<Pending> =
        records.filterNot { it.address.equals(pending.address, ignoreCase = true) } + pending

    private fun tokenFor(address: String): String = MessageDigest.getInstance("SHA-256")
        .digest(address.trim().uppercase().toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun scheduleCallbacks(
        context: Context,
        pending: Pending,
        minimumTriggerAtElapsedRealtimeMs: Long? = null
    ): Boolean = runCatching {
        val manager = context.getSystemService(AlarmManager::class.java)
        val hadExactOperation = hasPendingIntent(context, pending.address, fallback = false)
        val hadFallbackOperation = hasPendingIntent(context, pending.address, fallback = true)
        val exactOperation = pendingIntent(context, pending.address, fallback = false)
        val fallbackOperation = pendingIntent(context, pending.address, fallback = true)
        val deadline = maxOf(
            pending.deadlineElapsedRealtimeMs,
            android.os.SystemClock.elapsedRealtime(),
            minimumTriggerAtElapsedRealtimeMs ?: 0L
        )
        val exactPermissionState: Boolean? = if (Build.VERSION.SDK_INT < 31) {
            true
        } else {
            runCatching { manager.canScheduleExactAlarms() }.getOrNull()
        }
        val exactAllowed = A2DisconnectPolicy.shouldScheduleExactAlarm(
            Build.VERSION.SDK_INT, exactPermissionState == true
        )

        var exactScheduled = false
        if (exactAllowed) {
            try {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, exactOperation
                )
                exactScheduled = true
            } catch (error: Exception) {
                val permissionAfterFailure: Boolean? = if (Build.VERSION.SDK_INT < 31) {
                    true
                } else {
                    runCatching { manager.canScheduleExactAlarms() }.getOrNull()
                }
                if (AlarmCallbackPolicy.shouldCancelRejectedExactCallback(
                        permissionAfterFailure, hadExactOperation
                    )
                ) {
                    runCatching { cancelPendingIntent(manager, exactOperation) }
                }
                LocalTamperHistory.record(
                    context, "a2_exact_alarm_schedule_failed",
                    error.javaClass.simpleName + ": " + error.message + "; trying the inexact fallback"
                )
            }
        } else {
            if (AlarmCallbackPolicy.shouldCancelRejectedExactCallback(
                    exactPermissionState, hadExactOperation
                )
            ) {
                runCatching { cancelPendingIntent(manager, exactOperation) }
            }
            LocalTamperHistory.record(
                context, "a2_exact_alarm_access_missing",
                "Using the inexact idle-aware A2 deadline fallback; Android may delay delivery"
            )
        }

        var fallbackScheduled = false
        try {
            manager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, fallbackOperation
            )
            fallbackScheduled = true
        } catch (error: Exception) {
            if (!hadFallbackOperation) {
                runCatching { cancelPendingIntent(manager, fallbackOperation) }
            }
            LocalTamperHistory.record(
                context, "a2_inexact_fallback_schedule_failed",
                error.javaClass.simpleName + ": " + error.message
            )
        }

        // Keep a previously accepted callback when a replacement attempt fails.
        if (!AlarmCallbackPolicy.hasUsableDeadlineCallback(
                exactScheduled, fallbackScheduled
            )
        ) {
            throw IllegalStateException("Android rejected both exact and inexact A2 deadline callbacks")
        }
        true
    }.getOrElse { error ->
        LocalTamperHistory.record(
            context, "a2_deadline_schedule_failed",
            error.javaClass.simpleName + ": " + error.message
        )
        false
    }

    private fun cancelCallbacks(context: Context, address: String) {
        val manager = context.getSystemService(AlarmManager::class.java)
        cancelPendingIntent(manager, pendingIntent(context, address, fallback = false))
        cancelPendingIntent(manager, pendingIntent(context, address, fallback = true))
    }

    private fun cancelPendingIntent(manager: AlarmManager, operation: PendingIntent) {
        manager.cancel(operation)
        operation.cancel()
    }

    private fun pendingIntent(context: Context, address: String, fallback: Boolean): PendingIntent {
        val token = tokenFor(address)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + (token.take(7).toLong(16) % 1_000_000L).toInt(),
            Intent(context, AlarmDeadlineReceiver::class.java)
                .setAction(if (fallback) ACTION_FALLBACK else ACTION)
                .setData(Uri.parse(URI_PREFIX + token)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun hasPendingIntent(context: Context, address: String, fallback: Boolean): Boolean =
        A2DisconnectStateLock.withLock {
            if (!TrustedBluetoothRegistry.isValidAddress(address)) return@withLock false
            val token = tokenFor(address)
            val intent = Intent(context, AlarmDeadlineReceiver::class.java)
                .setAction(if (fallback) ACTION_FALLBACK else ACTION)
                .setData(Uri.parse(URI_PREFIX + token))
            val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            PendingIntent.getBroadcast(
                context,
                REQUEST_BASE + (token.take(7).toLong(16) % 1_000_000L).toInt(),
                intent,
                flags
            ) != null
        }

    fun isDeadlineAction(action: String?): Boolean =
        action == ACTION || action == ACTION_FALLBACK

    const val ACTION_DEADLINE = ACTION
    const val ACTION_FALLBACK_DEADLINE = ACTION_FALLBACK
}
