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

    @Test fun emptyRegistryRemainsEmpty() {
        assertTrue(TrustedBluetoothRegistry.decode("").isEmpty())
        assertFalse(TrustedBluetoothRegistry.decode("not-valid").any())
    }

    @Test fun base64RoundTripSupportsUnicodeAndEmptyNames() {
        val input = listOf(
            TrustedBluetoothDevice("AA:00", "HONOR Watch – 日本語"),
            TrustedBluetoothDevice("BB:00", "")
        )
        assertEquals(input, TrustedBluetoothRegistry.decode(TrustedBluetoothRegistry.encode(input)))
    }

    @Test fun base64DecoderRejectsInvalidPaddingAndCharacters() {
        assertTrue(TrustedBluetoothRegistry.decode("AA:00\tA===" ).isEmpty())
        assertTrue(TrustedBluetoothRegistry.decode("AA:00\t!!!!").isEmpty())
    }
}
