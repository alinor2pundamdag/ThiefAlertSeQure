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
    private const val PENDING_DISCONNECT = "pending_disconnect_retry"
    private const val PENDING_EVENT_EPOCH = "pending_disconnect_event_epoch"
    private const val PENDING_EVENT_ELAPSED = "pending_disconnect_event_elapsed"
    private const val PENDING_EVENT_BOOT = "pending_disconnect_event_boot"
    private val lock = Any()

    private fun booleanPref(prefs: android.content.SharedPreferences, key: String, default: Boolean) =
        runCatching { prefs.getBoolean(key, default) }.getOrDefault(default)

    private fun intPref(prefs: android.content.SharedPreferences, key: String, default: Int) =
        runCatching { prefs.getInt(key, default) }.getOrDefault(default)

    private fun longPref(prefs: android.content.SharedPreferences, key: String, default: Long) =
        runCatching { prefs.getLong(key, default) }.getOrDefault(default)

    private fun persistBaseline(
        context: Context,
        editor: SharedPreferences.Editor,
        operation: String
    ): Boolean {
        val saved = runCatching { editor.commit() }.getOrDefault(false)
        if (!saved) {
            LocalTamperHistory.record(
                context, "power_baseline_persist_failed",
                "$operation could not be durably saved; no dependent state transition was accepted"
            )
        }
        return saved
    }

    internal fun acceptsPowerConnectedEvent(liveConnected: Boolean?): Boolean = liveConnected == true

    fun initializeBaseline(context: Context, forceBootReconciliation: Boolean = false) = synchronized(lock) {
        initializeBaselineLocked(context, forceBootReconciliation)
    }

    private fun initializeBaselineLocked(context: Context, forceBootReconciliation: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baselineKnown = booleanPref(prefs, KNOWN, false)
        val currentBoot = ProtectionClock.bootCount(context)
        val priorBoot = intPref(prefs, BASELINE_BOOT_COUNT, -1)
        val newBoot = forceBootReconciliation || (currentBoot >= 0 && currentBoot != priorBoot)
        if (newBoot) clearPendingDisconnectLocked(context)

        // Old COUNTDOWN states have no trusted unplug-event provenance. Only
        // discard them on first migration; a valid reboot countdown is recovered
        // by BootReceiver with a fresh grace period.
        if (!baselineKnown) {
            val store = ProtectionStore(context)
            if (store.read().mode == ProtectionMode.COUNTDOWN) {
                val update = store.updatePersisted { current ->
                    if (current.mode == ProtectionMode.COUNTDOWN) {
                        ProtectionEngine.chargerConnected(current, System.currentTimeMillis())
                    } else current
                }
                // Cancel the unverifiable legacy alarm even if storage is failing;
                // the next initialization retries the state migration.
                AlarmScheduler.cancel(context)
                if (update.persisted) {
                    LocalTamperHistory.record(context, "legacy_countdown_discarded", "Unverifiable pre-transition countdown discarded during migration")
                } else {
                    LocalTamperHistory.record(context, "legacy_countdown_migration_failed", "Unverifiable countdown remains stored; scheduler canceled and migration will retry")
                }
            }
        }

        val connected = ExternalPowerState.readConnected(context)
        if (newBoot && connected == null) {
            // A previous-boot CONNECTED baseline must never authorize an unplug
            // candidate after reboot when the new boot's live state is unknown.
            if (!persistBaseline(
                    context,
                    prefs.edit()
                        .putBoolean(CONNECTED, false)
                        .putBoolean(KNOWN, true)
                        .putInt(BASELINE_BOOT_COUNT, currentBoot)
                        .putLong(GENERATION, longPref(prefs, GENERATION, 0L) + 1L),
                    "Unknown post-boot charger baseline"
                )
            ) return
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
            if (!persistBaseline(
                    context,
                    prefs.edit()
                        .putBoolean(CONNECTED, connected)
                        .putBoolean(KNOWN, true)
                        .putInt(BASELINE_BOOT_COUNT, currentBoot)
                        .putLong(GENERATION, longPref(prefs, GENERATION, 0L) + 1L),
                    "Initial or post-boot charger baseline"
                )
            ) return
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
        val generation = longPref(prefs, GENERATION, 0L) + 1L
        if (connected == null) {
            // Preserve an already-valid countdown; clearing KNOWN would make
            // first-run migration discard it on the monitor's next startup.
            if (ProtectionStore(context).read().mode != ProtectionMode.COUNTDOWN) {
                if (!persistBaseline(
                        context,
                        prefs.edit()
                            .putBoolean(KNOWN, false)
                            .putBoolean(CONNECTED, false)
                            .putInt(BASELINE_BOOT_COUNT, ProtectionClock.bootCount(context))
                            .putLong(GENERATION, generation),
                        "Unknown charger baseline reset"
                    )
                ) return@synchronized
            } else {
                if (!persistBaseline(
                        context, prefs.edit().putLong(GENERATION, generation),
                        "Countdown generation invalidation"
                    )
                ) return@synchronized
            }
            LocalTamperHistory.record(context, "power_baseline_unknown", "$reason; no new transition accepted until a reliable power state is available")
            return@synchronized
        }
        if (!persistBaseline(
                context,
                prefs.edit()
                    .putBoolean(KNOWN, true)
                    .putBoolean(CONNECTED, connected)
                    .putInt(BASELINE_BOOT_COUNT, ProtectionClock.bootCount(context))
                    .putLong(GENERATION, generation),
                "Owner-requested charger baseline reset"
            )
        ) return@synchronized
        if (connected) {
            confirmConnectedAndArmLocked(context, reason)
        } else {
            val update = ProtectionStore(context).updatePersisted { current ->
                if (current.mode == ProtectionMode.ALARM_TRIGGERED) current
                else ProtectionState(ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1)
            }
            if (!update.persisted) {
                LocalTamperHistory.record(context, "protection_state_persist_failed", "Owner-requested disarm could not be committed")
                return@synchronized
            }
            if (update.state.mode != ProtectionMode.ALARM_TRIGGERED) {
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
        if (longPref(prefs, GENERATION, -1L) != expectedGeneration ||
            booleanPref(prefs, CONNECTED, true)
        ) return
        if (!persistBaseline(
                context,
                prefs.edit()
                    .putBoolean(CONNECTED, true)
                    .putLong(GENERATION, expectedGeneration + 1L),
                "Restoring connected baseline after an unconfirmed unplug"
            )
        ) {
            LocalTamperHistory.record(context, "pending_disconnect_retained", "Baseline restore failed; persisted OS event remains available for retry")
        }
    }

    private fun clearPendingDisconnectLocked(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(PENDING_DISCONNECT, false)
            .remove(PENDING_EVENT_EPOCH)
            .remove(PENDING_EVENT_ELAPSED)
            .remove(PENDING_EVENT_BOOT)
            .commit()
        if (!saved) {
            LocalTamperHistory.record(
                context, "pending_disconnect_clear_failed",
                "Pending OS disconnect marker could not be cleared; live-state validation remains required on retry"
            )
        }
    }

    private fun notifyMonitorStateChanged(context: Context) {
        context.applicationContext.sendBroadcast(
            Intent(ProtectionMonitorService.ACTION_REFRESH).setPackage(context.packageName)
        )
    }

    fun onPowerConnected(context: Context): ProtectionState {
        val appContext = context.applicationContext
        // Capture the current generation, but do not clear a pending unplug until
        // live power confirms reconnect. A stale ACTION_POWER_CONNECTED broadcast
        // must not erase a real pending disconnect retry.
        val eventGeneration = synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            longPref(prefs, GENERATION, 0L)
        }
        var consecutiveConnected = 0
        var lastObserved: Boolean? = null
        repeat(5) { index ->
            val observed = ExternalPowerState.readConnected(appContext)
            lastObserved = observed
            consecutiveConnected = if (observed == true) consecutiveConnected + 1 else 0
            if (index < 4) Thread.sleep(100L)
        }
        val finalConnected = ExternalPowerState.readConnected(appContext)
        if (consecutiveConnected < 3 || !PowerTransitionRecoveryPolicy.acceptsConnectedEvent(
                finalConnected, eventGeneration,
                synchronized(lock) {
                    val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    longPref(prefs, GENERATION, 0L)
                }
            )
        ) {
            LocalTamperHistory.record(
                appContext, "power_connected_unconfirmed",
                "Connect event did not produce three consecutive connected samples or was superseded; final=$lastObserved"
            )
            return ProtectionStore(appContext).read()
        }
        return synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val currentGeneration = longPref(prefs, GENERATION, 0L)
            if (!PowerTransitionRecoveryPolicy.acceptsConnectedEvent(
                    finalConnected, eventGeneration, currentGeneration
                )
            ) {
                LocalTamperHistory.record(appContext, "stale_power_connect_rejected", "A newer power event superseded this reconnect candidate")
                return@synchronized ProtectionStore(appContext).read()
            }
            confirmConnectedAndArmLocked(
                appContext,
                "Power-connected event confirmed by five reads including three consecutive live-state samples"
            )
        }
    }

    fun hasPendingDisconnectRetry(context: Context): Boolean = runCatching {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PENDING_DISCONNECT, false)
    }.getOrDefault(false)

    /** Retry a previously observed OS unplug only after a later live battery snapshot confirms disconnection. */
    fun retryPendingDisconnect(context: Context): ProtectionState {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = booleanPref(prefs, PENDING_DISCONNECT, false)
        val liveConnected = ExternalPowerState.readConnected(appContext)
        if (pending && liveConnected == true) {
            LocalTamperHistory.record(appContext, "charger_unplug_retry_cancelled", "A later battery snapshot confirms external power reconnected")
            return onPowerConnected(appContext)
        }
        if (!PowerTransitionRecoveryPolicy.shouldRetry(pending, liveConnected)) {
            return ProtectionStore(appContext).read()
        }
        val epoch = runCatching { prefs.getLong(PENDING_EVENT_EPOCH, 0L) }.getOrDefault(0L)
        val elapsed = runCatching { prefs.getLong(PENDING_EVENT_ELAPSED, 0L) }.getOrDefault(0L)
        val boot = runCatching { prefs.getInt(PENDING_EVENT_BOOT, -1) }.getOrDefault(-1)
        if (epoch <= 0L || elapsed <= 0L) {
            synchronized(lock) { clearPendingDisconnectLocked(appContext) }
            return ProtectionStore(appContext).read()
        }
        LocalTamperHistory.record(appContext, "charger_unplug_retry", "Retrying previously observed unplug after battery state confirmed disconnection")
        return onPowerDisconnected(appContext, epoch, elapsed, boot)
    }

    /** Used only after a live connected-state confirmation, including post-auth recovery. */
    fun confirmConnectedAndArm(context: Context, reason: String): ProtectionState = synchronized(lock) {
        confirmConnectedAndArmLocked(context, reason)
    }

    private fun confirmConnectedAndArmLocked(context: Context, reason: String): ProtectionState {
        clearPendingDisconnectLocked(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val baselineSaved = prefs.edit()
            .putBoolean(CONNECTED, true)
            .putBoolean(KNOWN, true)
            .putInt(BASELINE_BOOT_COUNT, ProtectionClock.bootCount(context))
            .putLong(GENERATION, longPref(prefs, GENERATION, 0L) + 1L)
            .commit()
        if (!baselineSaved) {
            LocalTamperHistory.record(
                context, "power_baseline_persist_failed",
                "Connected state was observed but its transition baseline could not be durably saved"
            )
            return ProtectionStore(context).read()
        }

        val monitorEnabled = context.getSharedPreferences(
            ProtectionMonitorService.PREFS, Context.MODE_PRIVATE
        ).getBoolean(
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
                context, "protection_state_persist_failed",
                "Connected transition was confirmed but the protection state commit failed; existing alarm deadline was retained"
            )
            return update.state
        }
        if (update.state.mode != ProtectionMode.ALARM_TRIGGERED) {
            AlarmScheduler.cancel(context)
            if (monitorEnabled) notifyMonitorStateChanged(context)
        }
        LocalTamperHistory.record(context, "charger_connected_confirmed", reason)
        return update.state
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
        val candidateGeneration = synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val currentBoot = ProtectionClock.bootCount(appContext)
            val priorBoot = intPref(prefs, BASELINE_BOOT_COUNT, -1)
            val bootChanged = currentBoot >= 0 && currentBoot != priorBoot
            val eventFromDifferentBoot = currentBoot >= 0 && eventBootCount >= 0 && currentBoot != eventBootCount
            if (!booleanPref(prefs, KNOWN, false) || bootChanged || eventFromDifferentBoot) {
                clearPendingDisconnectLocked(appContext)
                initializeBaselineLocked(appContext)
                LocalTamperHistory.record(
                    appContext, "charger_unplug_rejected",
                    "No current-boot connected baseline is available"
                )
                return@synchronized null
            }
            // Do not reject on the first live snapshot: some devices update
            // ACTION_BATTERY_CHANGED just after ACTION_POWER_DISCONNECTED.
            // Five reads with three consecutive negatives plus a final live read are authoritative.
            val connectedBaseline = booleanPref(prefs, CONNECTED, false)
            val pendingRetry = booleanPref(prefs, PENDING_DISCONNECT, false)
            if (!PowerTransitionRecoveryPolicy.canAcceptDisconnectCandidate(
                    connectedBaseline, pendingRetry
                )
            ) {
                clearPendingDisconnectLocked(appContext)
                LocalTamperHistory.record(
                    appContext, "charger_unplug_rejected",
                    "No connected baseline or previously persisted OS disconnect retry; snapshots alone never create transitions"
                )
                return@synchronized null
            }
            val nextGeneration = longPref(prefs, GENERATION, 0L) + 1L
            val candidateSaved = prefs.edit()
                .putBoolean(CONNECTED, false)
                .putLong(GENERATION, nextGeneration)
                .putBoolean(PENDING_DISCONNECT, true)
                .putLong(PENDING_EVENT_EPOCH, eventEpochMs)
                .putLong(PENDING_EVENT_ELAPSED, eventElapsedRealtimeMs)
                .putInt(PENDING_EVENT_BOOT, eventBootCount)
                .commit()
            if (!candidateSaved) {
                LocalTamperHistory.record(
                    appContext, "power_candidate_persist_failed",
                    "OS unplug event could not be durably recorded; countdown was not started"
                )
                return@synchronized null
            }
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
                    Thread.sleep(100L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    synchronized(lock) {
                        restoreDisconnectCandidateLocked(appContext, candidateGeneration)
                    }
                    LocalTamperHistory.record(appContext, "charger_unplug_rejected", "Debounce sampling interrupted; pending OS unplug retained for retry after a reliable battery snapshot")
                    return ProtectionStore(appContext).read()
                }
            }
        }
        if (consecutiveDisconnected < 3) {
            if (lastObserved == true) {
                synchronized(lock) { clearPendingDisconnectLocked(appContext) }
                confirmConnectedAndArm(appContext, "Final debounce sample confirms external power is connected")
            } else {
                synchronized(lock) {
                    restoreDisconnectCandidateLocked(appContext, candidateGeneration)
                }
            }
            LocalTamperHistory.record(
                appContext, "charger_unplug_rejected",
                "Five-read debounce did not produce three consecutive disconnected readings; pending OS event retained for retry; final=$lastObserved"
            )
            return ProtectionStore(appContext).read()
        }

        return synchronized(lock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (longPref(prefs, GENERATION, -1L) != candidateGeneration ||
                booleanPref(prefs, CONNECTED, true)
            ) {
                LocalTamperHistory.record(appContext, "stale_power_candidate_rejected", "A newer power event superseded this unplug candidate")
                return@synchronized ProtectionStore(appContext).read()
            }

            val finalConnected = ExternalPowerState.readConnected(appContext)
            if (finalConnected != false) {
                if (finalConnected == true) {
                    clearPendingDisconnectLocked(appContext)
                    confirmConnectedAndArmLocked(appContext, "Final connected snapshot invalidated unplug candidate")
                } else {
                    restoreDisconnectCandidateLocked(appContext, candidateGeneration)
                    LocalTamperHistory.record(appContext, "charger_unplug_rejected", "Final power-state sample was unknown; pending OS unplug retained for later retry")
                }
                return@synchronized ProtectionStore(appContext).read()
            }

            val store = ProtectionStore(appContext)
            val current = store.read()
            if (current.mode != ProtectionMode.ARMED) {
                clearPendingDisconnectLocked(appContext)
                LocalTamperHistory.record(appContext, "charger_unplug_ignored", "Confirmed unplug while protection was not armed")
                return@synchronized current
            }

            val update = store.updatePersisted { old ->
                if (old.mode == ProtectionMode.ARMED) ProtectionEngine.chargerDisconnected(
                    old, eventEpochMs, eventElapsedRealtimeMs, eventBootCount
                ) else old
            }
            if (!update.persisted) {
                // Keep the confirmed OS unplug retry marker. The monitor's next
                // battery snapshot can retry this transition instead of silently
                // losing the only event that starts the countdown.
                LocalTamperHistory.record(
                    appContext, "protection_state_persist_failed",
                    "Confirmed unplug could not persist COUNTDOWN; pending disconnect was retained for retry"
                )
                return@synchronized update.state
            }
            clearPendingDisconnectLocked(appContext)
            if (update.state.mode == ProtectionMode.COUNTDOWN) {
                AlarmScheduler.schedule(appContext, update.state.deadlineEpochMs, update.state.deadlineElapsedRealtimeMs, update.state.bootCount)
                LocalTamperHistory.record(appContext, "charger_countdown_started", "OS unplug event confirmed by five reads, including three consecutive disconnected samples, and a final live-state read")
                notifyMonitorStateChanged(appContext)
            }
            update.state
        }
    }
}
