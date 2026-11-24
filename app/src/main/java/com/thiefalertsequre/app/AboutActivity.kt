package com.thiefalertsequre.app

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Local product identity, feature catalog and legal disclosures.
 * This screen is intentionally offline and never contacts a backend.
 */
class AboutActivity : FragmentActivity() {
    private lateinit var root: LinearLayout
    private var signatureAnimator: ValueAnimator? = null
    private var signatureText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) ThemeRuntime.apply(this, root)
        startSignatureAnimation()
    }

    override fun onStop() {
        signatureAnimator?.cancel()
        signatureAnimator = null
        super.onStop()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            contentDescription = "About ThiefAlertSeQure"
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(32))
            setBackgroundColor(getColor(R.color.tas_background))
        }
        scroll.addView(root)
        root.addView(button("‹  Back to dashboard") { finish() })
        root.addView(space(16))
        addDeveloperSignature()
        root.addView(space(18))
        root.addView(text("PRODUCT INFORMATION", 11f, R.color.tas_primary, true))
        root.addView(space(6))
        root.addView(text("ThiefAlertSeQure", 30f, R.color.tas_text, true).markAsPageHeading())
        root.addView(space(6))
        root.addView(text(
            "A local-first Android anti-theft protection app. Protection state, alarms and evidence remain on this device; no account or app backend is required.",
            14f, R.color.tas_muted
        ))
        root.addView(space(18))
        addVersionCard()
        root.addView(space(20))

        addSection("FEATURE CATALOG", "The catalog describes implemented app capabilities and their actual platform boundaries.")
        addFeatureGroup(
            "01 • Core protection",
            "Charger connection monitoring and automatic arming; a 10-second unplug countdown; reconnect cancellation before expiry; a latched alarm that cannot be cleared by reconnecting; optional motion-triggered protection; and optional trusted-Bluetooth separation alerts with an 8-second grace period per accessory.",
            "Permissions / prerequisites: foreground notification and service controls are managed by Android. Bluetooth Connect is requested only when the owner enables A2 and selects already-paired accessories. Motion protection is opt-in."
        )
        addFeatureGroup(
            "02 • Alarm outputs",
            "Looping alarm audio, owner-selected main and secondary sounds, built-in tone presets, volume restoration after authenticated disarm, optional rear-LED torch pulses, and haptic fallback. Optional full-screen alarm presentation is separate from the alarm notification.",
            "Permissions / prerequisites: notifications and exact-alarm access are controlled by Android. Torch output depends on compatible hardware and thermal policy. Torch control does not capture photos and does not require CAMERA permission."
        )
        addFeatureGroup(
            "03 • Owner authentication and managed protection",
            "Android device PIN, pattern or password confirmation and supported BIOMETRIC_STRONG verification; persisted biometric-failure cooldown; optional Android Device Administrator screen locking; and a separate opt-in Device Owner / Lock Task mode for supported managed devices.",
            "Permissions / prerequisites: credentials and biometric templates are verified by Android and are never read or stored by this app. Device Owner requires separate Android Enterprise provisioning; it is not a runtime permission."
        )
        addFeatureGroup(
            "04 • Evidence and local history",
            "Authentication-protected, searchable and filterable local event history; encrypted evidence metadata; integrity-checked backup and restore; optional owner-requested cached or one-shot location evidence; and optional front-camera still evidence after successful owner authentication disarms a latched alarm while the app is visible.",
            "Permissions / prerequisites: location is requested only after an owner action. CAMERA is requested only after separate photo-evidence opt-in. Photos are encrypted locally, retained for up to 30 days (maximum 20), and can be viewed, exported or deleted. No background camera, microphone, continuous location tracking or cloud upload is used."
        )
        addFeatureGroup(
            "05 • Device intelligence and readiness",
            "Android capability inventory, battery/background/thermal and permission readiness, Bluetooth capability details, OEM-specific background guidance, redacted diagnostics export, and a fail-closed Bypass Charging integration framework.",
            "Permissions / prerequisites: diagnostics use local Android APIs and do not export IMEI, serial number, MAC address, precise location or photo content. Bypass Charging ON/OFF remains unavailable until a model- and firmware-specific authorized adapter can prove and read back the hardware state."
        )
        addFeatureGroup(
            "06 • Appearance and interaction",
            "Locally saved themes, app marks, backgrounds, button animations, reduced-motion preference, intro audio/haptics, and the Seismic Genesis intro with a skip path and replay control.",
            "Permissions / prerequisites: appearance choices do not modify protection state, alarm latching or owner authentication. Reduced motion is available for the intro experience."
        )
        addFeatureGroup(
            "07 • Notifications, widgets and navigation",
            "Protection notifications, a read-only Quick Settings tile, seven read-only home-screen widget providers with multiple sizes, and the nine-destination dashboard navigation.",
            "Permissions / prerequisites: widgets, tile actions and notification actions are state projections only; they cannot disarm or silence a latched alarm. Notification delivery and lock-screen visibility remain Android-controlled."
        )
        addFeatureGroup(
            "08 • Settings, data and updates",
            "Permission and background setup guidance, alarm sound/flash controls, evidence and configuration backup, event CSV export, privacy notice review, and owner-initiated official update checks.",
            "Permissions / prerequisites: updates use network access only after the owner requests a check. The APK is verified for digest, package ID, version and pinned signer before Android's explicit package installer is opened. Android confirmation is required."
        )
        root.addView(space(20))

        addSection("NINE DASHBOARD DESTINATIONS", "The compact horizontal tab strip remains below the live protection shield.")
        addFeatureGroup(
            "Dashboard navigation",
            "Bluetooth Settings • Device Hardening & Reliability • Sounds & Flash Alert • Authentication Evidence • Device Intelligence • Appearance Studio • Local Tamper History • More • About.",
            "Each destination opens its dedicated screen or the corresponding anchored Settings section. The main protection shield remains visible above the tab strip."
        )
        root.addView(space(20))

        addSection("VERSION HISTORY", "Version information is read from the installed package; development candidates are not presented as published releases.")
        addFeatureGroup(
            "v1.1.4 development candidate",
            "Version identity: 1.1.4_Developer_Alinor • versionCode 6 • minSdk 26 • targetSdk 36. Development work includes expanded dashboard navigation, device intelligence, Appearance Studio, Seismic Genesis intro, widgets, Quick Settings projection, evidence controls, data-integrity hardening and verified update handoff.",
            "Status: development candidate on an unmerged branch. It is not an official release. The latest published release remains v1.1.3."
        )
        addFeatureGroup(
            "v1.1.3 • latest published baseline",
            "Preserves the existing local charger-protection and alarm behavior, with the synchronized optional rear-LED flashlight alert as its intended functional addition.",
            "The v1.1.4 candidate must preserve the existing package identity, production signing identity, user data and in-place update path."
        )
        root.addView(space(20))

        addSection("PRIVACY, TERMS AND OWNER CHOICE", "Review the current in-app notice and the limitations before enabling optional capabilities.")
        root.addView(text(
            "Privacy Notice version ${PolicyAcknowledgementPolicy.PRIVACY_VERSION} • Terms version ${PolicyAcknowledgementPolicy.TERMS_VERSION}. Acknowledgements and timestamps are stored locally. First launch requires both current versions. Reviewing the privacy notice from Settings resets its acknowledgement; the Terms dialog records its own acknowledgement. Increment the corresponding policy version to require fresh acknowledgement after a material policy change.",
            13f, R.color.tas_muted
        ))
        root.addView(space(10))
        root.addView(button("Privacy & Data") {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_PRIVACY))
        })
        root.addView(space(8))
        root.addView(outlinedButton("Open Android App Info") {
            AndroidDeviceCompatibility.open(this, AndroidDeviceCompatibility.appInfoIntent(packageName))
        })
        root.addView(space(8))
        root.addView(outlinedButton("Open-source license acknowledgements") {
            showLicenseAcknowledgements()
        })
        root.addView(space(8))
        root.addView(outlinedButton("Terms of Use & Platform Limitations") { showTerms() })
        root.addView(space(8))
        root.addView(outlinedButton("Permissions and Android Setup") {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_MORE))
        })
        root.addView(space(8))
        root.addView(outlinedButton("Official App Updates") {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_UPDATES))
        })
        root.addView(space(18))
        root.addView(text(
            "ThiefAlertSeQure is an assistance and deterrence tool, not a guarantee against theft or device compromise. Android and device manufacturers retain control of permissions, background execution, alarm delivery, hardware access, power-menu behavior and managed-device policy. No ordinary app can guarantee protection against force-stop, uninstall, physical shutdown, recovery/bootloader operations, battery removal or every OEM restriction.",
            12f, R.color.tas_muted
        ))
        root.addView(space(14))
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, root)
    }

    private fun addDeveloperSignature() {
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = Color.rgb(48, 51, 60)
            setCardBackgroundColor(Color.rgb(7, 8, 12))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }
        content.addView(TextView(this).apply {
            text = "DEVELOPER SIGNATURE"
            textSize = 10f
            letterSpacing = 0.18f
            setTextColor(Color.rgb(160, 168, 184))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        })
        content.addView(space(10))
        content.addView(space(12))
        signatureText = TextView(this).apply {
            text = "Developed by Alinor Pundamdag"
            textSize = 17f
            letterSpacing = 0.02f
            setTextColor(Color.rgb(90, 180, 255))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            contentDescription = "Animated developer credit: Developed by Alinor Pundamdag"
        }
        content.addView(signatureText)
        content.addView(space(8))
        content.addView(TextView(this).apply {
            text = "THIEFALERTSEQURE  /  LOCAL-FIRST ANDROID PROTECTION"
            textSize = 9f
            letterSpacing = 0.06f
            setTextColor(Color.rgb(145, 151, 164))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        })
        card.addView(content)
        root.addView(card)
    }

    private fun startSignatureAnimation() {
        val target = signatureText ?: return
        if (signatureAnimator?.isRunning == true) return
        val colors = intArrayOf(
            0xFF35C9FF.toInt(), 0xFF4D9DFF.toInt(), 0xFF6578FF.toInt(), 0xFF865FFF.toInt(),
            0xFFAB5BFF.toInt(), 0xFFD65BEB.toInt(), 0xFFFF5FA8.toInt(), 0xFFFF6B78.toInt(),
            0xFFFF8B5B.toInt(), 0xFFFFB454.toInt(), 0xFFFFD45A.toInt(), 0xFFB8E45A.toInt(),
            0xFF70D978.toInt(), 0xFF42D6A4.toInt(), 0xFF3BD6C8.toInt(), 0xFF39C6E8.toInt(),
            0xFF6BAEFF.toInt(), 0xFF9C9BFF.toInt(), 0xFFC18BFF.toInt(), 0xFFEF82D2.toInt()
        )
        signatureAnimator = ValueAnimator.ofObject(ArgbEvaluator(), *colors.toTypedArray()).apply {
            duration = 12_000L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator -> target.setTextColor(animator.animatedValue as Int) }
            start()
        }
    }

    private fun addVersionCard() {
        val info = packageInfo()
        val versionName = info?.versionName ?: "Unavailable"
        @Suppress("DEPRECATION")
        val versionCode = if (info == null) "Unavailable" else if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode.toString()
        } else {
            info.versionCode.toString()
        }
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.tas_surface_variant)
            setCardBackgroundColor(getColor(R.color.tas_surface))
        }
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        details.addView(text("INSTALLED APP VERSION", 10f, R.color.tas_primary, true))
        details.addView(space(4))
        details.addView(text("$versionName  •  versionCode $versionCode", 17f, R.color.tas_text, true))
        details.addView(space(10))
        details.addView(text("Application ID", 11f, R.color.tas_muted, true))
        details.addView(text(packageName, 13f, R.color.tas_text))
        details.addView(space(10))
        details.addView(text("Android compatibility", 11f, R.color.tas_muted, true))
        details.addView(text("Minimum API 26 • target API 36 • running API ${Build.VERSION.SDK_INT}", 13f, R.color.tas_text))
        details.addView(space(12))
        details.addView(text("APP AUTHENTICITY", 10f, R.color.tas_primary, true))
        val installedSigners = runCatching { AppUpdateManager.installedSignerSha256(this) }.getOrDefault(emptyList())
        val officialSigner = AppUpdatePolicy.hasOfficialSigner(installedSigners)
        details.addView(space(5))
        details.addView(text(
            if (officialSigner) "VERIFIED • installed signer matches the pinned official release key"
            else "DEVELOPMENT / NON-OFFICIAL SIGNER • official in-app updates are blocked for this build",
            12f, if (officialSigner) R.color.tas_success else R.color.tas_warning, true
        ))
        details.addView(space(5))
        details.addView(text("Installed signer SHA-256", 11f, R.color.tas_muted, true))
        details.addView(text(installedSigners.singleOrNull() ?: "Unavailable or multiple signers", 10f, R.color.tas_text))
        details.addView(space(5))
        details.addView(text("Pinned official signer SHA-256", 11f, R.color.tas_muted, true))
        details.addView(text(AppUpdatePolicy.EXPECTED_CERT_SHA256, 10f, R.color.tas_text))
        card.addView(details)
        root.addView(card)
    }

    private fun addSection(title: String, subtitle: String) {
        root.addView(text(title, 11f, R.color.tas_primary, true))
        root.addView(space(5))
        root.addView(text(subtitle, 12f, R.color.tas_muted))
        root.addView(space(10))
    }

    private fun addFeatureGroup(title: String, description: String, boundary: String) {
        val card = MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.tas_surface_variant)
            setCardBackgroundColor(getColor(R.color.tas_surface))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        body.addView(text(title, 14f, R.color.tas_text, true))
        body.addView(space(6))
        body.addView(text(description, 13f, R.color.tas_text))
        body.addView(space(8))
        body.addView(text(boundary, 12f, R.color.tas_muted))
        card.addView(body)
        root.addView(card)
        root.addView(space(8))
    }

    private fun showLicenseAcknowledgements() {
        val apacheLicense = runCatching {
            assets.open("licenses/Apache-2.0.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse {
            android.widget.Toast.makeText(this, "Bundled Apache 2.0 license text is unavailable.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        content.addView(text("PROJECT LICENSE", 11f, R.color.tas_primary, true))
        content.addView(space(6))
        content.addView(text("ThiefAlertSeQure source code • Apache License 2.0", 14f, R.color.tas_text, true))
        content.addView(space(14))
        content.addView(text("DIRECT RUNTIME DEPENDENCIES", 11f, R.color.tas_primary, true))
        content.addView(space(6))
        val dependencies = listOf(
            "AndroidX Core KTX • 1.15.0",
            "AndroidX Activity KTX • 1.10.0",
            "AndroidX Fragment KTX • 1.8.5",
            "AndroidX Biometric • 1.1.0",
            "AndroidX Lifecycle Runtime KTX • 2.8.7",
            "AndroidX DocumentFile • 1.0.1",
            "Google Material Components for Android • 1.12.0"
        )
        dependencies.forEach { dependency ->
            content.addView(text("$dependency — Apache License 2.0", 12f, R.color.tas_text))
            content.addView(space(5))
        }
        content.addView(space(8))
        content.addView(text(
            "Upstream projects: github.com/androidx/androidx and github.com/material-components/material-components-android. The complete Apache License 2.0 text follows.",
            11f, R.color.tas_muted
        ))
        content.addView(space(12))
        content.addView(text("APACHE LICENSE, VERSION 2.0", 11f, R.color.tas_primary, true))
        content.addView(space(6))
        content.addView(TextView(this).apply {
            text = apacheLicense
            textSize = 10f
            setTextColor(getColor(R.color.tas_muted))
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            layoutParams = android.view.ViewGroup.LayoutParams(-1, dp(520))
            addView(content)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Open-source license acknowledgements")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .showThemed(this)
    }

    private fun showTerms() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Terms of Use & Platform Limitations")
            .setMessage(
                "Use ThiefAlertSeQure only on a device you own or are authorized to manage. You are responsible for choosing optional features, reviewing Android permissions, securing exported backups, and keeping your device and app updated.\n\n" +
                    "The app provides local monitoring, alerts and deterrence. It does not guarantee theft prevention, recovery, uninterrupted background execution, exact alarm timing, camera/flashlight output, Bluetooth range detection, or resistance to force-stop, uninstall, physical shutdown, recovery/bootloader operations, battery removal or manufacturer restrictions. Android and the device manufacturer control those system behaviors.\n\n" +
                    "Device Owner / Lock Task features require separate authorized Android Enterprise provisioning and may restrict device functions. Enable them only on a device you administer and retain a recovery path. Bypass Charging control remains unavailable unless a compatible, authorized adapter can verify the actual hardware state.\n\n" +
                    "Optional location and camera evidence are owner-initiated and locally stored. Review the Privacy Notice for the exact capture, retention, export and deletion rules. Protect exported files and backup passwords; anyone with access to an exported file and its password may be able to read its contents.\n\n" +
                    "Official updates are checked only when you initiate the check. Android displays the final installation confirmation. Do not install modified or unofficial packages as official updates."
            )
            .setNegativeButton("Close", null)
            .setPositiveButton("I acknowledge") { _, _ ->
                val acceptedAt = System.currentTimeMillis()
                val saved = getSharedPreferences(PolicyAcknowledgementPolicy.TERMS_PREFS, MODE_PRIVATE).edit()
                    .putString("decision", "acknowledged")
                    .putInt("version", PolicyAcknowledgementPolicy.TERMS_VERSION)
                    .putLong("decision_at", acceptedAt)
                    .commit()
                if (!saved) {
                    android.widget.Toast.makeText(
                        this,
                        "Terms acknowledgement could not be saved. Review and acknowledge again.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            .showThemed(this)
    }

    private fun packageInfo(): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }.getOrNull()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(getColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun button(value: String, click: () -> Unit) = MaterialButton(this).apply {
        text = value
        isAllCaps = false
        minHeight = dp(48)
        setOnClickListener { click() }
    }

    private fun outlinedButton(value: String, click: () -> Unit) = MaterialButton(this).apply {
        text = value
        isAllCaps = false
        minHeight = dp(48)
        strokeWidth = dp(1)
        strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.tas_primary))
        backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tas_surface))
        setTextColor(getColor(R.color.tas_text))
        setOnClickListener { click() }
    }
}
