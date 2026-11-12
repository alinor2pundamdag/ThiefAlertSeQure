package com.thiefalertsequre.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class SetupStatusActivity : FragmentActivity() {
    private var rendered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (rendered) render()
    }

    override fun onResume() {
        super.onResume()
        ThemeRuntime.apply(this, window.decorView)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun render() {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }
        scroll.addView(root)
        root.addView(MaterialButton(this).apply {
            text = "‹ Back"
            minHeight = dp(48)
            setOnClickListener { finish() }
        })
        root.addView(MaterialButton(this).apply {
            text = "Refresh verified status"
            minHeight = dp(48)
            setOnClickListener { render() }
        })

        val palette = ThemeStore.get(this)
        root.addView(TextView(this).apply {
            text = "VERIFIED ANDROID SETUP"
            textSize = 11f
            setTextColor(palette.primary)
        })
        root.addView(TextView(this).apply {
            text = "Protection readiness"
            textSize = 27f
            setTextColor(palette.text)
        })
        root.addView(TextView(this).apply {
            text = "Fresh Android checks. Stored preferences alone never prove that a service is running."
            textSize = 13f
            setTextColor(palette.muted)
        })

        SetupStatusSnapshot.read(this).items.forEach { item ->
            val stateColor = when (item.state) {
                SetupState.VERIFIED -> palette.success
                SetupState.ACTION_REQUIRED -> palette.warning
                SetupState.NEUTRAL, SetupState.UNSUPPORTED, SetupState.UNKNOWN -> palette.muted
            }
            val card = MaterialCardView(this).apply {
                radius = dp(14).toFloat()
                setCardBackgroundColor(palette.surface)
                strokeColor = palette.variant
                strokeWidth = dp(1)
            }
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
            }
            body.addView(TextView(this).apply {
                text = item.title
                textSize = 16f
                setTextColor(palette.text)
            })
            body.addView(TextView(this).apply {
                text = item.state.name.replace('_', ' ')
                textSize = 11f
                setTextColor(stateColor)
            })
            body.addView(TextView(this).apply {
                text = item.detail
                textSize = 13f
                setTextColor(palette.muted)
            })
            if (item.state == SetupState.ACTION_REQUIRED || item.state == SetupState.UNKNOWN) {
                SetupStatusSnapshot.action(this, item.id)?.let { intent ->
                val actionLabel = when (item.id) {
                    "monitor" -> "Review monitor settings"
                    "a2" -> "Manage trusted accessories"
                    "bt" -> "Open Bluetooth settings"
                    "bt_permission" -> "Open app permissions"
                    "exact" -> "Open exact-alarm access"
                    "fullscreen" -> "Open full-screen access"
                    "battery" -> "Review battery restrictions"
                    "notifications" -> "Open notification settings"
                    "lock" -> "Open Android screen-lock settings"
                    else -> "Open relevant Android settings"
                }
                body.addView(MaterialButton(this).apply {
                    text = actionLabel
                    minHeight = dp(48)
                    setOnClickListener { openStatusAction(item.id, intent) }
                })
                }
            }
            card.addView(body)
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }

        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, scroll)
        rendered = true
    }

    private fun openStatusAction(id: String, intent: Intent) {
        when (id) {
            "monitor", "a2" -> runCatching { startActivity(intent) }.onFailure {
                Toast.makeText(this, "Could not open the in-app destination: " + it.message, Toast.LENGTH_LONG).show()
            }
            "exact" -> AndroidDeviceCompatibility.open(
                this, intent, AndroidDeviceCompatibility.appInfoIntent(packageName)
            )
            "fullscreen" -> AndroidDeviceCompatibility.open(
                this, intent,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
            "battery" -> AndroidDeviceCompatibility.open(
                this, intent,
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                AndroidDeviceCompatibility.appInfoIntent(packageName)
            )
            "notifications" -> AndroidDeviceCompatibility.open(
                this, intent, AndroidDeviceCompatibility.appInfoIntent(packageName)
            )
            "bt", "lock", "bt_permission" -> AndroidDeviceCompatibility.open(
                this, intent, Intent(Settings.ACTION_SETTINGS)
            )
            else -> AndroidDeviceCompatibility.open(this, intent)
        }
    }
}
