package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent

/** Single authority for charger transitions; snapshots alone never start countdowns. */
object PowerTransitionCoordinator {
    // v2 intentionally resets the old snapshot-derived baseline. Any legacy
    // COUNTDOWN from earlier builds lacks trustworthy transition provenance.
    private const val PREFS = "charger_transition_v2"
    private const val KNOWN = "baseline_known"
    private const val CONNECTED = "baseline_connected"
    private const val BASELINE_BOOT_COUNT = "baseline_boot_count"
    private const val GENERATION = "transition_generation"
    private val lock = Any()

    internal fun acceptsPowerConnectedEvent(liveConnected: Boolean?): Boolean = liveConnected == true

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

        val connected = ExternalPowerState.readConnected(context)
        if (newBoot && connected == null) {
            // A previous-boot CONNECTED baseline must never authorize an unplug
            // candidate after reboot when the new boot's live state is unknown.
            prefs.edit()
                .putBoolean(CONNECTED, false)
                .putBoolean(KNOWN, true)
                .putInt(BASELINE_BOOT_COUNT, currentBoot)
                .putLong(GENERATION, prefs.getLong(GENERATION, 0L) + 1L)
                .commit()
            LocalTamperHistory.record(
                context, "power_baseline_unknown_after_boot",
                "Previous-boot charger baseline invalidated; waiting for current-boot power confirmation"
            )
            return
        }
        if (connected == null) return
        // Only first-run or new-boot snapshots may establish a baseline.
        // During an established boot, a disconnected snapshot must never erase
        // CONNECTED before ACTION_POWER_DISCONNECTED is consumed by the receiver.
        if (!baselineKnown || newBoot) {
            prefs.edit()
                .putBoolean(CONNECTED, connected)
                .putBoolean(KNOWN, true)
                .putInt(BASELINE_BOOT_COUNT, currentBoot)
                .putLong(GENERATION, prefs.getLong(GENERATION, 0L) + 1L)
                .commit()
            LocalTamperHistory.record(
                context,
                if (newBoot) "power_baseline_reinitialized_after_boot" else "power_baseline_initialized",
                if (connected) {
                    "Connected snapshot recorded as baseline; no unplug transition inferred"
                } else {
                    "Disconnected snapshot recorded as baseline without creating a countdown"
                }
            )
        }

        // Connected is safe evidence to arm/cancel; disconnected is baseline
        // evidence only and never creates a countdown by itself.
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
        if (connected) {
            confirmConnectedAndArmLocked(context, reason)
        } else {
            val disarmed = ProtectionStore(context).update { current ->
                if (current.mode == ProtectionMode.ALARM_TRIGGERED) current
                else ProtectionState(ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1)
            }
            if (disarmed.mode != ProtectionMode.ALARM_TRIGGERED) {
                AlarmScheduler.cancel(context)
                notifyMonitorStateChanged(context)
            }
            LocalTamperHistory.record(
                context, "power_baseline_reset",
                "$reason; disconnected state recorded without creating a countdown; protection remains DISARMED until a new connect event"
            )
        }
    }


    /**
     * An unplug candidate is not authoritative until stable physical-state
     * sampling succeeds. Restore the prior connected baseline only when this
     * candidate still owns the generation; a newer connect event must win.
     */
    private fun restoreDisconnectCandidateLocked(context: Context, expectedGeneration: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(GENERATION, -1L) != expectedGeneration ||
            prefs.getBoolean(CONNECTED, true)
        ) return
        prefs.edit()
            .putBoolean(CONNECTED, true)
            .putLong(GENERATION, expectedGeneration + 1L)
            .commit()
    }

    private fun notifyMonitorStateChanged(context: Context) {
        context.applicationContext.sendBroadcast(
            Intent(ProtectionMonitorService.ACTION_REFRESH).setPackage(context.packageName)
        )
    }

    fun onPowerConnected(context: Context): ProtectionState = synchronized(lock) {
        val appContext = context.applicationContext
        // Delayed/reordered broadcasts must not let stale CONNECTED re-arm
        // protection after the cable has already been removed.
        val liveConnected = ExternalPowerState.readConnected(appContext)
        if (acceptsPowerConnectedEvent(liveConnected)) {
            confirmConnectedAndArmLocked(appContext, "Power-connected event confirmed by live external-power state")
        } else {
            if (liveConnected == false) {
                LocalTamperHistory.record(appContext, "stale_power_connected_rejected", "Connect broadcast rejected because live external power is disconnected")
            } else {
                LocalTamperHistory.record(appContext, "power_connected_unconfirmed", "Connect broadcast deferred because live external power state is unknown")
            }
            ProtectionStore(appContext).read()
        }
    }

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

        val monitorEnabled = context.getSharedPreferences(
            ProtectionMonitorService.PREFS, Context.MODE_PRIVATE
        ).getBoolean(
            ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val state = ProtectionStore(context).update { current ->
            when {
                current.mode == ProtectionMode.ALARM_TRIGGERED -> current
                !monitorEnabled -> ProtectionState(
                    ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1
                )
                current.mode == ProtectionMode.ARMED -> current
                else -> ProtectionEngine.chargerConnected(current, System.currentTimeMillis())
            }
        }
        if (state.mode != ProtectionMode.ALARM_TRIGGERED) {
            AlarmScheduler.cancel(context)
            if (monitorEnabled) notifyMonitorStateChanged(context)
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
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val currentBoot = ProtectionClock.bootCount(appContext)
            val priorBoot = prefs.getInt(BASELINE_BOOT_COUNT, -1)
            val bootChanged = currentBoot >= 0 && currentBoot != priorBoot
            if (!prefs.getBoolean(KNOWN, false) || bootChanged) {
                initializeBaselineLocked(appContext)
                LocalTamperHistory.record(
                    appContext, "charger_unplug_rejected",
                    "No current-boot connected baseline is available"
                )
                return@synchronized null
            }
            if (ExternalPowerState.readConnected(appContext) == true) {
                confirmConnectedAndArmLocked(appContext, "Disconnect broadcast arrived while live snapshot still shows connected power")
                LocalTamperHistory.record(appContext, "charger_unplug_rejected", "Live power snapshot still reports connected")
                return@synchronized null
            }
            if (!prefs.getBoolean(CONNECTED, false)) {
                LocalTamperHistory.record(
                    appContext, "charger_unplug_rejected",
                    "No previously confirmed connected baseline; disconnected snapshots never create transitions"
                )
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
                    synchronized(lock) {
                        restoreDisconnectCandidateLocked(appContext, candidateGeneration)
                    }
                    LocalTamperHistory.record(appContext, "charger_unplug_rejected", "Debounce sampling interrupted; connected baseline restored for a future genuine transition")
                    return ProtectionStore(appContext).read()
                }
            }
        }
        if (consecutiveDisconnected < 3) {
            if (lastObserved == true) {
                confirmConnectedAndArm(appContext, "Final debounce sample confirms external power is connected")
            } else {
                synchronized(lock) {
                    restoreDisconnectCandidateLocked(appContext, candidateGeneration)
                }
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
                    restoreDisconnectCandidateLocked(appContext, candidateGeneration)
                    LocalTamperHistory.record(appContext, "charger_unplug_rejected", "Final power-state sample was unknown; connected baseline restored for a future genuine transition")
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
