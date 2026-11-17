package com.thiefalertsequre.app

import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

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
            strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.tas_surface_variant))
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
        root.addView(space(18))
        root.addView(text("Android controls system-level permissions, device-management enrollment, hardware access and manufacturer-specific behavior. No ordinary app can guarantee protection against force-stop, hardware shutdown, recovery reset or every OEM restriction.", 12f, R.color.tas_muted))
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, root)
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
