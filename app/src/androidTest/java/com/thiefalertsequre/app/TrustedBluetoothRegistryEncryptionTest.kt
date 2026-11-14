package com.thiefalertsequre.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TrustedBluetoothRegistryEncryptionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefsName = "trusted_registry_test_${UUID.randomUUID()}"
    private val prefs by lazy { context.getSharedPreferences(prefsName, Context.MODE_PRIVATE) }

    @After fun cleanPreferences() {
        prefs.edit().clear().commit()
    }

    @Test fun keystoreCipherAuthenticatesPurposeAndRoundTrips() {
        val encoded = LocalPreferenceCipher.encrypt("test-purpose", "private Bluetooth identifier")
        assertTrue(LocalPreferenceCipher.isEncrypted(encoded))
        assertEquals("private Bluetooth identifier", LocalPreferenceCipher.decrypt("test-purpose", encoded))
        assertFalse(runCatching { LocalPreferenceCipher.decrypt("different-purpose", encoded) }.isSuccess)
    }

    @Test fun plaintextRegistryAndBaselinesMigrateWithoutLosingMembership() {
        val devices = listOf(TrustedBluetoothDevice("AA:BB:CC:DD:EE:FF", "Owner Watch"))
        prefs.edit()
            .putString(TrustedBluetoothRegistry.KEY_DEVICES, TrustedBluetoothRegistry.encode(devices))
            .putString("trusted_bluetooth_baselines_v1", "AA:BB:CC:DD:EE:FF")
            .commit()

        assertEquals(devices, TrustedBluetoothRegistry.read(prefs))
        assertTrue(TrustedBluetoothRegistry.isBaselineConnected(prefs, "AA:BB:CC:DD:EE:FF"))
        assertTrue(LocalPreferenceCipher.isEncrypted(prefs.getString(TrustedBluetoothRegistry.KEY_DEVICES, null)!!))
        assertTrue(LocalPreferenceCipher.isEncrypted(prefs.getString("trusted_bluetooth_baselines_v1", null)!!))
    }

    @Test fun addAndRemovePersistEncryptedRegistry() {
        assertTrue(TrustedBluetoothRegistry.add(prefs, "AA:BB:CC:DD:EE:01", "Earbuds"))
        val stored = prefs.getString(TrustedBluetoothRegistry.KEY_DEVICES, null)!!
        assertTrue(LocalPreferenceCipher.isEncrypted(stored))
        assertTrue(TrustedBluetoothRegistry.isTrusted(prefs, "AA:BB:CC:DD:EE:01"))
        assertTrue(TrustedBluetoothRegistry.remove(prefs, "AA:BB:CC:DD:EE:01"))
        assertTrue(TrustedBluetoothRegistry.read(prefs).isEmpty())
    }

    @Test fun unreadableEncryptedRegistryFailsClosedWithoutOverwritingData() {
        val unreadable = "tas-aesgcm:v1:not-valid"
        prefs.edit().putString(TrustedBluetoothRegistry.KEY_DEVICES, unreadable).commit()
        assertTrue(TrustedBluetoothRegistry.read(prefs).isEmpty())
        assertFalse(TrustedBluetoothRegistry.add(prefs, "AA:BB:CC:DD:EE:02", "Watch"))
        assertEquals(unreadable, prefs.getString(TrustedBluetoothRegistry.KEY_DEVICES, null))
    }
}
