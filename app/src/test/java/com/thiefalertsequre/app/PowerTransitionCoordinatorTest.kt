package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerTransitionCoordinatorTest {
    @Test fun acceptsOnlyConfirmedConnectedSnapshot() {
        assertTrue(PowerTransitionCoordinator.acceptsPowerConnectedEvent(true))
        assertFalse(PowerTransitionCoordinator.acceptsPowerConnectedEvent(false))
        assertFalse(PowerTransitionCoordinator.acceptsPowerConnectedEvent(null))
    }
}
