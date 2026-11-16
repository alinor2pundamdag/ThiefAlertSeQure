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

    fun schedule(context: Context, pending: Pending): Boolean {
        if (!TrustedBluetoothRegistry.isValidAddress(pending.address) ||
            pending.eventEpochMs <= 0L || pending.eventElapsedRealtimeMs < 0L
        ) return false
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: return false
        val updated = records.filterNot { it.address.equals(pending.address, true) } + pending
        if (!write(prefs, updated)) return false
        return runCatching {
            val manager = context.getSystemService(AlarmManager::class.java)
            val operation = pendingIntent(context, pending.address)
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
            runCatching { cancel(context, pending.address, pending.eventElapsedRealtimeMs) }
            false
        }
    }

    fun find(context: Context, token: String): Pending? {
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        return read(prefs)?.firstOrNull { tokenFor(it.address) == token }
    }

    fun cancel(context: Context, address: String, expectedEventElapsedMs: Long? = null): Boolean {
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: return false
        val matching = records.firstOrNull { it.address.equals(address, true) }
        if (matching == null || (expectedEventElapsedMs != null &&
                matching.eventElapsedRealtimeMs != expectedEventElapsedMs)
        ) return true
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, address))
        return write(prefs, records.filterNot { it.address.equals(address, true) })
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val records = read(prefs) ?: return
        val manager = context.getSystemService(AlarmManager::class.java)
        records.forEach { manager.cancel(pendingIntent(context, it.address)) }
        if (!prefs.edit().remove(KEY_PENDING).commit()) {
            LocalTamperHistory.record(context, "a2_pending_clear_failed", "Pending trusted-device deadlines could not be cleared")
        }
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
            decoded.lineSequence().filter { it.isNotBlank() }.map { line ->
                val fields = line.split('|')
                require(fields.size == 6 && TrustedBluetoothRegistry.isValidAddress(fields[0]))
                Pending(fields[0], fields[1].toLong(), fields[2].toLong(), fields[3].toInt(), fields[4].toLong(), fields[5].toLong())
            }.toList()
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

    private fun tokenFor(address: String): String = MessageDigest.getInstance("SHA-256")
        .digest(address.trim().uppercase().toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun pendingIntent(context: Context, address: String): PendingIntent {
        val token = tokenFor(address)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + (token.take(7).toLong(16) % 1_000_000L).toInt(),
            Intent(context, AlarmDeadlineReceiver::class.java)
                .setAction(ACTION)
                .setData(Uri.parse(URI_PREFIX + token)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val ACTION_DEADLINE = ACTION
}
