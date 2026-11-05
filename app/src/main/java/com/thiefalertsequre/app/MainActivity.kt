package com.thiefalertsequre.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : FragmentActivity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var biometricButton: MaterialButton
    private lateinit var credentialButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var managedButton: MaterialButton
    private lateinit var screenLockButton: MaterialButton
    private var fullScreenAccessButton: MaterialButton? = null
    private var biometricPrompt: BiometricPrompt? = null
    private var autoAuthenticationRequested = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        details.text = if (granted) {
            "Notifications enabled. Alarm status can now appear in the notification shade."
        } else {
            "Notifications are disabled. Enable them in Android settings to make the active-alarm recovery action visible."
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            render()
            launchAutomaticOwnerAuthenticationIfNeeded()
            status.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        if (!getSharedPreferences("privacy_notice_v1", MODE_PRIVATE).contains("decision")) {
            showPrivacyScreen()
            return
        }
        buildDashboard()
    }

    private fun showPrivacyScreen() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(getColor(R.color.tas_background))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(36), dp(26), dp(36))
        }
        scroll.addView(root)
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            contentDescription = "ThiefAlertSeQure app icon"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(icon, LinearLayout.LayoutParams(dp(84), dp(84)))
        root.addView(space(22))
        root.addView(label("YOUR DEVICE.\nYOUR CONTROL.", 30f, getColor(R.color.tas_text), true), matchWrap())
        root.addView(space(10))
        root.addView(label("PRIVACY NOTICE & LOCAL PROTECTION", 11f, getColor(R.color.tas_primary), true), matchWrap())
        root.addView(space(18))
        val notice = card()
        val body = column()
        body.setPadding(dp(18), dp(18), dp(18), dp(18))
        body.addView(label("What this app does", 18f, getColor(R.color.tas_text), true), matchWrap())
        body.addView(space(8))
        body.addView(label(
            "ThiefAlertSeQure monitors charger connection locally, starts a 10-second countdown when unplugged, and plays an alarm if the countdown expires. You can stop a latched alarm only after Android verifies your existing device credential or an enrolled supported strong biometric.",
            14f, getColor(R.color.tas_muted), false
        ), matchWrap())
        body.addView(space(16))
        body.addView(label("Data and privacy", 18f, getColor(R.color.tas_text), true), matchWrap())
        body.addView(space(8))
        body.addView(label(
            "The app is designed to work without an account, backend, analytics, advertising, or internet access. Protection state and biometric-failure cooldown counters are stored locally on this device. Your PIN, pattern, password, and biometric templates are never collected or stored by this app; Android performs verification.",
            14f, getColor(R.color.tas_muted), false
        ), matchWrap())
        body.addView(space(16))
        body.addView(label("System access and limitations", 18f, getColor(R.color.tas_text), true), matchWrap())
        body.addView(space(8))
        body.addView(label(
            "The app may use boot and charger broadcasts, alarm scheduling, notifications, alarm audio, and a foreground service. Exact alarm access and notification permission are controlled by Android. Optional Device Owner mode requires separate device enrollment and can restrict device settings. No ordinary app can guarantee protection against force-stop, uninstall, physical shutdown, recovery controls, battery removal, or every manufacturer-specific power policy. Alarm delivery may be delayed when Android restricts background execution or exact alarms.",
            14f, getColor(R.color.tas_muted), false
        ), matchWrap())
        body.addView(space(16))
        body.addView(label("Your choice", 18f, getColor(R.color.tas_text), true), matchWrap())
        body.addView(space(6))
        body.addView(label(
            "Accept records that you acknowledge this notice. Decline records that you do not accept it. Both choices open the dashboard and retain the same essential local protection functions; declining does not disable core protection or falsely represent consent. You can review this notice later in Settings.",
            13f, getColor(R.color.tas_muted), false
        ), matchWrap())
        notice.addView(body)
        root.addView(notice, matchWrap())
        root.addView(space(22))
        root.addView(button("Accept & continue") {
            getSharedPreferences("privacy_notice_v1", MODE_PRIVATE).edit()
                .putString("decision", "accepted").putLong("decision_at", System.currentTimeMillis()).apply()
            buildDashboard()
        }, matchWrap())
        root.addView(space(10))
        root.addView(outlinedButton("Decline & continue") {
            getSharedPreferences("privacy_notice_v1", MODE_PRIVATE).edit()
                .putString("decision", "declined").putLong("decision_at", System.currentTimeMillis()).apply()
            buildDashboard()
        }, matchWrap())
        root.addView(space(16))
        root.addView(label("LOCAL-FIRST • NO ACCOUNT • NO CLOUD SYNC", 10f, getColor(R.color.tas_muted), true), matchWrap())
        setContentView(scroll)
    }

    private fun buildDashboard() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(getColor(R.color.tas_background))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(32))
        }
        scroll.addView(root)

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandIcon = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            contentDescription = "ThiefAlertSeQure shield and alarm icon"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        brandRow.addView(brandIcon, LinearLayout.LayoutParams(dp(52), dp(52)))
        val brand = label("THIEF ALERT  /  SEQURE", 12f, getColor(R.color.tas_primary), true).apply {
            letterSpacing = 0.18f
        }
        val brandTextParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        brandTextParams.marginStart = dp(12)
        brandRow.addView(brand, brandTextParams)
        root.addView(brandRow, matchWrap())
        root.addView(space(8))

        root.addView(label("Protection that stays\non this device.", 30f, getColor(R.color.tas_text), true), matchWrap())
        root.addView(space(8))
        root.addView(label("LOCAL SECURITY  •  NO ACCOUNT  •  NO BACKEND", 10f, getColor(R.color.tas_muted), true), matchWrap())
        root.addView(space(24))

        val statusCard = card()
        val statusContent = column()
        statusContent.setPadding(dp(20), dp(20), dp(20), dp(20))
        statusContent.addView(label("LIVE PROTECTION STATUS", 11f, getColor(R.color.tas_muted), true), matchWrap())
        statusContent.addView(space(12))
        status = label("Checking device…", 24f, getColor(R.color.tas_text), true)
        statusContent.addView(status, matchWrap())
        statusContent.addView(space(12))
        details = label("Reading local protection state.", 14f, getColor(R.color.tas_muted), false)
        details.setLineSpacing(dp(4).toFloat(), 1f)
        statusContent.addView(details, matchWrap())
        statusCard.addView(statusContent)
        root.addView(statusCard, matchWrap())
        root.addView(space(20))

        root.addView(sectionTitle("OWNER AUTHENTICATION"), matchWrap())
        root.addView(space(10))
        biometricButton = button("Use fingerprint or supported strong face") { authenticateBiometric() }
        credentialButton = outlinedButton("Use device PIN, pattern or password") { authenticateDeviceCredential() }
        cancelButton = textButton("Cancel authentication") {
            biometricPrompt?.cancelAuthentication()
            biometricPrompt = null
            details.text = "Authentication cancelled. The alarm remains active."
            render()
        }
        root.addView(biometricButton, matchWrap())
        root.addView(space(8))
        root.addView(credentialButton, matchWrap())
        root.addView(space(4))
        root.addView(cancelButton, matchWrap())
        root.addView(space(24))

        root.addView(sectionTitle("DEVICE HARDENING"), matchWrap())
        root.addView(space(10))
        val hardeningCard = card()
        val hardeningContent = column()
        hardeningContent.setPadding(dp(16), dp(16), dp(16), dp(16))
        hardeningContent.addView(label("Managed-device lockdown", 17f, getColor(R.color.tas_text), true), matchWrap())
        hardeningContent.addView(space(6))
        hardeningContent.addView(label(
            "Optional Android Enterprise mode. On a properly enrolled Device Owner device, kiosk mode can hide the global-actions power menu and apply supported user restrictions. It cannot prevent a hardware-level forced shutdown or recovery reset.",
            13f, getColor(R.color.tas_muted), false
        ), matchWrap())
        hardeningContent.addView(space(12))
        val deviceAdmin = getSystemService(DevicePolicyManager::class.java)
        val adminComponent = ComponentName(this, ThiefAlertAdminReceiver::class.java)
        screenLockButton = outlinedButton(
            if (deviceAdmin.isAdminActive(adminComponent)) "Automatic screen lock: ENABLED" else "Enable automatic screen lock"
        ) { requestDeviceAdministrator() }
        hardeningContent.addView(screenLockButton, matchWrap())
        hardeningContent.addView(space(8))
        managedButton = button("Check Device Owner eligibility") { manageDeviceOwnerMode() }
        hardeningContent.addView(managedButton, matchWrap())
        hardeningContent.addView(space(8))
        hardeningContent.addView(label(
            "This is not a normal permission prompt. Device Owner enrollment generally requires a new or factory-reset device. Do not provision until personal data is backed up.",
            12f, getColor(R.color.tas_warning), false
        ), matchWrap())
        hardeningCard.addView(hardeningContent)
        root.addView(hardeningCard, matchWrap())
        root.addView(space(20))

        root.addView(sectionTitle("RELIABILITY & SYSTEM ACCESS"), matchWrap())
        root.addView(space(10))
        root.addView(outlinedButton("Enable precise 10-second alarm timing") { requestExactAlarmAccess() }, matchWrap())
        if (Build.VERSION.SDK_INT >= 34) {
            root.addView(space(8))
            fullScreenAccessButton = outlinedButton("Allow full-screen alarm and authentication") { requestFullScreenIntentAccess() }
            root.addView(fullScreenAccessButton, matchWrap())
        }
        root.addView(space(8))
        root.addView(outlinedButton("Open alarm notification settings") { openAlarmNotificationSettings() }, matchWrap())
        root.addView(space(8))
        root.addView(outlinedButton("Set device PIN, pattern or password") { openDeviceSecuritySettings() }, matchWrap())
        root.addView(space(8))
        root.addView(outlinedButton("Configure Do Not Disturb alarm exceptions") { openDndSettings() }, matchWrap())
        root.addView(space(8))
        root.addView(outlinedButton("Set battery usage to Unrestricted") { openBatteryOptimizationSettings() }, matchWrap())
        root.addView(space(8))
        root.addView(outlinedButton("Review app battery and background settings") { openAppSettings() }, matchWrap())
        root.addView(space(8))
        root.addView(outlinedButton("Open sound, export and advanced settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }, matchWrap())
        root.addView(space(8))
        root.addView(textButton("Refresh protection status") { reconcileCharging(); render() }, matchWrap())
        root.addView(space(18))
        root.addView(label(
            "Privacy: protection state, authentication counters and alarm assets remain on this device. Android controls device credentials and system-level restrictions.",
            12f, getColor(R.color.tas_muted), false
        ), matchWrap())

        setContentView(scroll)
        reconcileCharging()
        render()
        status.removeCallbacks(ticker)
        status.post(ticker)

        if (ProtectionStore(this).read().mode != ProtectionMode.ALARM_TRIGGERED && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        reconcileCharging()
        ManagedProtectionController.startIfConfigured(this)
        if (!::status.isInitialized) return
        render()
        launchAutomaticOwnerAuthenticationIfNeeded()
        status.removeCallbacks(ticker)
        status.post(ticker)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchAutomaticOwnerAuthenticationIfNeeded()
    }

    override fun onPause() {
        if (::status.isInitialized) status.removeCallbacks(ticker)
        super.onPause()
    }

    private fun requestDeviceAdministrator() {
        val manager = getSystemService(DevicePolicyManager::class.java)
        val component = ComponentName(this, ThiefAlertAdminReceiver::class.java)
        if (manager.isAdminActive(component)) {
            details.text = "Automatic screen locking is enabled. The device will lock when the alarm starts."
            render()
            return
        }
        val request = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allow ThiefAlertSeQure to lock the phone when its anti-theft alarm is triggered. Android still controls the PIN, pattern, password and biometric verification.")
        }
        startActivity(request)
    }

    private fun requestFullScreenIntentAccess() {
        if (Build.VERSION.SDK_INT < 34) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.canUseFullScreenIntent()) {
            details.text = "Full-screen alarm access is already enabled."
            return
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }.onFailure { showError("Android could not open full-screen notification access: ${it.message}") }
    }

    private fun launchAutomaticOwnerAuthenticationIfNeeded() {
        if (autoAuthenticationRequested || !::status.isInitialized) return
        if (ProtectionStore(this).read().mode != ProtectionMode.ALARM_TRIGGERED) {
            autoAuthenticationRequested = false
            return
        }
        autoAuthenticationRequested = true
        val keyguard = getSystemService(KeyguardManager::class.java)
        val strongBiometric = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if ((Build.VERSION.SDK_INT >= 30 && keyguard.isDeviceSecure) || strongBiometric) {
            authenticateBiometric()
        } else if (keyguard.isDeviceSecure) {
            authenticateDeviceCredential()
        } else {
            // Do not latch the one-shot prompt guard when the owner has not yet
            // configured a secure credential. Re-check automatically on resume.
            autoAuthenticationRequested = false
            details.text = "No secure device credential is configured. Set a PIN, pattern or password in Android settings to enable owner verification."
            render()
        }
    }

    private fun manageDeviceOwnerMode() {
        if (!ManagedProtectionController.isDeviceOwner(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Device Owner enrollment required")
                .setMessage(
                    "Android does not let an ordinary app grant itself Device Owner privileges. " +
                    "For a personally owned test device, enrollment is normally performed during initial setup after a factory reset using Android Enterprise QR provisioning, or by an authorized ADB provisioning workflow.\n\n" +
                    "ADB test command (only after backing up and preparing a reset device):\n" +
                    "adb shell dpm set-device-owner com.thiefalertsequre.app/.ThiefAlertAdminReceiver\n\n" +
                    "Do not run this on a device containing data you have not backed up. Device Owner policies affect the whole device."
                )
                .setPositiveButton("Open Android settings") { _, _ -> openAppSettings() }
                .setNegativeButton("Close", null)
                .show()
            return
        }

        val configured = ManagedProtectionController.isPolicyConfigured(this)
        if (configured) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Disable managed lockdown?")
                .setMessage("This removes the kiosk allowlist and the app's supported restrictions for factory reset in Settings, safe boot, debugging, adding users and USB file transfer. Hardware recovery controls remain outside app control.")
                .setNegativeButton("Keep protection", null)
                .setPositiveButton("Disable lockdown") { _, _ ->
                    runCatching { ManagedProtectionController.disable(this) }
                        .onSuccess { render() }
                        .onFailure { showError("Could not disable managed policies: ${it.message}") }
                }
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("Enable managed lockdown?")
                .setMessage("This is an explicit whole-device policy. It allowlists this app for lock task mode, hides Android global actions while kiosk mode is active, and restricts selected Settings actions. Keep this app's in-app exit control available. It does not block forced hardware shutdown, bootloader recovery, or OEM-level reset.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Enable managed protection") { _, _ ->
                    runCatching { ManagedProtectionController.enable(this) }
                        .onSuccess { render() }
                        .onFailure { showError("Managed protection could not be enabled: ${it.message}") }
                }
                .show()
        }
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Protection setup")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun reconcileCharging() {
        // A battery snapshot is state evidence, not proof of a physical transition.
        // Only PowerTransitionReceiver may create a charger countdown.
        PowerTransitionCoordinator.initializeBaseline(this)
        val store = ProtectionStore(this)
        val current = store.read()
        val monitorEnabled = getSharedPreferences(
            ProtectionMonitorService.PREFS, MODE_PRIVATE
        ).getBoolean(
            ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        // Owner opt-out is authoritative for every non-triggered state. A
        // connected snapshot may arm only while monitoring is enabled.
        val state = when {
            !monitorEnabled && current.mode != ProtectionMode.ALARM_TRIGGERED ->
                store.update { old ->
                    if (old.mode == ProtectionMode.ALARM_TRIGGERED) old
                    else ProtectionState(ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1)
                }
            monitorEnabled && current.mode != ProtectionMode.ALARM_TRIGGERED &&
                ExternalPowerState.readConnected(this) == true ->
                PowerTransitionCoordinator.confirmConnectedAndArm(this, "Connected power confirmed during app reconciliation")
            else -> current
        }
        if (state.mode != ProtectionMode.ALARM_TRIGGERED) {
            runCatching { AlarmVolumeController.restore(this, getSystemService(android.media.AudioManager::class.java)) }
        }
        when (state.mode) {
            ProtectionMode.COUNTDOWN -> AlarmScheduler.schedule(this, state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount)
            ProtectionMode.ALARM_TRIGGERED -> {
                AlarmScheduler.cancel(this)
                runCatching {
                    ContextCompat.startForegroundService(this, Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_START))
                }.onFailure {
                    LocalTamperHistory.record(this, "activity_alarm_restore_failed", it.javaClass.simpleName + ": " + it.message)
                    if (!AlarmScheduler.scheduleImmediateRecovery(this)) {
                        AlarmRecoveryNotice.show(this, "The active protection alarm could not restart automatically")
                    }
                }
            }
            else -> AlarmScheduler.cancel(this)
        }
        syncProtectionMonitor(state)
    }

    private fun syncProtectionMonitor(state: ProtectionState) {
        val prefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
        val enabled = prefs.getBoolean(ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
        if (ProtectionMonitorPolicy.shouldRun(enabled, state.mode)) {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, ProtectionMonitorService::class.java))
            }.onFailure {
                LocalTamperHistory.record(this, "monitor_start_blocked", it.javaClass.simpleName + ": " + it.message)
                prefs.edit().putBoolean(ProtectionMonitorService.KEY_RUNNING, false).apply()
            }
        } else if (!enabled || state.mode == ProtectionMode.DISARMED || state.mode == ProtectionMode.ALARM_TRIGGERED) {
            stopService(Intent(this, ProtectionMonitorService::class.java))
        }
    }

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= 31) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }.onFailure { showError("Android did not open exact-alarm access: ${it.message}") }
            } else {
                details.text = "Precise alarm scheduling access is already enabled."
            }
        } else {
            details.text = "Exact alarm access is not a separate permission on this Android version."
        }
    }

    private fun openAlarmNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching {
                startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, AlarmService.CHANNEL_ID)
                })
            }.onFailure { openAppSettings() }
        } else openAppSettings()
    }

    private fun openDeviceSecuritySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            .onFailure { showError("Android could not open device security settings: ${it.message}") }
    }

    private fun openDndSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)) }
            .onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                    .onFailure { showError("Open Settings > Sound & vibration > Do Not Disturb, then allow alarms for ThiefAlertSeQure where your device provides that control.") }
            }
    }

    private fun openBatteryOptimizationSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }.onFailure { openAppSettings() }
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
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
            ProtectionMode.DISARMED -> "Protection ready"
            ProtectionMode.ARMED -> "Protection armed"
            ProtectionMode.COUNTDOWN -> "Charger removed"
            ProtectionMode.ALARM_TRIGGERED -> "ALARM TRIGGERED"
        }
        status.setTextColor(getColor(when (state.mode) {
            ProtectionMode.DISARMED -> R.color.tas_text
            ProtectionMode.ARMED -> R.color.tas_success
            ProtectionMode.COUNTDOWN -> R.color.tas_warning
            ProtectionMode.ALARM_TRIGGERED -> R.color.tas_danger
        }))

        val stateMessage = when (state.mode) {
            ProtectionMode.DISARMED -> "Connect the charger to arm protection automatically. The built-in alarm is ready; a custom sound is optional."
            ProtectionMode.ARMED -> "Charger connected. Disconnecting it starts the 10-second grace period."
            ProtectionMode.COUNTDOWN -> "Alarm starts in " +
                ((ProtectionEngine.remainingMs(state, now, elapsed, boot) + 999) / 1000) + " seconds. Reconnect the charger to cancel this countdown."
            ProtectionMode.ALARM_TRIGGERED -> "Alarm is latched. Only successful Android device-credential or supported strong-biometric verification can disarm it."
        }
        val attemptMessage = when {
            coolingDown -> "Biometric cooldown: ${(cooldownMs + 999) / 1000}s remaining."
            state.mode == ProtectionMode.ALARM_TRIGGERED ->
                "Biometric failures: ${throttle.failedAttempts}/${AuthenticationThrottle.MAX_FAILURES}. Five consecutive failures start a 30-second cooldown."
            else -> ""
        }
        val exactAlarmMessage = if (Build.VERSION.SDK_INT >= 31 &&
            !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ) {
            "Precise alarm access is off. Android may delay the 10-second deadline; enable Alarms & reminders below."
        } else {
            "Precise alarm scheduling is available on this device."
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notificationsAllowed = notificationManager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val notificationMessage = if (!notificationsAllowed) {
            "Notifications are disabled; enable them for alarm presentation and recovery controls."
        } else {
            "Alarm notification access is available."
        }
        val adminComponent = ComponentName(this, ThiefAlertAdminReceiver::class.java)
        val adminActive = getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent)
        val screenLockMessage = if (adminActive) "Automatic screen locking: ENABLED." else "Automatic screen locking: OFF; enable the device administrator in Device Hardening."
        val fullScreenMessage = if (Build.VERSION.SDK_INT >= 34 && !notificationManager.canUseFullScreenIntent()) {
            "Full-screen alarm access is OFF; enable it in Reliability & System Access."
        } else {
            "Full-screen alarm access is available on this Android version."
        }
        val alarmChannel = if (Build.VERSION.SDK_INT >= 26) notificationManager.getNotificationChannel(AlarmService.CHANNEL_ID) else null
        val alarmChannelReady = alarmChannel == null || alarmChannel.importance >= NotificationManager.IMPORTANCE_HIGH
        val alarmChannelMessage = if (!alarmChannelReady) {
            "Alarm notification channel is below High importance; Android may suppress full-screen presentation. Open Alarm notification settings and set importance to High."
        } else {
            "Alarm notification channel is set to High importance or higher."
        }
        screenLockButton.text = if (adminActive) "Automatic screen lock: ENABLED" else "Enable automatic screen lock"
        if (Build.VERSION.SDK_INT >= 34) fullScreenAccessButton?.text = if (getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) "Full-screen alarm access: ENABLED" else "Allow full-screen alarm and authentication"
        val ownerMessage = if (ManagedProtectionController.isDeviceOwner(this)) {
            if (ManagedProtectionController.isPolicyConfigured(this)) "Managed Device Owner lockdown is configured." else "Device Owner is enrolled; managed lockdown is not enabled."
        } else {
            "Standard Android mode. Device Owner kiosk controls are unavailable until the device is enrolled."
        }
        val soundMessage = "Main alarm sound: ${AlarmSoundStore.displayName(this, AlarmSoundStore.ALARM)} (custom override optional)"
        val monitorPrefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
        val monitorMessage = if (monitorPrefs.getBoolean(ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)) {
            "Charger protection monitor: ENABLED; keep its persistent notification visible. Motion sensing is active only while armed."
        } else {
            "Charger protection monitor: OFF by owner choice; automatic plug/unplug detection is unavailable until re-enabled."
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        val secureCredentialReady = keyguard.isDeviceSecure
        val exactAlarmReady = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val fullScreenReady = Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent()
        val monitorEnabled = monitorPrefs.getBoolean(
            ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val monitorRunning = monitorPrefs.getBoolean(ProtectionMonitorService.KEY_RUNNING, false)
        val bootIdentityReady = ProtectionClock.bootCount(this) >= 0
        val bootIdentityMessage = if (bootIdentityReady) {
            "Boot identity: available for monotonic countdown recovery."
        } else {
            "Boot identity: UNAVAILABLE — countdown expiry is held fail-safe; verify Android system integrity/compatibility."
        }
        val readinessIssues = mutableListOf<String>()
        if (!bootIdentityReady) readinessIssues.add("Wait for Android boot identity before relying on automatic countdown expiry")
        if (!secureCredentialReady) readinessIssues.add("Set a device PIN, pattern or password")
        if (!adminActive) readinessIssues.add("Activate device administrator for automatic screen locking")
        if (!notificationsAllowed) readinessIssues.add("Allow notifications")
        if (!exactAlarmReady) readinessIssues.add("Allow Alarms & reminders for exact deadline recovery")
        if (!fullScreenReady) readinessIssues.add("Allow full-screen alarm access")
        if (!alarmChannelReady) readinessIssues.add("Set Anti-theft alarm notification importance to High")
        if (!monitorEnabled) readinessIssues.add("Enable charger monitoring")
        if (monitorEnabled && !monitorRunning) readinessIssues.add("Wait for the charger-monitor foreground service to start")
        val batteryPowerManager = getSystemService(PowerManager::class.java)
        val batteryOptimizationMessage = if (Build.VERSION.SDK_INT >= 23 && !batteryPowerManager.isIgnoringBatteryOptimizations(packageName)) {
            "Battery optimization is still active. Set ThiefAlertSeQure to Unrestricted in Android battery settings for better OEM background reliability."
        } else {
            "Battery optimization exemption is active or not required on this Android version."
        }
        val readinessMessage = if (readinessIssues.isEmpty()) {
            "CORE READINESS: All required Android capabilities are currently granted. Android/OEM power policies can still interrupt background work."
        } else {
            "CORE READINESS: INCOMPLETE — " + readinessIssues.joinToString("; ") + "."
        }
        details.text = listOf(stateMessage, readinessMessage, bootIdentityMessage, batteryOptimizationMessage, soundMessage, monitorMessage, attemptMessage, exactAlarmMessage, notificationMessage, screenLockMessage, fullScreenMessage, alarmChannelMessage, ownerMessage).filter { it.isNotBlank() }.joinToString("\n\n")

        val alarmActive = state.mode == ProtectionMode.ALARM_TRIGGERED
        val biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        val credentialAvailable = getSystemService(KeyguardManager::class.java).isDeviceSecure
        biometricButton.isEnabled = alarmActive && biometricAvailable && !coolingDown
        credentialButton.isEnabled = alarmActive && credentialAvailable
        cancelButton.isEnabled = biometricPrompt != null
        managedButton.text = when {
            !ManagedProtectionController.isDeviceOwner(this) -> "Device Owner setup guide"
            ManagedProtectionController.isPolicyConfigured(this) -> "Disable managed lockdown"
            else -> "Enable managed lockdown"
        }
    }

    private fun authenticateBiometric() {
        val store = ProtectionStore(this)
        if (store.read().mode != ProtectionMode.ALARM_TRIGGERED) return
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(this)
        val throttle = store.readAuthenticationThrottle(now, elapsed, boot)
        if (AuthenticationThrottle.remainingMs(throttle, now, elapsed, boot) > 0L) {
            if (getSystemService(KeyguardManager::class.java).isDeviceSecure) authenticateDeviceCredential()
            else {
                autoAuthenticationRequested = false
                render()
            }
            return
        }
        val manager = BiometricManager.from(this)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val allowedAuthenticators = if (Build.VERSION.SDK_INT >= 30 && keyguard.isDeviceSecure) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        if (manager.canAuthenticate(allowedAuthenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            if (keyguard.isDeviceSecure) authenticateDeviceCredential()
            else {
                autoAuthenticationRequested = false
                details.text = "No enrolled strong biometric or secure device credential is available."
            }
            return
        }

        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
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
                        details.text = "Five consecutive biometric failures. Use your device PIN, pattern or password."
                        if (getSystemService(KeyguardManager::class.java).isDeviceSecure) authenticateDeviceCredential()
                    } else {
                        details.text = "Biometric not recognized. Failed attempts: ${next.failedAttempts}/5."
                    }
                    render()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricPrompt = null
                    when (errorCode) {
                        BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            store.startAuthenticationCooldown(
                                System.currentTimeMillis(), ProtectionClock.elapsedRealtime(), ProtectionClock.bootCount(this@MainActivity)
                            )
                            details.text = "Android biometric lockout. Switching to device credentials."
                            if (getSystemService(KeyguardManager::class.java).isDeviceSecure) authenticateDeviceCredential()
                        }
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            if (getSystemService(KeyguardManager::class.java).isDeviceSecure) authenticateDeviceCredential()
                            else details.text = "Authentication cancelled. The alarm remains active."
                        }
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> {
                            autoAuthenticationRequested = false
                            details.text = "Authentication dismissed. The alarm remains active; owner verification will be presented again."
                        }
                        else -> {
                            if (getSystemService(KeyguardManager::class.java).isDeviceSecure) {
                                authenticateDeviceCredential()
                            } else {
                                autoAuthenticationRequested = false
                                details.text = "$errString. The alarm remains active."
                            }
                        }
                    }
                    render()
                }
            })

        biometricPrompt = prompt
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify ThiefAlertSeQure owner")
            .setSubtitle("Authenticate with your device biometric or screen-lock credential")
            .setAllowedAuthenticators(allowedAuthenticators)
        if ((allowedAuthenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) == 0) {
            promptInfo.setNegativeButtonText(if (keyguard.isDeviceSecure) "Use device PIN or password" else "Cancel")
        }
        prompt.authenticate(promptInfo.build())
        render()
    }

    @Suppress("DEPRECATION")
    private fun authenticateDeviceCredential() {
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(this)
        val store = ProtectionStore(this)
        if (store.read().mode != ProtectionMode.ALARM_TRIGGERED) return
        // The app cooldown applies to biometric callbacks only. Android Keyguard owns
        // PIN/pattern/password retry throttling and remains the independent recovery path.
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) {
            autoAuthenticationRequested = false
            details.text = "Set a secure device PIN, pattern or password in Android settings first."
            return
        }
        val credentialIntent = keyguard.createConfirmDeviceCredentialIntent(
            "Verify device owner", "Enter your existing phone PIN, pattern or password"
        )
        if (credentialIntent == null) {
            autoAuthenticationRequested = false
            details.text = "Android could not open device credential verification."
            return
        }
        startActivityForResult(credentialIntent, REQUEST_DEVICE_CREDENTIAL)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEVICE_CREDENTIAL) {
            if (resultCode == RESULT_OK) completeOwnerAuthentication()
            else {
                autoAuthenticationRequested = false
                details.text = "Device authentication dismissed or unsuccessful. The alarm remains active; owner verification will be presented again."
                render()
            }
        }
    }

    private fun completeOwnerAuthentication() {
        val store = ProtectionStore(this)
        val authenticated = store.update { ProtectionEngine.authenticated(it, System.currentTimeMillis()) }
        if (authenticated.mode == ProtectionMode.DISARMED) {
            autoAuthenticationRequested = false
            store.resetAuthenticationFailures()
            AlarmScheduler.cancel(this)
            stopService(Intent(this, AlarmService::class.java))
            runCatching { AlarmVolumeController.restore(this, getSystemService(android.media.AudioManager::class.java)) }

            // If the owner reconnected power before authenticating, preserve the
            // required next-cycle behavior: authentication stops the alarm, then
            // the confirmed connected state arms protection for the next unplug.
            val monitorEnabled = getSharedPreferences(
                ProtectionMonitorService.PREFS, MODE_PRIVATE
            ).getBoolean(
                ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
            )
            val next = if (monitorEnabled) {
                PowerTransitionCoordinator.resetBaselineToCurrentPower(
                    this, "Owner authentication completed; current charger state re-baselined"
                )
                store.read()
            } else authenticated
            details.text = if (next.mode == ProtectionMode.ARMED) {
                "Owner authenticated. Alarm stopped. Protection re-armed because the charger is connected."
            } else {
                "Owner authenticated. Alarm stopped. Protection remains disarmed until the charger is reconnected."
            }
            syncProtectionMonitor(next)
        } else {
            syncProtectionMonitor(authenticated)
        }
        render()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun sectionTitle(value: String) = label(value, 11f, getColor(R.color.tas_primary), true).apply {
        letterSpacing = 0.12f
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(22).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = getColor(R.color.tas_surface_variant)
        setCardBackgroundColor(getColor(R.color.tas_surface))
    }

    private fun button(value: String, action: () -> Unit) = MaterialButton(this).apply {
        text = value
        isAllCaps = false
        cornerRadius = dp(16)
        minHeight = dp(54)
        setTextSize(14f)
        setOnClickListener { action() }
    }

    private fun outlinedButton(value: String, action: () -> Unit) = MaterialButton(this).apply {
        text = value
        isAllCaps = false
        cornerRadius = dp(16)
        minHeight = dp(50)
        strokeWidth = dp(1)
        strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.tas_primary))
        backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tas_surface))
        setTextColor(getColor(R.color.tas_text))
        setOnClickListener { action() }
    }

    private fun textButton(value: String, action: () -> Unit) = MaterialButton(this).apply {
        text = value
        isAllCaps = false
        cornerRadius = dp(14)
        minHeight = dp(46)
        backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tas_surface_variant))
        setTextColor(getColor(R.color.tas_text))
        setOnClickListener { action() }
    }

    companion object {
        private const val REQUEST_DEVICE_CREDENTIAL = 4102
    }
}
