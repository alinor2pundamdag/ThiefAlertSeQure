package com.thiefalertsequre.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrustedBluetoothRegistryEncryptionTest {
    private val prefs by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("trusted_bluetooth_encryption_test", Context.MODE_PRIVATE)
    }

    @Test
    fun trustedDeviceNamesAddressesAndBaselinesAreEncryptedAtRest() {
        prefs.edit().clear().commit()
        val address = "AA:BB:CC:DD:EE:01"
        val name = "HONOR Watch Pro"
        try {
            assertTrue(TrustedBluetoothRegistry.add(prefs, address, name))
            TrustedBluetoothRegistry.markConnected(prefs, address, true)
            val storedDevices = prefs.getString(TrustedBluetoothRegistry.KEY_DEVICES, null)!!
            val storedBaselines = prefs.getString(TrustedBluetoothRegistry.KEY_BASELINES, null)!!
            assertTrue(LocalPreferenceCipher.isEncrypted(storedDevices))
            assertTrue(LocalPreferenceCipher.isEncrypted(storedBaselines))
            assertFalse(storedDevices.contains(address))
            assertFalse(storedDevices.contains(name))
            assertFalse(storedBaselines.contains(address))
            assertEquals(listOf(TrustedBluetoothDevice(address, name)), TrustedBluetoothRegistry.read(prefs))
            assertTrue(TrustedBluetoothRegistry.isBaselineConnected(prefs, address))
            val revisionKeys = prefs.all.keys.filter { it.startsWith("trusted_bluetooth_device_revision_v1_") }
            assertTrue(revisionKeys.none { it.contains("AA_BB_CC_DD_EE_01") })
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun legacyPlaintextRegistryBaselinesAndRevisionMigrateWithoutLosingTrust() {
        prefs.edit().clear().commit()
        val address = "AA:BB:CC:DD:EE:02"
        val name = "Legacy earbuds"
        val legacyRevisionKey = "trusted_bluetooth_device_revision_v1_AA_BB_CC_DD_EE_02"
        try {
            prefs.edit()
                .putString(TrustedBluetoothRegistry.KEY_DEVICES, TrustedBluetoothRegistry.encode(listOf(TrustedBluetoothDevice(address, name))))
                .putString(TrustedBluetoothRegistry.KEY_BASELINES, address)
                .putLong(legacyRevisionKey, 7L)
                .commit()

            assertEquals(listOf(TrustedBluetoothDevice(address, name)), TrustedBluetoothRegistry.read(prefs))
            assertTrue(TrustedBluetoothRegistry.isBaselineConnected(prefs, address))
            assertEquals(7L, TrustedBluetoothRegistry.deviceRevision(prefs, address))
            assertTrue(LocalPreferenceCipher.isEncrypted(prefs.getString(TrustedBluetoothRegistry.KEY_DEVICES, null)!!))
            assertTrue(LocalPreferenceCipher.isEncrypted(prefs.getString(TrustedBluetoothRegistry.KEY_BASELINES, null)!!))
            assertFalse(prefs.contains(legacyRevisionKey))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun legacySingleDeviceFieldsMigrateAndAreRemoved() {
        prefs.edit().clear().commit()
        val address = "AA:BB:CC:DD:EE:03"
        val name = "Legacy watch"
        try {
            prefs.edit()
                .putString(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS, address)
                .putString(ProtectionMonitorService.KEY_BT_TRUSTED_NAME, name)
                .commit()
            assertEquals(listOf(TrustedBluetoothDevice(address, name)), TrustedBluetoothRegistry.read(prefs))
            assertFalse(prefs.contains(ProtectionMonitorService.KEY_BT_TRUSTED_ADDRESS))
            assertFalse(prefs.contains(ProtectionMonitorService.KEY_BT_TRUSTED_NAME))
            assertTrue(LocalPreferenceCipher.isEncrypted(prefs.getString(TrustedBluetoothRegistry.KEY_DEVICES, null)!!))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
