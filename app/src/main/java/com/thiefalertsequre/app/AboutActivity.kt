package com.thiefalertsequre.app

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.security.MessageDigest

/** Local product identity and owner-facing information; no account or network request is made here. */
class AboutActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) ThemeRuntime.apply(this, root)
    }

    private lateinit var root: LinearLayout

    private fun render() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            contentDescription = "About ThiefAlertSeQure"
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
            setBackgroundColor(getColor(R.color.tas_background))
        }
        scroll.addView(root)
        root.addView(button("‹  Back to dashboard") { finish() })
        root.addView(space(16))
        root.addView(text("ABOUT", 11f, R.color.tas_primary, true))
        root.addView(space(6))
        root.addView(text("ThiefAlertSeQure", 30f, R.color.tas_text, true).markAsPageHeading())
        root.addView(space(6))
        root.addView(text(
            "A local-first Android anti-theft protection app. Protection state, alarms and evidence remain on this device; no account or app backend is required.",
            14f, R.color.tas_muted
        ))
        root.addView(space(18))

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
        details.addView(text("APP VERSION", 10f, R.color.tas_primary, true))
        details.addView(space(4))
        details.addView(text("$versionName  •  versionCode $versionCode", 16f, R.color.tas_text, true))
        details.addView(space(10))
        details.addView(text("Application ID", 11f, R.color.tas_muted, true))
        details.addView(text(packageName, 13f, R.color.tas_text))
        details.addView(space(10))
        details.addView(text("Android compatibility", 11f, R.color.tas_muted, true))
        details.addView(text("Minimum API 26 • target API 36 • running API ${Build.VERSION.SDK_INT}", 13f, R.color.tas_text))
        card.addView(details)
        root.addView(card)
        root.addView(space(12))
        root.addView(button("Open Android App Info") { openAndroidAppInfo() })
        root.addView(space(6))
        root.addView(text(
            "Opens Android's own App Info page for this installed package. This does not change your device settings.",
            12f, R.color.tas_muted
        ))
        root.addView(space(18))

        val signing = signingSnapshot()
        root.addView(text("RELEASE AUTHENTICITY", 11f, R.color.tas_primary, true))
        root.addView(space(6))
        root.addView(text(signing.status, 13f, R.color.tas_text, true))
        root.addView(space(6))
        root.addView(text("Installed signer SHA-256\n${signing.actualFingerprint}", 11f, R.color.tas_muted))
        root.addView(space(6))
        root.addView(text(
            "Pinned official release SHA-256\n${formatFingerprint(AppUpdatePolicy.EXPECTED_CERT_SHA256)}",
            11f, R.color.tas_muted
        ))
        root.addView(space(18))

        root.addView(text("PRIVACY BY DESIGN", 11f, R.color.tas_primary, true))
        root.addView(space(6))
        root.addView(text(
            "The core charger alarm works locally. Optional location snapshots require an owner action. Optional front-camera photo evidence requires a separate Settings opt-in and CAMERA permission; one photo is taken only after successful owner authentication that disarms a latched alarm while the app is visible. Photos are encrypted locally and are never uploaded. The flashlight uses Android torch control separately and does not capture images.",
            13f, R.color.tas_muted
        ))
        root.addView(space(18))
        root.addView(button("Privacy & Data") {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_PRIVACY))
        })
        root.addView(space(8))
        root.addView(outlinedButton("More Settings") {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_MORE))
        })
        root.addView(space(8))
        root.addView(outlinedButton("Official App Updates") {
            startActivity(Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.SECTION_UPDATES))
        })
        root.addView(space(22))
        root.addView(text("OPEN-SOURCE LICENSE ACKNOWLEDGMENTS", 11f, R.color.tas_primary, true))
        root.addView(space(6))
        root.addView(text(
            "ThiefAlertSeQure and its declared direct dependencies include open-source software. Review the dependency and license acknowledgments before redistribution.",
            13f, R.color.tas_muted
        ))
        root.addView(space(8))
        root.addView(outlinedButton("View third-party license acknowledgments") {
            showThirdPartyLicenses()
        })
        root.addView(space(18))
        root.addView(text("Android controls system-level permissions, device-management enrollment, hardware access and manufacturer-specific behavior. No ordinary app can guarantee protection against force-stop, hardware shutdown, recovery reset or every OEM restriction.", 12f, R.color.tas_muted))
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, root)
    }

    private data class SigningSnapshot(
        val status: String,
        val actualFingerprint: String
    )

    private fun signingSnapshot(): SigningSnapshot {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, flags)
            }
        }.getOrNull() ?: return SigningSnapshot(
            "UNKNOWN — Android did not provide the installed signing certificate.",
            "Unavailable"
        )
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val digests = signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }
        val actual = digests.joinToString("\n") { formatFingerprint(it) }.ifBlank { "Unavailable" }
        val status = when {
            packageName != AppUpdatePolicy.EXPECTED_PACKAGE ->
                "DEVELOPMENT / TEST BUILD — this separate package is not an official production update."
            digests.isEmpty() ->
                "UNKNOWN — no readable signing certificate was returned by Android."
            AppUpdatePolicy.hasOfficialSigner(digests) ->
                "VERIFIED — the installed production package matches the pinned official release certificate."
            else ->
                "WARNING — the installed production package does not match the pinned official release certificate."
        }
        return SigningSnapshot(status, actual)
    }

    private fun formatFingerprint(value: String): String =
        value.chunked(2).joinToString(":")

    private fun openAndroidAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "Android could not open this app's App Info page.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showThirdPartyLicenses() {
        val message = """
            Direct runtime dependencies:
            • AndroidX Core KTX 1.15.0 — Apache License 2.0
            • AndroidX Activity KTX 1.10.0 — Apache License 2.0
            • AndroidX Fragment KTX 1.8.5 — Apache License 2.0
            • AndroidX Biometric 1.1.0 — Apache License 2.0
            • AndroidX Lifecycle Runtime KTX 2.8.7 — Apache License 2.0
            • AndroidX DocumentFile 1.0.1 — Apache License 2.0
            • Material Components for Android 1.12.0 — Apache License 2.0

            Test dependency:
            • JUnit 4.13.2 — Eclipse Public License 1.0
            • Hamcrest Core 1.3 (JUnit test transitive dependency) — New BSD License

            Project source license:
            • Apache License 2.0 (see the repository LICENSE file)

            License texts:
            https://www.apache.org/licenses/LICENSE-2.0
            https://www.eclipse.org/legal/epl-v10.html
            https://opensource.org/license/bsd-3-clause/

            This in-app list identifies the direct declared dependencies and known JUnit test transitive dependency. Other resolved transitive dependencies retain their respective upstream license terms and notices.
        """.trimIndent()
        MaterialAlertDialogBuilder(this)
            .setTitle("Open-source acknowledgments")
            .setMessage(message)
            .setPositiveButton("Close", null)
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
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(getColor(color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
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
