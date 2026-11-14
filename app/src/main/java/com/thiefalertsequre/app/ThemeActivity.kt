package com.thiefalertsequre.app

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** Theme preview is isolated until Apply. Sample actions are intentionally non-interactive. */
class ThemeActivity : FragmentActivity() {
    private var previewId = ThemeStore.DEFAULT_ID
    private var mode = "Dark"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewId = ThemeStore.selected(this)
        mode = ThemeStore.get(this, previewId).mode
        render()
    }

    override fun onResume() {
        super.onResume()
        ThemeRuntime.apply(this, window.decorView, previewId)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setLineSpacing(dp(2).toFloat(), 1f)
        }

    private fun params(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
    }

    private fun sampleCard(p: ThemePalette, content: LinearLayout) =
        MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = p.variant
            setCardBackgroundColor(p.surface)
            addView(content)
        }

    private fun content() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
    }

    private fun sampleAction(parent: LinearLayout, label: String, p: ThemePalette) {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(p.onPrimary)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(p.primary)
            }
            contentDescription = "Preview sample only: " + label
        }, params(10))
    }

    private fun render() {
        val p = ThemeStore.get(this, previewId)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            contentDescription = "Theme preview and selection"
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }
        scroll.addView(root)
        root.addView(MaterialButton(this).apply {
            text = "‹  Back"
            isAllCaps = false
            minHeight = dp(48)
            setOnClickListener { finish() }
        }, params())
        root.addView(text("APPEARANCE", 11f, p.primary, true), params(14))
        root.addView(text("Choose your theme", 28f, p.text, true), params(5))
        root.addView(text(
            "Preview the dashboard, settings, setup status and dialogs. Apply saves locally; Cancel keeps your current theme.",
            13f, p.muted
        ), params(7))

        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("Dark", "Light").forEach { filter ->
            val selected = mode == filter
            filters.addView(MaterialButton(this).apply {
                text = filter
                isAllCaps = false
                minHeight = dp(48)
                backgroundTintList = ColorStateList.valueOf(if (selected) p.primary else p.surface)
                setTextColor(if (selected) p.onPrimary else p.text)
                strokeWidth = dp(1)
                strokeColor = ColorStateList.valueOf(p.variant)
                contentDescription = filter + " themes" + if (selected) ", selected" else ""
                setOnClickListener {
                    mode = filter
                    if (ThemeStore.get(this@ThemeActivity, previewId).mode != mode) {
                        previewId = ThemeStore.all.first { it.mode == mode }.id
                    }
                    render()
                }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        }
        root.addView(filters, params(14))
        root.addView(text("LIVE APP PREVIEW", 11f, p.primary, true), params(18))

        val dashboard = content()
        dashboard.addView(text("THIEF ALERT  /  SEQURE", 11f, p.primary, true))
        dashboard.addView(text("Protection that stays on this device.", 22f, p.text, true), params(7))
        dashboard.addView(text("LIVE PROTECTION STATUS", 10f, p.muted, true), params(12))
        dashboard.addView(text("Protection enabled", 17f, p.success, true), params(4))
        dashboard.addView(text("Local monitoring • Owner authentication • No cloud sync", 12f, p.muted), params(4))
        sampleAction(dashboard, "Open protection settings", p)
        root.addView(sampleCard(p, dashboard), params(8))

        val settings = content()
        settings.addView(text("SETTINGS", 11f, p.primary, true))
        settings.addView(text("Reliability & system access", 18f, p.text, true), params(7))
        settings.addView(text("Exact alarms", 15f, p.text, true), params(12))
        settings.addView(text("Android permission status and a clear action destination.", 12f, p.muted), params(3))
        settings.addView(View(this).apply { setBackgroundColor(p.variant) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(10) })
        settings.addView(text("Battery optimization", 15f, p.text, true), params(10))
        settings.addView(text("Review manufacturer background restrictions.", 12f, p.muted), params(3))
        sampleAction(settings, "Review Android settings", p)
        root.addView(sampleCard(p, settings), params(8))

        val readiness = content()
        readiness.addView(text("PROTECTION READINESS", 11f, p.primary, true))
        readiness.addView(text("Verified Android setup", 18f, p.text, true), params(7))
        readiness.addView(text("VERIFIED • Monitor permission", 13f, p.success, true), params(12))
        readiness.addView(text("ACTION REQUIRED • Bluetooth permission", 13f, p.warning, true), params(8))
        readiness.addView(text("UNKNOWN • Service state not confirmed", 13f, p.muted, true), params(8))
        readiness.addView(text("Status uses labels and text, not color alone.", 12f, p.muted), params(8))
        root.addView(sampleCard(p, readiness), params(8))

        val dialog = content()
        dialog.addView(text("SAMPLE CONFIRMATION DIALOG", 10f, p.primary, true))
        dialog.addView(text("Unregister trusted accessory?", 17f, p.text, true), params(8))
        dialog.addView(text(
            "This removes the app trust relationship only. Android Bluetooth pairing remains unchanged.",
            13f, p.muted
        ), params(6))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        actions.addView(text("CANCEL", 12f, p.muted, true).apply { setPadding(dp(12), dp(12), dp(12), dp(12)) })
        actions.addView(text("UNREGISTER", 12f, p.primary, true).apply { setPadding(dp(12), dp(12), dp(4), dp(12)) })
        dialog.addView(actions, params(8))
        root.addView(sampleCard(p, dialog), params(8))
        root.addView(text(
            "Preview only. Sample actions do not change protection, permissions, pairing, or alarm state.",
            12f, p.muted
        ), params(10))

        ThemeStore.all.filter { it.mode == mode }.forEach { theme ->
            root.addView(MaterialButton(this).apply {
                text = (if (previewId == theme.id) "✓  " else "") + theme.name
                isAllCaps = false
                minHeight = dp(52)
                backgroundTintList = ColorStateList.valueOf(theme.surface)
                setTextColor(theme.text)
                strokeColor = ColorStateList.valueOf(theme.primary)
                strokeWidth = dp(if (previewId == theme.id) 2 else 1)
                contentDescription = theme.name + if (previewId == theme.id) ", selected preview" else ", preview theme"
                setOnClickListener { previewId = theme.id; render() }
            }, params(7))
        }
        root.addView(MaterialButton(this).apply {
            text = "Apply selected theme"
            isAllCaps = false
            minHeight = dp(52)
            setOnClickListener {
                if (ThemeStore.save(this@ThemeActivity, previewId)) {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    android.widget.Toast.makeText(
                        this@ThemeActivity,
                        "Theme could not be saved. Your previous theme remains active.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, params(16))
        root.addView(MaterialButton(this).apply {
            text = "Cancel"
            isAllCaps = false
            minHeight = dp(48)
            setOnClickListener { finish() }
        }, params(6))

        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, scroll, previewId)
    }
}
