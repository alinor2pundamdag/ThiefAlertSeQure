package com.thiefalertseq ure.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var details: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        status = TextView(this).apply { textSize = 26f }
        details = TextView(this).apply { textSize = 16f }
        val authenticate = Button(this).apply { text = "Authenticate to stop alarm"; setOnClickListener { authenticateOwner() } }
        val refresh = Button(this).apply { text = "Refresh status"; setOnClickListener { render() } }
        root.addView(status); root.addView(details); root.addView(authenticate); root.addView(refresh)
        setContentView(root)
        render()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 22)
    }

    override fun onResume() { super.onResume(); render() }

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
            ProtectionMode.ALARM_TRIGGERED -> "Put the correct phone lock, face unlock, or fingerprint to stop the alarm. Charger reconnection cannot cancel it."
        }
    }

    private fun authenticateOwner() {
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            details.text = "Set a secure device PIN, pattern, or password and enroll an eligible biometric first."
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val old = ProtectionStore(this@MainActivity).read()
                    val next = ProtectionEngine.authenticated(old, System.currentTimeMillis())
                    ProtectionStore(this@MainActivity).write(next)
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
