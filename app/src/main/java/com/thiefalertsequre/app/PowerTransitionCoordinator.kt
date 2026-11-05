package com.thiefalertsequre.app

import android.content.Context

/** Single authority for charger transitions; snapshots alone never start countdowns. */
object PowerTransitionCoordinator {
    private const val PREFS = "charger_transition_v1"
    private const val KNOWN = "baseline_known"
    private const val CONNECTED = "baseline_connected"
    private const val GENERATION = "transition_generation"
    private val lock = Any()

    fun initializeBaseline(context: Context) = synchronized(lock) {
        initializeBaselineLocked(context)
    }

    private fun initializeBaselineLocked(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baselineKnown = prefs.getBoolean(KNOWN, false)

        // Old COUNTDOWN states have no trusted unplug-event provenance.
        if (!baselineKnown) {
            val store = ProtectionStore(context)
            if (store.read().mode == ProtectionMode.COUNTDOWN) {
                store.update { current ->
                    if (current.mode == ProtectionMode.COUNTDOWN) {
                        ProtectionEngine.chargerConnected(current, System.currentTimeMillis())
                    } else current
                }
                AlarmScheduler.cancel(context)
                LocalTamperHistory.record(context, "legacy_countdown_discarded", "Unverifiable pre-transition countdown discarded during migration")
            }
        }

        val connected = ExternalPowerState.readConnected(context) ?: return
        if (!baselineKnown) {
            prefs.edit()
                .putBoolean(CONNECTED, connected)
                .putBoolean(KNOWN, true)
                .putLong(GENERATION, prefs.getLong(GENERATION, 0L) + 1L)
                .commit()
            LocalTamperHistory.record(context, "power_baseline_initialized", "Initial state recorded; no unplug transition inferred")
        }

        // Connected is safe evidence to arm/cancel; disconnected is not evidence
        // that a new physical unplug happened.
        if (connected) confirmConnectedAndArmLocked(context, "Connected power snapshot reconciled")
    }

    fun onPowerConnected(context: Context): ProtectionState =
        confirmConnectedAndArm(context, "System power-connected transition received")

    /** Used only after a live connected-state confirmation, including post-auth recovery. */
    fun confirmConnectedAndArm(context: Context, reason: String): ProtectionState = synchronized(lock) {
        confirmConnectedAndArmLocked(context, reason)
    }

    private fun confirmConnectedAndArmLocked(context: Context, reason: String): ProtectionState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(CONNECTED, true)
            .putBoolean(KNOWN, true)
            .putLong(GENERATION, prefs.getLong(GENERATION, 0L) + 1L)
            .commit()

        val state = ProtectionStore(context).update { current ->
            if (current.mode == ProtectionMode.ARMED || current.mode == ProtectionMode.ALARM_TRIGGERED) {
                current
            } else {
                ProtectionEngine.chargerConnected(current, System.currentTimeMillis())
            }
        }
        if (state.mode != ProtectionMode.ALARM_TRIGGERED) AlarmScheduler.cancel(context)
        LocalTamperHistory.record(context, "charger_connected_confirmed", reason)
        return state
    }

    /**
     * A disconnect event is only a candidate until snapshots confirm unplug.
     * Sampling happens outside the coordinator lock. A generation token ensures
     * a concurrent connect event invalidates this candidate before it can commit.
     */
    fun onPowerDisconnected(context: Context): ProtectionState {
        val appContext = context.applicationContext
        val candidateGeneration = synchronized(lock) {
            initializeBaselineLocked(appContext)
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KNOWN, false) || !prefs.getBoolean(CONNECTED, false)) {
                LocalTamperHistory.record(appContext, "charger_unplug_rejected", "No previously confirmed connected baseline")
                return@synchronized null
            }
            val nextGeneration = prefs.getLong(GENERATION, 0L) + 1L
            prefs.edit()
                .putBoolean(CONNECTED, false)
                .putLong(GENERATION, nextGeneration)
                .commit()
            nextGeneration
        } ?: return ProtectionStore(appContext).read()

        repeat(3) { index ->
            val observed = ExternalPowerState.readConnected(appContext)
            if (observed != false) {
                if (observed == true) {
                    confirmConnectedAndArm(appContext, "Connected snapshot invalidated unplug candidate")
                }
                LocalTamperHistory.record(
                    appContext, "charger_unplug_rejected",
                    "Power state connected/unknown during sample ${index + 1}"
                )
                return ProtectionStore(appContext).read()
            }
            if (index < 2) {
                try {
                    Thread.sleep(350L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    LocalTamperHistory.record(appContext, "charger_unplug_rejected", "Debounce sampling interrupted")
                    return ProtectionStore(appContext).read()
                }
            }
        }

        return synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getLong(GENERATION, -1L) != candidateGeneration ||
                prefs.getBoolean(CONNECTED, true)
            ) {
                LocalTamperHistory.record(appContext, "stale_power_candidate_rejected", "A newer power event superseded this unplug candidate")
                return@synchronized ProtectionStore(appContext).read()
            }

            val finalConnected = ExternalPowerState.readConnected(appContext)
            if (finalConnected != false) {
                if (finalConnected == true) {
                    confirmConnectedAndArmLocked(appContext, "Final connected snapshot invalidated unplug candidate")
                } else {
                    LocalTamperHistory.record(appContext, "charger_unplug_rejected", "Final power-state sample was unknown")
                }
                return@synchronized ProtectionStore(appContext).read()
            }

            val store = ProtectionStore(appContext)
            val current = store.read()
            if (current.mode != ProtectionMode.ARMED) {
                LocalTamperHistory.record(appContext, "charger_unplug_ignored", "Confirmed unplug while protection was not armed")
                return@synchronized current
            }

            val next = store.update { old ->
                if (old.mode == ProtectionMode.ARMED) ProtectionEngine.chargerDisconnected(
                    old, System.currentTimeMillis(), ProtectionClock.elapsedRealtime(), ProtectionClock.bootCount(appContext)
                ) else old
            }
            if (next.mode == ProtectionMode.COUNTDOWN) {
                AlarmScheduler.schedule(appContext, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount)
                LocalTamperHistory.record(appContext, "charger_countdown_started", "OS unplug event confirmed by three disconnected snapshots")
            }
            next
        }
    }
}
