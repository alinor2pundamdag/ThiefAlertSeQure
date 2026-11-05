package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent

/** Single authority for charger transitions; snapshots alone never start countdowns. */
object PowerTransitionCoordinator {
    private const val PREFS = "charger_transition_v1"
    private const val KNOWN = "baseline_known"
    private const val CONNECTED = "baseline_connected"
    private const val BASELINE_BOOT_COUNT = "baseline_boot_count"
    private const val GENERATION = "transition_generation"
    private val lock = Any()

    fun initializeBaseline(context: Context) = synchronized(lock) {
        initializeBaselineLocked(context)
    }

    private fun initializeBaselineLocked(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baselineKnown = prefs.getBoolean(KNOWN, false)
        val currentBoot = ProtectionClock.bootCount(context)
        val priorBoot = prefs.getInt(BASELINE_BOOT_COUNT, -1)
        val newBoot = currentBoot >= 0 && currentBoot != priorBoot

        // Old COUNTDOWN states have no trusted unplug-event provenance. Only
        // discard them on first migration; a valid reboot countdown is recovered
        // by BootReceiver with a fresh grace period.
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
        if (!baselineKnown || newBoot) {
            prefs.edit()
                .putBoolean(CONNECTED, connected)
                .putBoolean(KNOWN, true)
                .putInt(BASELINE_BOOT_COUNT, currentBoot)
                .putLong(GENERATION, prefs.getLong(GENERATION, 0L) + 1L)
                .commit()
            LocalTamperHistory.record(
                context, if (newBoot) "power_baseline_reinitialized_after_boot" else "power_baseline_initialized",
                "Current power state recorded as baseline; no unplug transition inferred"
            )
        }

        // Connected is safe evidence to arm/cancel; disconnected is not evidence
        // that a new physical unplug happened.
        val monitorEnabled = context.getSharedPreferences(
            ProtectionMonitorService.PREFS, Context.MODE_PRIVATE
        ).getBoolean(
            ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        if (connected && monitorEnabled) {
            confirmConnectedAndArmLocked(context, "Connected power snapshot reconciled")
        }
    }

    /** Establish a fresh non-transition baseline when the owner re-enables monitoring. */
    fun resetBaselineToCurrentPower(context: Context, reason: String) = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val connected = ExternalPowerState.readConnected(context)
        val generation = prefs.getLong(GENERATION, 0L) + 1L
        if (connected == null) {
            // Preserve an already-valid countdown; clearing KNOWN would make
            // first-run migration discard it on the monitor's next startup.
            if (ProtectionStore(context).read().mode != ProtectionMode.COUNTDOWN) {
                prefs.edit()
                    .putBoolean(KNOWN, false)
                    .putBoolean(CONNECTED, false)
                    .putInt(BASELINE_BOOT_COUNT, ProtectionClock.bootCount(context))
                    .putLong(GENERATION, generation)
                    .commit()
            } else {
                prefs.edit().putLong(GENERATION, generation).commit()
            }
            LocalTamperHistory.record(context, "power_baseline_unknown", "$reason; no new transition accepted until a reliable power state is available")
            return@synchronized
        }
        prefs.edit()
            .putBoolean(KNOWN, true)
            .putBoolean(CONNECTED, connected)
            .putInt(BASELINE_BOOT_COUNT, ProtectionClock.bootCount(context))
            .putLong(GENERATION, generation)
            .commit()
        if (connected) confirmConnectedAndArmLocked(context, reason)
        else LocalTamperHistory.record(context, "power_baseline_reset", "$reason; disconnected state recorded without creating a countdown")
    }

    private fun notifyMonitorStateChanged(context: Context) {
        context.applicationContext.sendBroadcast(
            Intent(ProtectionMonitorService.ACTION_REFRESH).setPackage(context.packageName)
        )
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
            .putInt(BASELINE_BOOT_COUNT, ProtectionClock.bootCount(context))
            .putLong(GENERATION, prefs.getLong(GENERATION, 0L) + 1L)
            .commit()

        val state = ProtectionStore(context).update { current ->
            if (current.mode == ProtectionMode.ARMED || current.mode == ProtectionMode.ALARM_TRIGGERED) {
                current
            } else {
                ProtectionEngine.chargerConnected(current, System.currentTimeMillis())
            }
        }
        if (state.mode != ProtectionMode.ALARM_TRIGGERED) {
            AlarmScheduler.cancel(context)
            notifyMonitorStateChanged(context)
        }
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

        var consecutiveDisconnected = 0
        var lastObserved: Boolean? = null
        repeat(5) { index ->
            val observed = ExternalPowerState.readConnected(appContext)
            lastObserved = observed
            consecutiveDisconnected = if (observed == false) consecutiveDisconnected + 1 else 0
            if (index < 4) {
                try {
                    Thread.sleep(350L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    LocalTamperHistory.record(appContext, "charger_unplug_rejected", "Debounce sampling interrupted")
                    return ProtectionStore(appContext).read()
                }
            }
        }
        if (consecutiveDisconnected < 3) {
            if (lastObserved == true) {
                confirmConnectedAndArm(appContext, "Final debounce sample confirms external power is connected")
            }
            LocalTamperHistory.record(
                appContext, "charger_unplug_rejected",
                "Five-sample debounce did not produce three consecutive disconnected readings; final=$lastObserved"
            )
            return ProtectionStore(appContext).read()
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
                LocalTamperHistory.record(appContext, "charger_countdown_started", "OS unplug event confirmed by five samples with three consecutive disconnected readings")
                notifyMonitorStateChanged(appContext)
            }
            next
        }
    }
}
