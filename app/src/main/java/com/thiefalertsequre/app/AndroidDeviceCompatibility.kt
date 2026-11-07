package com.thiefalertsequre.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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

    /**
     * Android 15 enforces edge-to-edge for apps targeting API 35. Apply system-bar
     * and cutout insets to the outer scroll container so controls remain reachable
     * on gesture navigation, three-button navigation, and display cutouts.
     */
    fun applySystemBarInsets(view: View) {
        val baseLeft = view.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            target.setPadding(
                baseLeft + insets.left,
                baseTop + insets.top,
                baseRight + insets.right,
                baseBottom + insets.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun backgroundSettingsGuide(manufacturer: String, brand: String): String {
        val common = "First open App info for ThiefAlertSeQure and review Battery / App battery usage. Choose Unrestricted, Allow background activity, or the least restrictive option your Android build offers. Keep notifications enabled. Menu names and availability vary by model and OS update."
        val vendor = when (family(manufacturer, brand)) {
            Family.SAMSUNG ->
                "Samsung One UI: Settings → Battery (or Battery and device care → Battery) → Background usage limits → Never sleeping apps → add ThiefAlertSeQure. Also check App info → Battery for an Unrestricted option if your One UI release offers it. Do not place the app in Sleeping or Deep sleeping apps. Samsung may move these controls between One UI releases."
            Family.HONOR_HUAWEI ->
                "HONOR MagicOS / Huawei EMUI: Settings → Apps → App launch (or Launch management) → ThiefAlertSeQure. If Manage automatically is available, turn it off and enable Auto-launch, Secondary launch, and Run in background. Also review Battery optimization / App launch settings. Some releases use different labels."
            Family.XIAOMI ->
                "Xiaomi / Redmi / POCO: App info → Battery saver / Power → No restrictions (where offered). Then Settings → Apps → Permissions → Background autostart (or Autostart) → allow ThiefAlertSeQure. If available, lock the app in Recents. HyperOS and MIUI menus differ by release and region."
            Family.OPPO_REALME_ONEPLUS ->
                "OPPO / realme: Settings → Battery → App battery management; allow foreground activity, background activity, and auto-launch where offered. Some releases place this under Phone Manager → Privacy Permissions → Auto-launch apps. OnePlus / OxygenOS: also search Settings → Apps → Auto launch. Lock the app in Recents if the option exists. Labels vary by OS release."
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


    enum class LaunchControl(val title: String) {
        AUTO_LAUNCH("Auto-launch"),
        SECONDARY_LAUNCH("Secondary launch"),
        RUN_IN_BACKGROUND("Run in background")
    }

    /**
     * HONOR/Huawei expose App launch as a vendor Settings page with no public
     * Android action, so open Settings home and let the user search for App launch.
     * Other families open this app's public App info page. No undocumented OEM
     * component names are launched.
     */
    fun launchControlSettingsIntent(manufacturer: String, brand: String, packageName: String): Intent =
        when (family(manufacturer, brand)) {
            Family.HONOR_HUAWEI -> Intent(Settings.ACTION_SETTINGS)
            else -> appInfoIntent(packageName)
        }

    fun launchControlGuide(manufacturer: String, brand: String, control: LaunchControl): String {
        return when (family(manufacturer, brand)) {
            Family.HONOR_HUAWEI ->
                "Settings → Apps → App launch → ThiefAlertSeQure (or Settings → Battery → App launch on some MagicOS/EMUI versions). Turn Manage automatically OFF. In the Manage manually box, enable Auto-launch, Secondary launch, and Run in background, then tap OK. This is the recommended three-switch configuration on supported HONOR/Huawei builds. The app cannot read or change these OEM switches itself."
            Family.SAMSUNG -> when (control) {
                LaunchControl.AUTO_LAUNCH -> "Samsung One UI has no universal Auto-launch switch for ordinary apps. Open Settings → Battery and device care → Battery → Background usage limits → Never sleeping apps, then add ThiefAlertSeQure if available."
                LaunchControl.SECONDARY_LAUNCH -> "Samsung One UI does not expose a universal Secondary launch switch. Keep ThiefAlertSeQure out of Sleeping and Deep sleeping apps; review App info → Battery for background-use options."
                LaunchControl.RUN_IN_BACKGROUND -> "Open Settings → Battery and device care → Battery → Background usage limits → Never sleeping apps and add ThiefAlertSeQure. Also review App info → Battery and allow unrestricted/background use where offered."
            }
            Family.XIAOMI -> when (control) {
                LaunchControl.AUTO_LAUNCH -> "Open Settings → Apps → Permissions → Background autostart (or Autostart) and allow ThiefAlertSeQure. MIUI/HyperOS labels and availability vary."
                LaunchControl.SECONDARY_LAUNCH -> "Xiaomi/Redmi/POCO builds do not consistently expose a separate Secondary launch switch. Allow Background autostart and review any app-specific background-start permission shown by your ROM."
                LaunchControl.RUN_IN_BACKGROUND -> "Open App info → Battery saver / Battery → No restrictions where available. Lock ThiefAlertSeQure in Recents if your device offers that option."
            }
            Family.OPPO_REALME_ONEPLUS -> when (control) {
                LaunchControl.AUTO_LAUNCH -> "Search Settings for Auto-launch / Auto-start, or open Phone Manager → Privacy Permissions → Auto-launch apps where available, and allow ThiefAlertSeQure."
                LaunchControl.SECONDARY_LAUNCH -> "There is no consistent cross-version Secondary launch switch on OPPO/realme/OnePlus. Review app-specific background-start or associated-launch permissions if your OS exposes them."
                LaunchControl.RUN_IN_BACKGROUND -> "Open Settings → Battery → App battery management and allow foreground/background activity. Lock ThiefAlertSeQure in Recents if offered."
            }
            Family.VIVO_IQOO -> when (control) {
                LaunchControl.AUTO_LAUNCH -> "Search Settings for Autostart or Background start and allow ThiefAlertSeQure if your Funtouch OS/OriginOS version provides the control."
                LaunchControl.SECONDARY_LAUNCH -> "A separate Secondary launch switch is not consistently available on vivo/iQOO. Use the closest Background start or associated-launch permission if offered."
                LaunchControl.RUN_IN_BACKGROUND -> "Open Settings → Battery → Background power consumption management and allow ThiefAlertSeQure to run in the background where available."
            }
            Family.GOOGLE_PIXEL -> when (control) {
                LaunchControl.AUTO_LAUNCH -> "Pixel does not provide a general OEM Auto-launch allowlist. Android may restore eligible work after reboot, subject to its background-start rules. Open App info → App battery usage to review background access."
                LaunchControl.SECONDARY_LAUNCH -> "Pixel does not expose an OEM Secondary launch toggle. Android controls background activity launches; there is no equivalent switch to enable here."
                LaunchControl.RUN_IN_BACKGROUND -> "Open App info → App battery usage → Allow background usage, and choose Unrestricted if available."
            }
            Family.MOTOROLA, Family.OTHER -> when (control) {
                LaunchControl.AUTO_LAUNCH -> "This Android build may not provide a separate Auto-launch switch. Search Settings for Auto-start, Startup manager, or App launch. If none exists, Android manages eligible startup behavior."
                LaunchControl.SECONDARY_LAUNCH -> "A separate Secondary launch switch may not exist on this device. Search Settings for Background start or associated app-launch permissions; do not enable unrelated permissions."
                LaunchControl.RUN_IN_BACKGROUND -> "Open App info → App battery usage / Battery and allow background usage or Unrestricted where offered. Menu names vary by model and OS version."
            }
        }
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
