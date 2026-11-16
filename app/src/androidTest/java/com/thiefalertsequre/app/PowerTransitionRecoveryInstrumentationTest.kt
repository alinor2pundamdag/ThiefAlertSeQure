package com.thiefalertsequre.app

import android.app.Instrumentation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises persisted pending-disconnect recovery with a real Android Context,
 * SharedPreferences, BatteryManager sticky state and AlarmManager.
 */
@RunWith(AndroidJUnit4::class)
class PowerTransitionRecoveryInstrumentationTest {
    @Test fun persistedDisconnectCandidateRecoversIdempotently() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bootCount = ProtectionClock.bootCount(context)
        assumeTrue("This Android build does not expose a usable boot identity", bootCount >= 0)

        val monitorPrefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val previousMonitorEnabled = monitorPrefs.getBoolean(
            ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val transitionPrefs = context.getSharedPreferences("charger_transition_v2", 0)
        val store = ProtectionStore(context)

        try {
            runShell(instrumentation, "dumpsys battery unplug")
            assumeTrue(
                "Emulator did not expose a confirmed disconnected battery snapshot",
                ExternalPowerState.readConnected(context) == false
            )

            val eventEpochMs = System.currentTimeMillis() - 250L
            val eventElapsedMs = SystemClock.elapsedRealtime() - 250L
            assertTrue(eventElapsedMs > 0L)

            assertTrue(
                monitorPrefs.edit()
                    .putBoolean(ProtectionMonitorService.KEY_ENABLED, true)
                    .commit()
            )
            assertTrue(
                store.write(
                    ProtectionState(
                        mode = ProtectionMode.ARMED,
                        deadlineEpochMs = 0L,
                        changedAtEpochMs = eventEpochMs
                    )
                )
            )
            assertTrue(
                transitionPrefs.edit()
                    .clear()
                    .putBoolean("baseline_known", true)
                    .putBoolean("baseline_connected", true)
                    .putInt("baseline_boot_count", bootCount)
                    .putLong("transition_generation", 9L)
                    .putBoolean("pending_disconnect", true)
                    .putLong("pending_disconnect_epoch_ms", eventEpochMs)
                    .putLong("pending_disconnect_elapsed_ms", eventElapsedMs)
                    .putInt("pending_disconnect_boot_count", bootCount)
                    .putLong("pending_disconnect_generation", 9L)
                    .commit()
            )

            val recovered = PowerTransitionCoordinator.recoverPendingDisconnectCandidate(context)
            assertEquals(ProtectionMode.COUNTDOWN, recovered.mode)
            assertEquals(eventElapsedMs + COUNTDOWN_MS, recovered.deadlineElapsedRealtimeMs)

            // Replaying recovery after the first commit must not create a second
            // countdown or move the original event-time deadline.
            val replayed = PowerTransitionCoordinator.recoverPendingDisconnectCandidate(context)
            assertEquals(recovered, replayed)
        } finally {
            AlarmScheduler.cancel(context)
            transitionPrefs.edit().clear().commit()
            store.write(
                ProtectionState(
                    mode = ProtectionMode.DISARMED,
                    deadlineEpochMs = 0L,
                    changedAtEpochMs = System.currentTimeMillis()
                )
            )
            monitorPrefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, previousMonitorEnabled)
                .commit()
            runShell(instrumentation, "dumpsys battery reset")
        }
    }

    private fun runShell(instrumentation: Instrumentation, command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
