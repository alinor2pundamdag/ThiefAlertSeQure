package com.thiefalertsequre.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class SettingsActivity : FragmentActivity() {
    private lateinit var root: LinearLayout
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { renderStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(getColor(R.color.tas_background))
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(32))
        }
        scroll.addView(root)
        root.addView(text("CONTROL CENTER", 11f, R.color.tas_primary, true))
        root.addView(space(8))
        root.addView(text("Settings", 32f, R.color.tas_text, true))
        root.addView(space(8))
        root.addView(text("Manage Android permissions and reliability settings. Android remains in control of system-level access.", 14f, R.color.tas_muted))
        root.addView(space(18))

        val statusCard = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.tas_surface_variant)
            setCardBackgroundColor(getColor(R.color.tas_surface))
        }
        val statusContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        statusContent.addView(text("DEVICE READINESS", 11f, R.color.tas_primary, true))
        statusContent.addView(space(8))
        statusContent.addView(text(statusSummary(), 13f, R.color.tas_text))
        statusCard.addView(statusContent)
        root.addView(statusCard, matchWrap())
        root.addView(space(22))

        root.addView(text("SYSTEM ACCESS", 11f, R.color.tas_primary, true))
        root.addView(space(10))
        root.addView(action("Alarms & reminders access") { openExactAlarmSettings() })
        root.addView(action("Notifications permission") { requestNotifications() })
        root.addView(action("App notification settings") {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        })
        root.addView(action("Battery and background restrictions") { openBatterySettings() })
        root.addView(action("Do Not Disturb settings") { openDndSettings() })
        root.addView(action("Android app information") {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        root.addView(space(22))

        root.addView(text("LOCAL TAMPER HISTORY", 11f, R.color.tas_primary, true))
        root.addView(space(8))
        val historyText = LocalTamperHistory.read(this, 20).ifEmpty {
            listOf("No recorded protection events yet.")
        }.joinToString("\\n")
        root.addView(text(historyText, 12f, R.color.tas_text))
        root.addView(space(8))
        root.addView(action("Clear local event history") {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear local history?")
                .setMessage("This permanently clears the on-device protection event log. It does not change protection state.")
                .setNegativeButton("Keep history", null)
                .setPositiveButton("Clear") { _, _ ->
                    LocalTamperHistory.clear(this)
                    render()
                }.show()
        })
        root.addView(space(22))

        root.addView(text("PRIVACY & DATA", 11f, R.color.tas_primary, true))
        root.addView(space(10))
        root.addView(text(
            "No account, server, analytics, advertising SDK, or internet permission is configured. Protection state and biometric cooldown counters are stored locally. Device credentials and biometric templates are handled by Android and are not read by this app.",
            13f, R.color.tas_muted
        ))
        root.addView(space(10))
        root.addView(action("Review privacy notice / change choice") {
            getSharedPreferences("privacy_notice_v1", MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        })
        root.addView(space(22))
        root.addView(action("Back to protection dashboard") { finish() })
        setContentView(scroll)
    }

    private fun renderStatus() {
        if (::root.isInitialized) {
            // Recreate the screen so permission status is read from Android's current state.
            render()
        }
    }

    private fun statusSummary(): String {
        val alarm = if (Build.VERSION.SDK_INT < 31 ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ) "Exact alarm scheduling: available" else "Exact alarm scheduling: NOT granted — timing may be delayed"
        val runtimeAllowed = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val notifications = if (runtimeAllowed && NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            "Notifications: allowed"
        } else {
            "Notifications: disabled in Android settings or permission not granted"
        }
        return "$alarm\n$notifications\nMinimum supported Android: 8.0 (API 26)"
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        } else {
            android.widget.Toast.makeText(this, "Separate exact-alarm access is not required on this Android version.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun openBatterySettings() {
        open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openDndSettings() {
        open(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS), Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    private fun open(primary: Intent, fallback: Intent? = null) {
        try {
            startActivity(primary)
        } catch (_: Exception) {
            if (fallback != null) {
                try { startActivity(fallback) } catch (_: Exception) {
                    android.widget.Toast.makeText(this, "This settings page is not available on this device.", android.widget.Toast.LENGTH_LONG).show()
                }
            } else {
                android.widget.Toast.makeText(this, "This settings page is not available on this device.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun action(value: String, click: () -> Unit) = MaterialButton(this).apply {
        text = value
        isAllCaps = false
        cornerRadius = dp(15)
        minHeight = dp(52)
        strokeWidth = dp(1)
        strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.tas_primary))
        backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tas_surface))
        setTextColor(getColor(R.color.tas_text))
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(getColor(color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
