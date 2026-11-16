package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import android.app.Instrumentation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises persisted pending-disconnect recovery with a real Android Context,
 * SharedPreferences, BatteryManager sticky state and AlarmManager.
 */
@RunWith(AndroidJUnit4::class)
class PowerTransitionRecoveryInstrumentationTest {
    private lateinit var appContext: Context
    private lateinit var monitorSnapshot: SharedPreferencesSnapshot
    private lateinit var transitionSnapshot: SharedPreferencesSnapshot
    private lateinit var stateSnapshot: SharedPreferencesSnapshot
    private lateinit var corruptionSnapshot: SharedPreferencesSnapshot
    private lateinit var schedulerSnapshot: SharedPreferencesSnapshot
    private lateinit var historySnapshot: SharedPreferencesSnapshot
    private var originalProtectionState: ProtectionState? = null
    private var originalMonitorEnabled = false
    private var originalMonitorWasRunning = false

    @Before
    fun preserveExistingProtectionStateAndIsolateMonitor() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val initialProtectionState = ProtectionStore(appContext).read()
        val initialAlarmToken = SafePreferences.readString(
            appContext.getSharedPreferences("alarm_scheduler_v1", Context.MODE_PRIVATE),
            "last_token",
            null
        )
        val hasExistingAlarm = when (initialAlarmToken) {
            is PreferenceRead.Value -> initialAlarmToken.value != null
            is PreferenceRead.Corrupt -> true
        }
        assumeTrue(
            "Do not run recovery instrumentation while a real countdown or latched alarm is active",
            initialProtectionState.mode != ProtectionMode.COUNTDOWN &&
                initialProtectionState.mode != ProtectionMode.ALARM_TRIGGERED && !hasExistingAlarm
        )
        val monitorPrefs = appContext.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        monitorSnapshot = SharedPreferencesSnapshot(appContext, ProtectionMonitorService.PREFS)
        transitionSnapshot = SharedPreferencesSnapshot(appContext, "charger_transition_v2")
        stateSnapshot = SharedPreferencesSnapshot(appContext, "protection_state_v1")
        corruptionSnapshot = SharedPreferencesSnapshot(appContext, "protection_state_corrupt_backup_v1")
        schedulerSnapshot = SharedPreferencesSnapshot(appContext, "alarm_scheduler_v1")
        historySnapshot = SharedPreferencesSnapshot(appContext, "local_tamper_history_v1")
        originalProtectionState = ProtectionStore(appContext).read()
        originalMonitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        originalMonitorWasRunning = ProtectionMonitorService.isRunning
        check(monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, false).commit())
        appContext.stopService(Intent(appContext, ProtectionMonitorService::class.java))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @After
    fun restoreExistingProtectionStateAndMonitor() {
        transitionSnapshot.restore()
        stateSnapshot.restore()
        corruptionSnapshot.restore()
        historySnapshot.restore()
        monitorSnapshot.restore()

        val state = originalProtectionState
        if (state?.mode == ProtectionMode.COUNTDOWN) {
            check(
                AlarmScheduler.schedule(
                    appContext,
                    state.deadlineEpochMs,
                    state.deadlineElapsedRealtimeMs,
                    state.bootCount
                )
            ) { "The pre-test protection countdown could not be restored" }
        } else {
            AlarmScheduler.cancel(appContext)
        }
        schedulerSnapshot.restore()

        if (originalMonitorWasRunning && originalMonitorEnabled) {
            runCatching {
                ContextCompat.startForegroundService(
                    appContext, Intent(appContext, ProtectionMonitorService::class.java)
                )
            }
        }
    }

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
