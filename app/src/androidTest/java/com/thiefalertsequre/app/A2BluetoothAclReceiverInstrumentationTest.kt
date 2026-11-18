package com.thiefalertsequre.app

import android.bluetooth.BluetoothAdapter
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
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class A2BluetoothAclReceiverInstrumentationTest {
    @Test fun manifestReceiverPersistsDisconnectAndReconnectCancelsItWithoutTheService() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val store = ProtectionStore(context)
        val originalState = store.read()
        val previousMonitor = SafePreferences.boolean(
            prefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val previousA2 = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
        val hadBluetoothPermission = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        val wasRunning = ProtectionMonitorService.isRunning
        val address = "02:00:00:00:02:61"
        val bootCount = ProtectionClock.bootCount(context)
        assumeTrue("The emulator must expose BOOT_COUNT for durable A2 deadlines", bootCount >= 0)
        val adapter = BluetoothAdapter.getDefaultAdapter()
        assumeNotNull(adapter)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !hadBluetoothPermission) {
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName, android.Manifest.permission.BLUETOOTH_CONNECT
                )
            }
            val device = adapter!!.getRemoteDevice(address)
            context.stopService(Intent(context, ProtectionMonitorService::class.java))
            val stopDeadline = SystemClock.elapsedRealtime() + 5_000L
            while (ProtectionMonitorService.isRunning && SystemClock.elapsedRealtime() < stopDeadline) {
                SystemClock.sleep(50L)
            }
            ProtectionMonitorService.isBluetoothReceiverRegistered = false
            assumeTrue("A clean ARMED state is required for the manifest receiver test",
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
            enqueueA2BluetoothEvent(
                context,
                Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED).putExtra(BluetoothDevice.EXTRA_DEVICE, device),
                A2BluetoothEventReceipt.capture(context),
                "a2_instrumentation_manifest_path",
                onFinished = { disconnectFinished.countDown() }
            )
            assertTrue("The shared manifest/runtime event queue should finish the disconnect transition",
                disconnectFinished.await(5, TimeUnit.SECONDS))
            assertTrue("Manifest receiver should persist an A2 deadline while the service is absent",
                A2DisconnectScheduler.hasPendingIntent(context, address, fallback = true))

            val reconnectFinished = CountDownLatch(1)
            enqueueA2BluetoothEvent(
                context,
                Intent(BluetoothDevice.ACTION_ACL_CONNECTED).putExtra(BluetoothDevice.EXTRA_DEVICE, device),
                A2BluetoothEventReceipt.capture(context),
                "a2_instrumentation_manifest_path",
                onFinished = { reconnectFinished.countDown() }
            )
            assertTrue("The shared manifest/runtime event queue should finish the reconnect transition",
                reconnectFinished.await(5, TimeUnit.SECONDS))
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
            if (Build.VERSION.SDK_INT >= 31 && !hadBluetoothPermission) {
                runCatching {
                    instrumentation.uiAutomation.revokeRuntimePermission(
                        context.packageName, android.Manifest.permission.BLUETOOTH_CONNECT
                    )
                }
            }
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
