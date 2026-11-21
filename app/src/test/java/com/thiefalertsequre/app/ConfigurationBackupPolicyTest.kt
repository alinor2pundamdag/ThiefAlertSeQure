package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigurationBackupPolicyTest {
    @Test fun restoreIsAllowedOnlyWhileProtectionIsDisarmed() {
        assertTrue(ConfigurationBackupPolicy.canRestore(ProtectionMode.DISARMED))
        listOf(
            ProtectionMode.ARMED,
            ProtectionMode.COUNTDOWN,
            ProtectionMode.ALARM_TRIGGERED
        ).forEach { mode ->
            assertFalse(ConfigurationBackupPolicy.canRestore(mode))
            assertThrows(IllegalArgumentException::class.java) {
                ConfigurationBackupPolicy.requireSafeRestoreMode(mode)
            }
        }
        ConfigurationBackupPolicy.requireSafeRestoreMode(ProtectionMode.DISARMED)
    }
}
