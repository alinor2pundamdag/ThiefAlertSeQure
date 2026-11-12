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
    private lateinit var shield: ProtectionShieldView
    private lateinit var details: TextView
    private lateinit var setupSummary: TextView
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
        applyAlarmWindowPolicy()

        val privacyDecision = getSharedPreferences("privacy_notice_v2", MODE_PRIVATE)
            .getString("decision", null)
        // Require acknowledgement of this updated notice on every existing
        // install once; it now discloses the optional alarm flashlight and its safety toggle.
        if (privacyDecision != "acknowledged") {
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
            "The app does not require an account, analytics, advertising, or a cloud backend. Internet access is used only when you manually check for an official GitHub release and download its APK. The app does not upload protection state, event history, credentials, or other personal data. GitHub receives ordinary network request metadata, such as your request IP address, when you check for updates. Protection state, biometric-failure cooldown counters, a bounded local event history, monitor preferences, and references to audio files you select are stored locally on this device. Selected audio remains with its Android document provider; the app retains only the access grant and URI. Your PIN, pattern, password, and biometric templates are never collected or stored by this app; Android performs verification.",
            14f, getColor(R.color.tas_muted), false
        ), matchWrap())
        body.addView(space(16))
        body.addView(label("System access and limitations", 18f, getColor(R.color.tas_text), true), matchWrap())
        body.addView(space(8))
        body.addView(label(
            "The app may use boot and charger broadcasts, alarm scheduling, notifications, alarm audio, the camera flashlight's Android torch control while the alarm is active, and foreground services. The flash feature is enabled by default when compatible hardware is available, does not capture photos or video, and does not request camera permission. Flashing light may affect photosensitive users; you can disable the flash alert in Settings. Exact alarm access and notification permission are controlled by Android. Optional Device Owner mode requires separate device enrollment and can restrict device settings. No ordinary app can guarantee protection against force-stop, uninstall, physical shutdown, recovery controls, battery removal, or every manufacturer-specific power policy. Alarm delivery may be delayed when Android restricts background execution or exact alarms.",
            14f, getColor(R.color.tas_muted), false
        ), matchWrap())
        body.addView(space(16))
        body.addView(label("Acknowledgement", 18f, getColor(R.color.tas_text), true), matchWrap())
        body.addView(space(6))
        body.addView(label(
            "Review this notice before continuing. Tapping the button below records that you have acknowledged the app's local data handling, alarm behavior, optional flashlight alert, and Android platform limitations. You can review this notice again later in Settings.",
            13f, getColor(R.color.tas_muted), false
        ), matchWrap())
        notice.addView(body)
        root.addView(notice, matchWrap())
        root.addView(space(22))
        root.addView(button("I understand & continue") {
            getSharedPreferences("privacy_notice_v2", MODE_PRIVATE).edit()
                .putString("decision", "acknowledged").putLong("decision_at", System.currentTimeMillis()).apply()
            buildDashboard()
        }, matchWrap())
        root.addView(space(16))
        root.addView(label("LOCAL-FIRST • NO ACCOUNT • NO CLOUD SYNC", 10f, getColor(R.color.tas_muted), true), matchWrap())
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, scroll)
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
        statusContent.addView(space(4))
        val shieldRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        shield = ProtectionShieldView(this)
        shieldRow.addView(shield, LinearLayout.LayoutParams(dp(68), dp(68)))
        statusContent.addView(shieldRow, matchWrap())
        statusContent.addView(space(8))
        status = label("Checking device…", 24f, getColor(R.color.tas_text), true)
        statusContent.addView(status, matchWrap())
        statusContent.addView(space(12))
        details = label("Reading local protection state.", 14f, getColor(R.color.tas_muted), false)
        details.setLineSpacing(dp(4).toFloat(), 1f)
        statusContent.addView(details, matchWrap())
        setupSummary = label("Checking Android setup…", 12f, getColor(R.color.tas_muted), false)
        statusContent.addView(setupSummary, matchWrap())
        statusCard.addView(statusContent)
        root.addView(statusCard, matchWrap())
        root.addView(space(20))

        root.addView(sectionTitle("DEVICE INTELLIGENCE"), matchWrap())
        root.addView(space(10))
        val intelligenceCard = card()
        val intelligenceContent = column()
        intelligenceContent.setPadding(dp(16), dp(16), dp(16), dp(16))
        intelligenceContent.addView(label(
            "Automatically inspect this phone's Bluetooth controller, Android platform, sensors, hardware, resources, and protection-related readiness. Each capability has its own organized page.",
            13f, getColor(R.color.tas_muted), false
        ), matchWrap())
        intelligenceContent.addView(space(12))
        intelligenceContent.addView(outlinedButton("Open Bluetooth capability center") {
            startActivity(Intent(this, BluetoothCenterActivity::class.java))
        }, matchWrap())
        intelligenceContent.addView(space(8))
        intelligenceContent.addView(outlinedButton("Open Device Intelligence") {
            startActivity(Intent(this, DeviceIntelligenceActivity::class.java))
        }, matchWrap())
        intelligenceCard.addView(intelligenceContent)
        root.addView(intelligenceCard, matchWrap())
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
        root.addView(systemSettingRow("Enable precise 10-second alarm timing", "Exact alarms",
            "Android 12 (API 31)+ requires special Alarms & reminders access for exact scheduling. Allow ThiefAlertSeQure if listed. Android 8–11 has no separate exact-alarm access page; timing uses the platform's available alarm behavior.") { requestExactAlarmAccess() }, matchWrap())
        if (Build.VERSION.SDK_INT >= 34) {
            root.addView(space(8))
            val fullScreenRow = systemSettingRow("Allow full-screen alarm and authentication", "Full-screen intent",
                "Android 14 (API 34)+ controls full-screen intent access separately. This access is used only when the optional full-screen dashboard is enabled in Settings. Android may deny access based on alarm/calling eligibility; the ongoing alarm notification and sound remain the fallback.") { requestFullScreenIntentAccess() }
            fullScreenAccessButton = fullScreenRow.getChildAt(0) as? MaterialButton
            root.addView(fullScreenRow, matchWrap())
        }
        root.addView(space(8))
        root.addView(systemSettingRow("Open alarm notification settings", "Alarm notifications",
            "Open the Anti-theft alarm notification channel and enable it. Keep channel importance high and allow lock-screen display. Android 13+ also needs app-level notification permission.") { openAlarmNotificationSettings() }, matchWrap())
        root.addView(space(8))
        root.addView(systemSettingRow("Set device PIN, pattern or password", "Secure screen lock",
            "Set a secure PIN, pattern, or password in Android Security settings. ThiefAlertSeQure relies on Android Keyguard for credential verification and never reads or stores the credential.") { openDeviceSecuritySettings() }, matchWrap())
        root.addView(space(8))
        root.addView(systemSettingRow("Configure Do Not Disturb alarm exceptions", "Do Not Disturb",
            "Review DND schedules and allow alarms to interrupt if you need audible theft alerts during DND. The OS and OEM still control sound routing and interruption policy.") { openDndSettings() }, matchWrap())
        root.addView(space(8))
        root.addView(systemSettingRow("Set battery usage to Unrestricted", "Battery optimization",
            "Choose Unrestricted / Don't optimize for ThiefAlertSeQure where available. This reduces Doze restrictions but cannot prevent Force stop, uninstall, shutdown, or all OEM background limits.") { openBatteryOptimizationSettings() }, matchWrap())
        root.addView(space(8))
        root.addView(systemSettingRow("Review app battery and background settings", "App battery and background",
            AndroidDeviceCompatibility.backgroundSettingsGuide(Build.MANUFACTURER, Build.BRAND)) { openAppSettings() }, matchWrap())
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
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, scroll)
        setupSummary.text = SetupStatusSnapshot.read(this).dashboardSummary()
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
        applyAlarmWindowPolicy()
        reconcileCharging()
        ManagedProtectionController.startIfConfigured(this)
        if (!::status.isInitialized) return
        ThemeRuntime.apply(this, window.decorView)
        if (::setupSummary.isInitialized) setupSummary.text = SetupStatusSnapshot.read(this).dashboardSummary()
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

    private fun applyAlarmWindowPolicy() {
        val showOverLockScreen = getSharedPreferences(ALARM_DISPLAY_PREFS, MODE_PRIVATE)
            .getBoolean(KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN, false)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(showOverLockScreen)
            setTurnScreenOn(showOverLockScreen)
        } else {
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (showOverLockScreen) window.addFlags(flags) else window.clearFlags(flags)
        }
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
        AndroidDeviceCompatibility.open(this, request, Intent(Settings.ACTION_SECURITY_SETTINGS))
    }

    private fun requestFullScreenIntentAccess() {
        if (Build.VERSION.SDK_INT < 34) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.canUseFullScreenIntent()) {
            details.text = "Full-screen alarm access is already enabled."
            return
        }
        AndroidDeviceCompatibility.open(
            this,
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun launchAutomaticOwnerAuthenticationIfNeeded() {
        if (autoAuthenticationRequested || !::status.isInitialized) return
        if (ProtectionStore(this).read().mode != ProtectionMode.ALARM_TRIGGERED) {
            autoAuthenticationRequested = false
            return
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            // Android's own lock screen is the single authentication surface while
            // the phone is locked. Do not stack an in-app BiometricPrompt over it.
            autoAuthenticationRequested = false
            return
        }
        autoAuthenticationRequested = true
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
                .showThemed(this)
            return
        }

        val configured = ManagedProtectionController.isPolicyConfigured(this) ||
            ManagedProtectionController.hasPendingRollback(this)
        if (configured) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Disable managed lockdown?")
                .setMessage("This restores the saved pre-enable policy when a valid snapshot exists. If no valid snapshot exists, emergency recovery clears this app's managed restrictions, removes its Lock Task allowlist, and re-enables Home, Overview, notifications, system information, global actions and Keyguard; the old policy baseline cannot be reconstructed. Safe Boot and USB debugging remain available.")
                .setNegativeButton("Keep protection", null)
                .setPositiveButton("Disable lockdown") { _, _ ->
                    runCatching { ManagedProtectionController.disable(this) }
                        .onSuccess { render() }
                        .onFailure { showError("Could not disable managed policies: ${it.message}") }
                }
                .showThemed(this)
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("Enable managed lockdown?")
                .setMessage("This is an explicit whole-device policy. It allowlists this app for lock task mode, hides Android global actions while kiosk mode is active, and restricts Settings factory reset, adding users and USB file transfer. Safe Boot and USB debugging remain available for recovery. Keep this app's in-app exit control available. It does not block forced hardware shutdown, bootloader recovery, or OEM-level reset.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Enable managed protection") { _, _ ->
                    runCatching { ManagedProtectionController.enable(this) }
                        .onSuccess { render() }
                        .onFailure { showError("Managed protection could not be enabled: ${it.message}") }
                }
                .showThemed(this)
        }
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Protection setup")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .showThemed(this)
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
                AndroidDeviceCompatibility.open(
                    this,
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            } else {
                details.text = "Precise alarm scheduling access is already enabled."
            }
        } else {
            details.text = "Exact alarm access is not a separate permission on this Android version."
        }
    }

    private fun openAlarmNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 26) {
            AndroidDeviceCompatibility.open(
                this,
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, AlarmService.CHANNEL_ID)
                },
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } else openAppSettings()
    }

    private fun openDeviceSecuritySettings() {
        AndroidDeviceCompatibility.open(this, Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    }

    private fun openDndSettings() {
        AndroidDeviceCompatibility.open(
            this,
            Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
            Intent(Settings.ACTION_SOUND_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun openBatteryOptimizationSettings() {
        val power = getSystemService(PowerManager::class.java)
        if (Build.VERSION.SDK_INT >= 23 && power.isIgnoringBatteryOptimizations(packageName)) {
            details.text = "Battery optimization is already unrestricted for ThiefAlertSeQure."
            return
        }
        AndroidDeviceCompatibility.open(
            this,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
    }

    private fun androidVersionGuide(): String {
        val route = when (Build.VERSION.SDK_INT) {
            26, 27 -> "Android 8.x: App info is usually Settings → Apps & notifications → App info → ThiefAlertSeQure. Battery optimization is under Settings → Battery."
            28 -> "Android 9: App info → Battery; optimization allowlist is usually Apps & notifications → Special app access → Battery optimization."
            29 -> "Android 10: Settings → Apps → ThiefAlertSeQure → Battery; Special app access contains Battery optimization."
            30 -> "Android 11: App info → Battery; review Unrestricted and Background restriction if present."
            31, 32 -> "Android 12/12L: Settings → Apps → ThiefAlertSeQure → App battery usage. Exact alarms are under Apps → Special app access → Alarms & reminders."
            33 -> "Android 13: App info → Notifications and Battery. Notification permission is runtime-controlled."
            34 -> "Android 14: Apps → Special app access → Full-screen notifications; review app Notifications and Battery."
            35 -> "Android 15: Apps → Special app access → Full-screen notifications / Alarms & reminders; review App info → Battery and Notifications."
            36 -> "Android 16: Use Apps → Special app access and App info → Battery / Notifications; large-screen layouts must adapt to resizing and orientation changes." 
            37 -> "Android 17: Keep alarm audio inside the active foreground service; test background-audio hardening and large-screen resizing."
            else -> "Use Settings search for ThiefAlertSeQure, Battery, Notifications, Alarms & reminders, and Full-screen notifications."
        }
        val device = AndroidDeviceCompatibility.deviceSummary(
            Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT
        )
        val oemGuide = AndroidDeviceCompatibility.backgroundSettingsGuide(Build.MANUFACTURER, Build.BRAND)
        return "$device\n$route\n\n$oemGuide\n\nOEM menu labels may differ; use Settings search if needed."
    }

    private fun systemSettingRow(
        title: String,
        tutorialTitle: String,
        tutorial: String,
        click: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val launch = outlinedButton(title, click).apply {
            val statusId = when (tutorialTitle) { "Exact alarms" -> "exact"; "Full-screen intent" -> "fullscreen"; "Alarm notifications" -> "notifications"; "Secure screen lock" -> "lock"; "Battery optimization" -> "battery"; else -> null }
            statusId?.let { id ->
                val p = ThemeStore.get(this@MainActivity)
                val state = SetupStatusSnapshot.read(this@MainActivity).items.firstOrNull { it.id == id }?.state
                val accent = when (state) { SetupState.VERIFIED -> p.success; SetupState.ACTION_REQUIRED -> p.warning; SetupState.NEUTRAL, SetupState.UNSUPPORTED -> p.muted; SetupState.UNKNOWN -> p.danger; null -> p.primary }
                strokeColor = android.content.res.ColorStateList.valueOf(accent)
                setTextColor(accent)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        }
        val info = MaterialButton(this).apply {
            text = "i"
            isAllCaps = false
            minWidth = dp(44)
            minHeight = dp(48)
            cornerRadius = dp(14)
            contentDescription = "Tutorial: $tutorialTitle"
            setOnClickListener {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(tutorialTitle + " • Android " + Build.VERSION.SDK_INT)
                    .setMessage(androidVersionGuide() + "\n\n" + tutorial)
                    .setPositiveButton("Got it", null)
                    .showThemed(this@MainActivity)
            }
        }
        row.addView(launch)
        row.addView(info, LinearLayout.LayoutParams(dp(48), dp(52)))
        return row
    }

    private fun openAppSettings() {
        AndroidDeviceCompatibility.open(
            this,
            AndroidDeviceCompatibility.appInfoIntent(packageName),
            Intent(Settings.ACTION_SETTINGS)
        )
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
        val palette = ThemeStore.get(this)
        status.setTextColor(when (state.mode) {
            ProtectionMode.DISARMED -> palette.text
            ProtectionMode.ARMED -> palette.success
            ProtectionMode.COUNTDOWN -> palette.warning
            ProtectionMode.ALARM_TRIGGERED -> palette.danger
        })

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
        val fullScreenDashboardEnabled = getSharedPreferences(
            ALARM_DISPLAY_PREFS, MODE_PRIVATE
        ).getBoolean(KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN, false)
        val fullScreenAccessGranted = Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent()
        val fullScreenMessage = when {
            !fullScreenDashboardEnabled ->
                "Optional full-screen alarm dashboard: OFF; the standard ongoing alarm notification remains active."
            !fullScreenAccessGranted ->
                "Optional full-screen dashboard: ON, but Android full-screen access is not granted; the standard alarm notification remains the fallback."
            else ->
                "Optional full-screen alarm dashboard: ON and Android full-screen access is available."
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
        val motionAlarmEnabled = monitorPrefs.getBoolean(
            ProtectionMonitorService.KEY_MOTION_ENABLED, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED
        )
        val motionMessage = if (motionAlarmEnabled) {
            "Independent motion alarm: ON; it can trigger while the charger is connected."
        } else {
            "Independent motion alarm: OFF; charger-removal-only behavior is active."
        }
        val monitorMessage = if (monitorPrefs.getBoolean(ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)) {
            "Charger protection monitor: ENABLED; keep its persistent notification visible. $motionMessage"
        } else {
            "Charger protection monitor: OFF by owner choice; automatic plug/unplug detection is unavailable until re-enabled."
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        val secureCredentialReady = keyguard.isDeviceSecure
        val exactAlarmReady = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val monitorEnabled = monitorPrefs.getBoolean(
            ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val monitorRunning = ProtectionMonitorService.isRunning
        shield.update(state.mode, monitorEnabled && monitorRunning)
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
        if (!alarmChannelReady) readinessIssues.add("Set Anti-theft alarm notification importance to High")
        if (!monitorEnabled) readinessIssues.add("Enable charger monitoring")
        if (monitorEnabled && state.mode != ProtectionMode.ALARM_TRIGGERED && !monitorRunning) {
            readinessIssues.add("Wait for the charger-monitor foreground service to start")
        }
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
            ManagedProtectionController.hasPendingRollback(this) -> "Recover managed lockdown"
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
                            if (getSystemService(KeyguardManager::class.java).isDeviceSecure) {
                                details.text = "Android biometric lockout. Switching to device credentials."
                                authenticateDeviceCredential()
                            } else {
                                autoAuthenticationRequested = false
                                details.text = "Android biometric lockout. Set a secure device PIN, pattern or password to recover owner access."
                            }
                        }
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            if (getSystemService(KeyguardManager::class.java).isDeviceSecure) authenticateDeviceCredential()
                            else {
                                autoAuthenticationRequested = false
                                details.text = "Authentication cancelled. The alarm remains active."
                            }
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
        val next = OwnerAuthenticationCoordinator.complete(this, "in_app_authentication")
        if (next != null) {
            autoAuthenticationRequested = false
            details.text = if (next.mode == ProtectionMode.ARMED) {
                "Owner authenticated. Alarm stopped. Protection re-armed because the charger is connected."
            } else {
                "Owner authenticated. Alarm stopped. Protection remains disarmed until the charger is reconnected."
            }
        } else {
            syncProtectionMonitor(ProtectionStore(this).read())
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
        const val ALARM_DISPLAY_PREFS = "alarm_display_settings_v1"
        const val KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN = "show_alarm_screen_over_lockscreen"
        private const val REQUEST_DEVICE_CREDENTIAL = 4102
    }
}
