package com.thiefalertsequre.app

import org.junit.Assert.*
import org.junit.Test

class BypassChargingControllerTest {
    @Test fun noAdapterFailsClosed() {
        val result=BypassChargingController(emptyList()) { 123L }.inspect()
        assertEquals(BypassChargingStatus.OEM_INTERFACE_UNAVAILABLE,result.status)
        assertEquals(BypassChargingMode.UNKNOWN,result.mode)
        assertFalse(result.canToggle)
        assertFalse(result.isVerified)
    }

    @Test fun unsupportedHardwareCannotBeToggled() {
        val adapter=Fake(BypassChargingProbe(BypassChargingSupport.UNSUPPORTED,BypassChargingAuthorization.UNAVAILABLE,"unsupported"),false)
        val result=BypassChargingController(listOf(adapter)).setEnabled(true)
        assertEquals(BypassChargingStatus.UNSUPPORTED,result.status)
        assertEquals(0,adapter.commands)
    }

    @Test fun authorizationIsRequiredBeforeCommand() {
        val adapter=Fake(BypassChargingProbe(BypassChargingSupport.SUPPORTED,BypassChargingAuthorization.REQUIRED,"approval"),false)
        val result=BypassChargingController(listOf(adapter)).setEnabled(true)
        assertEquals(BypassChargingStatus.AUTHORIZATION_REQUIRED,result.status)
        assertEquals(0,adapter.commands)
    }

    @Test fun matchingReadBackVerifiesCommand() {
        val adapter=Fake(supported,false)
        val result=BypassChargingController(listOf(adapter)) { 456L }.setEnabled(true)
        assertEquals(BypassChargingStatus.ENABLED_VERIFIED,result.status)
        assertEquals(BypassChargingMode.ENABLED,result.mode)
        assertTrue(result.canToggle)
        assertTrue(result.isVerified)
        assertEquals(456L,result.checkedAtEpochMs)
    }

    @Test fun unreadableStateNeverReportsEnabled() {
        val adapter=Fake(supported,false,unknownAfterCommand=true)
        val result=BypassChargingController(listOf(adapter)).setEnabled(true)
        assertEquals(BypassChargingStatus.STATE_UNVERIFIED,result.status)
        assertEquals(BypassChargingMode.UNKNOWN,result.mode)
        assertFalse(result.isVerified)
    }

    @Test fun rejectedCommandIsFailure() {
        val adapter=Fake(supported,false,accept=false)
        val result=BypassChargingController(listOf(adapter)).setEnabled(true)
        assertEquals(BypassChargingStatus.ACTIVATION_FAILED,result.status)
        assertEquals(BypassChargingMode.DISABLED,result.mode)
    }

    @Test fun honorGuidanceDoesNotClaimThirdPartyControl() {
        val guidance=BypassChargingGuidance.instructions(BypassChargingDevice("HONOR","HONOR","200 Pro",36))
        assertTrue(guidance.contains("More battery settings"))
        assertTrue(guidance.contains("no verified third-party control adapter"))
    }

    private val supported=BypassChargingProbe(BypassChargingSupport.SUPPORTED,BypassChargingAuthorization.AUTHORIZED,"test")
    private class Fake(
        private val probeResult:BypassChargingProbe,
        private var state:Boolean?,
        private val accept:Boolean=true,
        private val unknownAfterCommand:Boolean=false
    ):BypassChargingAdapter {
        override val id="test-adapter"
        var commands=0
        override fun probe()=probeResult
        override fun setEnabled(enabled:Boolean):Boolean {
            commands++
            if(accept && !unknownAfterCommand) state=enabled
            return accept
        }
        override fun readEnabled():Boolean?=if(unknownAfterCommand && commands>0) null else state
    }
}
