package com.thiefalertsequre.app

enum class ProtectionMode { DISARMED, ARMED, COUNTDOWN, ALARM_TRIGGERED }

/** bootCount sentinel for a countdown awaiting the first reliable post-boot power sample. */
const val BOOT_RECONCILIATION_PENDING = -2

data class ProtectionState(
    val mode: ProtectionMode = ProtectionMode.DISARMED,
    val deadlineEpochMs: Long = 0L,
    val changedAtEpochMs: Long = 0L,
    val deadlineElapsedRealtimeMs: Long = 0L,
    val bootCount: Int = -1
) {
    companion object {
        /**
         * Decode persisted state without silently converting damaged protection
         * data into DISARMED. Missing state is a first-install default; malformed
         * state is conservatively latched so only owner authentication can clear it.
         */
        fun fromPersisted(
            modeName: String?,
            deadlineEpochMs: Long,
            changedAtEpochMs: Long,
            deadlineElapsedRealtimeMs: Long,
            bootCount: Int,
            hasOtherStateFields: Boolean = false
        ): ProtectionState {
            if (modeName == null) {
                return if (hasOtherStateFields) ProtectionState(
                    mode = ProtectionMode.ALARM_TRIGGERED,
                    changedAtEpochMs = changedAtEpochMs.coerceAtLeast(0L)
                ) else ProtectionState()
            }

            val mode = runCatching { ProtectionMode.valueOf(modeName) }.getOrNull()
                ?: return ProtectionState(
                    mode = ProtectionMode.ALARM_TRIGGERED,
                    changedAtEpochMs = changedAtEpochMs.coerceAtLeast(0L)
                )

            return when (mode) {
                ProtectionMode.COUNTDOWN -> {
                    val valid = deadlineEpochMs > 0L &&
                        changedAtEpochMs > 0L &&
                        deadlineElapsedRealtimeMs >= 0L &&
                        (bootCount >= -1 || (bootCount == BOOT_RECONCILIATION_PENDING && deadlineElapsedRealtimeMs == 0L))
                    if (valid) ProtectionState(
                        mode, deadlineEpochMs, changedAtEpochMs,
                        deadlineElapsedRealtimeMs, bootCount
                    ) else ProtectionState(
                        mode = ProtectionMode.ALARM_TRIGGERED,
                        changedAtEpochMs = changedAtEpochMs.coerceAtLeast(0L)
                    )
                }
                ProtectionMode.ALARM_TRIGGERED -> ProtectionState(
                    mode = mode,
                    changedAtEpochMs = changedAtEpochMs.coerceAtLeast(0L)
                )
                ProtectionMode.ARMED -> ProtectionState(
                    mode = mode,
                    changedAtEpochMs = changedAtEpochMs.coerceAtLeast(0L)
                )
                ProtectionMode.DISARMED -> ProtectionState(
                    mode = mode,
                    changedAtEpochMs = changedAtEpochMs.coerceAtLeast(0L)
                )
            }
        }
    }
}
