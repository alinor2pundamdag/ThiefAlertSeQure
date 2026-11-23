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
    fun duplicateDisconnectDoesNotCreateASecondConnectionBaselineTransition() {
        prefs.edit().clear().commit()
        val address = "AA:BB:CC:DD:EE:20"
        try {
            assertTrue(TrustedBluetoothRegistry.add(prefs, address, "Test watch"))
            assertFalse("First connect establishes, but does not replace, a baseline",
                TrustedBluetoothRegistry.markConnected(prefs, address, true))
            assertTrue("The first disconnect consumes the existing connected baseline",
                TrustedBluetoothRegistry.markConnected(prefs, address, false))
            assertFalse("A duplicate disconnect must not create a second grace-period candidate",
                TrustedBluetoothRegistry.markConnected(prefs, address, false))
            assertFalse(TrustedBluetoothRegistry.isBaselineConnected(prefs, address))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun wrongTypedRegistryPayloadFailsClosedAndIsRetained() {
        prefs.edit().clear().putBoolean(TrustedBluetoothRegistry.KEY_DEVICES, true).commit()
        try {
            assertTrue(TrustedBluetoothRegistry.read(prefs).isEmpty())
            assertTrue("Corrupt trust data must remain available for diagnosis", prefs.contains(TrustedBluetoothRegistry.KEY_DEVICES))
            assertTrue(prefs.getBoolean(TrustedBluetoothRegistry.KEY_DEVICES, false))
            assertFalse("Mutations must stop when the registry is corrupt", TrustedBluetoothRegistry.add(prefs, "AA:BB:CC:DD:EE:10", "Must not overwrite"))
            assertTrue(prefs.getBoolean(TrustedBluetoothRegistry.KEY_DEVICES, false))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun malformedEncryptedRegistryFailsClosedAndIsRetained() {
        val malformed = "tas-aesgcm:v1:not-valid-base64"
        prefs.edit().clear().putString(TrustedBluetoothRegistry.KEY_DEVICES, malformed).commit()
        try {
            assertTrue(TrustedBluetoothRegistry.read(prefs).isEmpty())
            assertEquals(malformed, prefs.getString(TrustedBluetoothRegistry.KEY_DEVICES, null))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun corruptEncryptedBaselinesPreventRegistryMutationAndAreRetained() {
        val malformed = "tas-aesgcm:v1:not-valid-base64"
        prefs.edit().clear()
            .putString(TrustedBluetoothRegistry.KEY_DEVICES, LocalPreferenceCipher.encrypt(
                TrustedBluetoothRegistry.KEY_DEVICES,
                TrustedBluetoothRegistry.encode(listOf(TrustedBluetoothDevice("AA:BB:CC:DD:EE:11", "Watch")))
            ))
            .putString(TrustedBluetoothRegistry.KEY_BASELINES, malformed)
            .commit()
        try {
            assertFalse(TrustedBluetoothRegistry.add(prefs, "AA:BB:CC:DD:EE:12", "Second"))
            assertTrue(
                "An unreadable baseline is treated as connected/unknown to prevent a false alarm",
                TrustedBluetoothRegistry.isBaselineConnected(prefs, "AA:BB:CC:DD:EE:11")
            )
            assertFalse(
                "The UI must not count an unreadable baseline as confirmed",
                TrustedBluetoothRegistry.hasConfirmedConnectionBaseline(prefs, "AA:BB:CC:DD:EE:11")
            )
            assertEquals(malformed, prefs.getString(TrustedBluetoothRegistry.KEY_BASELINES, null))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun invalidMacAddressesAreRejectedAtRegistryBoundaries() {
        prefs.edit().clear().commit()
        assertFalse(TrustedBluetoothRegistry.add(prefs, "not-a-mac", "Invalid"))
        assertFalse(TrustedBluetoothRegistry.remove(prefs, "not-a-mac"))
        var rejected = false
        try {
            TrustedBluetoothRegistry.decode("not-a-mac\\tSW52YWxpZA==")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Malformed persisted addresses must fail closed", rejected)
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
