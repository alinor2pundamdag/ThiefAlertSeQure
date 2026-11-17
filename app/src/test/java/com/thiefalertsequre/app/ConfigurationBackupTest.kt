package com.thiefalertsequre.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationBackupTest {
    @Test fun passwordDerivedAesGcmBackupRoundTrips() {
        val payload = """{"schema":1,"settings":[{"key":"monitor_enabled","value":true}]}"""
            .toByteArray(Charsets.UTF_8)
        val password = "correct horse battery".toCharArray()
        val encrypted = ConfigurationBackup.encryptPayload(payload, password)
        password.fill('\u0000')
        assertFalse(payload.contentEquals(encrypted))
        val restored = ConfigurationBackup.decryptPayload(encrypted, "correct horse battery".toCharArray())
        assertArrayEquals(payload, restored)
    }

    @Test fun wrongPasswordAndTamperingAreRejected() {
        val payload = """{"schema":1,"settings":[]}""".toByteArray(Charsets.UTF_8)
        val encrypted = ConfigurationBackup.encryptPayload(payload, "correct horse battery".toCharArray())
        assertTrue(runCatching {
            ConfigurationBackup.decryptPayload(encrypted, "incorrect horse battery".toCharArray())
        }.isFailure)
        val tampered = encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertTrue(runCatching {
            ConfigurationBackup.decryptPayload(tampered, "correct horse battery".toCharArray())
        }.isFailure)
    }
}
