package com.thiefalertsequre.app

import android.content.Context
import java.util.concurrent.TimeUnit

/** Single authority for charger transitions; snapshots alone never start countdowns. */
object PowerTransitionCoordinator {
    private const val PREFS = "charger_transition_v1"
    private const val KNOWN = "baseline_known"
    private const val CONNECTED = "baseline_connected"
    private val lock = Any()

    fun initializeBaseline(context: Context) = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baselineKnown = prefs.getBoolean(KNOWN, false)

        // Migration must run even when Android has not supplied a battery snapshot.
        // Old COUNTDOWN states have no trusted unplug-event provenance.
        if (!baselineKnown) {
            val store = ProtectionStore(context)
            val prior = store.read()
            if (prior.mode == ProtectionMode.COUNTDOWN) {
                store.update { current ->
                    if (current.mode == ProtectionMode.COUNTDOWN) {
                        ProtectionEngine.chargerConnected(current, System.currentTimeMillis())
                    } else current
                }
                AlarmScheduler.cancel(context)
                LocalTamperHistory.record(context, "legacy_countdown_discarded", "Unverifiable pre-transition countdown discarded during migration")
            }
        }

        val connected = ExternalPowerState.readConnected(context) ?: return@synchronized
        if (!baselineKnown) {
            prefs.edit().putBoolean(CONNECTED, connected).putBoolean(KNOWN, true).commit()
            LocalTamperHistory.record(context, "power_baseline_initialized", "Initial state recorded; no transition inferred")
        }

        // A connected snapshot can safely cancel a countdown and arm the next
        // cycle. A disconnected snapshot is never accepted as an unplug event.
        if (connected) {
            confirmConnectedAndArm(context, "Connected power snapshot reconciled")
        }
        Unit
    }

    fun onPowerConnected(context: Context): ProtectionState =
        confirmConnectedAndArm(context, "System power-connected transition received")

    /** Used only after a live connected-state confirmation, including post-auth recovery. */
    fun confirmConnectedAndArm(context: Context, reason: String): ProtectionState = synchronized(lock) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(CONNECTED, true).putBoolean(KNOWN, true).commit()
        val state = ProtectionStore(context).update { current ->
            if (current.mode == ProtectionMode.ARMED || current.mode == ProtectionMode.ALARM_TRIGGERED) {
                current
            } else {
                ProtectionEngine.chargerConnected(current, System.currentTimeMillis())
            }
        }
        if (state.mode != ProtectionMode.ALARM_TRIGGERED) {
            AlarmScheduler.cancel(context)
        }
        LocalTamperHistory.record(context, "charger_connected_confirmed", reason)
        state
    }

    fun onPowerDisconnected(context: Context) = synchronized(lock) {
        // Ensure pre-upgrade countdowns are sanitized before considering this event.
        initializeBaseline(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KNOWN, false) || !prefs.getBoolean(CONNECTED, false)) {
            val observed = ExternalPowerState.readConnected(context)
            if (observed != null) prefs.edit().putBoolean(CONNECTED, observed).putBoolean(KNOWN, true).commit()
            LocalTamperHistory.record(context, "charger_unplug_rejected", "No previously confirmed connected baseline")
            return@synchronized ProtectionStore(context).read()
        }
        repeat(3) { index ->
            val observed = ExternalPowerState.readConnected(context)
            if (observed != false) {
                if (observed == true) prefs.edit().putBoolean(CONNECTED, true).commit()
                LocalTamperHistory.record(context, "charger_unplug_rejected", "Power state connected/unknown during sample ${index + 1}")
                return@synchronized ProtectionStore(context).read()
            }
            if (index < 2) TimeUnit.MILLISECONDS.sleep(350)
        }
        // Final live-state gate immediately before committing the countdown.
        // This catches a reconnect racing the tail of the debounce samples.
        val finalConnected = ExternalPowerState.readConnected(context)
        if (finalConnected != false) {
            if (finalConnected == true) prefs.edit().putBoolean(CONNECTED, true).commit()
            LocalTamperHistory.record(context, "charger_unplug_rejected", "Final power-state confirmation was connected or unknown")
            return@synchronized ProtectionStore(context).read()
        }
        val current = ProtectionStore(context).read()
        if (current.mode != ProtectionMode.ARMED) {
            prefs.edit().putBoolean(CONNECTED, false).commit()
            LocalTamperHistory.record(context, "charger_unplug_ignored", "Confirmed unplug while protection was not armed")
            return@synchronized current
        }
        val next = ProtectionStore(context).update { old ->
            if (old.mode == ProtectionMode.ARMED) ProtectionEngine.chargerDisconnected(
                old, System.currentTimeMillis(), ProtectionClock.elapsedRealtime(), ProtectionClock.bootCount(context)
            ) else old
        }
        prefs.edit().putBoolean(CONNECTED, false).commit()
        if (next.mode == ProtectionMode.COUNTDOWN) {
            AlarmScheduler.schedule(context, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount)
            LocalTamperHistory.record(context, "charger_countdown_started", "OS unplug event confirmed by three disconnected snapshots")
        }
        next
    }
}
