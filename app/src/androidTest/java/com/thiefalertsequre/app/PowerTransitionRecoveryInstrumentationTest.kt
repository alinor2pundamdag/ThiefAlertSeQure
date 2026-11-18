package com.thiefalertsequre.app

import android.content.Intent
import android.net.Uri
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
    @Test fun confirmedConnectedPowerEventPersistsItsBaselineBeforeArming() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitorPrefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val previousMonitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val transitionPrefs = context.getSharedPreferences("charger_transition_v2", 0)
        val statePrefs = context.getSharedPreferences("protection_state_v1", 0)
        val corruptionPrefs = context.getSharedPreferences("protection_state_corrupt_backup_v1", 0)

        try {
            assertTrue(monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, true).commit())
            context.stopService(Intent(context, ProtectionMonitorService::class.java))
            instrumentation.waitForIdleSync()
            repeat(40) {
                if (ProtectionMonitorService.isRunning) SystemClock.sleep(25L)
            }

            runShell(instrumentation, "dumpsys battery set ac 1")
            assumeTrue(
                "Emulator did not expose a confirmed connected battery snapshot",
                ExternalPowerState.readConnected(context) == true
            )

            assertTrue(transitionPrefs.edit().clear().commit())
            assertTrue(statePrefs.edit().clear().commit())
            assertTrue(corruptionPrefs.edit().clear().commit())

            val armed = PowerTransitionCoordinator.onPowerConnected(context)
            assertEquals(ProtectionMode.ARMED, armed.mode)
            assertEquals(ProtectionMode.ARMED, ProtectionStore(context).read().mode)
            assertTrue("Connected baseline must be durable before ARMED is accepted", transitionPrefs.getBoolean("baseline_known", false))
            assertTrue("The persisted baseline must reflect confirmed external power", transitionPrefs.getBoolean("baseline_connected", false))
            assertEquals(ProtectionClock.bootCount(context), transitionPrefs.getInt("baseline_boot_count", -1))
        } finally {
            AlarmScheduler.cancel(context)
            transitionPrefs.edit().clear().commit()
            statePrefs.edit().clear().commit()
            corruptionPrefs.edit().clear().commit()
            monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, previousMonitorEnabled).commit()
            runShell(instrumentation, "dumpsys battery reset")
        }
    }

    @Test fun persistedDisconnectCandidateRecoversIdempotently() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bootCount = ProtectionClock.bootCount(context)
        assumeTrue("This Android build does not expose a usable boot identity", bootCount >= 0)

        val monitorPrefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val previousMonitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        context.stopService(Intent(context, ProtectionMonitorService::class.java))
        instrumentation.waitForIdleSync()
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
                    .putBoolean("baseline_connected", false) // A pending candidate has already transitioned its baseline to disconnected
                    .putInt("baseline_boot_count", bootCount)
                    .putLong("transition_generation", 9L)
                    .putBoolean("pending_disconnect", true)
                    .putLong("pending_disconnect_epoch_ms", eventEpochMs)
                    .putLong("pending_disconnect_elapsed_ms", eventElapsedMs)
                    .putInt("pending_disconnect_boot_count", bootCount)
                    .putLong("pending_disconnect_generation", 9L)
                    .commit()
            )

            assertTrue("A one-shot recovery callback must be scheduled with an inexact fallback",
                AlarmScheduler.scheduleRecoveryRetry(context, 250L))
            assertTrue(AlarmScheduler.hasPendingIntent(context, "recovery", fallback = true))
            val recoveryIntent = Intent(context, AlarmDeadlineReceiver::class.java)
                .setAction("com.thiefalertsequre.DEADLINE")
                .setData(Uri.parse("thiefalertsequre://alarm/recovery"))
            AlarmDeadlineReceiver().handleDeadline(context, recoveryIntent)
            val recovered = store.read()
            assertEquals(ProtectionMode.COUNTDOWN, recovered.mode)
            assertTrue("Successful countdown scheduling must replace the recovery callback",
                !AlarmScheduler.hasPendingIntent(context, "recovery", fallback = true))
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

    @Test fun pendingDisconnectRecoveryCannotStartCountdownAfterMonitorOptOut() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitorPrefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val previousMonitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val transitionPrefs = context.getSharedPreferences("charger_transition_v2", 0)
        val store = ProtectionStore(context)
        val originalState = store.read()
        assumeTrue(
            "Test requires no pre-existing countdown or latched alarm",
            originalState.mode != ProtectionMode.COUNTDOWN &&
                originalState.mode != ProtectionMode.ALARM_TRIGGERED
        )
        val bootCount = ProtectionClock.bootCount(context)
        assumeTrue("This Android build does not expose a usable boot identity", bootCount >= 0)

        try {
            context.stopService(Intent(context, ProtectionMonitorService::class.java))
            instrumentation.waitForIdleSync()
            runShell(instrumentation, "dumpsys battery unplug")
            assumeTrue(
                "Emulator did not expose a confirmed disconnected battery snapshot",
                ExternalPowerState.readConnected(context) == false
            )

            assertTrue(monitorPrefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, false)
                .commit())
            assertTrue(store.write(
                ProtectionState(
                    mode = ProtectionMode.ARMED,
                    deadlineEpochMs = 0L,
                    changedAtEpochMs = System.currentTimeMillis()
                )
            ))
            val eventEpochMs = System.currentTimeMillis() - 250L
            val eventElapsedMs = SystemClock.elapsedRealtime() - 250L
            assertTrue(eventElapsedMs > 0L)
            assertTrue(transitionPrefs.edit()
                .clear()
                .putBoolean("baseline_known", true)
                .putBoolean("baseline_connected", false)
                .putInt("baseline_boot_count", bootCount)
                .putLong("transition_generation", 12L)
                .putBoolean("pending_disconnect", true)
                .putLong("pending_disconnect_epoch_ms", eventEpochMs)
                .putLong("pending_disconnect_elapsed_ms", eventElapsedMs)
                .putInt("pending_disconnect_boot_count", bootCount)
                .putLong("pending_disconnect_generation", 12L)
                .commit())

            val recovered = PowerTransitionCoordinator.recoverPendingDisconnectCandidate(context)
            assertEquals("Monitor opt-out must keep protection disarmed", ProtectionMode.DISARMED, recovered.mode)
            assertTrue("Monitor opt-out must clear the pending unplug marker",
                !transitionPrefs.getBoolean("pending_disconnect", true))
        } finally {
            AlarmScheduler.cancel(context)
            transitionPrefs.edit().clear().commit()
            store.write(originalState)
            monitorPrefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, previousMonitorEnabled)
                .commit()
            runShell(instrumentation, "dumpsys battery reset")
        }
    }

    @Test fun newerDisconnectCandidateRestartsExistingCountdownFromNewEventTime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bootCount = ProtectionClock.bootCount(context)
        assumeTrue("This Android build does not expose a usable boot identity", bootCount >= 0)

        val monitorPrefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val previousMonitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val transitionPrefs = context.getSharedPreferences("charger_transition_v2", 0)
        val store = ProtectionStore(context)
        val originalState = store.read()
        assumeTrue(
            "Test requires no pre-existing countdown or latched alarm",
            originalState.mode != ProtectionMode.COUNTDOWN &&
                originalState.mode != ProtectionMode.ALARM_TRIGGERED
        )

        try {
            context.stopService(Intent(context, ProtectionMonitorService::class.java))
            instrumentation.waitForIdleSync()
            runShell(instrumentation, "dumpsys battery unplug")
            assumeTrue(
                "Emulator did not expose a confirmed disconnected battery snapshot",
                ExternalPowerState.readConnected(context) == false
            )
            assertTrue(monitorPrefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, true)
                .commit())

            val candidateEpochMs = System.currentTimeMillis() - 250L
            val candidateElapsedMs = SystemClock.elapsedRealtime() - 250L
            val priorEventEpochMs = candidateEpochMs - 5_000L
            val priorEventElapsedMs = candidateElapsedMs - 5_000L
            assertTrue(priorEventElapsedMs > 0L)
            assertTrue(store.write(
                ProtectionState(
                    mode = ProtectionMode.COUNTDOWN,
                    deadlineEpochMs = priorEventEpochMs + COUNTDOWN_MS,
                    changedAtEpochMs = priorEventEpochMs,
                    deadlineElapsedRealtimeMs = priorEventElapsedMs + COUNTDOWN_MS,
                    bootCount = bootCount
                )
            ))
            assertTrue(transitionPrefs.edit()
                .clear()
                .putBoolean("baseline_known", true)
                .putBoolean("baseline_connected", false)
                .putInt("baseline_boot_count", bootCount)
                .putLong("transition_generation", 18L)
                .putBoolean("pending_disconnect", true)
                .putLong("pending_disconnect_epoch_ms", candidateEpochMs)
                .putLong("pending_disconnect_elapsed_ms", candidateElapsedMs)
                .putInt("pending_disconnect_boot_count", bootCount)
                .putLong("pending_disconnect_generation", 18L)
                .commit())

            val recovered = PowerTransitionCoordinator.recoverPendingDisconnectCandidate(context)
            assertEquals(ProtectionMode.COUNTDOWN, recovered.mode)
            assertEquals("A newer unplug must anchor the replacement grace period to its own event time",
                candidateEpochMs, recovered.changedAtEpochMs)
            assertEquals(candidateElapsedMs + COUNTDOWN_MS, recovered.deadlineElapsedRealtimeMs)
            assertTrue("A successfully scheduled replacement must clear the pending marker",
                !transitionPrefs.getBoolean("pending_disconnect", true))
        } finally {
            AlarmScheduler.cancel(context)
            transitionPrefs.edit().clear().commit()
            store.write(originalState)
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
