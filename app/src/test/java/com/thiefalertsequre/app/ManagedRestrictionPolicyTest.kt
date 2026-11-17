package com.thiefalertsequre

import android.os.UserManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedRestrictionPolicyTest {
    @Test fun addUserRestrictionNeverUsesApi34GlobalScope() {
        assertFalse(ManagedRestrictionPolicy.useGlobalRestriction(34, UserManager.DISALLOW_ADD_USER))
        assertFalse(ManagedRestrictionPolicy.useGlobalRestriction(36, UserManager.DISALLOW_ADD_USER))
    }

    @Test fun supportedRestrictionsUseGlobalScopeOnApi34AndLater() {
        assertTrue(ManagedRestrictionPolicy.useGlobalRestriction(34, UserManager.DISALLOW_FACTORY_RESET))
        assertTrue(ManagedRestrictionPolicy.useGlobalRestriction(36, UserManager.DISALLOW_USB_FILE_TRANSFER))
    }

    @Test fun everyRestrictionUsesLocalScopeBeforeApi34() {
        assertFalse(ManagedRestrictionPolicy.useGlobalRestriction(26, UserManager.DISALLOW_FACTORY_RESET))
        assertFalse(ManagedRestrictionPolicy.useGlobalRestriction(33, UserManager.DISALLOW_USB_FILE_TRANSFER))
    }
}
