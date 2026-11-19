package com.thiefalertsequre.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val previousComponentState = context.packageManager.getComponentEnabledSetting(component)
        val hadBaseline = prefs.contains(TrustedBluetoothRegistry.KEY_BASELINES)
        val previousBaseline = prefs.getString(TrustedBluetoothRegistry.KEY_BASELINES, null)
        val hadRevision = prefs.contains("trusted_bluetooth_registry_revision_v1")
        val previousRevision = prefs.getLong("trusted_bluetooth_registry_revision_v1", 0L)
        val previousLegacyBaseline = SafePreferences.boolean(
            prefs, ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false
        )
        assertTrue(
            "Bluetooth ACL receiver must be exported so Android's privileged Bluetooth UID can deliver protected broadcasts after process death",
            context.packageManager.getReceiverInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).exported
        )
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
            val restore = prefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, previousMonitor)
                .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, previousA2)
                .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, previousLegacyBaseline)
            if (hadBaseline) restore.putString(TrustedBluetoothRegistry.KEY_BASELINES, previousBaseline)
            else restore.remove(TrustedBluetoothRegistry.KEY_BASELINES)
            if (hadRevision) restore.putLong("trusted_bluetooth_registry_revision_v1", previousRevision)
            else restore.remove("trusted_bluetooth_registry_revision_v1")
            assertTrue(restore.commit())
            context.packageManager.setComponentEnabledSetting(
                component, previousComponentState, PackageManager.DONT_KILL_APP
            )
        }
    }

    @Test fun disabledReceiverRequiresAReadableFreshBaselineBeforeReenable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val component = ComponentName(context, A2BluetoothAclReceiver::class.java)
        val permissionGranted = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        org.junit.Assume.assumeTrue("Test runner must grant BLUETOOTH_CONNECT", permissionGranted)

        val previousMonitor = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_ENABLED, true)
        val previousA2 = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
        val previousComponentState = context.packageManager.getComponentEnabledSetting(component)
        val hadBaseline = prefs.contains(TrustedBluetoothRegistry.KEY_BASELINES)
        val previousBaseline = prefs.getString(TrustedBluetoothRegistry.KEY_BASELINES, null)
        val hadRevision = prefs.contains("trusted_bluetooth_registry_revision_v1")
        val previousRevision = prefs.getLong("trusted_bluetooth_registry_revision_v1", 0L)
        val previousLegacyBaseline = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
        val address = "AA:BB:CC:DD:EE:31"
        try {
            assertTrue(prefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, true)
                .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, true)
                .putString(
                    TrustedBluetoothRegistry.KEY_BASELINES,
                    LocalPreferenceCipher.encrypt(TrustedBluetoothRegistry.KEY_BASELINES, address)
                )
                .putLong("trusted_bluetooth_registry_revision_v1", 17L)
                .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, true)
                .commit())
            context.packageManager.setComponentEnabledSetting(
                component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )

            assertTrue(A2BluetoothReceiverController.sync(context))
            assertFalse(TrustedBluetoothRegistry.hasConfirmedConnectionBaseline(prefs, address))
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                context.packageManager.getComponentEnabledSetting(component)
            )
        } finally {
            val restore = prefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, previousMonitor)
                .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, previousA2)
                .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, previousLegacyBaseline)
            if (hadBaseline) restore.putString(TrustedBluetoothRegistry.KEY_BASELINES, previousBaseline)
            else restore.remove(TrustedBluetoothRegistry.KEY_BASELINES)
            if (hadRevision) restore.putLong("trusted_bluetooth_registry_revision_v1", previousRevision)
            else restore.remove("trusted_bluetooth_registry_revision_v1")
            assertTrue(restore.commit())
            context.packageManager.setComponentEnabledSetting(
                component, previousComponentState, PackageManager.DONT_KILL_APP
            )
        }
    }

    @Test fun corruptBaselineKeepsManifestReceiverDisabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, 0)
        val component = ComponentName(context, A2BluetoothAclReceiver::class.java)
        val permissionGranted = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        org.junit.Assume.assumeTrue("Test runner must grant BLUETOOTH_CONNECT", permissionGranted)

        val previousMonitor = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_ENABLED, true)
        val previousA2 = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
        val previousComponentState = context.packageManager.getComponentEnabledSetting(component)
        val hadBaseline = prefs.contains(TrustedBluetoothRegistry.KEY_BASELINES)
        val previousBaseline = prefs.getString(TrustedBluetoothRegistry.KEY_BASELINES, null)
        val hadRevision = prefs.contains("trusted_bluetooth_registry_revision_v1")
        val previousRevision = prefs.getLong("trusted_bluetooth_registry_revision_v1", 0L)
        val previousLegacyBaseline = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, false)
        val malformed = "tas-aesgcm:v1:not-valid-base64"
        try {
            assertTrue(prefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, true)
                .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, true)
                .putString(TrustedBluetoothRegistry.KEY_BASELINES, malformed)
                .commit())
            context.packageManager.setComponentEnabledSetting(
                component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )

            assertFalse(A2BluetoothReceiverController.sync(context))
            assertEquals(malformed, prefs.getString(TrustedBluetoothRegistry.KEY_BASELINES, null))
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                context.packageManager.getComponentEnabledSetting(component)
            )
        } finally {
            val restore = prefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_ENABLED, previousMonitor)
                .putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, previousA2)
                .putBoolean(ProtectionMonitorService.KEY_BT_BASELINE_CONNECTED, previousLegacyBaseline)
            if (hadBaseline) restore.putString(TrustedBluetoothRegistry.KEY_BASELINES, previousBaseline)
            else restore.remove(TrustedBluetoothRegistry.KEY_BASELINES)
            if (hadRevision) restore.putLong("trusted_bluetooth_registry_revision_v1", previousRevision)
            else restore.remove("trusted_bluetooth_registry_revision_v1")
            assertTrue(restore.commit())
            context.packageManager.setComponentEnabledSetting(
                component, previousComponentState, PackageManager.DONT_KILL_APP
            )
        }
    }
}
