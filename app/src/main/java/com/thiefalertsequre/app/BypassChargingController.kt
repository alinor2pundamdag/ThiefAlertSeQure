package com.thiefalertsequre.app

enum class BypassChargingSupport { SUPPORTED, UNSUPPORTED, UNKNOWN }
enum class BypassChargingAuthorization { AUTHORIZED, REQUIRED, UNAVAILABLE }
enum class BypassChargingMode { ENABLED, DISABLED, UNKNOWN }
enum class BypassChargingStatus {
    ENABLED_VERIFIED, DISABLED_VERIFIED, UNSUPPORTED, AUTHORIZATION_REQUIRED,
    OEM_INTERFACE_UNAVAILABLE, STATE_UNVERIFIED, ACTIVATION_FAILED
}

data class BypassChargingDevice(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val apiLevel: Int,
    /** Exact Android build fingerprint; unknown/empty values never qualify for certification. */
    val firmwareFingerprint: String = ""
)

data class BypassChargingCertification(
    val adapterId: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val minApiLevel: Int,
    val maxApiLevel: Int,
    /** Exact reviewed fingerprints only. Wildcards and prefix matching are forbidden. */
    val firmwareFingerprints: Set<String>
) {
    fun matches(device: BypassChargingDevice): Boolean {
        fun normalized(value: String) = value.trim().lowercase(java.util.Locale.ROOT)
        return adapterId.isNotBlank() &&
            normalized(manufacturer) == normalized(device.manufacturer) &&
            normalized(brand) == normalized(device.brand) &&
            normalized(model) == normalized(device.model) &&
            minApiLevel > 0 && maxApiLevel >= minApiLevel &&
            device.apiLevel in minApiLevel..maxApiLevel &&
            device.firmwareFingerprint.isNotBlank() &&
            device.firmwareFingerprint != "unknown" &&
            firmwareFingerprints.isNotEmpty() &&
            firmwareFingerprints.none { it.isBlank() || it.contains("*") } &&
            firmwareFingerprints.any { it == device.firmwareFingerprint }
    }
}

data class BypassChargingAdapterRegistration(
    val certification: BypassChargingCertification,
    val factory: (BypassChargingDevice) -> BypassChargingAdapter?
)
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
    @Synchronized fun inspect():BypassChargingSnapshot =
        selectSnapshot(adapters.map { adapter -> adapter to assess(adapter) })

    @Synchronized fun setEnabled(enabled:Boolean):BypassChargingSnapshot {
        // Never let an unsupported or broken first adapter mask a later
        // compatible adapter. Prefer an adapter with a verified current state.
        val assessed=adapters.map { adapter -> adapter to assess(adapter) }
        val selected=assessed.firstOrNull { it.second.canToggle }
            ?: return selectSnapshot(assessed)
        val (adapter,before)=selected
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

    private fun selectSnapshot(
        assessed:List<Pair<BypassChargingAdapter,BypassChargingSnapshot>>
    ):BypassChargingSnapshot {
        if(assessed.isEmpty()) return unavailable()
        // Prefer verified state, then an authorized supported adapter, then
        // useful denial/unsupported diagnostics. Provider order is only a tie-break.
        return assessed.maxByOrNull { (_,snapshot) ->
            when {
                snapshot.isVerified -> 100
                snapshot.support==BypassChargingSupport.SUPPORTED &&
                    snapshot.authorization==BypassChargingAuthorization.AUTHORIZED -> 80
                snapshot.support==BypassChargingSupport.SUPPORTED &&
                    snapshot.authorization==BypassChargingAuthorization.REQUIRED -> 70
                snapshot.support==BypassChargingSupport.UNSUPPORTED -> 40
                else -> 10
            }
        }?.second ?: unavailable()
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

/**
 * Only exact, reviewed model/API/build-fingerprint certifications may resolve.
 * The production registration list intentionally remains empty until an OEM
 * interface has written authorization, state read-back and rollback evidence.
 */
object BypassChargingAdapterRegistry {
    private val certifiedRegistrations: List<BypassChargingAdapterRegistration> = emptyList()

    fun forDevice(device: BypassChargingDevice): List<BypassChargingAdapter> =
        resolve(device, certifiedRegistrations)

    internal fun resolve(
        device: BypassChargingDevice,
        registrations: List<BypassChargingAdapterRegistration>
    ): List<BypassChargingAdapter> {
        val matching = registrations.filter { it.certification.matches(device) }
        // A duplicated certification is ambiguous and fails closed.
        if (matching.map { it.certification.adapterId }.distinct().size != matching.size) {
            return emptyList()
        }
        return matching.mapNotNull { registration ->
            val adapter = runCatching { registration.factory(device) }.getOrNull()
                ?: return@mapNotNull null
            // Bind the returned implementation to the exact certification that
            // created it. A different registration's ID must never authorize it.
            val registeredId = registration.certification.adapterId
            val actualId = runCatching { adapter.id }.getOrNull()
            adapter.takeIf { actualId.isNotBlank() && actualId == registeredId }
        }
    }
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
