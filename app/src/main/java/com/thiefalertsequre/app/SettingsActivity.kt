package com.thiefalertsequre.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.media.MediaPlayer
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.concurrent.Executors

class SettingsActivity : FragmentActivity() {
    private lateinit var root: LinearLayout
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var soundSlot = AlarmSoundStore.ALARM
    private var previewPlayer: MediaPlayer? = null
    private val previewToneHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var previewToneController: AlarmToneController? = null
    private var previewToneStopRunnable: Runnable? = null
    private var flashPreviewController: AlarmFlashController? = null
    private var flashPreviewThermalController: ThermalFlashController? = null
    private var flashPreviewStopRunnable: Runnable? = null
    private var pendingUpdateInstall: AppUpdateResult.Ready? = null
    private var pendingCameraEvidenceExportId: String? = null
    private var requestedSection: String? = null
    private val sectionAnchors = mutableMapOf<String, View>()
    private var protectionStateReceiverRegistered = false
    private val protectionStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent?) {
            val mode = ProtectionStore(this@SettingsActivity).read().mode
            if (mode != ProtectionMode.DISARMED) {
                val flashPreviewWasActive = flashPreviewController != null
                // Release preview-owned audio/torch first. If the alarm latched
                // during a flash preview, refresh its outputs after the torch is free.
                stopPreview()
                stopFlashPreview()
                if (mode == ProtectionMode.ALARM_TRIGGERED && flashPreviewWasActive) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            this@SettingsActivity,
                            Intent(this@SettingsActivity, AlarmService::class.java)
                                .setAction(AlarmService.ACTION_REFRESH_FLASH)
                        )
                    }.onFailure {
                        LocalTamperHistory.record(
                            this@SettingsActivity, "alarm_flash_refresh_after_preview_failed",
                            it.javaClass.simpleName + ": " + it.message
                        )
                    }
                }
            }
        }
    }
    private val pickSoundFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) validateAndSaveSound(uri, soundSlot, uri)
    }
    private val pickSoundFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) searchAudioFolder(treeUri, soundSlot)
    }
    private val exportEvents = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) exportHistory(uri)
    }
    private val exportEvidenceBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) promptEvidenceBackupPassword("Encrypt evidence backup") { password -> writeEvidenceBackup(uri, password) } }
    private val importEvidenceBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) promptEvidenceBackupPassword("Decrypt evidence backup") { password -> restoreEvidenceBackup(uri, password) } }
    private val exportConfigurationBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) promptEvidenceBackupPassword("Encrypt app configuration backup") { password -> writeConfigurationBackup(uri, password) } }
    private val importConfigurationBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) promptEvidenceBackupPassword("Decrypt app configuration backup") { password -> restoreConfigurationBackup(uri, password) } }
    private val requestEvidenceLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) saveLastKnownEvidenceLocation()
        else android.widget.Toast.makeText(this, "Location permission was not granted; no location was saved.", android.widget.Toast.LENGTH_LONG).show()
    }
    private val requestCurrentEvidenceLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) requestFreshEvidenceLocation()
        else android.widget.Toast.makeText(this, "Location permission was not granted; no current location was requested.", android.widget.Toast.LENGTH_LONG).show()
    }
    private val requestLegacyGalleryStoragePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val readGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (readGranted && writeGranted) {
            val saved = CameraEvidenceCapture.setEncryptionEnabled(this, false)
            LocalTamperHistory.record(this, "camera_evidence_encryption_setting_changed", "Owner selected unencrypted Gallery storage for future photos; setting_saved=$saved")
            Toast.makeText(this, if (saved) "Future photos will appear in Gallery." else "Could not save the Gallery storage setting.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Encryption remains ON because Gallery read/write permission was not granted.", Toast.LENGTH_LONG).show()
        }
        render()
    }
    private val requestGalleryMigrationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val readGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        Toast.makeText(
            this,
            if (readGranted && writeGranted) "Gallery access granted. Tap Encrypt existing Gallery photos again to start the verified migration."
            else "Gallery migration was not started because read/write permission was not granted.",
            Toast.LENGTH_LONG
        ).show()
    }
    private val requestCameraEvidencePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val galleryPermissionRequired = !CameraEvidenceCapture.isEncryptionEnabled(this) && Build.VERSION.SDK_INT < 29
        val galleryGranted = !galleryPermissionRequired || (
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        )
        if (cameraGranted && galleryGranted) {
            if (CameraEvidenceCapture.setEnabled(this, true)) {
                LocalTamperHistory.record(this, "camera_evidence_opted_in", "Owner enabled front-camera photo evidence after successful alarm authentication")
                android.widget.Toast.makeText(this, "Front-camera evidence enabled. New photos will be saved to ${if (CameraEvidenceCapture.isEncryptionEnabled(this)) "the encrypted private vault" else "Gallery"} after successful owner authentication.", android.widget.Toast.LENGTH_LONG).show()
            } else {
                CameraEvidenceCapture.setEnabled(this, false)
                android.widget.Toast.makeText(this, "Android permissions were granted, but the app could not save the evidence setting. The feature remains OFF.", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            CameraEvidenceCapture.setEnabled(this, false)
            LocalTamperHistory.record(this, "camera_evidence_permission_denied", "Front-camera evidence remains disabled because a required camera or Gallery storage permission was not granted")
            android.widget.Toast.makeText(this, if (!cameraGranted) "Camera permission was not granted; front-camera evidence remains OFF." else "Gallery storage permission was not granted. Enable photo encryption in Settings to avoid this legacy Android permission.", android.widget.Toast.LENGTH_LONG).show()
        }
        render()
    }
    private val exportCameraEvidence = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        val id = pendingCameraEvidenceExportId
        pendingCameraEvidenceExportId = null
        if (uri != null && id != null) writeCameraEvidenceExport(uri, id)
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { renderStatus() }
    override fun onStart() {
        super.onStart()
        if (!protectionStateReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                protectionStateReceiver,
                android.content.IntentFilter(ProtectionStore.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            protectionStateReceiverRegistered = true
        }
    }

    override fun onStop() {
        if (protectionStateReceiverRegistered) {
            runCatching { unregisterReceiver(protectionStateReceiver) }
            protectionStateReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedSection = intent.getStringExtra(EXTRA_SECTION)
        render()
    }

    private fun render() {
        sectionAnchors.clear()
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
        root.addView(text("Settings", 32f, R.color.tas_text, true).markAsPageHeading())
        root.addView(space(8))
        root.addView(text("Manage Android permissions and reliability settings. Android remains in control of system-level access.", 14f, R.color.tas_muted))
        root.addView(space(10))
        root.addView(action("Appearance & 16 themes") { startActivity(Intent(this, ThemeActivity::class.java)) })
        root.addView(space(7))
        root.addView(action("Verified Android setup status") { startActivity(Intent(this, SetupStatusActivity::class.java)) })
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

        root.addView(text("DEVICE CAPABILITY CENTERS", 11f, R.color.tas_primary, true))
        root.addView(space(8))
        root.addView(text(
            "Explore your phone's Bluetooth controller and Android hardware capabilities in dedicated, organized pages. Each page refreshes local device information on demand.",
            13f, R.color.tas_muted
        ))
        root.addView(space(10))
        root.addView(action("Bluetooth capability center") {
            startActivity(Intent(this, BluetoothCenterActivity::class.java))
        })
        root.addView(space(8))
        root.addView(action("Device intelligence") {
            startActivity(Intent(this, DeviceIntelligenceActivity::class.java))
        })
        root.addView(space(22))

        val updatesHeading = text("OFFICIAL APP UPDATES", 11f, R.color.tas_primary, true)
        sectionAnchors[SECTION_UPDATES] = updatesHeading
        root.addView(updatesHeading)
        root.addView(space(8))
        root.addView(text(
            "Check GitHub only when you choose. ThiefAlertSeQure downloads the published release APK, verifies its SHA-256 checksum, application ID, version and pinned signing certificate, then opens Android's installer. Android confirmation is required; a matching official update installs over the existing app and keeps its private app data.",
            13f, R.color.tas_muted
        ))
        root.addView(space(8))
        root.addView(action("Check for official updates") { checkForOfficialUpdate() })
        root.addView(space(22))

        root.addView(text("MANUFACTURER BACKGROUND PROTECTION", 11f, R.color.tas_primary, true))
        root.addView(space(8))
        root.addView(text(
            AndroidDeviceCompatibility.backgroundSettingsGuide(Build.MANUFACTURER, Build.BRAND),
            13f, R.color.tas_muted
        ))
        root.addView(space(10))
        renderAppLaunchManagementSection()
        addSystemSettingAction(
            "Open app battery/background settings",
            "Background and auto-start controls",
            AndroidDeviceCompatibility.backgroundSettingsGuide(Build.MANUFACTURER, Build.BRAND)
        ) { openAppBatterySettings() }
        addSystemSettingAction("Battery optimization — allow unrestricted", "Battery optimization", "Choose Unrestricted / Don't optimize for ThiefAlertSeQure if offered. Android 8–10 generally expose the battery-optimization list; Android 11+ may also show per-app battery controls. OEM battery managers can add separate auto-start/background controls. This reduces Doze restrictions but cannot prevent Force stop or guarantee the OEM keeps every process alive.") { requestIgnoreBatteryOptimization() }
        addSystemSettingAction("Lock app in Recent apps — instructions", "Recent-app lock", "Open Recents, find ThiefAlertSeQure, then use the app-card menu or manufacturer-specific swipe gesture to choose Lock / Keep open if available. This is an OEM convenience feature, not a security guarantee.") { showRecentAppsTutorial() }
        root.addView(space(22))

        root.addView(text("ALARM PRESENTATION", 11f, R.color.tas_primary, true))
        root.addView(space(8))
        root.addView(text(
            "Choose whether ThiefAlertSeQure may show its own alarm dashboard over the Android lock screen and wake the display. OFF keeps the native Android lock screen in front; the alarm service, sound, countdown, and device lock continue independently.",
            13f, R.color.tas_muted
        ))
        val alarmDisplayPrefs = getSharedPreferences(MainActivity.ALARM_DISPLAY_PREFS, MODE_PRIVATE)
        val showAlarmScreenOverLockScreen = SafePreferences.boolean(alarmDisplayPrefs, MainActivity.KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN, false)
        val fullScreenAccessGranted = Build.VERSION.SDK_INT < 34 ||
            getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
        val dashboardStateLabel = when {
            !showAlarmScreenOverLockScreen -> "OFF"
            fullScreenAccessGranted -> "ON"
            else -> "ON — Android access not granted"
        }
        root.addView(action("Full-screen app alarm dashboard: $dashboardStateLabel") {
            if (!requireProtectionDisarmed()) return@action
            val enabled = !SafePreferences.boolean(alarmDisplayPrefs, MainActivity.KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN, false)
            val saved = ProtectionStore(this).runIfDisarmed {
                alarmDisplayPrefs.edit()
                    .putBoolean(MainActivity.KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN, enabled)
                    .commit()
            }
            if (saved != true) {
                showProtectionSettingsSaveFailure()
                return@action
            }
            if (enabled && Build.VERSION.SDK_INT >= 34 &&
                !getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
            ) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Android access required")
                    .setMessage("The optional dashboard is enabled in the app, but Android has not granted full-screen intent access. The normal alarm notification and sound remain available.")
                    .setNegativeButton("Later", null)
                    .setPositiveButton("Open access settings") { _, _ -> openFullScreenIntentSettings() }
                    .showThemed(this)
            }
            render()
        })
        root.addView(space(10))
        val soundsFlashHeading = text("ALARM FLASH ALERT", 11f, R.color.tas_primary, true)
        sectionAnchors[SECTION_SOUNDS_FLASH] = soundsFlashHeading
        root.addView(soundsFlashHeading)
        root.addView(space(6))
        root.addView(text(
            "Enabled by default, the rear camera LED pulses slowly only while the theft alarm is latched. It uses Android's torch API without camera permission and never captures photos or video. Flash hardware and availability vary by device; the sound alarm remains independent. Disable this option if flashing light is unsuitable for you.",
            13f, R.color.tas_muted
        ))
        val flashEnabled = AlarmFlashSettings.isEnabled(this)
        root.addView(action("Camera flash alert: ${if (flashEnabled) "ON" else "OFF"}") {
            if (!requireProtectionDisarmed()) return@action
            val enabled = !AlarmFlashSettings.isEnabled(this)
            if (!AlarmFlashSettings.setEnabled(this, enabled)) {
                showProtectionSettingsSaveFailure()
                return@action
            }
            render()
        })
        root.addView(space(6))
        val flashPattern = AlarmFlashPatternPolicy.read(this)
        root.addView(action("Flash pattern: ${flashPattern.label} (tap to cycle)") {
            if (!requireProtectionDisarmed()) return@action
            val next = AlarmFlashPatternPolicy.next(this)
            if (next != flashPattern) {
                LocalTamperHistory.record(this, "alarm_flash_pattern_changed", "Selected ${next.label}; applies on the next alarm start")
            }
            render()
        })
        root.addView(space(6))
        val flashSpeed = AlarmFlashPatternPolicy.speedPercent(this)
        root.addView(action("Flash speed: $flashSpeed% (tap to cycle)") {
            if (!requireProtectionDisarmed()) return@action
            val speeds = listOf(50, 75, 100, 125, 150, 200)
            val current = AlarmFlashPatternPolicy.speedPercent(this)
            val next = speeds[(speeds.indexOf(current).coerceAtLeast(0) + 1) % speeds.size]
            if (AlarmFlashPatternPolicy.saveSpeedPercent(this, next)) {
                LocalTamperHistory.record(this, "alarm_flash_speed_changed", "Flash cadence set to $next percent")
            }
            render()
        })
        root.addView(space(6))
        val customTiming = AlarmFlashPatternPolicy.customTimings(this)
        root.addView(action("Custom flash timing: ON ${customTiming.first} ms / OFF ${customTiming.second} ms") {
            if (!requireProtectionDisarmed()) return@action
            showCustomFlashTimingDialog()
        })
        root.addView(space(6))
        root.addView(action("Preview selected flash pattern (5 seconds)") { previewFlashPattern() })
        root.addView(space(6))
        root.addView(action("Check flashlight hardware availability") { checkFlashHardware() })
        root.addView(space(22))

        val moreHeading = text("SYSTEM ACCESS & PERMISSIONS", 11f, R.color.tas_primary, true)
        sectionAnchors[SECTION_MORE] = moreHeading
        root.addView(moreHeading)
        root.addView(space(10))
        addSystemSettingAction("Grant notifications permission", "Notifications", "Android 13 (API 33)+ asks for notification permission at runtime. If denied, open App info → Notifications and enable it. Android 8–12 uses per-app notification controls without this runtime prompt. Verify the Anti-theft alarm channel is enabled and allowed to appear on the lock screen.") { requestNotifications() }
        addSystemSettingAction("Alarms & reminders / exact alarm access", "Exact alarms", "Android 12 (API 31)+ exposes special Alarms & reminders access. Allow ThiefAlertSeQure if listed. Android 8–11 does not expose this special access. Without it, countdown delivery may use fallback scheduling and can be delayed.") { openExactAlarmSettings() }
        addSystemSettingAction("All app permissions and app information", "App permissions", "Open App info → Permissions and review only permissions requested by this build. The local charger-alarm core does not need contacts, microphone, or SMS. Optional CAMERA permission is requested only after you explicitly enable front-camera photo evidence; one photo is taken only after successful owner authentication that disarms a latched alarm while the app is visible. New photos appear in Gallery unencrypted by default; Settings can encrypt future captures and offers an explicit verified migration for existing app-owned Gallery photos. On Android 8–9 only, Gallery mode also requires legacy read/write storage permission. Android shows its camera privacy indicator. The optional flashlight uses Android's torch API without opening the camera and does not capture images. Optional coarse/fine location permission is requested only after you explicitly choose a one-shot location snapshot; no background tracking is used. The optional trusted Bluetooth separation alert requests Bluetooth Connect access only when you choose a paired device; it does not scan for nearby devices. Internet access is used only when you manually check for official GitHub updates; the app does not upload protection data. Do not grant unrelated permissions.") { openAppInfo() }
        addSystemSettingAction("App notification channels and settings", "App notifications", "This opens ThiefAlertSeQure's own notification settings. Enable app notifications and the Anti-theft alarm channel. Android 8+ uses channels; Android 13+ also requires app-level notification permission.") { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
        addSystemSettingAction("Battery and background restrictions", "Background activity", "Review ThiefAlertSeQure battery usage and select Unrestricted / Allow background activity where available. Android 8+ applies background execution limits; Android 9+ adds stricter standby behavior; Android 12+ exposes additional battery controls; Android 13–15 may apply OEM restrictions. Keep the persistent protection notification enabled. No setting overrides Force stop.") { openBatterySettings() }
        addSystemSettingAction("Do Not Disturb settings", "Do Not Disturb", "Review DND mode, schedules, and permitted alarm interruptions. The alarm uses Android alarm audio usage, but DND policy, silent mode, and OEM behavior can still affect audible output. Allow alarms during DND if you require alarm audio to remain audible.") { openDndSettings() }
        addSystemSettingAction("Android full-screen intent access (system permission)", "Full-screen intent permission",
            "This permission is used only for the optional full-screen alarm dashboard when you enable that dashboard above. On Android 14+, the system may deny access based on alarm/calling eligibility. If access is unavailable, the ongoing high-priority alarm notification and sound still operate; countdown and native device-lock behavior do not depend on full-screen access.") { openFullScreenIntentSettings() }
        addSystemSettingAction("Secure lock screen settings", "Secure device lock",
            "Set a PIN, pattern, or password in Android Security settings. ThiefAlertSeQure can request the keyguard through the optional active Device Administrator, but it cannot create or read your credential. Keep a secure screen lock configured for owner authentication.") { open(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
        addSystemSettingAction("Enable automatic alarm screen lock", "Device Administrator",
            "This opens Android's explicit Device Administrator consent screen. Activate ThiefAlertSeQure only if you want it to call lockNow() when the theft alarm starts. Android may show a warning because the app can lock the screen. You can deactivate it later in Android Security settings. This is not Device Owner enrollment and does not provide kiosk-level control.") { requestDeviceAdministrator() }
        addSystemSettingAction("Alarm volume and sound settings", "Alarm volume",
            "Set the device alarm stream to an audible level and review silent/DND policies. During an active theft alarm, the app saves the previous alarm volume and repeatedly requests the maximum alarm-stream level. Android/OEM policy, fixed-volume hardware, Bluetooth routing, and external audio devices can still limit output.") { open(Intent(Settings.ACTION_SOUND_SETTINGS)) }
        root.addView(space(22))

        root.addView(text("BUILT-IN DEFAULT + OPTIONAL CUSTOM ALARM SOUNDS", 11f, R.color.tas_primary, true))
        root.addView(space(8))
        root.addView(text(
            "The packaged ThiefAlertSeQure alarm is the default and loops while an alarm is active. You can optionally choose a custom main alarm to override it. File access is limited to the file or folder you select.",
            13f, R.color.tas_muted
        ))
        root.addView(space(8))
        root.addView(text("Built-in alarm tone: " + AlarmSoundStore.selectedBuiltInPreset(this).label, 13f, R.color.tas_text))
        root.addView(action("Change built-in alarm tone (5 presets + packaged default)") {
            if (!requireProtectionDisarmed()) return@action
            val selected = AlarmSoundStore.selectNextBuiltInPreset(this)
            android.widget.Toast.makeText(this, "Built-in alarm tone: " + selected.label + ". Applies to the next alarm.", android.widget.Toast.LENGTH_LONG).show()
            render()
        })
        root.addView(text("A custom main alarm file takes priority over this built-in tone selection. Clear the custom file to use the selected built-in tone.", 12f, R.color.tas_muted))
        root.addView(text("Main alarm: " + AlarmSoundStore.displayName(this, AlarmSoundStore.ALARM), 13f, R.color.tas_text))
        root.addView(action("Use built-in default alarm (clear custom override)") {
            if (!requireProtectionDisarmed()) return@action
            val cleared = AlarmSoundStore.clear(this, AlarmSoundStore.ALARM)
            android.widget.Toast.makeText(
                this,
                if (cleared) "Built-in default alarm selected." else "Could not save the sound reset. The previous selection was retained.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            render()
        })
        root.addView(action("Choose custom main alarm audio file") {
            if (!requireProtectionDisarmed()) return@action
            soundSlot = AlarmSoundStore.ALARM
            pickSoundFile.launch(arrayOf("audio/*"))
        })
        root.addView(action("Search a folder and its subfolders for alarm audio") {
            if (!requireProtectionDisarmed()) return@action
            soundSlot = AlarmSoundStore.ALARM
            pickSoundFolder.launch(null)
        })
        root.addView(action("Preview selected main alarm") { previewSound(AlarmSoundStore.ALARM) })
        root.addView(space(6))
        root.addView(text("Secondary alert: " + AlarmSoundStore.displayName(this, AlarmSoundStore.ALERT), 13f, R.color.tas_text))
        root.addView(action("Choose secondary alert audio file") {
            if (!requireProtectionDisarmed()) return@action
            soundSlot = AlarmSoundStore.ALERT
            pickSoundFile.launch(arrayOf("audio/*"))
        })
        root.addView(action("Search a folder and its subfolders for alert audio") {
            if (!requireProtectionDisarmed()) return@action
            soundSlot = AlarmSoundStore.ALERT
            pickSoundFolder.launch(null)
        })
        root.addView(action("Preview selected secondary alert") { previewSound(AlarmSoundStore.ALERT) })
        root.addView(action("Stop audio preview") { stopPreview() })
        root.addView(space(6))
        val secondaryEnabled = AlarmSoundStore.isSecondaryAlarmEnabled(this)
        root.addView(action("Persistent secondary theft alarm: ${if (secondaryEnabled) "ON" else "OFF"}") {
            if (!requireProtectionDisarmed()) return@action
            val enable = !AlarmSoundStore.isSecondaryAlarmEnabled(this)
            if (enable && AlarmSoundStore.uri(this, AlarmSoundStore.ALERT) == null) {
                android.widget.Toast.makeText(this, "Choose a secondary alert audio file before enabling the persistent secondary alarm.", android.widget.Toast.LENGTH_LONG).show()
            } else if (AlarmSoundStore.setSecondaryAlarmEnabled(this, enable)) {
                if (ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            this, Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_REFRESH_OUTPUTS)
                        )
                    }
                }
                render()
            } else {
                android.widget.Toast.makeText(this, "Could not save the secondary alarm setting.", android.widget.Toast.LENGTH_LONG).show()
            }
        })
        root.addView(space(6))
        val hapticsEnabled = AlarmHapticsPolicy.isEnabled(this)
        root.addView(action("Independent repeating vibration: ${if (hapticsEnabled) "ON" else "OFF"}") {
            if (!requireProtectionDisarmed()) return@action
            val enabled = !AlarmHapticsPolicy.isEnabled(this)
            if (AlarmHapticsPolicy.setEnabled(this, enabled)) {
                if (ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            this, Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_REFRESH_OUTPUTS)
                        )
                    }
                }
                render()
            }
        })
        root.addView(space(6))
        val hapticPattern = AlarmHapticsPolicy.readPattern(this)
        root.addView(action("Vibration pattern: ${hapticPattern.label} (tap to cycle)") {
            if (!requireProtectionDisarmed()) return@action
            val next = AlarmHapticsPolicy.nextPattern(this)
            if (next != hapticPattern && ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED) {
                runCatching {
                    ContextCompat.startForegroundService(
                        this, Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_REFRESH_OUTPUTS)
                    )
                }
            }
            render()
        })
        root.addView(space(6))
        root.addView(text(
            "The main alarm selection drives the primary theft alarm. When enabled and configured, the secondary selection loops independently until the alarm is stopped. It is also used for the optional battery-threshold alert. Vibration has its own independent enable and pattern controls.",
            12f, R.color.tas_muted
        ))
        root.addView(space(18))
        root.addView(text("BACKGROUND PROTECTION MONITOR", 11f, R.color.tas_primary, true))
        root.addView(space(8))
        root.addView(text(
            "The charger-protection monitor is enabled by default and must remain running to detect plug/unplug events while the app is closed. Its persistent notification stays visible; the independent motion alarm is OFF by default and can trigger while charging if explicitly enabled. Battery-threshold alerts are optional. You can disable monitoring, but automatic charger detection will stop. Android and device manufacturers may still stop it.",
            13f, R.color.tas_muted
        ))
        val monitorPrefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
        val monitorEnabled = SafePreferences.boolean(monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
        root.addView(action(if (monitorEnabled) "Disable background monitoring" else "Enable background monitoring") {
            if (!requireProtectionDisarmed()) return@action
            val enabled = !SafePreferences.boolean(monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
            val monitorSave = ProtectionStore(this).runIfDisarmed {
                monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, enabled).commit()
            }
            if (monitorSave != true) {
                android.widget.Toast.makeText(this, "Android did not confirm the monitoring setting. No service or protection state was changed.", android.widget.Toast.LENGTH_LONG).show()
                render()
                return@action
            }
            val state = ProtectionStore(this).read()
            if (enabled && state.mode != ProtectionMode.ALARM_TRIGGERED) {
                PowerTransitionCoordinator.resetBaselineToCurrentPower(
                    this, "Owner re-enabled background monitoring"
                )
                runCatching {
                    ContextCompat.startForegroundService(this, Intent(this, ProtectionMonitorService::class.java))
                }.onFailure {
                    android.widget.Toast.makeText(this, "Monitor could not start: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } else if (!enabled) {
                // The service owns durable disarm, deadline cleanup and latched-alarm preservation.
                // Do not stop it directly: it retries if the protection-state commit fails.
                runCatching {
                    startService(
                        Intent(this, ProtectionMonitorService::class.java)
                            .setAction(ProtectionMonitorService.ACTION_STOP)
                            // The OFF preference was committed above; this lets the service
                            // reject this request if a newer owner action re-enables monitoring.
                            .putExtra(ProtectionMonitorService.EXTRA_STOP_PREFERENCE_COMMITTED, true)
                    )
                }.onFailure {
                    android.widget.Toast.makeText(this, "Monitor stop could not be requested: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } else {
                android.widget.Toast.makeText(this, "Monitoring will resume after owner authentication.", android.widget.Toast.LENGTH_LONG).show()
            }
            render()
        })
        val motionEnabled = SafePreferences.boolean(monitorPrefs, ProtectionMonitorService.KEY_MOTION_ENABLED, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED)
        root.addView(action("Independent motion alarm: ${if (motionEnabled) "ON" else "OFF"}") {
            if (!requireProtectionDisarmed()) return@action
            val enabled = !SafePreferences.boolean(monitorPrefs, ProtectionMonitorService.KEY_MOTION_ENABLED, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED)
            val saved = ProtectionStore(this).runIfDisarmed {
                monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_MOTION_ENABLED, enabled).commit()
            }
            if (saved != true) { showProtectionSettingsSaveFailure(); return@action }
            sendBroadcast(Intent(ProtectionMonitorService.ACTION_REFRESH).setPackage(packageName))
            render()
        })
        root.addView(text(
            "Motion is a separate alarm source and can trigger while the charger is still connected. Leave it OFF for charger-removal-only protection.",
            12f, R.color.tas_muted
        ))
        root.addView(space(18))
        val sensitivity = MotionSensitivity.fromStored(
            SafePreferences.string(monitorPrefs, ProtectionMonitorService.KEY_SENSITIVITY, MotionSensitivity.MEDIUM.name)
        )
        root.addView(action("Motion sensitivity (when enabled): ${sensitivity.name.lowercase()}") {
            if (!requireProtectionDisarmed()) return@action
            val choices = MotionSensitivity.values().map { it.name.lowercase() }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle("Motion sensitivity")
                .setSingleChoiceItems(choices, MotionSensitivity.values().indexOf(sensitivity)) { dialog, which ->
                    val saved = ProtectionStore(this).runIfDisarmed {
                        monitorPrefs.edit().putString(ProtectionMonitorService.KEY_SENSITIVITY, MotionSensitivity.values()[which].name).commit()
                    }
                    if (saved != true) { showProtectionSettingsSaveFailure(); return@setSingleChoiceItems }
                    dialog.dismiss()
                    render()
                }
                .setNegativeButton("Cancel", null)
                .showThemed(this)
        })
        val batteryAlertEnabled = SafePreferences.boolean(monitorPrefs, ProtectionMonitorService.KEY_BATTERY_ALERT_ENABLED, false)
        root.addView(action("Battery threshold alert: ${if (batteryAlertEnabled) "ON" else "OFF"}") {
            if (!requireProtectionDisarmed()) return@action
            val enabled = !batteryAlertEnabled
            val batteryAlertEdit = monitorPrefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_BATTERY_ALERT_ENABLED, enabled)
            if (enabled) batteryAlertEdit.putBoolean(ProtectionMonitorService.KEY_ENABLED, true)
            val batteryAlertSave = ProtectionStore(this).runIfDisarmed { batteryAlertEdit.commit() }
            if (batteryAlertSave != true) {
                android.widget.Toast.makeText(this, "Android did not confirm the battery-alert setting. No service state was changed.", android.widget.Toast.LENGTH_LONG).show()
                render()
                return@action
            }
            if (enabled) {
                if (ProtectionStore(this).read().mode != ProtectionMode.ALARM_TRIGGERED) {
                    PowerTransitionCoordinator.resetBaselineToCurrentPower(
                        this, "Battery threshold alert enabled background monitoring"
                    )
                    runCatching {
                        ContextCompat.startForegroundService(this, Intent(this, ProtectionMonitorService::class.java))
                    }.onFailure {
                        android.widget.Toast.makeText(this, "Battery monitor could not start: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
            render()
        })
        val threshold = SafePreferences.int(monitorPrefs, ProtectionMonitorService.KEY_BATTERY_THRESHOLD, 80)
        root.addView(action("Battery alert threshold: ${threshold}%") {
            if (!requireProtectionDisarmed()) return@action
            val choices = arrayOf("80%", "85%", "90%", "95%", "100%")
            val values = intArrayOf(80, 85, 90, 95, 100)
            MaterialAlertDialogBuilder(this)
                .setTitle("Battery alert threshold")
                .setSingleChoiceItems(choices, values.indexOf(threshold).coerceAtLeast(0)) { dialog, which ->
                    val saved = ProtectionStore(this).runIfDisarmed {
                        monitorPrefs.edit().putInt(ProtectionMonitorService.KEY_BATTERY_THRESHOLD, values[which]).commit()
                    }
                    if (saved != true) { showProtectionSettingsSaveFailure(); return@setSingleChoiceItems }
                    dialog.dismiss()
                    render()
                }
                .setNegativeButton("Cancel", null)
                .showThemed(this)
        })
        root.addView(space(22))

        val tamperHeading = text("LOCAL TAMPER HISTORY", 11f, R.color.tas_primary, true)
        sectionAnchors[SECTION_TAMPER_HISTORY] = tamperHeading
        root.addView(tamperHeading)
        root.addView(space(8))
        val historyText = if (ProtectionStore(this).read().mode == ProtectionMode.DISARMED) {
            LocalTamperHistory.read(this, 20).ifEmpty { listOf("No recorded protection events yet.") }.joinToString("\n")
        } else {
            "Event details are hidden while protection is active. Authenticate and disarm first to review or export local tamper evidence."
        }
        root.addView(text(historyText, 12f, R.color.tas_text))
        root.addView(space(6))
        root.addView(text(
            "This readable event list is a convenience log. Integrity-checked encrypted event metadata is stored separately in the evidence vault below.",
            11f, R.color.tas_muted
        ))
        root.addView(space(8))
        root.addView(action("Export local event history (CSV)") {
            if (!requireProtectionDisarmed()) return@action
            exportEvents.launch("thiefalertsequre-events.csv")
        })
        root.addView(action("Clear local event history") {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear local history?")
                .setMessage("This permanently clears the readable local event log and encrypted event-metadata vault. It does not change protection state and is available only while protection is DISARMED.")
                .setNegativeButton("Keep history", null)
                .setPositiveButton("Clear") { _, _ ->
                    val cleared = LocalTamperHistory.clear(this)
                    if (cleared) {
                        LocalTamperHistory.record(this, "history_cleared", "Owner cleared local event history")
                        android.widget.Toast.makeText(this, "Local event history cleared.", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(this, "History clearing was blocked or incomplete; some entries may already have been removed. Confirm protection is DISARMED and review storage before retrying.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    render()
                }.showThemed(this)
        })
        root.addView(space(10))
        root.addView(text("CAMERA EVIDENCE STORAGE", 11f, R.color.tas_primary, true))
        root.addView(space(6))
        val cameraEvidenceEnabled = CameraEvidenceCapture.isEnabled(this)
        val cameraPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val frontCameraAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
        // Vault index reads, retention pruning and Keystore-backed integrity
        // checks run on the IO executor below, never during UI rendering.
        var cameraEvidenceCount = -1
        var galleryEvidenceCount = -1
        var cameraEvidenceIntegrityVerified = false
        val encryptNewPhotos = CameraEvidenceCapture.isEncryptionEnabled(this)
        val galleryStoragePermissionRequired = !encryptNewPhotos && Build.VERSION.SDK_INT < 29
        val galleryStoragePermissionGranted = !galleryStoragePermissionRequired || (
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        )
        val cameraEvidenceStatus = text(
            "Front-camera photo evidence: ${when { !frontCameraAvailable -> "UNSUPPORTED"; cameraEvidenceEnabled && !cameraPermissionGranted -> "NEEDS CAMERA PERMISSION"; cameraEvidenceEnabled && !galleryStoragePermissionGranted -> "NEEDS GALLERY PERMISSION"; cameraEvidenceEnabled -> "ON"; else -> "OFF" }} • new photos: ${if (encryptNewPhotos) "ENCRYPTED PRIVATE VAULT" else "GALLERY (UNENCRYPTED)"} • encrypted count CHECKING / Gallery count CHECKING • vault integrity CHECKING",
            12f, R.color.tas_muted
        )
        root.addView(cameraEvidenceStatus)
        val evidenceRenderRoot = root
        ioExecutor.execute {
            val vaultEntries = runCatching {
                CameraEvidenceVault.entries(applicationContext)
            }.getOrNull()
            val cameraEvidenceIntegrity = runCatching {
                CameraEvidenceVault.verifyIntegrity(applicationContext)
            }.getOrDefault(false)
            val galleryCount = runCatching {
                CameraEvidenceMediaStore.count(applicationContext)
            }.getOrDefault(-1)
            runOnUiThread {
                if (!isFinishing && !isDestroyed && root === evidenceRenderRoot) {
                    cameraEvidenceCount = vaultEntries?.size ?: -1
                    galleryEvidenceCount = galleryCount
                    cameraEvidenceIntegrityVerified = cameraEvidenceIntegrity
                    val encryptedCountLabel = if (cameraEvidenceCount >= 0) "$cameraEvidenceCount encrypted" else "encrypted count UNKNOWN"
                    val galleryCountLabel = if (galleryCount >= 0) {
                        if (Build.VERSION.SDK_INT < 29) "$galleryCount Gallery (legacy ownership verified by private index)"
                        else "$galleryCount Gallery"
                    } else "Gallery count UNKNOWN (permission/storage unavailable)"
                    cameraEvidenceStatus.text =
                        "Front-camera photo evidence: ${when { !frontCameraAvailable -> "UNSUPPORTED"; cameraEvidenceEnabled && !cameraPermissionGranted -> "NEEDS CAMERA PERMISSION"; cameraEvidenceEnabled && !galleryStoragePermissionGranted -> "NEEDS GALLERY PERMISSION"; cameraEvidenceEnabled -> "ON"; else -> "OFF" }} • new photos: ${if (encryptNewPhotos) "ENCRYPTED PRIVATE VAULT" else "GALLERY (UNENCRYPTED)"} • $encryptedCountLabel / $galleryCountLabel • vault integrity ${if (cameraEvidenceIntegrity) "VERIFIED" else "FAILED / UNKNOWN"}"
                }
            }
        }
        root.addView(action("Encrypt new camera photos: ${if (encryptNewPhotos) "ON — private encrypted vault" else "OFF — visible in Gallery"}") {
            if (!requireProtectionDisarmed()) return@action
            MaterialAlertDialogBuilder(this)
                .setTitle(if (encryptNewPhotos) "Save new photos to Gallery?" else "Encrypt new camera photos?")
                .setMessage(if (encryptNewPhotos)
                    "Turning encryption OFF saves future evidence photos as normal, unencrypted images in Pictures/ThiefAlertSeQure so they appear in Gallery. Existing encrypted-vault photos stay encrypted."
                    else "Turning encryption ON stores future evidence photos in the app private AES-GCM vault; they will not appear in Gallery unless exported. Existing Gallery photos remain visible until you explicitly use the verified migration action below.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(if (encryptNewPhotos) "Save to Gallery" else "Encrypt new photos") { _, _ ->
                    if (encryptNewPhotos && Build.VERSION.SDK_INT < 29 && (
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    )) {
                        requestLegacyGalleryStoragePermissions.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                        return@setPositiveButton
                    }
                    val saved = CameraEvidenceCapture.setEncryptionEnabled(this, !encryptNewPhotos)
                    if (saved) LocalTamperHistory.record(this, "camera_evidence_encryption_setting_changed", "Encrypt future captures: ${!encryptNewPhotos}")
                    Toast.makeText(this, if (saved) "Storage setting updated for future photos." else "Could not save the storage setting.", Toast.LENGTH_LONG).show()
                    render()
                }
                .showThemed(this)
        })
        root.addView(action("Encrypt existing Gallery photos into the private vault") {
            if (!requireProtectionDisarmed()) return@action
            if (Build.VERSION.SDK_INT < 29 && (
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            )) {
                requestGalleryMigrationPermissions.launch(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                )
                return@action
            }
            if (!CameraEvidenceCapture.isEncryptionEnabled(this)) {
                android.widget.Toast.makeText(
                    this,
                    "Turn ON encryption for new photos first, then run the existing-photo migration.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@action
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Encrypt existing Gallery photos?")
                .setMessage(
                    "The app will copy each app-owned Gallery photo into the AES-GCM private vault, read it back and verify the exact bytes, then remove the Gallery original only if it is still unchanged and protection remains DISARMED. If verification, deletion, or storage capacity prevents a safe move, the original stays in Gallery. The vault retains up to ${CameraEvidencePolicy.MAX_IMAGES} photos."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Encrypt and verify") { _, _ ->
                    android.widget.Toast.makeText(this, "Encrypting and verifying existing photos…", android.widget.Toast.LENGTH_SHORT).show()
                    ioExecutor.execute {
                        val result = runCatching {
                            CameraEvidenceMediaStore.migrateAllToEncryptedVault(applicationContext)
                        }.getOrNull()
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                val message = when {
                                    result == null -> "Migration stopped unexpectedly; some photos may already have been moved. Check both Gallery and the encrypted vault before deleting or retrying anything."
                                    result.blockedByProtection -> "Migration stopped because protection became active. Remaining Gallery originals were kept."
                                    result.blockedByEncryptionSetting -> "Migration was not started because encryption is OFF. Enable it first."
                                    !result.enumerationSucceeded -> "Gallery photos could not be enumerated. No Gallery originals were removed."
                                    result.copiedToVault == 0 && result.copyFailures == 0 && result.capacityLimited == 0 &&
                                        result.unverifiedLegacyPhotos > 0 ->
                                        "${result.unverifiedLegacyPhotos} older Gallery photo(s) could not be proven app-owned on this Android version, so they were left untouched. Only photos recorded in the app's encrypted ownership index can be migrated or deleted safely."
                                    result.copiedToVault == 0 && result.copyFailures == 0 && result.capacityLimited == 0 ->
                                        "No verified app-owned Gallery photos were available to migrate."
                                    else -> "Encrypted copies verified: ${result.copiedToVault}. Gallery originals removed: ${result.originalsRemoved}. Copy failures: ${result.copyFailures}. Originals kept because removal was not confirmed: ${result.removalFailures}. Capacity-limited originals kept in Gallery: ${result.capacityLimited}. Unverified legacy photos left untouched: ${result.unverifiedLegacyPhotos}. Vault integrity: ${if (result.vaultIntegrityVerified) "VERIFIED" else "FAILED / UNKNOWN"}."
                                }
                                MaterialAlertDialogBuilder(this@SettingsActivity)
                                    .setTitle("Photo migration report")
                                    .setMessage(message)
                                    .setPositiveButton("OK", null)
                                    .showThemed(this@SettingsActivity)
                                render()
                            }
                        }
                    }
                }
                .showThemed(this)
        })
        root.addView(action("Front-camera evidence after successful owner authentication: ${when { cameraEvidenceEnabled && cameraPermissionGranted && galleryStoragePermissionGranted -> "ON"; cameraEvidenceEnabled && cameraPermissionGranted -> "NEEDS GALLERY PERMISSION"; cameraEvidenceEnabled -> "NEEDS CAMERA PERMISSION"; else -> "OFF / NEEDS SETUP" }}") {
            if (!requireProtectionDisarmed()) return@action
            if (cameraEvidenceEnabled && (!cameraPermissionGranted || !galleryStoragePermissionGranted)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Front-camera evidence needs permission")
                    .setMessage("The feature is enabled, but it cannot save a photo until every required permission is granted. You can grant only the missing permissions or disable the feature.")
                    .setNegativeButton("Disable evidence") { _, _ ->
                        val saved = CameraEvidenceCapture.setEnabled(this, false)
                        if (saved) LocalTamperHistory.record(this, "camera_evidence_opt_out", "Owner disabled front-camera photo evidence")
                        Toast.makeText(this, if (saved) "Front-camera evidence disabled." else "Could not save the setting.", Toast.LENGTH_LONG).show()
                        render()
                    }
                    .setPositiveButton("Grant missing permissions") { _, _ ->
                        val permissions = buildList {
                            if (!cameraPermissionGranted) add(Manifest.permission.CAMERA)
                            if (galleryStoragePermissionRequired) {
                                if (ContextCompat.checkSelfPermission(this@SettingsActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                if (ContextCompat.checkSelfPermission(this@SettingsActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            }
                        }
                        if (permissions.isNotEmpty()) requestCameraEvidencePermissions.launch(permissions.toTypedArray())
                    }
                    .showThemed(this)
            } else if (cameraEvidenceEnabled) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Disable front-camera evidence?")
                    .setMessage("No new photo will be captured after successful alarm authentication. Existing encrypted-vault and Gallery photos remain until you delete them.")
                    .setNegativeButton("Keep enabled", null)
                    .setPositiveButton("Disable") { _, _ ->
                        if (CameraEvidenceCapture.setEnabled(this, false)) {
                            LocalTamperHistory.record(this, "camera_evidence_opt_out", "Owner disabled front-camera photo evidence")
                        } else {
                            android.widget.Toast.makeText(this, "Could not save the setting; front-camera evidence may still be enabled.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        render()
                    }.showThemed(this)
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Enable front-camera photo evidence?")
                    .setMessage("After a successful owner PIN/password or supported strong-biometric verification disarms a latched alarm, ThiefAlertSeQure will take one still photo using the FRONT camera while this app is visible. Android will show its camera privacy indicator. CAMERA permission is requested only after you choose Enable. New photos are saved as normal, unencrypted images in Pictures/ThiefAlertSeQure and appear in Gallery by default; Gallery images are visible to apps with photo access. Settings can enable AES-GCM encryption for future captures, storing them in the private app vault instead. Existing Gallery images remain visible until you run the explicit verified migration in Settings; originals are removed only after encrypted read-back and source recheck. Existing encrypted photos remain encrypted. App-managed photos are limited to 20 images per storage location and 30 days. No photo is taken on failed/cancelled authentication; no background camera service, microphone, or cloud upload is used. If camera access is unavailable, alarm disarming still succeeds.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Enable") { _, _ ->
                        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
                            android.widget.Toast.makeText(this, "This device does not report front-facing camera hardware; no permission was requested.", android.widget.Toast.LENGTH_LONG).show()
                        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && (CameraEvidenceCapture.isEncryptionEnabled(this) || Build.VERSION.SDK_INT >= 29 || (
                            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        ))) {
                            if (CameraEvidenceCapture.setEnabled(this, true)) {
                                LocalTamperHistory.record(this, "camera_evidence_opted_in", "Owner enabled front-camera photo evidence")
                            } else {
                                android.widget.Toast.makeText(this, "Could not save the setting; front-camera evidence remains OFF.", android.widget.Toast.LENGTH_LONG).show()
                            }
                            render()
                        } else {
                            val permissions = buildList {
                                add(Manifest.permission.CAMERA)
                                if (!CameraEvidenceCapture.isEncryptionEnabled(this@SettingsActivity) && Build.VERSION.SDK_INT < 29) {
                                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                            }
                            requestCameraEvidencePermissions.launch(permissions.toTypedArray())
                        }
                    }.showThemed(this)
            }
        })
        root.addView(action("Review / view / export encrypted-vault photos") {
            showCameraEvidenceArchive()
        })
        root.addView(action("Delete all front-camera photos") {
            if (cameraEvidenceCount == 0 && galleryEvidenceCount == 0 && cameraEvidenceIntegrityVerified) {
                android.widget.Toast.makeText(this, "No app-managed front-camera photos are stored.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete all front-camera photos?")
                    .setMessage("This permanently deletes encrypted-vault photos and Gallery images whose app ownership can be verified. On Android 8–9, older Gallery photos without a private ownership-index record are left untouched. Unrelated Gallery images are never deleted. This does not change the owner-authentication setting.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete all") { _, _ ->
                        ioExecutor.execute {
                            // Remove Gallery originals first, one DISARMED-guarded row at a time.
                            // Keep the encrypted vault intact if Gallery enumeration/deletion is incomplete.
                            val galleryDeleted = runCatching {
                                CameraEvidenceMediaStore.deleteAll(applicationContext)
                            }.getOrDefault(false)
                            val vaultDeleted = if (galleryDeleted) {
                                runCatching { CameraEvidenceVault.deleteAll(applicationContext) }.getOrDefault(false)
                            } else {
                                false
                            }
                            val deletion: Boolean? = when {
                                ProtectionStore(applicationContext).read().mode != ProtectionMode.DISARMED -> null
                                else -> galleryDeleted && vaultDeleted == true
                            }
                            LocalEvidenceVault.recordAsync(
                                applicationContext,
                                "camera_evidence_deleted",
                                "Owner requested deletion of all app-managed front-camera photo evidence; fullyConfirmed=${deletion == true}"
                            )
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    val message = when (deletion) {
                                        true -> "Front-camera photos deleted."
                                        false -> "Photo deletion could not be confirmed; review storage and retry."
                                        null -> "Photo deletion is blocked while protection is active. Disarm first."
                                    }
                                    android.widget.Toast.makeText(this@SettingsActivity, message, android.widget.Toast.LENGTH_LONG).show()
                                    render()
                                }
                            }
                        }
                    }.showThemed(this)
            }
        })
        root.addView(space(8))
        val integrityStatus = text(
            "Encrypted local event archive • checking integrity… • no cloud upload",
            12f, R.color.tas_muted
        )
        root.addView(integrityStatus)
        ioExecutor.execute {
            val evidenceEntries = LocalEvidenceVault.read(this@SettingsActivity, 100)
            val integrity = runCatching {
                LocalEvidenceVault.verifyIntegrity(this@SettingsActivity)
            }.getOrDefault(false)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    integrityStatus.text =
                        "${evidenceEntries.size} encrypted local event records • 30-day retention • AES-GCM integrity ${if (integrity) "VERIFIED" else "FAILED / UNKNOWN"} • no cloud upload"
                }
            }
        }
        root.addView(space(6))
        root.addView(action("Verify local evidence integrity now") {
            Toast.makeText(this, "Checking local evidence integrity…", Toast.LENGTH_SHORT).show()
            ioExecutor.execute {
                val eventIntegrity = runCatching {
                    LocalEvidenceVault.verifyIntegrity(this@SettingsActivity)
                }.getOrDefault(false)
                val photoIntegrity = runCatching {
                    CameraEvidenceVault.verifyIntegrity(this@SettingsActivity)
                }.getOrDefault(false)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        MaterialAlertDialogBuilder(this@SettingsActivity)
                            .setTitle("Local evidence integrity")
                            .setMessage(
                                "Encrypted event archive: ${if (eventIntegrity) "VERIFIED" else "FAILED / UNKNOWN"}\n" +
                                "Encrypted photo vault: ${if (photoIntegrity) "VERIFIED" else "FAILED / UNKNOWN"}\n\n" +
                                "A failed or unknown result means the affected archive should not be treated as verified. This check does not change protection or alarm state."
                            )
                            .setPositiveButton("Close", null)
                            .showThemed(this@SettingsActivity)
                    }
                }
            }
        })
        root.addView(space(6))
        root.addView(action("Review encrypted evidence records") {
            Toast.makeText(this, "Loading encrypted evidence records…", Toast.LENGTH_SHORT).show()
            ioExecutor.execute {
                val entries = LocalEvidenceVault.read(this@SettingsActivity, 100)
                val summary = entries.take(20).joinToString("\n\n") { entry ->
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date(entry.timestampEpochMs))
                    "$date (UTC offset ${entry.timezoneOffsetMinutes}m)\n${entry.event}\n${entry.detail}\nSHA-256: ${entry.integritySha256.take(16)}…"
                }.ifBlank { "No encrypted evidence records are available." }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        MaterialAlertDialogBuilder(this@SettingsActivity)
                            .setTitle("Encrypted evidence archive")
                            .setMessage(summary)
                            .setPositiveButton("Close", null)
                            .showThemed(this@SettingsActivity)
                    }
                }
            }
        })
        root.addView(action("Export encrypted evidence backup") {
            if (!requireProtectionDisarmed()) return@action
            exportEvidenceBackup.launch("ThiefAlertSeQure-evidence-backup.tasbackup")
        })
        root.addView(action("Restore encrypted evidence backup") {
            MaterialAlertDialogBuilder(this)
                .setTitle("Replace local evidence archive?")
                .setMessage("Restore replaces the current encrypted evidence vault with records from the selected backup. The file must be a valid ThiefAlertSeQure backup and you must know its password. Existing local evidence will be replaced only after successful decryption and integrity validation.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose backup") { _, _ -> importEvidenceBackup.launch(arrayOf("application/octet-stream", "*/*")) }
                .showThemed(this)
        })
        root.addView(space(14))
        root.addView(text("ENCRYPTED CONFIGURATION BACKUP", 11f, R.color.tas_primary, true))
        root.addView(space(6))
        root.addView(text(
            "Backs up allowlisted owner settings with password-derived AES-GCM encryption. It excludes authentication secrets, protection/alarm state, Device Owner policies, permission grants, Bluetooth identities, custom audio URI grants and the secondary-audio toggle, and the separate evidence vault.",
            12f, R.color.tas_muted
        ))
        root.addView(action("Export encrypted app configuration") {
            if (!requireProtectionDisarmed()) return@action
            exportConfigurationBackup.launch("ThiefAlertSeQure-configuration.tascfg")
        })
        root.addView(action("Restore encrypted app configuration") {
            if (!requireProtectionDisarmed()) return@action
            MaterialAlertDialogBuilder(this)
                .setTitle("Restore owner settings?")
                .setMessage("Restore is allowed only while protection is DISARMED; it is blocked during ARMED, COUNTDOWN, or ALARM_TRIGGERED states. It restores supported preferences, then reapplies the monitor setting and charger baseline. It does not restore authentication secrets, permissions, evidence, custom audio files, Bluetooth identities, or the active protection/alarm state. Existing settings are retained if validation or a write fails.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose backup") { _, _ -> importConfigurationBackup.launch(arrayOf("application/octet-stream", "*/*")) }
                .showThemed(this)
        })
        root.addView(action("Save last-known GPS location (optional, owner-requested)") {
            if (!requireProtectionDisarmed()) return@action
            MaterialAlertDialogBuilder(this)
                .setTitle("Save a location snapshot?")
                .setMessage("Only after you grant location permission, the app reads one cached Android location fix and saves its coordinates, provider, accuracy, and fix age into the encrypted local evidence vault. No background tracking, continuous updates, cloud upload, or automatic location capture occurs. A cached fix may be old or unavailable.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue") { _, _ ->
                    if (EvidenceLocationCapture.hasPermission(this)) {
                        saveLastKnownEvidenceLocation()
                    } else {
                        requestEvidenceLocation.launch(arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ))
                    }
                }.showThemed(this)
        })
        root.addView(action("Request current GPS/network location (one time, owner-requested)") {
            if (!requireProtectionDisarmed()) return@action
            MaterialAlertDialogBuilder(this)
                .setTitle("Request a current location?")
                .setMessage("After you approve, Android is asked for one current GPS or network location fix. The request times out after 20 seconds. The result, its age, accuracy and provider are saved only to the encrypted local evidence vault. This does not enable background tracking or automatic location capture.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Request once") { _, _ ->
                    if (EvidenceLocationCapture.hasPermission(this)) {
                        requestFreshEvidenceLocation()
                    } else {
                        requestCurrentEvidenceLocation.launch(arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ))
                    }
                }.showThemed(this)
        })
        root.addView(text(
            "The encrypted archive contains app-observed event metadata and only the location snapshots you explicitly request. New optional front-camera photos are saved unencrypted in the app-owned Gallery folder by default; Settings can encrypt future captures into the separate AES-GCM private vault. Existing Gallery photos can be migrated explicitly after encrypted read-back verification; the original remains if the copy or deletion cannot be confirmed. No microphone recordings, PINs, passwords, or biometric material are stored.",
            12f, R.color.tas_muted
        ))
        root.addView(space(22))

        val privacyHeading = text("PRIVACY & DATA", 11f, R.color.tas_primary, true)
        sectionAnchors[SECTION_PRIVACY] = privacyHeading
        root.addView(privacyHeading)
        root.addView(space(10))
        root.addView(text(
            "No account, analytics, advertising SDK, or app backend is used. Internet access is used only when you manually check for an official GitHub release and download its APK; protection state and personal data are not uploaded. The optional alarm flashlight uses Android's torch API without opening the camera. Separately, optional front-camera photo evidence requires an explicit Settings opt-in and CAMERA permission; it captures one photo only after successful owner authentication while this app is visible and shows Android's camera privacy indicator. New photos are unencrypted in Gallery by default; the Settings encryption option stores future captures in the private AES-GCM vault and provides a verified migration for existing app-owned Gallery photos. Gallery images are visible to apps with photo access. No photos are uploaded. Device credentials and biometric templates are handled by Android and are not read by the app.",
            13f, R.color.tas_muted
        ))
        root.addView(space(10))
        root.addView(action("Review privacy notice") {
            getSharedPreferences(MainActivity.PRIVACY_NOTICE_PREFS, MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        })
        root.addView(space(22))
        root.addView(action("Back to protection dashboard") { finish() })
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, scroll)
        requestedSection?.let { section ->
            sectionAnchors[section]?.let { target ->
                scroll.post { scroll.smoothScrollTo(0, target.top) }
            }
        }
    }


    private fun addBypassChargingSection() {
        val device = BypassChargingDevice(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.VERSION.SDK_INT)
        val controller = BypassChargingController(BypassChargingAdapterRegistry.forDevice(device))
        val snapshot = controller.inspect()
        root.addView(text("BYPASS CHARGING", 11f, R.color.tas_primary, true))
        root.addView(space(8))
        root.addView(text("Actual mode: ${snapshot.modeLabel} • ${snapshot.statusLabel}",13f,R.color.tas_text,true))
        root.addView(space(6))
        val control = Switch(this).apply {
            text = "Bypass Charging ON/OFF"
            isChecked = snapshot.mode == BypassChargingMode.ENABLED
            isEnabled = snapshot.canToggle
            contentDescription = "Bypass Charging ON/OFF; unavailable until OEM interface and actual state are verified"
            setOnCheckedChangeListener { _, enabled ->
                val result = controller.setEnabled(enabled)
                LocalTamperHistory.record(this@SettingsActivity,"bypass_charging_control",result.status.name + ": " + result.detail)
                val message = if(result.isVerified) "Bypass Charging ${if(enabled) "ON" else "OFF"} verified by OEM read-back."
                    else result.statusLabel + ". " + result.detail
                android.widget.Toast.makeText(this@SettingsActivity,message,android.widget.Toast.LENGTH_LONG).show()
                render()
            }
        }
        root.addView(control,matchWrap())
        root.addView(space(6))
        root.addView(text(
            if(snapshot.canToggle) "The ON/OFF switch is enabled only because an authorized OEM interface and readable state are verified."
            else "ON/OFF control is unavailable. The switch is intentionally disabled; UNKNOWN does not mean OFF. A normal app cannot grant itself OEM/system privileges.",
            12f,R.color.tas_muted
        ))
        root.addView(space(8))
        root.addView(text(BypassChargingGuidance.instructions(device),12f,R.color.tas_muted))
        root.addView(space(8))
        root.addView(action("Open Android battery and charging settings") {
            val opened=runCatching { startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)); true }.getOrElse {
                runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)); true }.getOrDefault(false)
            }
            if(!opened) android.widget.Toast.makeText(this,"Android Settings could not be opened.",android.widget.Toast.LENGTH_LONG).show()
        })
        root.addView(space(22))
    }

    private fun isSafeForAppUpdate(): Boolean {
        val state = ProtectionStore(this).read()
        return AppUpdatePolicy.canUpdateWhileProtectionDisarmed(state.mode)
    }

    private fun checkForOfficialUpdate() {
        if (!isSafeForAppUpdate()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Disable protection before updating")
                .setMessage("For safety, protection must be DISARMED with no countdown or active alarm. If background monitoring was enabled, Android stops the old process during installation and the app attempts to restart the monitor after package replacement.")
                .setPositiveButton("OK", null)
                .showThemed(this)
            return
        }
        android.widget.Toast.makeText(this, "Checking official GitHub release…", android.widget.Toast.LENGTH_SHORT).show()
        AppUpdateManager.checkAndDownload(this, ioExecutor) { result ->
            if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@checkAndDownload
            when (result) {
                is AppUpdateResult.Ready -> {
                    val notes = result.releaseNotes.ifBlank { "No release notes were provided." }
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Version \${result.versionName} is available")
                        .setMessage(
                            notes + "\n\nAndroid will ask you to confirm installation. " +
                                "Because the package ID and official signing key match, this is an in-place update; Android preserves app-private data. Any enabled background monitor is restored after package replacement."
                        )
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Install update") { _, _ -> beginUpdateInstall(result) }
                        .showThemed(this)
                }
                AppUpdateResult.UpToDate -> android.widget.Toast.makeText(
                    this, "ThiefAlertSeQure is already up to date.", android.widget.Toast.LENGTH_LONG
                ).show()
                is AppUpdateResult.Failed -> MaterialAlertDialogBuilder(this)
                    .setTitle("Update unavailable")
                    .setMessage(result.reason)
                    .setPositiveButton("OK", null)
                    .showThemed(this)
            }
        }
    }

    private fun beginUpdateInstall(update: AppUpdateResult.Ready) {
        val apkFile = update.apkFile
        if (!isSafeForAppUpdate()) {
            android.widget.Toast.makeText(this, "Update paused: disarm protection and clear any countdown/alarm first.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (!apkFile.isFile || apkFile.length() <= 0L || !AppUpdateManager.isVerifiedCachedApk(this, update)) {
            android.widget.Toast.makeText(this, "The verified official update changed or is no longer available. Check again.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            pendingUpdateInstall = update
            MaterialAlertDialogBuilder(this)
                .setTitle("Allow Android to install this update")
                .setMessage(
                    "Android requires your approval for installs initiated outside an app store. " +
                        "Allow ThiefAlertSeQure to install this verified official update. You can turn this access off again afterward."
                )
                .setNegativeButton("Cancel") { _, _ -> pendingUpdateInstall = null }
                .setPositiveButton("Open Android setting") { _, _ ->
                    AndroidDeviceCompatibility.open(
                        this,
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
                        AndroidDeviceCompatibility.appInfoIntent(packageName),
                        Intent(Settings.ACTION_SETTINGS)
                    )
                }
                .showThemed(this)
            return
        }
        launchAndroidInstaller(update)
    }

    private fun launchAndroidInstaller(update: AppUpdateResult.Ready) {
        if (!isSafeForAppUpdate() || !AppUpdateManager.isVerifiedCachedApk(this, update)) {
            android.widget.Toast.makeText(this, "Update verification failed. No APK was handed to Android.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val apkFile = update.apkFile
        runCatching {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.updates", apkFile)
            // This handoff is restricted to the app-private cache and revalidated immediately
            // above for SHA-256, production package ID, higher versionCode and the pinned signer.

            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                // codeql[java/android/arbitrary-apk-installation]
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            // OEM fallback uses the same app-private, checksum/package/version/signer-verified APK.

            val installerFallback = Intent(Intent.ACTION_VIEW).apply {
                // codeql[java/android/arbitrary-apk-installation]
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (!AndroidDeviceCompatibility.open(this, installIntent, installerFallback)) {
                android.widget.Toast.makeText(this, "No Android package installer is available.", android.widget.Toast.LENGTH_LONG).show()
            }
        }.onFailure {
            android.widget.Toast.makeText(
                this, "Android could not open the package installer: \${it.message}", android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        PermissionChangeMonitor.check(this)
        // Re-read Android permission, channel, battery and service state after
        // returning from system Settings instead of showing stale readiness.
        if (::root.isInitialized) render()
        val pending = pendingUpdateInstall ?: return
        pendingUpdateInstall = null
        if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) {
            launchAndroidInstaller(pending)
        } else {
            android.widget.Toast.makeText(
                this, "Install permission was not enabled. Tap Check for official updates to try again.", android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun validateAndSaveSound(uri: Uri, slot: String, permissionUri: Uri) {
        android.widget.Toast.makeText(this, "Validating selected audio…", android.widget.Toast.LENGTH_SHORT).show()
        ioExecutor.execute {
            val validation = runCatching {
                val player = MediaPlayer()
                try {
                    player.setDataSource(this, uri)
                    player.prepare()
                    check(player.duration > 0) { "Audio duration is zero or unsupported." }
                } finally {
                    player.release()
                }
            }
            val saved = validation.isSuccess && AlarmSoundStore.save(this, slot, uri, permissionUri)
            runOnUiThread {
                if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@runOnUiThread
                val message = when {
                    saved -> "Audio validated and saved: " + AlarmSoundStore.displayName(this, slot)
                    validation.isFailure -> "Audio could not be decoded: " + (validation.exceptionOrNull()?.message ?: "unsupported file")
                    else -> "Android did not grant persistent read access. Select the file again."
                }
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
                render()
            }
        }
    }

    private fun searchAudioFolder(treeUri: Uri, slot: String) {
        runCatching {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure {
            android.widget.Toast.makeText(this, "Folder permission could not be retained: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        android.widget.Toast.makeText(this, "Searching selected folder and accessible subfolders…", android.widget.Toast.LENGTH_LONG).show()
        ioExecutor.execute {
            val matches = mutableListOf<DocumentFile>()
            var visited = 0
            fun walk(directory: DocumentFile, depth: Int) {
                if (depth > 24 || visited >= 5000) return
                val children = runCatching { directory.listFiles() }.getOrDefault(emptyArray())
                for (child in children) {
                    if (visited++ >= 5000) break
                    if (child.isDirectory) walk(child, depth + 1)
                    else if (child.isFile && isAudioDocument(child)) matches.add(child)
                }
            }
            DocumentFile.fromTreeUri(this, treeUri)?.let { walk(it, 0) }
            runOnUiThread {
                if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@runOnUiThread
                if (matches.isEmpty()) {
                    android.widget.Toast.makeText(this, "No supported audio files found in the selected folder.", android.widget.Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val names = matches.mapIndexed { index, file -> file.name ?: "Audio file ${index + 1}" }.toTypedArray()
                MaterialAlertDialogBuilder(this)
                    .setTitle("Select audio (${matches.size} found${if (visited >= 5000) ", search capped" else ""})")
                    .setItems(names) { _, index ->
                        validateAndSaveSound(matches[index].uri, slot, treeUri)
                    }
                    .setNegativeButton("Cancel", null)
                    .showThemed(this)
            }
        }
    }

    private fun isAudioDocument(file: DocumentFile): Boolean {
        if (file.type.orEmpty().startsWith("audio/")) return true
        val name = file.name.orEmpty().lowercase()
        return listOf(".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".flac", ".3gp", ".amr")
            .any { name.endsWith(it) }
    }

    private fun promptEvidenceBackupPassword(title: String, onPassword: (CharArray) -> Unit) {
        val field = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "At least 12 characters"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage("Use a strong password with at least 12 characters. It is not saved by the app. You will need the same password to restore this backup.")
            .setView(field)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                val password = field.text?.toString().orEmpty()
                field.text?.clear()
                if (password.length < 12) {
                    android.widget.Toast.makeText(this, "Password must contain at least 12 characters.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    onPassword(password.toCharArray())
                }
            }
            .showThemed(this)
    }

    private fun writeEvidenceBackup(uri: Uri, password: CharArray) {
        ioExecutor.execute {
            val result = runCatching {
                val exportedBytes = ProtectionStore(applicationContext).runIfDisarmed {
                    val entries = LocalEvidenceVault.read(applicationContext, 100)
                    val bytes = EvidenceVaultBackup.encrypt(entries, password)
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(bytes)
                        stream.flush()
                    } ?: error("Android could not open the selected backup destination.")
                    bytes.size
                } ?: throw IllegalStateException("Evidence backup export is available only while protection is DISARMED.")
                exportedBytes
            }.also { password.fill('\u0000') }
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    result.fold({ "Encrypted evidence backup exported (${it} bytes)." }, { "Backup export failed: ${it.message}" }),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun restoreEvidenceBackup(uri: Uri, password: CharArray) {
        ioExecutor.execute {
            val result = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8_192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 1_000_000) { "Backup file is too large." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("Android could not open the selected backup file.")
                val entries = EvidenceVaultBackup.decrypt(bytes, password)
                val restored = ProtectionStore(this@SettingsActivity).runIfDisarmed {
                    LocalEvidenceVault.replaceAll(this@SettingsActivity, entries)
                } ?: throw IllegalStateException("Evidence restore is available only while protection is DISARMED.")
                require(restored) { "The validated evidence archive could not be stored." }
                entries.size
            }.also { password.fill('\u0000') }
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    result.fold({ "Evidence backup restored: ${it} records." }, { "Backup restore failed: ${it.message}" }),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                render()
            }
        }
    }

    private fun writeConfigurationBackup(uri: Uri, password: CharArray) {
        ioExecutor.execute {
            val result = runCatching {
                val bytes = ConfigurationBackup.encrypt(this, password)
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
                    stream.flush()
                } ?: error("Android could not open the selected configuration backup destination.")
                bytes.size
            }.also { password.fill('\u0000') }
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    result.fold({ "Encrypted configuration backup exported (${it} bytes)." }, { "Configuration backup export failed: ${it.message}" }),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun restoreConfigurationBackup(uri: Uri, password: CharArray) {
        ioExecutor.execute {
            val result = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4_096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 128_000) { "Configuration backup file is too large." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("Android could not open the selected configuration backup file.")
                val restored = ConfigurationBackup.decryptAndRestore(this, bytes, password)
                applyRestoredConfiguration()
                restored
            }.also { password.fill('\u0000') }
            runOnUiThread {
                result.onSuccess { count ->
                    if (::root.isInitialized) ThemeRuntime.apply(this, root)
                    android.widget.Toast.makeText(
                        this, "Restored $count supported owner settings. Protection/alarm state, authentication, permissions, and evidence were not imported; monitor synchronization ran only while DISARMED.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    render()
                }.onFailure { error ->
                    android.widget.Toast.makeText(
                        this, "Configuration restore failed: ${error.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    // A failed import may have rolled back successfully. Reconcile
                    // the existing monitor state; if recovery is still pending the
                    // helper below deliberately leaves monitoring paused.
                    applyRestoredConfiguration()
                }
            }
        }
    }

    private fun applyRestoredConfiguration() {
        if (ConfigurationBackup.isRestoreOrRecoveryInProgress() ||
            !ConfigurationBackup.recoverPendingRestore(this)
        ) {
            stopService(Intent(this, ProtectionMonitorService::class.java))
            ProtectionMonitorRecoveryNotice.show(
                this, "Monitoring remains paused until interrupted settings recovery completes."
            )
            return
        }
        val protectionMode = ProtectionStore(this).read().mode
        if (protectionMode != ProtectionMode.DISARMED) {
            // A rollback callback must never stop or re-baseline a monitor after
            // protection has advanced. Refresh the service appropriate to the
            // active state without changing the state or the imported preference.
            when (protectionMode) {
                ProtectionMode.ARMED, ProtectionMode.COUNTDOWN -> runCatching {
                    ContextCompat.startForegroundService(
                        this, Intent(this, ProtectionMonitorService::class.java)
                            .setAction(ProtectionMonitorService.ACTION_REFRESH)
                    )
                }.onFailure {
                    LocalTamperHistory.record(this, "configuration_restore_monitor_refresh_failed", it.javaClass.simpleName + ": " + it.message)
                }
                ProtectionMode.ALARM_TRIGGERED -> runCatching {
                    ContextCompat.startForegroundService(
                        this, Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_REFRESH_OUTPUTS)
                    )
                }.onFailure {
                    LocalTamperHistory.record(this, "configuration_restore_alarm_refresh_failed", it.javaClass.simpleName + ": " + it.message)
                }
                ProtectionMode.DISARMED -> Unit
            }
            LocalTamperHistory.record(
                this, "configuration_restore_monitor_sync_deferred",
                "Protection became active during restore recovery; active service was refreshed without resetting or stopping protection"
            )
            return
        }
        val monitorPrefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
        val monitorEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        if (monitorEnabled) {
            // resetBaselineToCurrentPower is itself serialized against alarm and
            // charger transitions. If state became active after the import commit,
            // it rejects the reset and the monitor is still refreshed safely.
            runCatching {
                PowerTransitionCoordinator.resetBaselineToCurrentPower(this, "Owner restored configuration backup")
            }.onFailure {
                LocalTamperHistory.record(this, "configuration_restore_baseline_failed", it.javaClass.simpleName + ": " + it.message)
            }
            runCatching {
                ContextCompat.startForegroundService(
                    this, Intent(this, ProtectionMonitorService::class.java).setAction(ProtectionMonitorService.ACTION_REFRESH)
                )
            }.onFailure {
                LocalTamperHistory.record(this, "configuration_restore_monitor_start_failed", it.javaClass.simpleName + ": " + it.message)
            }
        } else {
            // Never stop the monitor if a countdown/armed state began between
            // the preference commit and this post-restore synchronization.
            val stopResult = ProtectionStore(this).runIfDisarmed {
                runCatching {
                    ContextCompat.startForegroundService(
                        this,
                        Intent(this, ProtectionMonitorService::class.java)
                            .setAction(ProtectionMonitorService.ACTION_STOP)
                            .putExtra(ProtectionMonitorService.EXTRA_STOP_PREFERENCE_COMMITTED, true)
                    )
                    true
                }.onFailure {
                    LocalTamperHistory.record(this, "configuration_restore_monitor_stop_failed", it.javaClass.simpleName + ": " + it.message)
                }.getOrDefault(false)
            }
            if (stopResult == null) {
                LocalTamperHistory.record(
                    this, "configuration_restore_monitor_stop_deferred",
                    "Protection became active after restore; monitor stop was deferred to preserve active protection"
                )
            }
        }
        if (ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED) {
            runCatching {
                ContextCompat.startForegroundService(
                    this, Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_REFRESH_OUTPUTS)
                )
            }
        }
    }

    private fun showCameraEvidenceArchive() {
        if (!requireProtectionDisarmed()) return
        // Keystore reads, encrypted index parsing and retention pruning are disk
        // work. Keep them off the Settings main thread, including the empty state.
        ioExecutor.execute {
            val entries = runCatching {
                CameraEvidenceVault.entries(applicationContext)
            }.getOrDefault(emptyList())
            val integrityHealthy = if (entries.isEmpty()) {
                runCatching { CameraEvidenceVault.verifyIntegrity(applicationContext) }.getOrDefault(false)
            } else true

            runOnUiThread archiveUi@{
                if (isFinishing || isDestroyed) return@archiveUi
                if (!requireProtectionDisarmed()) return@archiveUi
                if (entries.isEmpty()) {
                    MaterialAlertDialogBuilder(this@SettingsActivity)
                        .setTitle("Front-camera evidence")
                        .setMessage(if (integrityHealthy) {
                            "No encrypted-vault photos are stored. New captures go to Gallery by default unless photo encryption is enabled in Settings; existing app-owned Gallery photos can be migrated explicitly after verification."
                        } else {
                            "The encrypted photo vault failed its integrity check. No photo is being displayed or exported. Review local tamper history and preserve the app data for diagnosis."
                        })
                        .setPositiveButton("Close", null)
                        .showThemed(this@SettingsActivity)
                    return@archiveUi
                }

                val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                val labels = entries.map { entry ->
                    "${formatter.format(java.util.Date(entry.timestampEpochMs))} • ${entry.sizeBytes} bytes • SHA-256 ${entry.sha256.take(16)}…"
                }.toTypedArray()
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle("Encrypted front-camera photos")
                    .setItems(labels) { _, index ->
                        if (!requireProtectionDisarmed()) return@setItems
                        val entry = entries[index]
                        ioExecutor.execute {
                            val bytes = if (ProtectionStore(applicationContext).read().mode == ProtectionMode.DISARMED) {
                                runCatching { CameraEvidenceVault.read(applicationContext, entry.id) }.getOrNull()
                            } else null
                            val bitmap = bytes?.let { payload ->
                                runCatching { decodeCameraEvidencePreview(payload) }.getOrNull()
                            }
                            runOnUiThread photoUi@{
                                if (isFinishing || isDestroyed) {
                                    bitmap?.recycle()
                                    return@photoUi
                                }
                                if (ProtectionStore(this@SettingsActivity).read().mode != ProtectionMode.DISARMED) {
                                    bitmap?.recycle()
                                    requireProtectionDisarmed()
                                    return@photoUi
                                }
                                if (bitmap == null) {
                                    android.widget.Toast.makeText(this@SettingsActivity, "Photo failed its integrity check or is unavailable.", android.widget.Toast.LENGTH_LONG).show()
                                    return@photoUi
                                }
                                val image = ImageView(this@SettingsActivity).apply {
                                    contentDescription = "Encrypted front-camera evidence photo captured after successful owner authentication"
                                    setImageBitmap(bitmap)
                                    adjustViewBounds = true
                                    maxHeight = dp(440)
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    setPadding(dp(8), dp(8), dp(8), dp(8))
                                }
                                MaterialAlertDialogBuilder(this@SettingsActivity)
                                    .setTitle("Verified encrypted photo")
                                    .setMessage("Captured after successful owner authentication. SHA-256: ${entry.sha256}")
                                    .setView(image)
                                    .setNeutralButton("Export") { _, _ ->
                                        pendingCameraEvidenceExportId = entry.id
                                        exportCameraEvidence.launch("ThiefAlertSeQure-front-camera-${entry.timestampEpochMs}.jpg")
                                    }
                                    .setNegativeButton("Delete") { _, _ ->
                                        ioExecutor.execute {
                                            val deleted = runCatching {
                                                CameraEvidenceVault.delete(applicationContext, entry.id)
                                            }.getOrDefault(false)
                                            val deletion: Boolean? = if (
                                                ProtectionStore(applicationContext).read().mode == ProtectionMode.DISARMED
                                            ) deleted else null
                                            LocalEvidenceVault.recordAsync(
                                                applicationContext,
                                                "camera_evidence_deleted",
                                                "Owner deleted photo id=${entry.id}; success=$deleted"
                                            )
                                            runOnUiThread {
                                                if (!isFinishing && !isDestroyed) {
                                                    android.widget.Toast.makeText(
                                                        this@SettingsActivity,
                                                        when (deletion) {
                                                            true -> "Photo deleted."
                                                            false -> "Photo deletion could not be confirmed; retry after checking storage."
                                                            null -> "Photo deletion is blocked while protection is active. Disarm first."
                                                        },
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                    render()
                                                }
                                            }
                                        }
                                    }
                                    .setPositiveButton("Close", null)
                                    .showThemed(this@SettingsActivity)
                            }
                        }
                    }
                    .setNegativeButton("Close", null)
                    .showThemed(this@SettingsActivity)
            }
        }
    }

    private fun decodeCameraEvidencePreview(bytes: ByteArray): android.graphics.Bitmap? {
        if (!CameraEvidencePolicy.isValidImageSize(bytes.size)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > 200_000_000L) return null
        var sampleSize = 1
        while (width / sampleSize > 2_048 || height / sampleSize > 2_048) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun writeCameraEvidenceExport(uri: Uri, id: String) {
        ioExecutor.execute {
            val result = runCatching {
                if (ProtectionStore(applicationContext).read().mode != ProtectionMode.DISARMED) {
                    throw IllegalStateException("Photo export is available only while protection is DISARMED.")
                }
                val bytes = CameraEvidenceVault.read(applicationContext, id)
                    ?: throw IllegalStateException("Photo is missing or failed its integrity check.")
                ProtectionStore(applicationContext).runIfDisarmed {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(bytes)
                        output.flush()
                    } ?: throw IllegalStateException("Android could not open the selected export destination.")
                    bytes.size
                } ?: throw IllegalStateException("Protection became active before the photo export could be written.")
            }
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    result.fold({ "Photo exported as JPEG (${it} bytes)." }, { "Photo export failed: ${it.message}" }),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun requestFreshEvidenceLocation() {
        if (!requireProtectionDisarmed()) return
        android.widget.Toast.makeText(this, "Requesting one current location fix…", android.widget.Toast.LENGTH_SHORT).show()
        EvidenceLocationCapture.requestCurrent(this) { snapshot ->
            if (snapshot == null) {
                android.widget.Toast.makeText(this, "No current location fix was returned within 20 seconds. Nothing was saved.", android.widget.Toast.LENGTH_LONG).show()
                return@requestCurrent
            }
            val saved = ProtectionStore(this).runIfDisarmed {
                LocalEvidenceVault.record(this, "owner_requested_current_location", snapshot.toVaultDetail())
            } == true
            val ageSeconds = snapshot.fixAgeMs / 1_000L
            android.widget.Toast.makeText(
                this,
                if (saved) "Location result saved (${snapshot.classification}, ${ageSeconds}s old, ±${snapshot.accuracyMeters ?: "?"}m)." else "The encrypted location result could not be saved.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveLastKnownEvidenceLocation() {
        if (!requireProtectionDisarmed()) return
        val snapshot = EvidenceLocationCapture.readLastKnown(this)
        if (snapshot == null) {
            android.widget.Toast.makeText(this, "No cached location fix is available. No location was saved.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val saved = ProtectionStore(this).runIfDisarmed {
            LocalEvidenceVault.record(this, "owner_requested_location_snapshot", snapshot.toVaultDetail())
        } == true
        val ageSeconds = snapshot.fixAgeMs / 1_000L
        android.widget.Toast.makeText(
            this,
            if (saved) "Location snapshot saved (${snapshot.classification}, ${ageSeconds}s old, ±${snapshot.accuracyMeters ?: "?"}m)."
            else "The encrypted location snapshot could not be saved.",
            android.widget.Toast.LENGTH_LONG
        ).show()
        if (saved) render()
    }

    private fun exportHistory(uri: Uri) {
        if (!requireProtectionDisarmed()) return
        ioExecutor.execute {
            val result = runCatching {
                ProtectionStore(applicationContext).runIfDisarmed {
                    val output = contentResolver.openOutputStream(uri)
                        ?: error("Android could not open the selected export destination.")
                    output.bufferedWriter(Charsets.UTF_8).use { writer ->
                        fun csv(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
                        writer.appendLine("record_type,event,timestamp_epoch_ms,timezone_offset_minutes,detail,integrity_sha256")
                        LocalTamperHistory.read(applicationContext, 100).asReversed().forEach { event ->
                            writer.appendLine(listOf("event_history", event, "", "", "", "").joinToString(",") { csv(it) })
                        }
                        LocalEvidenceVault.read(applicationContext, 100).asReversed().forEach { entry ->
                            writer.appendLine(listOf(
                                "encrypted_evidence",
                                entry.event,
                                entry.timestampEpochMs.toString(),
                                entry.timezoneOffsetMinutes.toString(),
                                entry.detail,
                                entry.integritySha256
                            ).joinToString(",") { csv(it) })
                        }
                    }
                } ?: throw IllegalStateException("Event-history export is available only while protection is DISARMED.")
            }
            runOnUiThread {
                result.onSuccess {
                    android.widget.Toast.makeText(this@SettingsActivity, "Event history exported.", android.widget.Toast.LENGTH_LONG).show()
                }.onFailure {
                    android.widget.Toast.makeText(this@SettingsActivity, "Export failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showCustomFlashTimingDialog() {
        val current = AlarmFlashPatternPolicy.customTimings(this)
        val values = (2..40).map { "${it * 50} ms" }.toTypedArray()
        val onPicker = NumberPicker(this).apply {
            minValue = 2
            maxValue = 40
            displayedValues = values
            value = (current.first / 50L).toInt().coerceIn(2, 40)
            wrapSelectorWheel = false
            contentDescription = "Custom flash ON duration"
        }
        val offPicker = NumberPicker(this).apply {
            minValue = 2
            maxValue = 40
            displayedValues = values
            value = (current.second / 50L).toInt().coerceIn(2, 40)
            wrapSelectorWheel = false
            contentDescription = "Custom flash OFF duration"
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            addView(onPicker, LinearLayout.LayoutParams(0, dp(150), 1f))
            addView(offPicker, LinearLayout.LayoutParams(0, dp(150), 1f))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Custom flash cadence")
            .setMessage("Choose independent ON and OFF durations. Values are stored locally and applied on the next alarm start.")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val saved = AlarmFlashPatternPolicy.saveCustomTimings(
                    this, onPicker.value * 50L, offPicker.value * 50L
                )
                android.widget.Toast.makeText(
                    this,
                    if (saved) "Custom flash timing saved." else "Could not save custom flash timing.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                if (saved) {
                    AlarmFlashPatternPolicy.save(this, AlarmFlashPattern.CUSTOM)
                    render()
                }
            }
            .showThemed(this)
    }

    private fun previewFlashPattern() {
        if (ProtectionStore(this).read().mode != ProtectionMode.DISARMED) {
            android.widget.Toast.makeText(this, "Flash preview is available only while protection is DISARMED.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        stopFlashPreview()
        val controller = AlarmFlashController(this, previewToneHandler) { _, _ -> }
        if (!controller.hasFlashHardware()) {
            android.widget.Toast.makeText(this, "No compatible flashlight hardware is available on this device.", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        // Use the same fail-closed thermal boundary as the active alarm service.
        // A preview must never bypass a severe/unknown thermal state.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thermalGuard = runCatching {
                ThermalFlashController(this) { suppressed ->
                    if (suppressed && flashPreviewController != null) {
                        stopFlashPreview()
                        android.widget.Toast.makeText(
                            this,
                            "Flash preview stopped because Android reported a severe thermal state.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.getOrNull()
            if (thermalGuard == null) {
                stopFlashPreview()
                android.widget.Toast.makeText(
                    this,
                    "Flash preview is unavailable because Android thermal status could not be verified.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }
            flashPreviewThermalController = thermalGuard
            if (runCatching { thermalGuard.start() }.getOrDefault(true)) {
                stopFlashPreview()
                android.widget.Toast.makeText(
                    this,
                    "Flash preview is unavailable because thermal safety status is severe or could not be verified.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        if (!controller.start()) {
            stopFlashPreview()
            android.widget.Toast.makeText(this, "Android could not start the flashlight. Another camera app, thermal limit, or OEM policy may be using it.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        flashPreviewController = controller
        val stop = Runnable { stopFlashPreview() }
        flashPreviewStopRunnable = stop
        previewToneHandler.postDelayed(stop, 5_000L)
    }

    private fun checkFlashHardware() {
        val available = AlarmFlashController(this, previewToneHandler) { _, _ -> }.hasFlashHardware()
        android.widget.Toast.makeText(
            this,
            if (available) "A compatible camera LED / torch was detected. Actual access can still be denied while another app or thermal policy owns it."
            else "No compatible camera LED / torch was detected.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    private fun stopFlashPreview() {
        flashPreviewStopRunnable?.let(previewToneHandler::removeCallbacks)
        flashPreviewStopRunnable = null
        flashPreviewController?.stop()
        flashPreviewController = null
        flashPreviewThermalController?.stop()
        flashPreviewThermalController = null
    }

    private fun previewSound(slot: String) {
        if (ProtectionStore(this).read().mode != ProtectionMode.DISARMED) {
            android.widget.Toast.makeText(this, "Sound preview is available only while protection is DISARMED.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (slot == AlarmSoundStore.ALARM && AlarmSoundStore.uri(this, slot) == null) {
            val preset = AlarmSoundStore.selectedBuiltInPreset(this)
            if (preset != AlarmTonePreset.DEFAULT_AUDIO) {
                previewBuiltInAlarmTone(preset)
                return
            }
        }
        val uri = AlarmSoundStore.effectiveUri(this, slot)
        if (uri == null) {
            android.widget.Toast.makeText(this, "No sound is configured for this slot.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        ioExecutor.execute {
            val candidate = runCatching {
                MediaPlayer().apply {
                    setDataSource(this@SettingsActivity, uri)
                    isLooping = false
                    prepare()
                }
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed ||
                    !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ||
                    ProtectionStore(this@SettingsActivity).read().mode != ProtectionMode.DISARMED
                ) {
                    runCatching { candidate?.release() }
                    return@runOnUiThread
                }
                if (candidate == null) {
                    android.widget.Toast.makeText(this, "Preview failed. The file may have moved or become inaccessible.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    stopPreview()
                    previewPlayer = candidate
                    candidate.setOnCompletionListener { stopPreview() }
                    runCatching { candidate.start() }.onFailure {
                        stopPreview()
                        android.widget.Toast.makeText(this, "Could not play preview: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun previewBuiltInAlarmTone(preset: AlarmTonePreset) {
        if (ProtectionStore(this).read().mode != ProtectionMode.DISARMED) {
            android.widget.Toast.makeText(this, "Sound preview is available only while protection is DISARMED.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        stopPreview()
        val controller = AlarmToneController(previewToneHandler, preset) { error ->
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    "Built-in tone preview failed: ${error.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        previewToneController = controller
        if (!controller.start()) {
            previewToneController = null
            android.widget.Toast.makeText(this, "Android could not start this tone. The packaged alarm remains available.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val stop = Runnable { stopPreview() }
        previewToneStopRunnable = stop
        previewToneHandler.postDelayed(stop, 5_000L)
    }

    private fun stopPreview() {
        previewToneStopRunnable?.let(previewToneHandler::removeCallbacks)
        previewToneStopRunnable = null
        previewToneController?.stop()
        previewToneController = null
        runCatching { previewPlayer?.stop() }
        runCatching { previewPlayer?.release() }
        previewPlayer = null
    }

    override fun onPause() {
        stopPreview()
        stopFlashPreview()
        super.onPause()
    }

    override fun onDestroy() {
        stopPreview()
        stopFlashPreview()
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private fun renderStatus() {
        if (::root.isInitialized) render()
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
        val batteryUnrestricted = if (Build.VERSION.SDK_INT >= 23) {
            if (getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(packageName))
                "Battery optimization: unrestricted" else "Battery optimization: may restrict background work"
        } else "Battery optimization: not applicable"
        val device = AndroidDeviceCompatibility.deviceSummary(
            Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT
        )
        return "$device\n$alarm\n$notifications\n$batteryUnrestricted\nMinimum supported Android: 8.0 (API 26)"
    }


    /**
     * This preference changes only which setup guide is expanded in this app.
     * It never pretends to toggle an OEM's private App launch controls.
     */
    private fun renderAppLaunchManagementSection() {
        val family = AndroidDeviceCompatibility.family(Build.MANUFACTURER, Build.BRAND)
        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = getColor(R.color.tas_surface_variant)
            setCardBackgroundColor(getColor(R.color.tas_surface))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        content.addView(text("DEVICE-SPECIFIC BACKGROUND SETUP", 11f, R.color.tas_primary, true))
        content.addView(space(8))
        content.addView(text(
            AndroidDeviceCompatibility.deviceSummary(
                Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT
            ),
            12f, R.color.tas_muted
        ))
        content.addView(space(8))
        content.addView(text(
            "Detected automatically from this phone. The instructions below are selected for this device family and Android version. OEM settings are controlled by Android/the manufacturer, not by this app.",
            13f, R.color.tas_muted
        ))
        content.addView(space(10))
        content.addView(text(
            AndroidDeviceCompatibility.backgroundSettingsGuide(Build.MANUFACTURER, Build.BRAND),
            13f, R.color.tas_text
        ))
        content.addView(space(10))
        content.addView(text(
            when (family) {
                AndroidDeviceCompatibility.Family.HONOR_HUAWEI ->
                    "HONOR/Huawei: open Settings, search for App launch, select ThiefAlertSeQure, turn Manage automatically OFF, enable Auto-launch, Secondary launch, and Run in background, then confirm."
                AndroidDeviceCompatibility.Family.SAMSUNG ->
                    "Samsung: there is no equivalent three-switch panel. The key control is Never sleeping apps. Do not add this app to Sleeping apps or Deep sleeping apps."
                AndroidDeviceCompatibility.Family.XIAOMI ->
                    "Xiaomi/Redmi/POCO: enable Background autostart and choose No restrictions for battery use when those options exist."
                AndroidDeviceCompatibility.Family.OPPO_REALME_ONEPLUS ->
                    "OPPO/realme/OnePlus: enable Auto launch and allow background activity where your software version exposes those controls."
                AndroidDeviceCompatibility.Family.VIVO_IQOO ->
                    "vivo/iQOO: allow background power use and Autostart/Background start where your software version exposes them."
                AndroidDeviceCompatibility.Family.GOOGLE_PIXEL ->
                    "Pixel: use Android's App battery usage controls. There is no separate HONOR-style Auto-launch/Secondary launch panel."
                AndroidDeviceCompatibility.Family.MOTOROLA ->
                    "Motorola: use Android App battery usage controls. A separate OEM auto-start switch may not exist."
                AndroidDeviceCompatibility.Family.ASUS,
                AndroidDeviceCompatibility.Family.SONY,
                AndroidDeviceCompatibility.Family.NOTHING,
                AndroidDeviceCompatibility.Family.TRANSSION,
                AndroidDeviceCompatibility.Family.ZTE,
                AndroidDeviceCompatibility.Family.LENOVO,
                AndroidDeviceCompatibility.Family.OTHER ->
                    "This Android device may use a model-specific battery manager. Use only the background/autostart controls actually provided by this phone. Do not enable unrelated permissions."
            },
            12f, R.color.tas_text
        ))
        content.addView(space(10))
        AndroidDeviceCompatibility.LaunchControl.values().forEach { control ->
            val item = MaterialCardView(this).apply {
                radius = dp(14).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                strokeColor = getColor(R.color.tas_surface_variant)
                setCardBackgroundColor(getColor(R.color.tas_background))
            }
            val itemContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(8))
            }
            itemContent.addView(text(control.title, 15f, R.color.tas_text, true))
            itemContent.addView(space(5))
            itemContent.addView(text(
                AndroidDeviceCompatibility.launchControlGuide(
                    Build.MANUFACTURER, Build.BRAND, control, Build.MODEL,
                    Build.VERSION.RELEASE, Build.VERSION.SDK_INT
                ),
                12f, R.color.tas_muted
            ))
            itemContent.addView(space(6))
            itemContent.addView(action(
                if (family == AndroidDeviceCompatibility.Family.HONOR_HUAWEI)
                    "Open Settings — search App launch"
                else
                    "Open this phone's supported Settings"
            ) {
                openLaunchControlSettings(control)
            })
            item.addView(itemContent)
            content.addView(item, matchWrap())
            content.addView(space(8))
        }
        content.addView(text(
            "OEM switch status: Not readable by ordinary Android apps. These cards are device-specific instructions, not a false ON/OFF status. After changing settings, return and test monitoring with the screen off.",
            11f, R.color.tas_muted
        ))
        card.addView(content)
        root.addView(card, matchWrap())
    }

    private fun openLaunchControlSettings(control: AndroidDeviceCompatibility.LaunchControl) {
        val family = AndroidDeviceCompatibility.family(Build.MANUFACTURER, Build.BRAND)
        val primary = AndroidDeviceCompatibility.launchControlSettingsIntent(
            Build.MANUFACTURER, Build.BRAND, packageName, control
        )
        val opened = AndroidDeviceCompatibility.open(
            this,
            primary,
            AndroidDeviceCompatibility.appInfoIntent(packageName),
            Intent(Settings.ACTION_SETTINGS)
        )
        if (opened) {
            val searchTerm = AndroidDeviceCompatibility.settingsSearchHint(
                Build.MANUFACTURER, Build.BRAND, control
            )
            val nextStep = if (family == AndroidDeviceCompatibility.Family.HONOR_HUAWEI) {
                "In Settings, search App launch → select ThiefAlertSeQure → turn Manage automatically OFF and enable all three switches."
            } else {
                "In Settings, search for " + searchTerm + " and follow the " + control.title + " instructions shown in ThiefAlertSeQure."
            }
            android.widget.Toast.makeText(this, nextStep, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppBatterySettings() {
        AndroidDeviceCompatibility.open(
            this,
            AndroidDeviceCompatibility.appInfoIntent(packageName),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= 23) {
            val power = getSystemService(android.os.PowerManager::class.java)
            if (power.isIgnoringBatteryOptimizations(packageName)) {
                android.widget.Toast.makeText(this, "Battery optimization is already unrestricted for ThiefAlertSeQure.", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            AndroidDeviceCompatibility.open(
                this,
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                AndroidDeviceCompatibility.appInfoIntent(packageName)
            )
        } else {
            openBatterySettings()
        }
    }

    private fun openAppInfo() {
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
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
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openDndSettings() {
        open(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS), Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            open(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:$packageName")),
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } else {
            android.widget.Toast.makeText(this,
                "Android " + Build.VERSION.SDK_INT + " does not have a separate full-screen intent access page.",
                android.widget.Toast.LENGTH_LONG).show()
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun requestDeviceAdministrator() {
        if (!requireProtectionDisarmed()) return
        val component = android.content.ComponentName(this, ThiefAlertAdminReceiver::class.java)
        val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Allow ThiefAlertSeQure to lock the screen when its anti-theft alarm is triggered.")
        open(intent, Intent(Settings.ACTION_SECURITY_SETTINGS))
    }

    private fun showRecentAppsTutorial() {
        MaterialAlertDialogBuilder(this).setTitle("Keep ThiefAlertSeQure in Recents")
            .setMessage(androidVersionContext() + "\n\nOpen Recents, locate ThiefAlertSeQure, then use the app-card menu or manufacturer-specific gesture to choose Lock / Keep open if available. Android may still stop the app.")
            .setPositiveButton("OK", null).showThemed(this)
    }

    private fun androidVersionContext(): String {
        val release = Build.VERSION.RELEASE ?: "unknown"
        val name = when (Build.VERSION.SDK_INT) {
            26 -> "Android 8.0"; 27 -> "Android 8.1"; 28 -> "Android 9"; 29 -> "Android 10"
            30 -> "Android 11"; 31 -> "Android 12"; 32 -> "Android 12L"; 33 -> "Android 13"
            34 -> "Android 14"; 35 -> "Android 15"; 36 -> "Android 16"; 37 -> "Android 17"
            else -> "Android API " + Build.VERSION.SDK_INT
        }
        val route = when (Build.VERSION.SDK_INT) {
            26, 27 -> "Typical route: Settings → Apps & notifications → App info → ThiefAlertSeQure. Battery optimization is usually under Settings → Battery. Notification controls are under App info → Notifications."
            28 -> "Typical route: Settings → Apps & notifications → ThiefAlertSeQure → Battery. For optimization, check Settings → Apps & notifications → Special app access → Battery optimization."
            29 -> "Typical route: Settings → Apps → ThiefAlertSeQure → Battery. Check Settings → Apps & notifications → Special app access → Battery optimization for the allowlist."
            30 -> "Typical route: Settings → Apps & notifications → ThiefAlertSeQure → Battery. Review Background restriction and Unrestricted options where present."
            31, 32 -> "Typical route: Settings → Apps → ThiefAlertSeQure → App battery usage. Exact alarms are under Settings → Apps → Special app access → Alarms & reminders."
            33 -> "Typical route: Settings → Apps → ThiefAlertSeQure → Notifications and Battery. Notification permission is runtime-controlled; exact alarms remain under Special app access."
            34 -> "Typical route: Settings → Apps → Special app access → Full-screen notifications, and Settings → Apps → ThiefAlertSeQure → Notifications / Battery."
            35, 36, 37 -> "Typical route: Settings → Apps → Special app access → Full-screen notifications / Alarms & reminders, and App info → ThiefAlertSeQure → Notifications / Battery. Android 17 also requires background audio to run with a qualifying foreground service."
            else -> "Use Settings search for ThiefAlertSeQure, Battery, Notifications, Alarms & reminders, or Full-screen notifications."
        }
        val device = AndroidDeviceCompatibility.deviceSummary(
            Build.MANUFACTURER, Build.BRAND, Build.MODEL, release, Build.VERSION.SDK_INT
        )
        val oemGuide = AndroidDeviceCompatibility.backgroundSettingsGuide(Build.MANUFACTURER, Build.BRAND)
        return "$device\n$route\n\n$oemGuide\n\nManufacturer menus can differ; use Settings search if a label is unavailable."
    }

    private fun addSystemSettingAction(label: String, tutorialTitle: String, tutorial: String, click: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val launch = action(label, click).apply {
            val statusId = when (tutorialTitle) { "Notifications", "App notifications" -> "notifications"; "Exact alarms" -> "exact"; "Full-screen intent permission" -> "fullscreen"; "Secure device lock" -> "lock"; "Battery optimization", "Background activity" -> "battery"; "Bluetooth permission" -> "bt_permission"; else -> null }
            statusId?.let { id ->
                val p = ThemeStore.get(this@SettingsActivity)
                val state = SetupStatusSnapshot.read(this@SettingsActivity).items.firstOrNull { it.id == id }?.state
                val accent = when (state) { SetupState.VERIFIED -> p.success; SetupState.ACTION_REQUIRED -> p.warning; SetupState.NEUTRAL, SetupState.UNSUPPORTED -> p.muted; SetupState.UNKNOWN -> p.danger; null -> p.primary }
                strokeColor = android.content.res.ColorStateList.valueOf(accent)
                setTextColor(accent)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        }
        val info = MaterialButton(this).apply {
            text = "i"; isAllCaps = false; minWidth = dp(44); minHeight = dp(48); cornerRadius = dp(14)
            contentDescription = "Tutorial: $tutorialTitle"
            setOnClickListener {
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(tutorialTitle + " • Android " + Build.VERSION.SDK_INT)
                    .setMessage(androidVersionContext() + "\n\n" + tutorial)
                    .setPositiveButton("Got it", null).showThemed(this@SettingsActivity)
            }
        }
        row.addView(launch); row.addView(info, LinearLayout.LayoutParams(dp(48), dp(52)))
        root.addView(row, matchWrap())
    }

    companion object {
        const val EXTRA_SECTION = "com.thiefalertsequre.settings.SECTION"
        const val SECTION_SOUNDS_FLASH = "sounds_flash"
        const val SECTION_TAMPER_HISTORY = "tamper_history"
        const val SECTION_MORE = "more"
        const val SECTION_UPDATES = "updates"
        const val SECTION_PRIVACY = "privacy"
    }

    private fun open(primary: Intent, fallback: Intent? = null) {
        if (fallback == null) {
            AndroidDeviceCompatibility.open(this, primary)
        } else {
            AndroidDeviceCompatibility.open(this, primary, fallback)
        }
    }

    private fun requireProtectionDisarmed(): Boolean {
        if (ConfigurationBackup.isRestoreOrRecoveryInProgress() ||
            !ConfigurationBackup.recoverPendingRestore(this)
        ) {
            android.widget.Toast.makeText(
                this,
                "Settings changes are temporarily locked while an interrupted configuration restore is being recovered.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return false
        }
        if (ProtectionStore(this).read().mode == ProtectionMode.DISARMED) return true
        android.widget.Toast.makeText(
            this,
            "Protection settings are locked while protection is ARMED, COUNTDOWN, or ALARM_TRIGGERED. Disarm first.",
            android.widget.Toast.LENGTH_LONG
        ).show()
        return false
    }

    private fun showProtectionSettingsSaveFailure() {
        android.widget.Toast.makeText(
            this,
            "The setting was not changed. Confirm protection is DISARMED and retry.",
            android.widget.Toast.LENGTH_LONG
        ).show()
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
