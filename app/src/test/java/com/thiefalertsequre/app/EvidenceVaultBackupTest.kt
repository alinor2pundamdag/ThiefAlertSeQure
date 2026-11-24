package com.thiefalertsequre.app

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceVaultBackupTest {
    @Test fun exportRejectsShortPasswordAndClearsBuffer() {
        val password = "too-short".toCharArray()
        assertThrows(IllegalArgumentException::class.java) {
            EvidenceVaultBackup.encrypt(emptyList(), password)
        }
        assertTrue(password.all { it == '\u0000' })
    }

    @Test fun restoreRejectsUnknownHeaderAndClearsBuffer() {
        // Minimum structurally sized payload, but not a valid TASEV1 backup.
        val password = "strong-backup-password".toCharArray()
        val invalid = ByteArray(58)
        assertThrows(IllegalArgumentException::class.java) {
            EvidenceVaultBackup.decrypt(invalid, password)
        }
        assertTrue(password.all { it == '\u0000' })
    }
}
