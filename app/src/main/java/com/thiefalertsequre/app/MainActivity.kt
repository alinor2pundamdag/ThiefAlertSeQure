package com.thiefalertsequre.app

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var biometricButton: Button
    private lateinit var credentialButton: Button
    private lateinit var cancelButton: Button
    private var biometricPrompt: BiometricPrompt? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        details.text = if (granted) "Notifications enabled." else
            "Notifications are disabled. Enable them in Android settings so the alarm notification can be shown."
    }

    private val ticker = object : Runnable {
        override fun run() {
            render()
            status.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        val appTitle = TextView(this).apply {
            text = "ThiefAlertSeQure"
            textSize = 28f
        }
        status = TextView(this).apply { textSize = 23f }
        details = TextView(this).apply { textSize = 16f }
        biometricButton = Button(this).apply {
            text = "Authenticate with fingerprint / strong face"
            setOnClickListener { authenticateBiometric() }
        }
        credentialButton = Button(this).apply {
            text = "Authenticate with device PIN / pattern / password"
            setOnClickListener { authenticateDeviceCredential() }
        }
        cancelButton = Button(this).apply {
            text = "Cancel authentication"
            setOnClickListener {
                biometricPrompt?.cancelAuthentication()
                biometricPrompt = null
                details.text = "Authentication cancelled. The alarm remains active."
                render()
            }
        }
        val exactAlarm = Button(this).apply {
            text = "Enable precise 10-second alarm timing"
            setOnClickListener { requestExactAlarmAccess() }
        }
        val refresh = Button(this).apply {
            text = "Refresh protection status"
            setOnClickListener { reconcileCharging(); render() }
        }
        root.addView(appTitle)
        root.addView(status)
        root.addView(details)
        root.addView(biometricButton)
        root.addView(credentialButton)
        root.addView(cancelButton)
        root.addView(exactAlarm)
        root.addView(refresh)
        setContentView(root)

        reconcileCharging()
        render()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        reconcileCharging()
        render()
        status.removeCallbacks(ticker)
        status.post(ticker)
    }

    override fun onPause() {
        status.removeCallbacks(ticker)
        super.onPause()
    }

    private fun reconcileCharging() {
        val charging = getSystemService(BatteryManager::class.java).isCharging
        val store = ProtectionStore(this)
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(this)
        val next = store.update { old ->
            when {
                charging -> ProtectionEngine.chargerConnected(old, now)
                old.mode == ProtectionMode.ARMED -> ProtectionEngine.chargerDisconnected(old, now, elapsed, boot)
                old.mode == ProtectionMode.COUNTDOWN -> ProtectionEngine.evaluateDeadline(old, now, elapsed, boot)
                else -> old
            }
        }
        when (next.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(this, next.deadlineEpochMs, next.deadlineElapsedRealtimeMs, next.bootCount)
            ProtectionMode.ALARM_TRIGGERED -> runCatching {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_START)
                )
            }
            else -> AlarmScheduler.cancel(this)
        }
    }

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= 31) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            } else {
                details.text = "Precise alarm scheduling access is already enabled."
            }
        } else {
            details.text = "Exact alarm access is not a separate permission on this Android version."
        }
    }

    private fun render() {
        val store = ProtectionStore(this)
        val state = store.read()
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(this)
        val throttle = store.readAuthenticationThrottle(now, elapsed, boot)
        val cooldownMs = AuthenticationThrottle.remainingMs(throttle, now, elapsed, boot)
        val coolingDown = cooldownMs > 0L
        status.text = when (state.mode) {
            ProtectionMode.DISARMED -> "Protection disarmed"
            ProtectionMode.ARMED -> "PROTECTION ARMED"
            ProtectionMode.COUNTDOWN -> "CHARGER REMOVED — COUNTDOWN"
            ProtectionMode.ALARM_TRIGGERED -> "ALARM TRIGGERED"
        }
        val stateMessage = when (state.mode) {
            ProtectionMode.DISARMED -> "Connect the charger to arm protection automatically."
            ProtectionMode.ARMED -> "Charger connected. Unplugging starts a 10-second countdown."
            ProtectionMode.COUNTDOWN -> "Remaining: " +
                ((ProtectionEngine.remainingMs(state, now, ProtectionClock.elapsedRealtime(), ProtectionClock.bootCount(this)) + 999) / 1000) + " seconds"
            ProtectionMode.ALARM_TRIGGERED ->
                "Use the phone's own PIN, pattern, password, fingerprint, or supported strong face authentication to stop the alarm."
        }
        val attemptMessage = when {
            coolingDown -> "Authentication cooldown: " + ((cooldownMs + 999) / 1000) +
                " seconds remaining. Biometric attempts are temporarily disabled."
            state.mode == ProtectionMode.ALARM_TRIGGERED ->
                "Failed biometric attempts: ${throttle.failedAttempts}/${AuthenticationThrottle.MAX_FAILURES}. " +
                    "Five failures start a 30-second cooldown."
            else -> ""
        }
        details.text = listOf(stateMessage, attemptMessage).filter { it.isNotBlank() }.joinToString("\n\n")
        val alarmActive = state.mode == ProtectionMode.ALARM_TRIGGERED
        val biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val credentialAvailable = getSystemService(KeyguardManager::class.java).isDeviceSecure
        biometricButton.isEnabled = alarmActive && biometricAvailable && !coolingDown
        credentialButton.isEnabled = alarmActive && credentialAvailable && !coolingDown
        cancelButton.isEnabled = biometricPrompt != null
    }

    private fun authenticateBiometric() {
        val store = ProtectionStore(this)
        val state = store.read()
        if (state.mode != ProtectionMode.ALARM_TRIGGERED) return
        val authNow = System.currentTimeMillis()
        val authElapsed = ProtectionClock.elapsedRealtime()
        val authBoot = ProtectionClock.bootCount(this)
        val throttle = store.readAuthenticationThrottle(authNow, authElapsed, authBoot)
        if (AuthenticationThrottle.remainingMs(throttle, authNow, authElapsed, authBoot) > 0L) {
            render()
            return
        }
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            details.text = "No enrolled strong biometric is available. Use your device PIN, pattern, or password."
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPrompt = null
                    completeOwnerAuthentication()
                }

                override fun onAuthenticationFailed() {
                    val failedAt = System.currentTimeMillis()
                    val failedElapsed = ProtectionClock.elapsedRealtime()
                    val failedBoot = ProtectionClock.bootCount(this@MainActivity)
                    val next = store.recordAuthenticationFailure(failedAt, failedElapsed, failedBoot)
                    if (AuthenticationThrottle.remainingMs(next, failedAt, failedElapsed, failedBoot) > 0L) {
                        biometricPrompt?.cancelAuthentication()
                        biometricPrompt = null
                        details.text = "Five consecutive biometric failures. Wait 30 seconds before trying again."
                    } else {
                        details.text = "Biometric not recognized. Failed attempts: ${next.failedAttempts}/5."
                    }
                    render()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricPrompt = null
                    when (errorCode) {
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            store.startAuthenticationCooldown(
                                System.currentTimeMillis(), ProtectionClock.elapsedRealtime(), ProtectionClock.bootCount(this@MainActivity)
                            )
                            details.text = "Android biometric lockout. Wait 30 seconds or use device credentials if Android permits."
                        }
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            details.text = "Authentication cancelled. The alarm remains active."
                        }
                        else -> details.text = "$errString. The alarm remains active."
                    }
                    render()
                }
            }
        )
        biometricPrompt = prompt
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify ThiefAlertSeQure owner")
            .setSubtitle("Use an enrolled strong fingerprint or strong face biometric")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(promptInfo)
        render()
    }

    @Suppress("DEPRECATION")
    private fun authenticateDeviceCredential() {
        val state = ProtectionStore(this).read()
        if (state.mode != ProtectionMode.ALARM_TRIGGERED) return
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(this)
        val throttle = ProtectionStore(this).readAuthenticationThrottle(now, elapsed, boot)
        if (AuthenticationThrottle.remainingMs(throttle, now, elapsed, boot) > 0L) {
            render()
            return
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) {
            details.text = "Set a secure device PIN, pattern, or password in Android settings first."
            return
        }
        val credentialIntent = keyguard.createConfirmDeviceCredentialIntent(
            "Verify device owner",
            "Enter your existing phone PIN, pattern, or password"
        )
        if (credentialIntent == null) {
            details.text = "Android could not open device credential verification."
            return
        }
        startActivityForResult(credentialIntent, REQUEST_DEVICE_CREDENTIAL)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEVICE_CREDENTIAL) {
            if (resultCode == RESULT_OK) {
                completeOwnerAuthentication()
            } else {
                details.text = "Device authentication cancelled or unsuccessful. The alarm remains active. Android manages device-credential lockout."
                render()
            }
        }
    }

    private fun completeOwnerAuthentication() {
        val store = ProtectionStore(this)
        val next = store.update {
            ProtectionEngine.authenticated(it, System.currentTimeMillis())
        }
        if (next.mode == ProtectionMode.DISARMED) {
            store.resetAuthenticationFailures()
            AlarmScheduler.cancel(this)
            stopService(Intent(this, AlarmService::class.java))
            details.text = "Owner authenticated. Alarm stopped."
        }
        render()
    }

    companion object {
        private const val REQUEST_DEVICE_CREDENTIAL = 4102
    }
}
