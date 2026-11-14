package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/** Single authority for charger transitions; snapshots alone never start countdowns. */
object PowerTransitionCoordinator {
    // v2 intentionally resets the old snapshot-derived baseline. Any legacy
    // COUNTDOWN from earlier builds lacks trustworthy transition provenance.
    private const val PREFS = "charger_transition_v2"
    private const val KNOWN = "baseline_known"
    private const val CONNECTED = "baseline_connected"
    private const val BASELINE_BOOT_COUNT = "baseline_boot_count"
    private const val GENERATION = "transition_generation"
    private const val PENDING_DISCONNECT = "pending_disconnect"
    private const val PENDING_EPOCH_MS = "pending_disconnect_epoch_ms"
    private const val PENDING_ELAPSED_MS = "pending_disconnect_elapsed_ms"
    private const val PENDING_BOOT_COUNT = "pending_disconnect_boot_count"
    private const val PENDING_GENERATION = "pending_disconnect_generation"
    private val lock = Any()

    private data class DisconnectCandidate(
        val generation: Long,
        val eventEpochMs: Long,
        val eventElapsedRealtimeMs: Long,
        val eventBootCount: Int
    )

    internal fun acceptsPowerConnectedEvent(liveConnected: Boolean?): Boolean = liveConnected == true

    fun initializeBaseline(context: Context, forceBootReconciliation: Boolean = false) = synchronized(lock) {
        initializeBaselineLocked(context, forceBootReconciliation)
    }

    private fun initializeBaselineLocked(context: Context, forceBootReconciliation: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baselineKnown = prefs.getBoolean(KNOWN, false)
        val currentBoot = ProtectionClock.bootCount(context)
        val priorBoot = prefs.getInt(BASELINE_BOOT_COUNT, -1)
        val newBoot = forceBootReconciliation || (currentBoot >= 0 && currentBoot != priorBoot)

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
                .putBoolean(PENDING_DISCONNECT, false)
                .remove(PENDING_EPOCH_MS)
                .remove(PENDING_ELAPSED_MS)
                .remove(PENDING_BOOT_COUNT)
                .remove(PENDING_GENERATION)
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
                .putBoolean(PENDING_DISCONNECT, false)
                .remove(PENDING_EPOCH_MS)
                .remove(PENDING_ELAPSED_MS)
                .remove(PENDING_BOOT_COUNT)
                .remove(PENDING_GENERATION)
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
            .putBoolean(PENDING_DISCONNECT, false)
            .remove(PENDING_EPOCH_MS)
            .remove(PENDING_ELAPSED_MS)
            .remove(PENDING_BOOT_COUNT)
            .remove(PENDING_GENERATION)
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
            .putBoolean(PENDING_DISCONNECT, false)
            .remove(PENDING_EPOCH_MS)
            .remove(PENDING_ELAPSED_MS)
            .remove(PENDING_BOOT_COUNT)
            .remove(PENDING_GENERATION)
            .commit()
    }

    private fun clearPendingDisconnectLocked(prefs: SharedPreferences) {
        prefs.edit()
            .putBoolean(PENDING_DISCONNECT, false)
            .remove(PENDING_EPOCH_MS)
            .remove(PENDING_ELAPSED_MS)
            .remove(PENDING_BOOT_COUNT)
            .remove(PENDING_GENERATION)
            .commit()
    }

    private fun isPendingCandidateCurrent(
        prefs: SharedPreferences,
        candidate: DisconnectCandidate
    ): Boolean =
        prefs.getBoolean(PENDING_DISCONNECT, false) &&
            prefs.getLong(PENDING_GENERATION, -1L) == candidate.generation &&
            prefs.getLong(GENERATION, -1L) == candidate.generation &&
            !prefs.getBoolean(CONNECTED, true)

    private fun notifyMonitorStateChanged(context: Context) {
        context.applicationContext.sendBroadcast(
            Intent(ProtectionMonitorService.ACTION_REFRESH).setPackage(context.packageName)
        )
    }

    fun onPowerConnected(context: Context): ProtectionState {
        val appContext = context.applicationContext
        // Keep a five-read window so one or two stale snapshots after the OS
        // broadcast do not reject a real reconnect. Three consecutive positives
        // are enough; 100 ms spacing bounds normal confirmation to about 400 ms.
        var consecutiveConnected = 0
        var lastObserved: Boolean? = null
        repeat(5) { index ->
            val observed = ExternalPowerState.readConnected(appContext)
            lastObserved = observed
            consecutiveConnected = if (observed == true) consecutiveConnected + 1 else 0
            if (index < 4) Thread.sleep(100L)
        }
        if (consecutiveConnected < 3 || ExternalPowerState.readConnected(appContext) != true) {
            LocalTamperHistory.record(
                appContext, "power_connected_unconfirmed",
                "Connect event did not produce three consecutive connected samples; final=$lastObserved"
            )
            return ProtectionStore(appContext).read()
        }
        return synchronized(lock) {
            confirmConnectedAndArmLocked(appContext, "Power-connected event confirmed by five reads including three consecutive live-state samples")
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
            .putBoolean(PENDING_DISCONNECT, false)
            .remove(PENDING_EPOCH_MS)
            .remove(PENDING_ELAPSED_MS)
            .remove(PENDING_BOOT_COUNT)
            .remove(PENDING_GENERATION)
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
    fun onPowerDisconnected(
        context: Context,
        eventEpochMs: Long = System.currentTimeMillis(),
        eventElapsedRealtimeMs: Long = ProtectionClock.elapsedRealtime(),
        eventBootCount: Int = ProtectionClock.bootCount(context)
    ): ProtectionState {
        val appContext = context.applicationContext
        val candidate = synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val currentBoot = ProtectionClock.bootCount(appContext)
            val priorBoot = prefs.getInt(BASELINE_BOOT_COUNT, -1)
            val bootChanged = currentBoot >= 0 && currentBoot != priorBoot
            if (!prefs.getBoolean(KNOWN, false) || bootChanged ||
                (eventBootCount >= 0 && currentBoot >= 0 && eventBootCount != currentBoot)
            ) {
                initializeBaselineLocked(appContext, forceBootReconciliation = true)
                LocalTamperHistory.record(
                    appContext, "charger_unplug_rejected",
                    "No trustworthy current-boot connected baseline is available"
                )
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
            val saved = prefs.edit()
                .putBoolean(CONNECTED, false)
                .putLong(GENERATION, nextGeneration)
                .putBoolean(PENDING_DISCONNECT, true)
                .putLong(PENDING_EPOCH_MS, eventEpochMs)
                .putLong(PENDING_ELAPSED_MS, eventElapsedRealtimeMs)
                .putInt(PENDING_BOOT_COUNT, eventBootCount)
                .putLong(PENDING_GENERATION, nextGeneration)
                .commit()
            if (!saved) {
                // A transition is not actionable until its provenance is durable.
                prefs.edit()
                    .putBoolean(CONNECTED, true)
                    .putLong(GENERATION, nextGeneration + 1L)
                    .putBoolean(PENDING_DISCONNECT, false)
                    .remove(PENDING_EPOCH_MS)
                    .remove(PENDING_ELAPSED_MS)
                    .remove(PENDING_BOOT_COUNT)
                    .remove(PENDING_GENERATION)
                    .commit()
                LocalTamperHistory.record(appContext, "charger_candidate_persist_failed", "Unplug candidate was not accepted because its event provenance could not be saved")
                return@synchronized null
            }
            DisconnectCandidate(nextGeneration, eventEpochMs, eventElapsedRealtimeMs, eventBootCount)
        } ?: return ProtectionStore(appContext).read()

        return confirmDisconnectCandidate(appContext, candidate)
    }

    /**
     * Resume a persisted, protected OS unplug event after a monitor process restart.
     * The candidate is accepted only on the same boot and only while it still owns
     * the current generation; an old-boot or superseded event is never replayed.
     */
    fun recoverPendingDisconnectCandidate(context: Context): ProtectionState {
        val appContext = context.applicationContext
        val candidate = synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PENDING_DISCONNECT, false)) return@synchronized null

            val generation = prefs.getLong(PENDING_GENERATION, -1L)
            if (generation != prefs.getLong(GENERATION, -2L) ||
                prefs.getBoolean(CONNECTED, true)
            ) {
                clearPendingDisconnectLocked(prefs)
                return@synchronized null
            }

            val eventElapsed = prefs.getLong(PENDING_ELAPSED_MS, 0L)
            val eventBoot = prefs.getInt(PENDING_BOOT_COUNT, -1)
            val currentBoot = ProtectionClock.bootCount(appContext)
            val nowElapsed = ProtectionClock.elapsedRealtime()
            if (!PowerTransitionRecoveryPolicy.isSameBoot(
                    eventBoot, currentBoot, eventElapsed, nowElapsed
                )
            ) {
                initializeBaselineLocked(appContext, forceBootReconciliation = true)
                LocalTamperHistory.record(
                    appContext, "pending_unplug_discarded_after_restart",
                    "Persisted unplug candidate belonged to another boot or elapsed clock reset"
                )
                return@synchronized null
            }

            DisconnectCandidate(
                generation = generation,
                eventEpochMs = prefs.getLong(PENDING_EPOCH_MS, 0L),
                eventElapsedRealtimeMs = eventElapsed,
                eventBootCount = eventBoot
            )
        } ?: return ProtectionStore(appContext).read()

        return confirmDisconnectCandidate(appContext, candidate)
    }

    private fun confirmDisconnectCandidate(
        context: Context,
        candidate: DisconnectCandidate
    ): ProtectionState {
        var consecutiveDisconnected = 0
        var lastObserved: Boolean? = null
        repeat(5) { index ->
            val observed = ExternalPowerState.readConnected(context)
            lastObserved = observed
            consecutiveDisconnected = if (observed == false) consecutiveDisconnected + 1 else 0
            if (index < 4) {
                try {
                    Thread.sleep(100L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    synchronized(lock) {
                        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        if (isPendingCandidateCurrent(prefs, candidate)) {
                            restoreDisconnectCandidateLocked(context, candidate.generation)
                        }
                    }
                    LocalTamperHistory.record(context, "charger_unplug_rejected", "Debounce sampling interrupted; pending event cleared safely")
                    return ProtectionStore(context).read()
                }
            }
        }

        if (consecutiveDisconnected < 3) {
            if (lastObserved == true) {
                confirmConnectedAndArm(context, "Final debounce sample confirms external power is connected")
            } else {
                synchronized(lock) {
                    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    if (isPendingCandidateCurrent(prefs, candidate)) {
                        restoreDisconnectCandidateLocked(context, candidate.generation)
                    }
                }
            }
            LocalTamperHistory.record(
                context, "charger_unplug_rejected",
                "Five-read debounce did not produce three consecutive disconnected readings; final=$lastObserved"
            )
            return ProtectionStore(context).read()
        }

        return synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!isPendingCandidateCurrent(prefs, candidate)) {
                LocalTamperHistory.record(context, "stale_power_candidate_rejected", "A newer power event or owner action superseded this unplug candidate")
                return@synchronized ProtectionStore(context).read()
            }

            val finalConnected = ExternalPowerState.readConnected(context)
            if (finalConnected != false) {
                if (finalConnected == true) {
                    confirmConnectedAndArmLocked(context, "Final connected snapshot invalidated unplug candidate")
                } else {
                    restoreDisconnectCandidateLocked(context, candidate.generation)
                    LocalTamperHistory.record(context, "charger_unplug_rejected", "Final power-state sample was unknown; connected baseline restored")
                }
                return@synchronized ProtectionStore(context).read()
            }

            val store = ProtectionStore(context)
            val current = store.read()
            if (current.mode == ProtectionMode.COUNTDOWN) {
                // A process may have died after the state write but before the
                // pending-event marker was cleared. Resume the existing deadline.
                clearPendingDisconnectLocked(prefs)
                AlarmScheduler.schedule(context, current.deadlineEpochMs, current.deadlineElapsedRealtimeMs, current.bootCount)
                return@synchronized current
            }
            if (current.mode != ProtectionMode.ARMED) {
                clearPendingDisconnectLocked(prefs)
                LocalTamperHistory.record(context, "charger_unplug_ignored", "Confirmed unplug while protection was not armed")
                return@synchronized current
            }

            val next = store.update { old ->
                if (old.mode == ProtectionMode.ARMED) ProtectionEngine.chargerDisconnected(
                    old,
                    candidate.eventEpochMs,
                    candidate.eventElapsedRealtimeMs,
                    candidate.eventBootCount
                ) else old
            }
            if (next.mode == ProtectionMode.COUNTDOWN) {
                clearPendingDisconnectLocked(prefs)
                AlarmScheduler.schedule(context, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount)
                LocalTamperHistory.record(context, "charger_countdown_started", "OS unplug event confirmed by stable live-state samples; recovered candidates retain their original event time")
                notifyMonitorStateChanged(context)
            } else if (next.mode != ProtectionMode.ARMED) {
                clearPendingDisconnectLocked(prefs)
            } else {
                LocalTamperHistory.record(context, "charger_countdown_persist_pending", "Protection state write did not commit; durable unplug provenance retained for recovery")
            }
            next
        }
    }

}
