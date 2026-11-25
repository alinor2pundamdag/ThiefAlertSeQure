package com.thiefalertsequre.app

import org.junit.Assert.*
import org.junit.Test

class BypassChargingControllerTest {
    @Test fun adapterCertificationRequiresExactModelApiAndFirmwareFingerprint() {
        val certification = BypassChargingCertification(
            adapterId = "honor-test",
            manufacturer = "HONOR",
            brand = "HONOR",
            model = "ELP-NX9",
            minApiLevel = 36,
            maxApiLevel = 36,
            firmwareFingerprints = setOf("honor/elp-nx9/build:1.0/release-keys")
        )
        val exact = BypassChargingDevice(
            "HONOR", "HONOR", "ELP-NX9", 36,
            "honor/elp-nx9/build:1.0/release-keys"
        )
        assertTrue(certification.matches(exact))
        assertFalse(certification.matches(exact.copy(firmwareFingerprint = "honor/elp-nx9/build:2.0/release-keys")))
        assertFalse(certification.matches(exact.copy(model = "ELP-NX9-OTHER")))
        assertFalse(certification.matches(exact.copy(apiLevel = 35)))
        assertFalse(certification.matches(exact.copy(firmwareFingerprint = "")))
        assertFalse(certification.copy(firmwareFingerprints = setOf("*")).matches(exact))
    }

    @Test fun duplicateAdapterCertificationFailsClosed() {
        val certification = BypassChargingCertification(
            "duplicate", "HONOR", "HONOR", "ELP-NX9", 36, 36,
            setOf("honor/elp-nx9/build:1.0/release-keys")
        )
        val device = BypassChargingDevice(
            "HONOR", "HONOR", "ELP-NX9", 36,
            "honor/elp-nx9/build:1.0/release-keys"
        )
        val adapter = Fake(supported, false)
        val registration = BypassChargingAdapterRegistration(certification) { adapter }
        assertTrue(BypassChargingAdapterRegistry.resolve(device, listOf(registration, registration)).isEmpty())
    }

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

    @Test fun unsupportedFirstAdapterDoesNotMaskLaterSupportedAdapter() {
        val unsupported=Fake(BypassChargingProbe(BypassChargingSupport.UNSUPPORTED,BypassChargingAuthorization.UNAVAILABLE,"not this model"),false)
        val supportedAdapter=Fake(supported,false)
        val result=BypassChargingController(listOf(unsupported,supportedAdapter)).setEnabled(true)
        assertEquals(BypassChargingStatus.ENABLED_VERIFIED,result.status)
        assertEquals("test-adapter",result.providerId)
        assertEquals(0,unsupported.commands)
        assertEquals(1,supportedAdapter.commands)
    }

    @Test fun failedProbeDoesNotMaskLaterSupportedAdapter() {
        val broken=object:BypassChargingAdapter {
            override val id="broken"
            override fun probe():BypassChargingProbe=error("OEM service unavailable")
            override fun setEnabled(enabled:Boolean)=error("must not be called")
            override fun readEnabled():Boolean?=null
        }
        val supportedAdapter=Fake(supported,false)
        val result=BypassChargingController(listOf(broken,supportedAdapter)).inspect()
        assertEquals(BypassChargingStatus.DISABLED_VERIFIED,result.status)
        assertEquals("test-adapter",result.providerId)
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
