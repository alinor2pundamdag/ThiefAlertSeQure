package com.thiefalertsequre.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PowerTransitionReceiverRuntimeInstrumentationTest {
    @Test fun activeForegroundMonitorReceivesPowerDisconnectAtRuntime() {
        exercisePowerDisconnect(missedWhileServiceStopped = false)
    }

    @Test fun stickyServiceRestartRecoversMissedDisconnectFromSameBootBaseline() {
        exercisePowerDisconnect(missedWhileServiceStopped = true)
    }

    private fun exercisePowerDisconnect(missedWhileServiceStopped: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitorPrefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val transitionPrefs = context.getSharedPreferences("charger_transition_v2", Context.MODE_PRIVATE)
        val statePrefs = context.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)
        val corruptionPrefs = context.getSharedPreferences("protection_state_corrupt_backup_v1", Context.MODE_PRIVATE)
        val monitorSnapshot = monitorPrefs.all.toMap()
        val transitionSnapshot = transitionPrefs.all.toMap()
        val stateSnapshot = statePrefs.all.toMap()
        val corruptionSnapshot = corruptionPrefs.all.toMap()
        val wasRunning = ProtectionMonitorService.isRunning
        val previousMonitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val originalState = ProtectionStore(context).read()
        assumeTrue(
            "Test requires no pre-existing countdown or latched alarm",
            originalState.mode != ProtectionMode.COUNTDOWN &&
                originalState.mode != ProtectionMode.ALARM_TRIGGERED
        )
        val bootCount = ProtectionClock.bootCount(context)
        assumeTrue("This Android build does not expose a usable boot identity", bootCount >= 0)

        try {
            context.stopService(Intent(context, ProtectionMonitorService::class.java))
            waitForService(false)
            runShell(instrumentation, "dumpsys battery set ac 1")
            assumeTrue(
                "Emulator did not expose a confirmed connected battery snapshot",
                waitForPowerState(context, expectedConnected = true)
            )

            assertTrue(
                monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, true).commit()
            )
            assertTrue(transitionPrefs.edit().clear().commit())
            assertTrue(statePrefs.edit().clear().commit())
            assertTrue(corruptionPrefs.edit().clear().commit())
            val armed = PowerTransitionCoordinator.onPowerConnected(context)
            assertEquals(ProtectionMode.ARMED, armed.mode)

            startMonitorService(context)
            waitForService(true)

            var restartRequestedAt = 0L
            if (missedWhileServiceStopped) {
                context.stopService(Intent(context, ProtectionMonitorService::class.java))
                waitForService(false)
                runShell(instrumentation, "dumpsys battery unplug")
                assumeTrue(
                    "Emulator did not expose a confirmed disconnected battery snapshot",
                    waitForPowerState(context, expectedConnected = false)
                )
                assertEquals(
                    "No runtime receiver is active while the monitor service is stopped",
                    ProtectionMode.ARMED,
                    ProtectionStore(context).read().mode
                )
                restartRequestedAt = System.currentTimeMillis()
                startMonitorService(context)
                waitForService(true)
            } else {
                runShell(instrumentation, "dumpsys battery unplug")
                assumeTrue(
                    "Emulator did not expose a confirmed disconnected battery snapshot",
                    waitForPowerState(context, expectedConnected = false)
                )
            }

            val deadline = SystemClock.elapsedRealtime() + 15_000L
            var state = ProtectionStore(context).read()
            while (state.mode != ProtectionMode.COUNTDOWN &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(50L)
                state = ProtectionStore(context).read()
            }
            assertEquals(
                if (missedWhileServiceStopped) {
                    "Sticky-service restart must recover a missed same-boot unplug"
                } else {
                    "The active foreground monitor must receive the runtime power broadcast"
                },
                ProtectionMode.COUNTDOWN,
                state.mode
            )
            assertTrue(ProtectionMonitorService.isPowerTransitionReceiverRegistered)
            if (missedWhileServiceStopped) {
                assertTrue(
                    "A missed event uses recovery-detection time, not an unknown original event time",
                    state.changedAtEpochMs >= restartRequestedAt
                )
            }
        } finally {
            context.stopService(Intent(context, ProtectionMonitorService::class.java))
            runCatching { waitForService(false) }
            AlarmScheduler.cancel(context)
            restorePreferences(transitionPrefs, transitionSnapshot)
            restorePreferences(statePrefs, stateSnapshot)
            restorePreferences(corruptionPrefs, corruptionSnapshot)
            restorePreferences(monitorPrefs, monitorSnapshot)
            runCatching { runShell(instrumentation, "dumpsys battery reset") }
            if (wasRunning && previousMonitorEnabled) {
                runCatching {
                    startMonitorService(context)
                    waitForService(true)
                }
            }
        }
    }

    private fun startMonitorService(context: Context) {
        ContextCompat.startForegroundService(
            context, Intent(context, ProtectionMonitorService::class.java)
        )
    }

    private fun waitForService(expectedRunning: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (ProtectionMonitorService.isRunning == expectedRunning &&
                ProtectionMonitorService.isPowerTransitionReceiverRegistered == expectedRunning
            ) return
            SystemClock.sleep(50L)
        }
        assertEquals(expectedRunning, ProtectionMonitorService.isRunning)
        assertEquals(expectedRunning, ProtectionMonitorService.isPowerTransitionReceiverRegistered)
    }

    private fun waitForPowerState(context: Context, expectedConnected: Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (ExternalPowerState.readConnected(context) == expectedConnected) return true
            SystemClock.sleep(50L)
        }
        return ExternalPowerState.readConnected(context) == expectedConnected
    }

    private fun restorePreferences(prefs: SharedPreferences, snapshot: Map<String, *>) {
        val editor = prefs.edit().clear()
        snapshot.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        assertTrue("SharedPreferences test state must be restored", editor.commit())
    }

    private fun runShell(instrumentation: android.app.Instrumentation, command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
