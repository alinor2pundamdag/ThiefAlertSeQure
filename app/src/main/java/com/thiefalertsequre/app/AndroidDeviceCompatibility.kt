package com.thiefalertsequre.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * OEM-aware guidance and resilient navigation for Android Settings.
 *
 * Android does not publish a universal API for vendor-specific auto-start or
 * background-management pages. This class therefore uses public Settings
 * actions first and provides manufacturer-specific user guidance rather than
 * depending on undocumented OEM component names.
 */
object AndroidDeviceCompatibility {
    enum class Family {
        SAMSUNG, HONOR_HUAWEI, XIAOMI, OPPO_REALME_ONEPLUS, VIVO_IQOO,
        GOOGLE_PIXEL, MOTOROLA, OTHER
    }

    fun family(manufacturer: String, brand: String): Family {
        val value = "${manufacturer.lowercase()} ${brand.lowercase()}"
        return when {
            "samsung" in value -> Family.SAMSUNG
            "honor" in value || "huawei" in value -> Family.HONOR_HUAWEI
            "xiaomi" in value || "redmi" in value || "poco" in value -> Family.XIAOMI
            "oppo" in value || "realme" in value || "oneplus" in value -> Family.OPPO_REALME_ONEPLUS
            "vivo" in value || "iqoo" in value -> Family.VIVO_IQOO
            "google" in value || "pixel" in value -> Family.GOOGLE_PIXEL
            "motorola" in value || "lenovo" in value -> Family.MOTOROLA
            else -> Family.OTHER
        }
    }

    fun familyLabel(manufacturer: String, brand: String): String = when (family(manufacturer, brand)) {
        Family.SAMSUNG -> "Samsung One UI"
        Family.HONOR_HUAWEI -> if ("huawei" in manufacturer.lowercase() + brand.lowercase()) "Huawei / EMUI" else "HONOR / MagicOS"
        Family.XIAOMI -> "Xiaomi / Redmi / POCO (MIUI / HyperOS)"
        Family.OPPO_REALME_ONEPLUS -> "OPPO / realme / OnePlus"
        Family.VIVO_IQOO -> "vivo / iQOO"
        Family.GOOGLE_PIXEL -> "Google Pixel"
        Family.MOTOROLA -> "Motorola"
        Family.OTHER -> if (brand.isNotBlank()) brand else "Android device"
    }

    fun deviceSummary(manufacturer: String, brand: String, model: String, release: String, sdk: Int): String {
        val device = listOf(manufacturer, model).filter { it.isNotBlank() }.distinct().joinToString(" ")
        return "${familyLabel(manufacturer, brand)} • ${device.ifBlank { "Android device" }} • Android ${release.ifBlank { "unknown" }} (API $sdk)"
    }

    fun backgroundSettingsGuide(manufacturer: String, brand: String): String {
        val common = "First open App info for ThiefAlertSeQure and review Battery / App battery usage. Choose Unrestricted, Allow background activity, or the least restrictive option your Android build offers. Keep notifications enabled. Menu names and availability vary by model and OS update."
        val vendor = when (family(manufacturer, brand)) {
            Family.SAMSUNG ->
                "Samsung One UI: App info → Battery → Unrestricted (where offered). Also inspect Settings → Battery → Background usage limits and add ThiefAlertSeQure to Never sleeping apps if that list exists. Samsung may move these controls between One UI releases."
            Family.HONOR_HUAWEI ->
                "HONOR MagicOS / Huawei EMUI: Settings → Apps → App launch (or Launch management) → ThiefAlertSeQure. If Manage automatically is available, turn it off and enable Auto-launch, Secondary launch, and Run in background. Also review Battery optimization / App launch settings. Some releases use different labels."
            Family.XIAOMI ->
                "Xiaomi / Redmi / POCO: App info → Battery saver → No restrictions (where offered). Then look in Security / Settings → Apps → Permissions → Autostart (or Background autostart) and allow ThiefAlertSeQure if that control exists. HyperOS and MIUI menus differ by release and region."
            Family.OPPO_REALME_ONEPLUS ->
                "OPPO / realme / OnePlus: App info → Battery usage / App battery management; allow background activity or unrestricted use where offered. Also search Settings for Auto launch, Startup manager, or App auto-launch and allow ThiefAlertSeQure if present. ColorOS, realme UI, and OxygenOS labels differ."
            Family.VIVO_IQOO ->
                "vivo / iQOO: Review Settings → Battery → Background power consumption management, then search Settings for Autostart / Background start and allow ThiefAlertSeQure where available. Funtouch OS and OriginOS menus vary."
            Family.GOOGLE_PIXEL ->
                "Google Pixel: App info → App battery usage → Allow background usage; choose Unrestricted if the option is available. Pixel generally relies on Android's standard battery controls rather than a separate OEM auto-start switch."
            Family.MOTOROLA ->
                "Motorola: App info → App battery usage / Battery; allow background usage and choose Unrestricted where available. Also review Adaptive Battery if Android offers an app-specific control. Menu names vary by model."
            Family.OTHER ->
                "Other Android brands: Open App info → Battery and allow background activity / unrestricted use if available. Search Settings for Auto-start, Startup manager, Background activity, or App launch. Some devices have no separate OEM auto-start control."
        }
        return "${familyLabel(manufacturer, brand)} guidance:\n$vendor\n\n$common\n\nIf a named menu is missing, use the Settings search box with the terms above. These are manual OEM controls; Android does not provide a public API that lets an ordinary app switch them on automatically."
    }

    fun appInfoIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    /**
     * Tries public Settings destinations in order. Do not preflight with
     * resolveActivity(): package-visibility filtering can make that check
     * misleading on Android 11+ even when startActivity can handle the route.
     */
    fun open(activity: Activity, primary: Intent, vararg fallbacks: Intent): Boolean {
        val candidates = (listOf(primary) + fallbacks.toList() + listOf(
            appInfoIntent(activity.packageName),
            Intent(Settings.ACTION_SETTINGS)
        )).distinctBy { it.action to it.dataString }
        for (candidate in candidates) {
            try {
                activity.startActivity(candidate)
                return true
            } catch (_: Exception) {
                // Continue to the next supported system destination.
            }
        }
        Toast.makeText(activity, "Android did not provide an available Settings page for this action.", Toast.LENGTH_LONG).show()
        return false
    }
}
