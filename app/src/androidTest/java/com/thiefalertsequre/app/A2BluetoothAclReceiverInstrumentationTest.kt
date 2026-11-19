package com.thiefalertsequre.app

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class A2BluetoothAclReceiverInstrumentationTest {
    @Test fun sharedProcessorPersistsDisconnectAndReconnectCancelsItWithoutTheService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val store = ProtectionStore(context)
        val originalState = store.read()
        val previousMonitor = SafePreferences.boolean(
            prefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val previousA2 = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
        val hasBluetoothPermission = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        assumeTrue(
            "The instrumentation harness must grant BLUETOOTH_CONNECT on API 31+ before this test",
            hasBluetoothPermission
        )
        val wasRunning = ProtectionMonitorService.isRunning
        val address = "02:00:00:00:02:61"
        val bootCount = ProtectionClock.bootCount(context)
        assumeTrue("The emulator must expose BOOT_COUNT for durable A2 deadlines", bootCount >= 0)
        try {
            context.stopService(Intent(context, ProtectionMonitorService::class.java))
            val stopDeadline = SystemClock.elapsedRealtime() + 5_000L
            while (ProtectionMonitorService.isRunning && SystemClock.elapsedRealtime() < stopDeadline) {
                SystemClock.sleep(50L)
            }
            ProtectionMonitorService.isBluetoothReceiverRegistered = false
            assumeTrue("A clean ARMED state is required for the shared A2 processor test",
                store.write(ProtectionState(
                    ProtectionMode.ARMED, 0L, System.currentTimeMillis(), 0L, bootCount
                )))
            assertTrue(prefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, true)
                .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, true)
                .commit())
            assertTrue(TrustedBluetoothRegistry.add(prefs, address, "Test accessory"))
            assertFalse(TrustedBluetoothRegistry.markConnected(prefs, address, true))
            assertTrue(A2BluetoothReceiverController.sync(context))

            val disconnectFinished = CountDownLatch(1)
            enqueueA2BluetoothAddressEvent(
                context,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                address,
                A2BluetoothEventReceipt.capture(context),
                "a2_instrumentation_manifest_path",
                onFinished = { disconnectFinished.countDown() }
            )
            assertTrue("The shared manifest/runtime event queue should finish the disconnect transition",
                disconnectFinished.await(30, TimeUnit.SECONDS))
            assertTrue("shared A2 processor should persist an A2 deadline while the service is absent",
                A2DisconnectScheduler.hasPendingIntent(context, address, fallback = true))

            val reconnectFinished = CountDownLatch(1)
            enqueueA2BluetoothAddressEvent(
                context,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                address,
                A2BluetoothEventReceipt.capture(context),
                "a2_instrumentation_manifest_path",
                onFinished = { reconnectFinished.countDown() }
            )
            assertTrue("The shared manifest/runtime event queue should finish the reconnect transition",
                reconnectFinished.await(30, TimeUnit.SECONDS))
            assertFalse("Reconnect must cancel the durable deadline without a running service",
                A2DisconnectScheduler.hasPendingIntent(context, address, fallback = true))
            assertTrue("Reconnect must restore the trusted device's connected baseline",
                TrustedBluetoothRegistry.isBaselineConnected(prefs, address))
        } finally {
            A2DisconnectScheduler.cancel(context, address)
            TrustedBluetoothRegistry.remove(prefs, address)
            prefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, previousMonitor)
                .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, previousA2)
                .commit()
            store.write(originalState)
            A2BluetoothReceiverController.sync(context)
            if (wasRunning && previousMonitor) {
                runCatching {
                    ContextCompat.startForegroundService(
                        context, Intent(context, ProtectionMonitorService::class.java)
                    )
                }
            }
        }
    }
}
