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
        val baselineKnown = SafePreferences.boolean(prefs, KNOWN, false)
        val currentBoot = ProtectionClock.bootCount(context)
        val priorBoot = SafePreferences.int(prefs, BASELINE_BOOT_COUNT, -1)
        val newBoot = forceBootReconciliation || (currentBoot >= 0 && currentBoot != priorBoot)

        // Old COUNTDOWN states have no trusted unplug-event provenance. Only
        // discard them on first migration; a valid reboot countdown is recovered
        // by BootReceiver with a fresh grace period.
        if (!baselineKnown) {
            val store = ProtectionStore(context)
            if (store.read().mode == ProtectionMode.COUNTDOWN) {
                val migration = store.updatePersisted { current ->
                    if (current.mode == ProtectionMode.COUNTDOWN) {
                        ProtectionEngine.chargerConnected(current, System.currentTimeMillis())
                    } else current
                }
                if (migration.persisted) {
                    AlarmScheduler.cancel(context)
                    LocalTamperHistory.record(context, "legacy_countdown_discarded", "Unverifiable pre-transition countdown discarded during migration")
                } else {
                    LocalTamperHistory.record(context, "legacy_countdown_migration_failed", "Legacy countdown state was not changed because persistence failed")
                }
            }
        }

        val connected = ExternalPowerState.readConnected(context)
        if (newBoot && connected == null) {
            // A previous-boot CONNECTED baseline must never authorize an unplug
            // candidate after reboot when the new boot's live state is unknown.
            val saved = prefs.edit()
                .putBoolean(CONNECTED, false)
                .putBoolean(KNOWN, true)
                .putInt(BASELINE_BOOT_COUNT, currentBoot)
                .putLong(GENERATION, SafePreferences.long(prefs, GENERATION, 0L) + 1L)
                .putBoolean(PENDING_DISCONNECT, false)
                .remove(PENDING_EPOCH_MS)
                .remove(PENDING_ELAPSED_MS)
                .remove(PENDING_BOOT_COUNT)
                .remove(PENDING_GENERATION)
                .commit()
            LocalTamperHistory.record(
                context,
                if (saved) "power_baseline_unknown_after_boot" else "power_baseline_unknown_persist_failed",
                "Previous-boot charger baseline invalidated; waiting for current-boot power confirmation"
            )
            return
        }
        if (connected == null) return
        // Only first-run or new-boot snapshots may establish a baseline.
        // During an established boot, a disconnected snapshot must never erase
        // CONNECTED before ACTION_POWER_DISCONNECTED is consumed by the receiver.
        if (!baselineKnown || newBoot) {
            val saved = prefs.edit()
                .putBoolean(CONNECTED, connected)
                .putBoolean(KNOWN, true)
                .putInt(BASELINE_BOOT_COUNT, currentBoot)
                .putLong(GENERATION, SafePreferences.long(prefs, GENERATION, 0L) + 1L)
                .putBoolean(PENDING_DISCONNECT, false)
                .remove(PENDING_EPOCH_MS)
                .remove(PENDING_ELAPSED_MS)
                .remove(PENDING_BOOT_COUNT)
                .remove(PENDING_GENERATION)
                .commit()
            if (!saved) {
                LocalTamperHistory.record(context, "power_baseline_initialize_failed", "Initial charger baseline was not persisted; no connected state was accepted")
                return
            }
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
        val monitorPrefs = context.getSharedPreferences(
            ProtectionMonitorService.PREFS, Context.MODE_PRIVATE
        )
        val monitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        if (connected && monitorEnabled) {
            confirmConnectedAndArmLocked(context, "Connected power snapshot reconciled")
        }
    }

    /** Establish a fresh non-transition baseline when the owner re-enables monitoring. */
    fun resetBaselineToCurrentPower(context: Context, reason: String) = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val store = ProtectionStore(context)
        val currentState = store.read()
        if (currentState.mode == ProtectionMode.ALARM_TRIGGERED) {
            LocalTamperHistory.record(context, "power_baseline_reset_rejected", "$reason; active alarm remains latched")
            return@synchronized
        }

        val connected = ExternalPowerState.readConnected(context)
        val generation = SafePreferences.long(prefs, GENERATION, 0L) + 1L
        if (connected == null) {
            // Preserve an already-valid countdown; clearing KNOWN would make
            // first-run migration discard it on the monitor's next startup.
            val edit = prefs.edit()
            if (currentState.mode != ProtectionMode.COUNTDOWN) {
                edit.putBoolean(KNOWN, false)
                    .putBoolean(CONNECTED, false)
                    .putInt(BASELINE_BOOT_COUNT, ProtectionClock.bootCount(context))
            }
            val saved = edit.putLong(GENERATION, generation)
                .putBoolean(PENDING_DISCONNECT, false)
                .remove(PENDING_EPOCH_MS)
                .remove(PENDING_ELAPSED_MS)
                .remove(PENDING_BOOT_COUNT)
                .remove(PENDING_GENERATION)
                .commit()
            if (!saved) {
                LocalTamperHistory.record(context, "power_baseline_unknown_persist_failed", "$reason; prior baseline retained because unknown state could not be persisted")
            } else {
                LocalTamperHistory.record(context, "power_baseline_unknown", "$reason; no new transition accepted until a reliable power state is available")
            }
            return@synchronized
        }

        if (!connected) {
            // Disarm durably before changing the baseline. A failed state commit
            // must never leave an ARMED state paired with a disconnected baseline.
            val disarm = store.updatePersisted { current ->
                if (current.mode == ProtectionMode.ALARM_TRIGGERED) current
                else ProtectionState(ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1)
            }
            if (!disarm.persisted || disarm.state.mode == ProtectionMode.ALARM_TRIGGERED) {
                LocalTamperHistory.record(context, "power_baseline_reset_state_failed", "$reason; protection state was not durably disarmed")
                return@synchronized
            }
            val baselineSaved = prefs.edit()
                .putBoolean(KNOWN, true)
                .putBoolean(CONNECTED, false)
                .putInt(BASELINE_BOOT_COUNT, ProtectionClock.bootCount(context))
                .putLong(GENERATION, generation)
                .putBoolean(PENDING_DISCONNECT, false)
                .remove(PENDING_EPOCH_MS)
                .remove(PENDING_ELAPSED_MS)
                .remove(PENDING_BOOT_COUNT)
                .remove(PENDING_GENERATION)
                .commit()
            if (!baselineSaved) {
                LocalTamperHistory.record(context, "power_baseline_reset_persist_failed", "$reason; protection is safely DISARMED but baseline cleanup needs recovery")
            }
            AlarmScheduler.cancel(context)
            notifyMonitorStateChanged(context)
            LocalTamperHistory.record(
                context, "power_baseline_reset",
                "$reason; disconnected state recorded without creating a countdown; protection remains DISARMED until a new connect event"
            )
            return@synchronized
        }

        confirmConnectedAndArmLocked(context, reason)
    }

    /**
     * An unplug candidate is not authoritative until stable physical-state
     * sampling succeeds. Restore the prior connected baseline only when this
     * candidate still owns the generation; a newer connect event must win.
     */
    private fun restoreDisconnectCandidateLocked(context: Context, expectedGeneration: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (SafePreferences.long(prefs, GENERATION, -1L) != expectedGeneration ||
            SafePreferences.boolean(prefs, CONNECTED, true)
        ) return
        val saved = prefs.edit()
            .putBoolean(CONNECTED, true)
            .putLong(GENERATION, expectedGeneration + 1L)
            .putBoolean(PENDING_DISCONNECT, false)
            .remove(PENDING_EPOCH_MS)
            .remove(PENDING_ELAPSED_MS)
            .remove(PENDING_BOOT_COUNT)
            .remove(PENDING_GENERATION)
            .commit()
        if (!saved) {
            LocalTamperHistory.record(context, "power_candidate_restore_failed", "Could not restore the prior connected baseline; pending event retained for live-state revalidation")
        }
    }

    private fun clearPendingDisconnectLocked(context: Context, prefs: SharedPreferences) {
        val generation = when (val stored = SafePreferences.readLong(prefs, GENERATION, 0L)) {
            is PreferenceRead.Value -> stored.value
            is PreferenceRead.Corrupt -> {
                LocalTamperHistory.record(context, "power_pending_clear_generation_corrupt", "Pending disconnect retained because generation metadata is corrupt")
                return
            }
        }
        val saved = prefs.edit()
            .putLong(GENERATION, generation + 1L)
            .putBoolean(PENDING_DISCONNECT, false)
            .remove(PENDING_EPOCH_MS)
            .remove(PENDING_ELAPSED_MS)
            .remove(PENDING_BOOT_COUNT)
            .remove(PENDING_GENERATION)
            .commit()
        if (!saved) {
            LocalTamperHistory.record(context, "power_pending_clear_failed", "Pending disconnect marker could not be cleared; it remains subject to generation and live-state validation")
        }
    }

    private fun isPendingCandidateCurrent(
        prefs: SharedPreferences,
        candidate: DisconnectCandidate
    ): Boolean = PowerTransitionRecoveryPolicy.isCurrentCandidate(
        pending = SafePreferences.boolean(prefs, PENDING_DISCONNECT, false),
        pendingGeneration = SafePreferences.long(prefs, PENDING_GENERATION, -1L),
        currentGeneration = SafePreferences.long(prefs, GENERATION, -1L),
        baselineConnected = SafePreferences.boolean(prefs, CONNECTED, true)
    ) && SafePreferences.long(prefs, PENDING_GENERATION, -1L) == candidate.generation &&
        candidate.generation == SafePreferences.long(prefs, GENERATION, -1L)

    private fun notifyMonitorStateChanged(context: Context) {
        context.applicationContext.sendBroadcast(
            Intent(ProtectionMonitorService.ACTION_REFRESH).setPackage(context.packageName)
        )
    }

    fun onPowerConnected(context: Context): ProtectionState {
        val appContext = context.applicationContext
        // Stop as soon as three consecutive live samples confirm the event.
        // The original OS event timestamp is captured by the receiver before this
        // sampling window, so confirmation time never extends the 10-second grace.
        val confirmation = try {
            PowerTransitionSamplingPolicy.confirm(
                expectedConnected = true,
                readConnected = { ExternalPowerState.readConnected(appContext) }
            )
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            LocalTamperHistory.record(
                appContext, "power_connected_confirmation_interrupted",
                "Live-state confirmation was interrupted; the unconfirmed event was ignored"
            )
            return ProtectionStore(appContext).read()
        }
        if (!confirmation.confirmed || ExternalPowerState.readConnected(appContext) != true) {
            LocalTamperHistory.record(
                appContext, "power_connected_unconfirmed",
                "Connect event was not stable after " + confirmation.sampleCount + " samples; final=" + confirmation.lastObserved
            )
            return ProtectionStore(appContext).read()
        }
        return synchronized(lock) {
            confirmConnectedAndArmLocked(appContext, "Power-connected event confirmed by three consecutive live-state samples")
        }
    }

    /** Used only after a live connected-state confirmation, including post-auth recovery. */
    fun confirmConnectedAndArm(context: Context, reason: String): ProtectionState = synchronized(lock) {
        confirmConnectedAndArmLocked(context, reason)
    }

    private fun confirmConnectedAndArmLocked(context: Context, reason: String): ProtectionState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baselineSaved = prefs.edit()
            .putBoolean(CONNECTED, true)
            .putBoolean(KNOWN, true)
            .putInt(BASELINE_BOOT_COUNT, ProtectionClock.bootCount(context))
            .putLong(GENERATION, SafePreferences.long(prefs, GENERATION, 0L) + 1L)
            .putBoolean(PENDING_DISCONNECT, false)
            .remove(PENDING_EPOCH_MS)
            .remove(PENDING_ELAPSED_MS)
            .remove(PENDING_BOOT_COUNT)
            .remove(PENDING_GENERATION)
            .commit()
        if (!baselineSaved) {
            LocalTamperHistory.record(
                context, "power_connected_baseline_persist_failed",
                "Connected event was not accepted because its baseline could not be durably saved"
            )
            return ProtectionStore(context).read()
        }

        val monitorEnabled = SafePreferences.boolean(
            context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE),
            ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val update = ProtectionStore(context).updatePersisted { current ->
            when {
                current.mode == ProtectionMode.ALARM_TRIGGERED -> current
                !monitorEnabled -> ProtectionState(
                    ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1
                )
                current.mode == ProtectionMode.ARMED -> current
                else -> ProtectionEngine.chargerConnected(current, System.currentTimeMillis())
            }
        }
        if (!update.persisted) {
            LocalTamperHistory.record(
                context, "power_connected_state_persist_failed",
                "Connected baseline was saved, but protection state did not commit; scheduler was left unchanged"
            )
            return update.state
        }

        val state = update.state
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
            val priorBoot = SafePreferences.int(prefs, BASELINE_BOOT_COUNT, -1)
            val bootChanged = currentBoot >= 0 && currentBoot != priorBoot
            if (!SafePreferences.boolean(prefs, KNOWN, false) || bootChanged ||
                (eventBootCount >= 0 && currentBoot >= 0 && eventBootCount != currentBoot)
            ) {
                initializeBaselineLocked(appContext, forceBootReconciliation = true)
                LocalTamperHistory.record(
                    appContext, "charger_unplug_rejected",
                    "No trustworthy current-boot connected baseline is available"
                )
                return@synchronized null
            }
            if (!SafePreferences.boolean(prefs, CONNECTED, false)) {
                LocalTamperHistory.record(
                    appContext, "charger_unplug_rejected",
                    "No previously confirmed connected baseline; disconnected snapshots never create transitions"
                )
                return@synchronized null
            }

            val nextGeneration = SafePreferences.long(prefs, GENERATION, 0L) + 1L
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
                val restored = prefs.edit()
                    .putBoolean(CONNECTED, true)
                    .putLong(GENERATION, nextGeneration + 1L)
                    .putBoolean(PENDING_DISCONNECT, false)
                    .remove(PENDING_EPOCH_MS)
                    .remove(PENDING_ELAPSED_MS)
                    .remove(PENDING_BOOT_COUNT)
                    .remove(PENDING_GENERATION)
                    .commit()
                LocalTamperHistory.record(
                    appContext,
                    if (restored) "charger_candidate_persist_failed" else "charger_candidate_rollback_failed",
                    "Unplug candidate was not accepted because its event provenance could not be saved"
                )
                return@synchronized null
            }
            DisconnectCandidate(nextGeneration, eventEpochMs, eventElapsedRealtimeMs, eventBootCount)
        } ?: return ProtectionStore(appContext).read()

        return confirmDisconnectCandidate(appContext, candidate, allowRecoveryRetry = true)
    }

    /**
     * Resume a persisted, protected OS unplug event after a monitor process restart.
     * The candidate is accepted only on the same boot and only while it still owns
     * the current generation; an old-boot or superseded event is never replayed.
     */
    fun recoverPendingDisconnectCandidate(
        context: Context,
        allowRecoveryRetry: Boolean = true
    ): ProtectionState {
        val appContext = context.applicationContext
        val candidate = synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!SafePreferences.boolean(prefs, PENDING_DISCONNECT, false)) return@synchronized null

            val generation = SafePreferences.long(prefs, PENDING_GENERATION, -1L)
            if (generation != SafePreferences.long(prefs, GENERATION, -2L) ||
                SafePreferences.boolean(prefs, CONNECTED, true)
            ) {
                clearPendingDisconnectLocked(context, prefs)
                return@synchronized null
            }

            val eventElapsed = SafePreferences.long(prefs, PENDING_ELAPSED_MS, 0L)
            val eventBoot = SafePreferences.int(prefs, PENDING_BOOT_COUNT, -1)
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
                eventEpochMs = SafePreferences.long(prefs, PENDING_EPOCH_MS, 0L),
                eventElapsedRealtimeMs = eventElapsed,
                eventBootCount = eventBoot
            )
        } ?: return ProtectionStore(appContext).read()

        return confirmDisconnectCandidate(appContext, candidate, allowRecoveryRetry)
    }

    private fun confirmDisconnectCandidate(
        context: Context,
        candidate: DisconnectCandidate,
        allowRecoveryRetry: Boolean
    ): ProtectionState {
        val confirmation = try {
            PowerTransitionSamplingPolicy.confirm(
                expectedConnected = false,
                readConnected = { ExternalPowerState.readConnected(context) }
            )
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            synchronized(lock) {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (isPendingCandidateCurrent(prefs, candidate)) {
                    restoreDisconnectCandidateLocked(context, candidate.generation)
                }
            }
            LocalTamperHistory.record(
                context, "charger_unplug_rejected",
                "Live-state confirmation was interrupted; pending event was safely cleared"
            )
            return ProtectionStore(context).read()
        }

        if (!confirmation.confirmed) {
            if (confirmation.lastObserved == true) {
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
                "Disconnect did not stabilize after " + confirmation.sampleCount + " samples; final=" + confirmation.lastObserved
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
                if (AlarmScheduler.schedule(
                        context, current.deadlineEpochMs, current.deadlineElapsedRealtimeMs, current.bootCount
                    )
                ) {
                    clearPendingDisconnectLocked(context, prefs)
                } else {
                    LocalTamperHistory.record(context, "charger_countdown_schedule_pending", "Existing countdown remains durable; pending unplug provenance retained for retry")
                    AlarmScheduler.scheduleRetry(context, 1_000L)
                    notifyMonitorStateChanged(context)
                }
                return@synchronized current
            }
            if (current.mode != ProtectionMode.ARMED) {
                clearPendingDisconnectLocked(context, prefs)
                LocalTamperHistory.record(context, "charger_unplug_ignored", "Confirmed unplug while protection was not armed")
                return@synchronized current
            }

            val update = store.updatePersisted { old ->
                if (old.mode == ProtectionMode.ARMED) ProtectionEngine.chargerDisconnected(
                    old,
                    candidate.eventEpochMs,
                    candidate.eventElapsedRealtimeMs,
                    candidate.eventBootCount
                ) else old
            }
            val next = update.state
            if (!update.persisted) {
                LocalTamperHistory.record(context, "charger_countdown_persist_pending", "Protection state commit failed; durable unplug provenance retained for recovery")
                if (allowRecoveryRetry && !AlarmScheduler.scheduleRecoveryRetry(context, 1_000L)) {
                    LocalTamperHistory.record(context, "charger_recovery_retry_schedule_failed", "The pending unplug candidate remains durable, but Android did not accept its one-shot recovery callback")
                }
                return@synchronized next
            }
            if (next.mode == ProtectionMode.COUNTDOWN) {
                if (AlarmScheduler.schedule(
                        context, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount
                    )
                ) {
                    clearPendingDisconnectLocked(context, prefs)
                    LocalTamperHistory.record(context, "charger_countdown_started", "OS unplug event confirmed by stable live-state samples; recovered candidates retain their original event time")
                    notifyMonitorStateChanged(context)
                } else {
                    LocalTamperHistory.record(context, "charger_countdown_schedule_pending", "Countdown state is durable but alarm scheduling failed; pending unplug provenance retained for recovery")
                    AlarmScheduler.scheduleRetry(context, 1_000L)
                    notifyMonitorStateChanged(context)
                }
            } else if (next.mode != ProtectionMode.ARMED) {
                clearPendingDisconnectLocked(context, prefs)
            } else {
                LocalTamperHistory.record(context, "charger_countdown_persist_pending", "Protection state write did not commit; durable unplug provenance retained for recovery")
                if (allowRecoveryRetry && !AlarmScheduler.scheduleRecoveryRetry(context, 1_000L)) {
                    LocalTamperHistory.record(context, "charger_recovery_retry_schedule_failed", "The pending unplug candidate remains durable, but Android did not accept its one-shot recovery callback")
                }
            }
            next
        }
    }

}
