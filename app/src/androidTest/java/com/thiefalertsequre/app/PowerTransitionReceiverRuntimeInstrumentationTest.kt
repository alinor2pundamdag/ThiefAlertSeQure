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
    @Test fun manifestReceiverHandlesPowerDisconnectWhileMonitorIsRunning() {
        exercisePowerDisconnect(monitorServiceStopped = false)
    }

    @Test fun manifestReceiverHandlesPowerDisconnectWhileMonitorServiceIsStopped() {
        exercisePowerDisconnect(monitorServiceStopped = true)
    }

    private fun exercisePowerDisconnect(monitorServiceStopped: Boolean) {
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
                monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, false).commit()
            )
            assertTrue(PowerTransitionReceiverController.sync(context))
            assertTrue("The manifest receiver must be disabled when monitoring is opted out",
                !PowerTransitionReceiverController.isEnabled(context))
            assertTrue(
                monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, true).commit()
            )
            assertTrue(PowerTransitionReceiverController.sync(context))
            assertTrue("The manifest receiver must be enabled while monitoring is opted in",
                PowerTransitionReceiverController.isEnabled(context))
            assertTrue(transitionPrefs.edit().clear().commit())
            assertTrue(statePrefs.edit().clear().commit())
            assertTrue(corruptionPrefs.edit().clear().commit())
            val armed = PowerTransitionCoordinator.onPowerConnected(context)
            assertEquals(ProtectionMode.ARMED, armed.mode)

            startMonitorService(context)
            waitForService(true)

            var disconnectRequestedAt = 0L
            if (monitorServiceStopped) {
                context.stopService(Intent(context, ProtectionMonitorService::class.java))
                waitForService(false)
                assertTrue("The manifest receiver remains enabled while monitoring is opted in",
                    PowerTransitionReceiverController.isEnabled(context))
                assertEquals(ProtectionMode.ARMED, ProtectionStore(context).read().mode)
                disconnectRequestedAt = System.currentTimeMillis()
                runShell(instrumentation, "dumpsys battery unplug")
                assumeTrue(
                    "Emulator did not expose a confirmed disconnected battery snapshot",
                    waitForPowerState(context, expectedConnected = false)
                )
            } else {
                disconnectRequestedAt = System.currentTimeMillis()
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
                if (monitorServiceStopped) {
                    "The manifest receiver must process a disconnect while the service is stopped"
                } else {
                    "The manifest receiver must process a disconnect while the service is active"
                },
                ProtectionMode.COUNTDOWN,
                state.mode
            )
            assertTrue("The protected manifest receiver remains enabled after processing",
                PowerTransitionReceiverController.isEnabled(context))
            assertEquals(monitorServiceStopped, !ProtectionMonitorService.isRunning)
            if (monitorServiceStopped) {
                assertTrue(
                    "A manifest-delivered disconnect uses event-receipt time while the service is absent",
                    state.changedAtEpochMs >= disconnectRequestedAt
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
            PowerTransitionReceiverController.sync(context)
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
            if (ProtectionMonitorService.isRunning == expectedRunning) return
            SystemClock.sleep(50L)
        }
        assertEquals(expectedRunning, ProtectionMonitorService.isRunning)
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
