package com.thiefalertsequre.app

enum class BypassChargingSupport { SUPPORTED, UNSUPPORTED, UNKNOWN }
enum class BypassChargingAuthorization { AUTHORIZED, REQUIRED, UNAVAILABLE }
enum class BypassChargingMode { ENABLED, DISABLED, UNKNOWN }
enum class BypassChargingStatus {
    ENABLED_VERIFIED, DISABLED_VERIFIED, UNSUPPORTED, AUTHORIZATION_REQUIRED,
    OEM_INTERFACE_UNAVAILABLE, STATE_UNVERIFIED, ACTIVATION_FAILED
}

data class BypassChargingDevice(val manufacturer:String,val brand:String,val model:String,val apiLevel:Int)
data class BypassChargingProbe(val support:BypassChargingSupport,val authorization:BypassChargingAuthorization,val detail:String)

interface BypassChargingAdapter {
    val id:String
    fun probe():BypassChargingProbe
    fun setEnabled(enabled:Boolean):Boolean
    fun readEnabled():Boolean?
}

data class BypassChargingSnapshot(
    val support:BypassChargingSupport,
    val authorization:BypassChargingAuthorization,
    val mode:BypassChargingMode,
    val status:BypassChargingStatus,
    val providerId:String?,
    val detail:String,
    val checkedAtEpochMs:Long
) {
    val isVerified:Boolean get() = status==BypassChargingStatus.ENABLED_VERIFIED || status==BypassChargingStatus.DISABLED_VERIFIED
    val canToggle:Boolean get() = support==BypassChargingSupport.SUPPORTED &&
        authorization==BypassChargingAuthorization.AUTHORIZED && mode!=BypassChargingMode.UNKNOWN
    val modeLabel:String get() = when(mode) {
        BypassChargingMode.ENABLED -> "ON (verified)"
        BypassChargingMode.DISABLED -> "OFF (verified)"
        BypassChargingMode.UNKNOWN -> "UNKNOWN"
    }
    val statusLabel:String get() = when(status) {
        BypassChargingStatus.ENABLED_VERIFIED -> "Enabled — hardware state verified"
        BypassChargingStatus.DISABLED_VERIFIED -> "Disabled — hardware state verified"
        BypassChargingStatus.UNSUPPORTED -> "Unsupported on this device"
        BypassChargingStatus.AUTHORIZATION_REQUIRED -> "OEM authorization required"
        BypassChargingStatus.OEM_INTERFACE_UNAVAILABLE -> "No verified OEM control interface"
        BypassChargingStatus.STATE_UNVERIFIED -> "Hardware state could not be verified"
        BypassChargingStatus.ACTIVATION_FAILED -> "Activation/deactivation failed"
    }
}

/**
 * A vendor adapter must prove hardware support and authorization. A command is
 * never treated as success until readEnabled() confirms the resulting state.
 */
class BypassChargingController(
    private val adapters:List<BypassChargingAdapter>,
    private val nowEpochMs:()->Long = { System.currentTimeMillis() }
) {
    @Synchronized fun inspect():BypassChargingSnapshot {
        val adapter=adapters.firstOrNull() ?: return unavailable()
        return assess(adapter)
    }

    @Synchronized fun setEnabled(enabled:Boolean):BypassChargingSnapshot {
        val adapter=adapters.firstOrNull() ?: return unavailable()
        val before=assess(adapter)
        if(!before.canToggle) return before
        val requested=if(enabled) BypassChargingMode.ENABLED else BypassChargingMode.DISABLED
        if(before.mode==requested) return before
        val accepted=runCatching { adapter.setEnabled(enabled) }.getOrDefault(false)
        val observed=readMode(adapter)
        val status=when {
            observed==requested -> verifiedStatus(observed)
            observed==BypassChargingMode.UNKNOWN && accepted -> BypassChargingStatus.STATE_UNVERIFIED
            else -> BypassChargingStatus.ACTIVATION_FAILED
        }
        return BypassChargingSnapshot(
            before.support,before.authorization,observed,status,adapter.id,
            when {
                observed==requested -> "The OEM interface read-back confirms the requested mode."
                observed==BypassChargingMode.UNKNOWN && accepted -> "The OEM command was accepted, but the actual mode could not be read back."
                !accepted -> "The OEM interface did not confirm the requested change."
                else -> "The observed mode does not match the requested mode."
            },timestamp()
        )
    }

    private fun assess(adapter:BypassChargingAdapter):BypassChargingSnapshot {
        val probe=runCatching { adapter.probe() }.getOrElse {
            return snapshot(BypassChargingSupport.UNKNOWN,BypassChargingAuthorization.UNAVAILABLE,
                BypassChargingMode.UNKNOWN,BypassChargingStatus.OEM_INTERFACE_UNAVAILABLE,adapter.id,
                "The OEM capability probe failed. No system setting was changed.")
        }
        if(probe.support==BypassChargingSupport.UNSUPPORTED)
            return snapshot(probe.support,probe.authorization,BypassChargingMode.UNKNOWN,
                BypassChargingStatus.UNSUPPORTED,adapter.id,probe.detail)
        if(probe.support!=BypassChargingSupport.SUPPORTED)
            return snapshot(probe.support,probe.authorization,BypassChargingMode.UNKNOWN,
                BypassChargingStatus.OEM_INTERFACE_UNAVAILABLE,adapter.id,probe.detail)
        if(probe.authorization==BypassChargingAuthorization.REQUIRED)
            return snapshot(probe.support,probe.authorization,BypassChargingMode.UNKNOWN,
                BypassChargingStatus.AUTHORIZATION_REQUIRED,adapter.id,probe.detail)
        if(probe.authorization!=BypassChargingAuthorization.AUTHORIZED)
            return snapshot(probe.support,probe.authorization,BypassChargingMode.UNKNOWN,
                BypassChargingStatus.OEM_INTERFACE_UNAVAILABLE,adapter.id,probe.detail)
        val mode=readMode(adapter)
        return snapshot(probe.support,probe.authorization,mode,
            if(mode==BypassChargingMode.UNKNOWN) BypassChargingStatus.STATE_UNVERIFIED else verifiedStatus(mode),
            adapter.id,if(mode==BypassChargingMode.UNKNOWN)
                "The OEM interface did not provide a verifiable charging-mode state." else probe.detail)
    }

    private fun readMode(adapter:BypassChargingAdapter):BypassChargingMode =
        runCatching { adapter.readEnabled() }.getOrNull()?.let {
            if(it) BypassChargingMode.ENABLED else BypassChargingMode.DISABLED
        } ?: BypassChargingMode.UNKNOWN

    private fun verifiedStatus(mode:BypassChargingMode)=
        if(mode==BypassChargingMode.ENABLED) BypassChargingStatus.ENABLED_VERIFIED
        else BypassChargingStatus.DISABLED_VERIFIED

    private fun unavailable()=snapshot(BypassChargingSupport.UNKNOWN,BypassChargingAuthorization.UNAVAILABLE,
        BypassChargingMode.UNKNOWN,BypassChargingStatus.OEM_INTERFACE_UNAVAILABLE,null,
        "No reviewed OEM bypass-charging interface is registered. The system charging mode was not changed.")

    private fun snapshot(s:BypassChargingSupport,a:BypassChargingAuthorization,m:BypassChargingMode,
        status:BypassChargingStatus,id:String?,detail:String)=
        BypassChargingSnapshot(s,a,m,status,id,detail,timestamp())

    private fun timestamp()=runCatching { nowEpochMs().coerceAtLeast(0L) }.getOrDefault(0L)
}

/** Intentionally empty until a documented, authorized model/firmware adapter is verified. */
object BypassChargingAdapterRegistry {
    fun forDevice(device:BypassChargingDevice):List<BypassChargingAdapter> = emptyList()
}

object BypassChargingGuidance {
    fun instructions(device:BypassChargingDevice):String {
        val identity=(device.manufacturer+" "+device.brand).lowercase(java.util.Locale.ROOT)
        val vendor=when {
            "honor" in identity -> "HONOR: On supported models, check Settings → Battery → More battery settings → Bypass Charging. Availability varies by model and MagicOS version."
            "samsung" in identity -> "Samsung: Some Galaxy models provide “Pause USB PD charging when gaming” only under supported gaming conditions; this is not a universal bypass switch."
            "asus" in identity -> "ASUS ROG: Some supported models expose Bypass Charging or Smart Bypass in Game Genie / game settings. Availability varies by model and firmware."
            "oneplus" in identity -> "OnePlus: Availability varies by model and OxygenOS version. Search Settings for “Bypass Charging” or “Charge Separation”."
            else -> "Android: Search Settings for “Bypass Charging”, “Charge Separation”, or “Pause USB PD charging”. Availability depends on hardware and firmware."
        }
        return vendor+" ThiefAlertSeQure has no verified third-party control adapter for this device; ON/OFF remains disabled and the actual mode is UNKNOWN."
    }
}
