package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedBluetoothRegistryTest {
    @Test fun encodesAndDecodesMultipleDevicesWithoutLosingNames() {
        val input = listOf(
            TrustedBluetoothDevice("AA:BB:CC:DD:EE:01", "Watch One"),
            TrustedBluetoothDevice("AA:BB:CC:DD:EE:02", "Earbuds | Left")
        )
        assertEquals(input, TrustedBluetoothRegistry.decode(TrustedBluetoothRegistry.encode(input)))
    }

    @Test fun duplicateAddressesAreCollapsedCaseInsensitively() {
        val input = listOf(
            TrustedBluetoothDevice("aa:bb:cc:dd:ee:ff", "First"),
            TrustedBluetoothDevice("AA:BB:CC:DD:EE:FF", "Second")
        )
        assertEquals(1, TrustedBluetoothRegistry.decode(TrustedBluetoothRegistry.encode(input)).size)
    }

    @Test fun malformedEntriesAreIgnoredWithoutDiscardingValidEntries() {
        val valid = TrustedBluetoothRegistry.encode(listOf(TrustedBluetoothDevice("AA:BB", "Watch")))
        assertEquals(listOf(TrustedBluetoothDevice("AA:BB", "Watch")), TrustedBluetoothRegistry.decode("bad-entry\n$valid"))
    }

    @Test fun acceptsOnlyBluetoothMacAddressFormat() {
        assertTrue(TrustedBluetoothRegistry.isValidAddress("AA:BB:CC:DD:EE:FF"))
        assertTrue(TrustedBluetoothRegistry.isValidAddress("aa:bb:cc:dd:ee:ff"))
        assertFalse(TrustedBluetoothRegistry.isValidAddress("AA:BB"))
        assertFalse(TrustedBluetoothRegistry.isValidAddress("not-an-address"))
        assertFalse(TrustedBluetoothRegistry.isValidAddress("AA:BB:CC:DD:EE:FG"))
    }

    @Test fun decodedRegistryDropsInvalidAddresses() {
        val mixed = TrustedBluetoothRegistry.encode(listOf(
            TrustedBluetoothDevice("AA:BB:CC:DD:EE:FF", "Valid"),
            TrustedBluetoothDevice("AA:BB", "Invalid")
        ))
        assertEquals(listOf(TrustedBluetoothDevice("AA:BB:CC:DD:EE:FF", "Valid")),
            TrustedBluetoothRegistry.decode(mixed).filter { TrustedBluetoothRegistry.isValidAddress(it.address) })
    }

    @Test fun emptyRegistryRemainsEmpty() {
        assertTrue(TrustedBluetoothRegistry.decode("").isEmpty())
        assertFalse(TrustedBluetoothRegistry.decode("not-valid").any())
    }
}
