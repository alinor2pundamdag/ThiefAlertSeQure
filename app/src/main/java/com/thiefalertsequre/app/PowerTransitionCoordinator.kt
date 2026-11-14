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
                .putLong(GENERATION, longPref(prefs, GENERATION, 0L) + 1L)
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
                .putLong(GENERATION, longPref(prefs, GENERATION, 0L) + 1L)
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
        val generation = longPref(prefs, GENERATION, 0L) + 1L
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
        if (longPref(prefs, GENERATION, -1L) != expectedGeneration ||
            booleanPref(prefs, CONNECTED, true)
        ) return
        prefs.edit()
            .putBoolean(CONNECTED, true)
            .putLong(GENERATION, expectedGeneration + 1L)
            .commit()
    }

    private fun clearPendingDisconnectLocked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(PENDING_DISCONNECT, false)
            .remove(PENDING_EVENT_EPOCH)
            .remove(PENDING_EVENT_ELAPSED)
            .remove(PENDING_EVENT_BOOT)
            .commit()
    }

    private fun notifyMonitorStateChanged(context: Context) {
        context.applicationContext.sendBroadcast(
            Intent(ProtectionMonitorService.ACTION_REFRESH).setPackage(context.packageName)
        )
    }

    fun onPowerConnected(context: Context): ProtectionState {
        val appContext = context.applicationContext
        // A newer OS connect event invalidates any pending disconnect retry immediately,
        // even if its first battery snapshots are stale. The event cannot arm protection
        // until live connected-state confirmation succeeds below.
        synchronized(lock) {
            clearPendingDisconnectLocked(appContext)
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putLong(GENERATION, longPref(prefs, GENERATION, 0L) + 1L).commit()
        }
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
            clearPendingDisconnectLocked(appContext)
            confirmConnectedAndArmLocked(appContext, "Power-connected event confirmed by five reads including three consecutive live-state samples")
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
        if (!PowerTransitionRecoveryPolicy.shouldRetry(
                booleanPref(prefs, PENDING_DISCONNECT, false),
                ExternalPowerState.readConnected(appContext)
            )
        ) return ProtectionStore(appContext).read()
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
        prefs.edit()
            .putBoolean(CONNECTED, true)
            .putBoolean(KNOWN, true)
            .putInt(BASELINE_BOOT_COUNT, ProtectionClock.bootCount(context))
            .putLong(GENERATION, longPref(prefs, GENERATION, 0L) + 1L)
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
            if (!booleanPref(prefs, CONNECTED, false)) {
                clearPendingDisconnectLocked(appContext)
                LocalTamperHistory.record(
                    appContext, "charger_unplug_rejected",
                    "No previously confirmed connected baseline; disconnected snapshots never create transitions"
                )
                return@synchronized null
            }
            val nextGeneration = longPref(prefs, GENERATION, 0L) + 1L
            prefs.edit()
                .putBoolean(CONNECTED, false)
                .putLong(GENERATION, nextGeneration)
                .putBoolean(PENDING_DISCONNECT, true)
                .putLong(PENDING_EVENT_EPOCH, eventEpochMs)
                .putLong(PENDING_EVENT_ELAPSED, eventElapsedRealtimeMs)
                .putInt(PENDING_EVENT_BOOT, eventBootCount)
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

            val next = store.update { old ->
                if (old.mode == ProtectionMode.ARMED) ProtectionEngine.chargerDisconnected(
                    old, eventEpochMs, eventElapsedRealtimeMs, eventBootCount
                ) else old
            }
            clearPendingDisconnectLocked(appContext)
            if (next.mode == ProtectionMode.COUNTDOWN) {
                AlarmScheduler.schedule(appContext, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount)
                LocalTamperHistory.record(appContext, "charger_countdown_started", "OS unplug event confirmed by five reads, including three consecutive disconnected samples, and a final live-state read")
                notifyMonitorStateChanged(appContext)
            }
            next
        }
    }
}
