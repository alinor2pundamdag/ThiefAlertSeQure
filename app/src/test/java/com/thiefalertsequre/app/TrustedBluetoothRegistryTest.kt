package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
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

    @Test fun malformedEntriesRejectTheWholeRegistryWithoutPartialTrust() {
        val valid = TrustedBluetoothRegistry.encode(
            listOf(TrustedBluetoothDevice("AA:BB:CC:DD:EE:10", "Watch"))
        )
        assertTrue(runCatching { TrustedBluetoothRegistry.decode("bad-entry\\n$valid") }.isFailure)
    }

    @Test fun emptyRegistryRemainsEmptyAndMalformedRegistryIsRejected() {
        assertTrue(TrustedBluetoothRegistry.decode("").isEmpty())
        assertTrue(runCatching { TrustedBluetoothRegistry.decode("not-valid") }.isFailure)
    }

    @Test fun base64RoundTripSupportsUnicodeAndEmptyNames() {
        val input = listOf(
            TrustedBluetoothDevice("AA:BB:CC:DD:EE:11", "HONOR Watch – 日本語"),
            TrustedBluetoothDevice("AA:BB:CC:DD:EE:12", "")
        )
        val expected = listOf(
            input[0],
            TrustedBluetoothDevice("AA:BB:CC:DD:EE:12", "Bluetooth accessory")
        )
        assertEquals(expected, TrustedBluetoothRegistry.decode(TrustedBluetoothRegistry.encode(input)))
    }

    @Test fun base64DecoderRejectsInvalidPaddingAndCharacters() {
        assertTrue(runCatching {
            TrustedBluetoothRegistry.decode("AA:BB:CC:DD:EE:13\\tA===")
        }.isFailure)
        assertTrue(runCatching {
            TrustedBluetoothRegistry.decode("AA:BB:CC:DD:EE:14\\t!!!!")
        }.isFailure)
    }

    @Test fun base64DecoderPreservesPreviouslyStoredJavaBase64Values() {
        assertEquals(
            listOf(TrustedBluetoothDevice("AA:BB:CC:DD:EE:15", "Watch One")),
            TrustedBluetoothRegistry.decode("AA:BB:CC:DD:EE:15\\tV2F0Y2ggT25l")
        )
    }
}
