package com.thiefalertsequre.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
                runCatching { manager.getLastKnownLocation(provider) }
                    .getOrNull()
                    ?.takeIf(::isValidLocation)
            }
        }.getOrDefault(emptyList())
        val best = candidates.maxByOrNull { it.elapsedRealtimeNanos } ?: return null
        val ageMs = TimeUnit.NANOSECONDS.toMillis(
            (SystemClock.elapsedRealtimeNanos() - best.elapsedRealtimeNanos).coerceAtLeast(0L)
        )
        val accuracy = best.accuracy.takeIf {
            best.hasAccuracy() && it.isFinite() && it >= 0f
        }
        return Snapshot(
            latitude = best.latitude,
            longitude = best.longitude,
            accuracyMeters = accuracy,
            provider = best.provider ?: "unknown",
            fixAgeMs = ageMs,
            classification = classifyFixAge(ageMs)
        )
    }


    /**
     * One-shot current-location request from a visible owner action. The request
     * is bounded to 20 seconds and never starts a background location service.
     */
    @Suppress("MissingPermission", "DEPRECATION")
    fun requestCurrent(context: Context, onResult: (Snapshot?) -> Unit) {
        if (!hasPermission(context)) {
            onResult(null)
            return
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: run {
            onResult(null)
            return
        }
        // Try every enabled location provider. A GPS-only request can time out
        // indoors even when the network provider could return a useful fix.
        val providers = enabledProviders(
            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false),
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        )
        if (providers.isEmpty()) {
            onResult(null)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean(false)
        val pendingProviders = AtomicInteger(providers.size)
        val listeners = mutableListOf<LocationListener>()
        val cancellations = mutableListOf<CancellationSignal>()
        var timeout: Runnable? = null

        fun finish(location: Location?) {
            if (!completed.compareAndSet(false, true)) return
            timeout?.let(handler::removeCallbacks)
            listeners.forEach { listener -> runCatching { manager.removeUpdates(listener) } }
            cancellations.forEach { signal -> runCatching { signal.cancel() } }
            onResult(location?.takeIf(::isValidLocation)?.let { snapshot(it, "CURRENT_REQUEST_RESULT") })
        }

        fun providerFinished(location: Location?) {
            if (location != null && isValidLocation(location)) {
                finish(location)
            } else if (pendingProviders.decrementAndGet() == 0) {
                finish(null)
            }
        }

        val timeoutRunnable = Runnable { finish(null) }
        timeout = timeoutRunnable
        handler.postDelayed(timeoutRunnable, CURRENT_LOCATION_TIMEOUT_MS)

        providers.forEach { provider ->
            if (completed.get()) return@forEach
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val cancellation = CancellationSignal()
                    cancellations += cancellation
                    manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location ->
                        providerFinished(location)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) = providerFinished(location)
                        override fun onStatusChanged(providerName: String?, status: Int, extras: android.os.Bundle?) = Unit
                        override fun onProviderEnabled(providerName: String) = Unit
                        override fun onProviderDisabled(providerName: String) = Unit
                    }
                    listeners += listener
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            } catch (_: Exception) {
                providerFinished(null)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isValidLocation(location: Location): Boolean {
        val elapsed = location.elapsedRealtimeNanos
        val now = SystemClock.elapsedRealtimeNanos()
        val ageNanos = now - elapsed
        val notFutureDated = elapsed > 0L && ageNanos >= -TimeUnit.SECONDS.toNanos(1)
        val notMock = if (Build.VERSION.SDK_INT >= 31) !location.isMock else !location.isFromMockProvider
        return notMock && notFutureDated &&
            location.latitude.isFinite() &&
            location.longitude.isFinite() &&
            location.latitude in -90.0..90.0 &&
            location.longitude in -180.0..180.0
    }

    internal fun enabledProviders(gpsEnabled: Boolean, networkEnabled: Boolean): List<String> =
        buildList {
            if (gpsEnabled) add(LocationManager.GPS_PROVIDER)
            if (networkEnabled) add(LocationManager.NETWORK_PROVIDER)
        }

    private fun snapshot(location: Location, source: String): Snapshot {
        val ageMs = TimeUnit.NANOSECONDS.toMillis(
            (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos).coerceAtLeast(0L)
        )
        return Snapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy() && location.accuracy.isFinite() && location.accuracy >= 0f) location.accuracy else null,
            provider = location.provider ?: "unknown",
            fixAgeMs = ageMs,
            classification = if (ageMs <= RECENT_FIX_MAX_AGE_MS) "${source}_RECENT" else "${source}_STALE"
        )
    }

    const val CURRENT_LOCATION_TIMEOUT_MS = 20_000L

    const val RECENT_FIX_MAX_AGE_MS = 120_000L

    fun classifyFixAge(ageMs: Long): String =
        if (ageMs in 0..RECENT_FIX_MAX_AGE_MS) "RECENT_CACHED_FIX" else "STALE_CACHED_FIX"
}
