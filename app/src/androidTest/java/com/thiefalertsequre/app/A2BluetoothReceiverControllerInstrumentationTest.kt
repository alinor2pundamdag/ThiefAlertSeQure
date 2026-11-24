package com.thiefalertsequre.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class A2BluetoothReceiverControllerInstrumentationTest {
    @Test fun manifestReceiverIsGatedByMonitorA2AndBluetoothPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val previousMonitor = SafePreferences.boolean(
            prefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val previousA2 = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
        val component = ComponentName(context, A2BluetoothAclReceiver::class.java)
        try {
            assertTrue(prefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, false)
                .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, true)
                .commit())
            assertTrue(A2BluetoothReceiverController.sync(context))
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                context.packageManager.getComponentEnabledSetting(component))

            assertTrue(prefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, true)
                .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, true)
                .commit())
            assertTrue(A2BluetoothReceiverController.sync(context))
            val permissionGranted = Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            assertEquals(
                if (permissionGranted) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                context.packageManager.getComponentEnabledSetting(component)
            )
        } finally {
            prefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, previousMonitor)
                .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, previousA2)
                .commit()
            A2BluetoothReceiverController.sync(context)
        }
    }
}
