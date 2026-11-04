package com.thiefalertsequre.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 1_000L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        status = TextView(this).apply { textSize = 26f }
        details = TextView(this).apply { textSize = 16f }
        val authenticate = Button(this).apply { text = "Authenticate to stop alarm"; setOnClickListener { authenticateOwner() } }
        val exactAlarm = Button(this).apply { text = "Enable precise 10-second alarm timing"; setOnClickListener { requestExactAlarmAccess() } }
        val refresh = Button(this).apply { text = "Refresh protection status"; setOnClickListener { reconcileCharging(); render() } }
        root.addView(status); root.addView(details); root.addView(authenticate); root.addView(exactAlarm); root.addView(refresh)
        setContentView(root)
        reconcileCharging()
        render()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 22)
    }

    override fun onResume() { super.onResume(); reconcileCharging(); render(); handler.removeCallbacks(ticker); handler.post(ticker) }
    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }

    private fun reconcileCharging() {
        val charging = getSystemService(BatteryManager::class.java).isCharging
        val store = ProtectionStore(this)
        val old = store.read()
        val now = System.currentTimeMillis()
        val next = when {
            charging -> ProtectionEngine.chargerConnected(old, now)
            old.mode == ProtectionMode.ARMED -> ProtectionEngine.chargerDisconnected(old, now)
            old.mode == ProtectionMode.COUNTDOWN -> ProtectionEngine.evaluateDeadline(old, now)
            else -> old
        }
        if (next != old) store.write(next)
        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(this, next.deadlineEpochMs)
            ProtectionMode.ALARM_TRIGGERED -> runCatching {
                ContextCompat.startForegroundService(this, Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_START))
            }
            else -> AlarmScheduler.cancel(this)
        }
    }

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
            } else details.text = "Precise alarm scheduling access is already enabled."
        } else details.text = "Exact alarm access is not a separate permission on this Android version."
    }

    private fun render() {
        val s = ProtectionStore(this).read()
        status.text = when (s.mode) {
            ProtectionMode.DISARMED -> "Protection disarmed"
            ProtectionMode.ARMED -> "PROTECTION ARMED"
            ProtectionMode.COUNTDOWN -> "CHARGER REMOVED — COUNTDOWN"
            ProtectionMode.ALARM_TRIGGERED -> "ALARM TRIGGERED"
        }
        details.text = when (s.mode) {
            ProtectionMode.DISARMED -> "Connect the charger to arm protection automatically."
            ProtectionMode.ARMED -> "Charger connected. Unplugging starts a 10-second countdown."
            ProtectionMode.COUNTDOWN -> "Remaining: " + (((s.deadlineEpochMs - System.currentTimeMillis()).coerceAtLeast(0L) + 999) / 1000) + " seconds"
            ProtectionMode.ALARM_TRIGGERED -> "Put the correct phone lock, face unlock, or fingerprint to stop the noise charging alert. Reconnecting the charger cannot cancel it."
        }
    }

    private fun authenticateOwner() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            details.text = "Set a secure device PIN, pattern, or password and enroll an eligible biometric first."
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val store = ProtectionStore(this@MainActivity)
                    val next = ProtectionEngine.authenticated(store.read(), System.currentTimeMillis())
                    store.write(next)
                    AlarmScheduler.cancel(this@MainActivity)
                    stopService(Intent(this@MainActivity, AlarmService::class.java))
                    render()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify device owner")
            .setSubtitle("Use your phone PIN, pattern, password, or eligible biometric")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}
