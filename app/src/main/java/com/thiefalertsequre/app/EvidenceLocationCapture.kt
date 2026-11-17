package com.thiefalertsequre.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/** One-shot, owner-requested location snapshot. No background tracking or polling. */
object EvidenceLocationCapture {
    data class Snapshot(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float?,
        val provider: String,
        val fixAgeMs: Long,
        val classification: String
    ) {
        fun toVaultDetail(): String =
            "lat=$latitude,lon=$longitude,accuracyM=${accuracyMeters ?: "unknown"},provider=$provider,fixAgeMs=$fixAgeMs,class=$classification"
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun readLastKnown(context: Context): Snapshot? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val candidates = runCatching {
            manager.getProviders(true).mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
        }.getOrDefault(emptyList())
        val best = candidates.maxByOrNull { it.elapsedRealtimeNanos } ?: return null
        val ageMs = TimeUnit.NANOSECONDS.toMillis(
            (SystemClock.elapsedRealtimeNanos() - best.elapsedRealtimeNanos).coerceAtLeast(0L)
        )
        val accuracy = if (best.hasAccuracy()) best.accuracy else null
        return Snapshot(
            latitude = best.latitude,
            longitude = best.longitude,
            accuracyMeters = accuracy,
            provider = best.provider ?: "unknown",
            fixAgeMs = ageMs,
            classification = classifyFixAge(ageMs)
        )
    }

    const val RECENT_FIX_MAX_AGE_MS = 120_000L

    fun classifyFixAge(ageMs: Long): String =
        if (ageMs in 0..RECENT_FIX_MAX_AGE_MS) "RECENT_CACHED_FIX" else "STALE_CACHED_FIX"
}
