package com.thiefalertsequre.app

import android.content.Context
import android.content.SharedPreferences

data class ProtectionStoreUpdateResult(
    val state: ProtectionState,
    val persisted: Boolean
)

data class ProtectionStoreIntegrity(
    val protectionStateHealthy: Boolean,
    val authenticationThrottleHealthy: Boolean
)

class ProtectionStore internal constructor(
    context: Context,
    private val commitEditor: (SharedPreferences.Editor) -> Boolean
) {
    constructor(context: Context) : this(context.applicationContext, { it.commit() })

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)
    private var stateCorruptionDetected = false
    private var throttleCorruptionDetected = false

    fun read(): ProtectionState = synchronized(LOCK) { readUnlocked() }

    /** Read-only health snapshot; does not clear corruption latches or disarm protection. */
    fun integritySnapshot(): ProtectionStoreIntegrity = synchronized(LOCK) {
        readUnlocked()
        readThrottleUnlocked(
            System.currentTimeMillis(),
            ProtectionClock.elapsedRealtime(),
            ProtectionClock.bootCount(appContext)
        )
        ProtectionStoreIntegrity(
            protectionStateHealthy = !stateCorruptionDetected,
            authenticationThrottleHealthy = !throttleCorruptionDetected
        )
    }

    fun write(state: ProtectionState): Boolean = synchronized(LOCK) {
        readUnlocked()
        if (stateCorruptionDetected) false else writeUnlocked(state)
    }

    fun update(transform: (ProtectionState) -> ProtectionState): ProtectionState =
        updatePersisted(transform).state

    /**
     * Runs a short owner-settings transaction while the authoritative state is
     * DISARMED. Charger/alarm transitions share LOCK and therefore cannot race
     * a restore that is explicitly restricted to the disarmed state.
     */
    internal fun <T> runIfDisarmed(block: () -> T): T? = synchronized(LOCK) {
        val current = readUnlocked()
        if (stateCorruptionDetected || current.mode != ProtectionMode.DISARMED) {
            return@synchronized null
        }
        block()
    }

    /** Returns the actual commit result so callers do not cancel alarms on an unpersisted transition. */
    fun updatePersisted(transform: (ProtectionState) -> ProtectionState): ProtectionStoreUpdateResult =
        synchronized(LOCK) {
            val current = readUnlocked()
            if (stateCorruptionDetected) return@synchronized ProtectionStoreUpdateResult(current, false)
            val next = transform(current)
            val persisted = writeUnlocked(next)
            ProtectionStoreUpdateResult(if (persisted) next else current, persisted)
        }

    /**
     * The only state transition allowed to clear a corruption latch. Callers
     * must first validate a fresh Android owner-authentication proof.
     */
    internal fun disarmAfterOwnerAuthentication(nowEpochMs: Long): ProtectionState? = synchronized(LOCK) {
        val current = readUnlocked()
        if (current.mode != ProtectionMode.ALARM_TRIGGERED) return@synchronized null

        if (stateCorruptionDetected) {
            if (!preserveCorruptPreferences(
                    "Protection state corruption was encountered before authenticated disarm",
                    KEY_STATE_CORRUPTION_ACTIVE
                ) || !clearCorruptionFlag(KEY_STATE_CORRUPTION_ACTIVE)
            ) {
                record(
                    "protection_state_corruption_recovery_blocked",
                    "Owner authentication succeeded, but the original corrupt state could not be safely preserved or its latch could not be cleared"
                )
                return@synchronized null
            }
        }

        val next = ProtectionEngine.authenticated(current, nowEpochMs)
        if (next.mode != ProtectionMode.DISARMED) return@synchronized null
        if (!writeUnlocked(next)) {
            record("protection_state_authenticated_disarm_persist_failed", "Authenticated disarm could not be durably committed; the alarm remains active")
            return@synchronized null
        }
        stateCorruptionDetected = false
        next
    }

    fun readAuthenticationThrottle(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): AuthThrottleState = synchronized(LOCK) {
        val current = readThrottleUnlocked(nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        if (throttleCorruptionDetected) return@synchronized current
        val normalized = AuthenticationThrottle.normalize(current, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        if (normalized == current) normalized
        else if (writeThrottleUnlocked(normalized)) normalized
        else {
            record("authentication_throttle_normalize_failed", "Normalized owner-authentication throttle could not be persisted")
            current
        }
    }

    fun recordAuthenticationFailure(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): AuthThrottleState = synchronized(LOCK) {
        val current = readThrottleUnlocked(nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        if (throttleCorruptionDetected) return@synchronized current
        val next = AuthenticationThrottle.onFailure(current, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        if (writeThrottleUnlocked(next)) next else {
            record("authentication_throttle_failure_persist_failed", "Owner-authentication failure counter could not be committed")
            current
        }
    }

    fun startAuthenticationCooldown(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -1
    ): AuthThrottleState = synchronized(LOCK) {
        val current = readThrottleUnlocked(nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        if (throttleCorruptionDetected) return@synchronized current
        val next = AuthenticationThrottle.startCooldown(current, nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        if (writeThrottleUnlocked(next)) next else {
            record("authentication_throttle_cooldown_persist_failed", "Owner-authentication cooldown could not be committed")
            current
        }
    }

    /** Successful owner authentication is the only caller that may clear a throttle-corruption latch. */
    fun resetAuthenticationFailures(): Boolean = synchronized(LOCK) {
        readThrottleUnlocked(
            System.currentTimeMillis(),
            ProtectionClock.elapsedRealtime(),
            ProtectionClock.bootCount(appContext)
        )
        if (throttleCorruptionDetected) {
            if (!preserveCorruptPreferences(
                    "Authentication throttle corruption was encountered before successful owner authentication",
                    KEY_AUTH_CORRUPTION_ACTIVE
                ) || !clearCorruptionFlag(KEY_AUTH_CORRUPTION_ACTIVE)
            ) {
                record("authentication_throttle_recovery_blocked", "Original corrupt throttle data could not be preserved or its latch could not be cleared")
                return@synchronized false
            }
            throttleCorruptionDetected = false
        }
        val saved = writeThrottleUnlocked(AuthThrottleState())
        if (!saved) record("authentication_throttle_reset_failed", "Successful authentication could not reset the persisted failure counter")
        saved
    }

    /**
     * Corrupt persisted protection data never silently becomes DISARMED.
     * Its original typed values are copied to a separate private snapshot;
     * the source remains untouched until a fresh owner-authentication proof
     * explicitly authorizes disarm.
     */
    private fun readUnlocked(): ProtectionState {
        stateCorruptionDetected = false
        when (corruptionFlag(KEY_STATE_CORRUPTION_ACTIVE)) {
            true -> {
                stateCorruptionDetected = true
                return failClosedState()
            }
            null -> return failClosedFromCorruption("The protection-state corruption marker is unreadable")
            false -> Unit
        }

        val modeName = when (val value = SafePreferences.readString(prefs, "mode", null)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return failClosedFromCorruption("Protection mode has the wrong stored type")
        }
        val deadlineEpochMs = when (val value = SafePreferences.readLong(prefs, "deadline", 0L)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return failClosedFromCorruption("Protection deadline has the wrong stored type")
        }
        val changedAtEpochMs = when (val value = SafePreferences.readLong(prefs, "changed", 0L)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return failClosedFromCorruption("Protection change timestamp has the wrong stored type")
        }
        val deadlineElapsedRealtimeMs = when (val value = SafePreferences.readLong(prefs, "deadline_elapsed", 0L)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return failClosedFromCorruption("Protection monotonic deadline has the wrong stored type")
        }
        val bootCount = when (val value = SafePreferences.readInt(prefs, "boot_count", -1)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return failClosedFromCorruption("Protection boot identity has the wrong stored type")
        }
        val hasOtherStateFields = listOf(
            "deadline", "changed", "deadline_elapsed", "boot_count"
        ).any(prefs::contains)
        val decoded = ProtectionState.fromPersisted(
            modeName = modeName,
            deadlineEpochMs = deadlineEpochMs,
            changedAtEpochMs = changedAtEpochMs,
            deadlineElapsedRealtimeMs = deadlineElapsedRealtimeMs,
            bootCount = bootCount,
            hasOtherStateFields = hasOtherStateFields
        )

        val corruptionReason = when {
            modeName == null && hasOtherStateFields -> "Protection state is partial: fields exist without a mode"
            modeName != null && runCatching { ProtectionMode.valueOf(modeName) }.isFailure ->
                "Protection mode is not a recognized state"
            modeName == ProtectionMode.COUNTDOWN.name && decoded.mode == ProtectionMode.ALARM_TRIGGERED ->
                "Persisted countdown is incomplete or invalid"
            else -> null
        }
        return if (corruptionReason != null) failClosedFromCorruption(corruptionReason) else decoded
    }

    private fun failClosedFromCorruption(reason: String): ProtectionState {
        stateCorruptionDetected = true
        val preserved = preserveCorruptPreferences(reason, KEY_STATE_CORRUPTION_ACTIVE)
        if (!preserved) {
            record(
                "protection_state_corruption_backup_failed",
                "$reason; original preferences were not overwritten and all state mutations are blocked"
            )
        } else {
            record(
                "protection_state_corrupt_preserved",
                "$reason; original typed values were preserved and owner authentication is required"
            )
        }
        return failClosedState()
    }

    private fun failClosedState(): ProtectionState = ProtectionState(
        mode = ProtectionMode.ALARM_TRIGGERED,
        changedAtEpochMs = System.currentTimeMillis()
    )

    private fun readThrottleUnlocked(
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long,
        currentBootCount: Int
    ): AuthThrottleState {
        throttleCorruptionDetected = false
        when (corruptionFlag(KEY_AUTH_CORRUPTION_ACTIVE)) {
            true -> {
                throttleCorruptionDetected = true
                return AuthenticationThrottle.recoverCorruptState(nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
            }
            null -> {
                throttleCorruptionDetected = true
                record("authentication_throttle_marker_corrupt", "Throttle corruption marker is unreadable; Android credential fallback remains available")
                return AuthenticationThrottle.recoverCorruptState(nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
            }
            false -> Unit
        }

        val failures = when (val value = SafePreferences.readInt(prefs, KEY_AUTH_FAILURES, 0)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return recoverCorruptThrottle("Authentication failure count has the wrong stored type", nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        }
        val cooldownEpoch = when (val value = SafePreferences.readLong(prefs, KEY_AUTH_COOLDOWN, 0L)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return recoverCorruptThrottle("Authentication cooldown has the wrong stored type", nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        }
        val cooldownElapsed = when (val value = SafePreferences.readLong(prefs, KEY_AUTH_COOLDOWN_ELAPSED, 0L)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return recoverCorruptThrottle("Authentication monotonic cooldown has the wrong stored type", nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        }
        val bootCount = when (val value = SafePreferences.readInt(prefs, KEY_AUTH_BOOT_COUNT, -1)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return recoverCorruptThrottle("Authentication boot identity has the wrong stored type", nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
        }
        return AuthThrottleState(
            failures.coerceIn(0, AuthenticationThrottle.MAX_FAILURES),
            cooldownEpoch,
            cooldownElapsed,
            bootCount
        )
    }

    private fun recoverCorruptThrottle(
        reason: String,
        nowEpochMs: Long,
        nowElapsedRealtimeMs: Long,
        currentBootCount: Int
    ): AuthThrottleState {
        throttleCorruptionDetected = true
        val preserved = preserveCorruptPreferences(reason, KEY_AUTH_CORRUPTION_ACTIVE)
        if (!preserved) {
            record("authentication_throttle_backup_failed", "$reason; original throttle data was not overwritten")
        } else {
            record("authentication_throttle_corrupt_preserved", "$reason; biometric retry is cooled down and Android credential fallback remains available")
        }
        return AuthenticationThrottle.recoverCorruptState(nowEpochMs, nowElapsedRealtimeMs, currentBootCount)
    }

    private fun writeUnlocked(state: ProtectionState): Boolean {
        val saved = commitWithOneRetry {
            prefs.edit()
                .putString("mode", state.mode.name)
                .putLong("deadline", state.deadlineEpochMs)
                .putLong("changed", state.changedAtEpochMs)
                .putLong("deadline_elapsed", state.deadlineElapsedRealtimeMs)
                .putInt("boot_count", state.bootCount)
        }
        if (saved) runCatching {
            appContext.sendBroadcast(
                android.content.Intent(ACTION_STATE_CHANGED).setPackage(appContext.packageName)
            )
        }
        return saved
    }

    private fun writeThrottleUnlocked(state: AuthThrottleState): Boolean = commitWithOneRetry {
        prefs.edit()
            .putInt(KEY_AUTH_FAILURES, state.failedAttempts)
            .putLong(KEY_AUTH_COOLDOWN, state.cooldownUntilEpochMs)
            .putLong(KEY_AUTH_COOLDOWN_ELAPSED, state.cooldownUntilElapsedRealtimeMs)
            .putInt(KEY_AUTH_BOOT_COUNT, state.bootCount)
    }

    private fun commitWithOneRetry(editorFactory: () -> SharedPreferences.Editor): Boolean {
        val first = runCatching { commitEditor(editorFactory()) }.getOrDefault(false)
        if (first) return true
        android.util.Log.w(TAG, "SharedPreferences commit failed; retrying once")
        return runCatching { commitEditor(editorFactory()) }.getOrDefault(false)
    }

    private fun corruptionFlag(key: String): Boolean? = when (
        val value = SafePreferences.readBoolean(corruptionPrefs(), key, false)
    ) {
        is PreferenceRead.Value -> value.value
        is PreferenceRead.Corrupt -> null
    }

    private fun preserveCorruptPreferences(reason: String, activeKey: String): Boolean {
        val backup = corruptionPrefs()
        val active = when (val value = SafePreferences.readBoolean(backup, activeKey, false)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return false
        }
        if (active) return true

        val count = when (val value = SafePreferences.readLong(backup, KEY_SNAPSHOT_COUNT, 0L)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return false
        }
        if (count == Long.MAX_VALUE) return false
        val snapshot = count + 1L
        val rawValues = runCatching { prefs.all }.getOrNull() ?: return false
        for ((_, value) in rawValues) {
            when (value) {
                is String, is Int, is Long, is Float, is Boolean -> Unit
                is Set<*> -> if (value.filterIsInstance<String>().size != value.size) return false
                else -> return false
            }
        }
        val saved = commitWithOneRetry {
            backup.edit().also { retryEditor ->
                for ((key, value) in rawValues) {
                    val snapshotKey = "snapshot_${snapshot}_$key"
                    when (value) {
                        is String -> retryEditor.putString(snapshotKey, value)
                        is Int -> retryEditor.putInt(snapshotKey, value)
                        is Long -> retryEditor.putLong(snapshotKey, value)
                        is Float -> retryEditor.putFloat(snapshotKey, value)
                        is Boolean -> retryEditor.putBoolean(snapshotKey, value)
                        is Set<*> -> retryEditor.putStringSet(snapshotKey, value.filterIsInstance<String>().toSet())
                    }
                }
                retryEditor
                    .putString("snapshot_${snapshot}_reason", reason.take(256))
                    .putLong("snapshot_${snapshot}_captured_at_epoch_ms", System.currentTimeMillis())
                    .putLong(KEY_SNAPSHOT_COUNT, snapshot)
                    .putBoolean(activeKey, true)
            }
        }
        return saved
    }

    private fun clearCorruptionFlag(key: String): Boolean {
        val backup = corruptionPrefs()
        val active = when (val value = SafePreferences.readBoolean(backup, key, false)) {
            is PreferenceRead.Value -> value.value
            is PreferenceRead.Corrupt -> return false
        }
        return !active || commitWithOneRetry { backup.edit().putBoolean(key, false) }
    }

    private fun corruptionPrefs(): SharedPreferences = appContext.getSharedPreferences(
        CORRUPT_BACKUP_PREFS, Context.MODE_PRIVATE
    )

    private fun record(event: String, detail: String) {
        runCatching { LocalTamperHistory.record(appContext, event, detail) }
    }

    companion object {
        const val ACTION_STATE_CHANGED = "com.thiefalertsequre.PROTECTION_STATE_CHANGED"
        private val LOCK = Any()
        private const val CORRUPT_BACKUP_PREFS = "protection_state_corrupt_backup_v1"
        private const val KEY_SNAPSHOT_COUNT = "snapshot_count"
        private const val KEY_STATE_CORRUPTION_ACTIVE = "state_corruption_active"
        private const val KEY_AUTH_CORRUPTION_ACTIVE = "auth_corruption_active"
        private const val KEY_AUTH_FAILURES = "auth_failed_attempts"
        private const val KEY_AUTH_COOLDOWN = "auth_cooldown_until"
        private const val KEY_AUTH_COOLDOWN_ELAPSED = "auth_cooldown_until_elapsed"
        private const val KEY_AUTH_BOOT_COUNT = "auth_cooldown_boot_count"
    }
}
