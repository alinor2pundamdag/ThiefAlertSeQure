package com.thiefalertsequre.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.media.MediaPlayer
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
import androidx.core.content.ContextCompat
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
    private var pendingUpdateInstall: File? = null
    private val pickSoundFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) validateAndSaveSound(uri, soundSlot, uri)
    }
    private val pickSoundFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) searchAudioFolder(treeUri, soundSlot)
    }
    private val exportEvents = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) exportHistory(uri)
    }
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

        root.addView(text("OFFICIAL APP UPDATES", 11f, R.color.tas_primary, true))
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
        val showAlarmScreenOverLockScreen = alarmDisplayPrefs.getBoolean(
            MainActivity.KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN, false
        )
        root.addView(action(
            "Full-screen app alarm dashboard: ${if (showAlarmScreenOverLockScreen) "ON" else "OFF"}"
        ) {
            val enabled = !alarmDisplayPrefs.getBoolean(
                MainActivity.KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN, false
            )
            alarmDisplayPrefs.edit()
                .putBoolean(MainActivity.KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN, enabled)
                .apply()
            render()
        })
        root.addView(space(10))
        root.addView(text("ALARM FLASH ALERT", 11f, R.color.tas_primary, true))
        root.addView(space(6))
        root.addView(text(
            "Enabled by default, the rear camera LED pulses slowly only while the theft alarm is latched. It uses Android's torch API without camera permission and never captures photos or video. Flash hardware and availability vary by device; the sound alarm remains independent. Disable this option if flashing light is unsuitable for you.",
            13f, R.color.tas_muted
        ))
        val flashEnabled = AlarmFlashSettings.isEnabled(this)
        root.addView(action("Camera flash alert: ${if (flashEnabled) "ON" else "OFF"}") {
            val enabled = !AlarmFlashSettings.isEnabled(this)
            getSharedPreferences(AlarmFlashSettings.PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(AlarmFlashSettings.KEY_ENABLED, enabled).apply()
            if (ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED) {
                runCatching {
                    ContextCompat.startForegroundService(
                        this,
                        Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_REFRESH_FLASH)
                    )
                }
            }
            render()
        })
        root.addView(space(22))

        root.addView(text("SYSTEM ACCESS & PERMISSIONS", 11f, R.color.tas_primary, true))
        root.addView(space(10))
        addSystemSettingAction("Grant notifications permission", "Notifications", "Android 13 (API 33)+ asks for notification permission at runtime. If denied, open App info → Notifications and enable it. Android 8–12 uses per-app notification controls without this runtime prompt. Verify the Anti-theft alarm channel is enabled and allowed to appear on the lock screen.") { requestNotifications() }
        addSystemSettingAction("Alarms & reminders / exact alarm access", "Exact alarms", "Android 12 (API 31)+ exposes special Alarms & reminders access. Allow ThiefAlertSeQure if listed. Android 8–11 does not expose this special access. Without it, countdown delivery may use fallback scheduling and can be delayed.") { openExactAlarmSettings() }
        addSystemSettingAction("All app permissions and app information", "App permissions", "Open App info → Permissions and review only permissions requested by this build. The local charger-alarm core does not need contacts, location, microphone, camera permission, SMS, or internet. The optional flashlight uses Android's torch API without camera permission and does not capture images or video. Do not grant unrelated permissions.") { openAppInfo() }
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
        root.addView(text("Main alarm: " + AlarmSoundStore.displayName(this, AlarmSoundStore.ALARM), 13f, R.color.tas_text))
        root.addView(action("Use built-in default alarm (clear custom override)") {
            AlarmSoundStore.clear(this, AlarmSoundStore.ALARM)
            android.widget.Toast.makeText(this, "Built-in default alarm selected.", android.widget.Toast.LENGTH_SHORT).show()
            render()
        })
        root.addView(action("Choose custom main alarm audio file") {
            soundSlot = AlarmSoundStore.ALARM
            pickSoundFile.launch(arrayOf("audio/*"))
        })
        root.addView(action("Search a folder and its subfolders for alarm audio") {
            soundSlot = AlarmSoundStore.ALARM
            pickSoundFolder.launch(null)
        })
        root.addView(action("Preview selected main alarm") { previewSound(AlarmSoundStore.ALARM) })
        root.addView(space(6))
        root.addView(text("Secondary alert: " + AlarmSoundStore.displayName(this, AlarmSoundStore.ALERT), 13f, R.color.tas_text))
        root.addView(action("Choose secondary alert audio file") {
            soundSlot = AlarmSoundStore.ALERT
            pickSoundFile.launch(arrayOf("audio/*"))
        })
        root.addView(action("Search a folder and its subfolders for alert audio") {
            soundSlot = AlarmSoundStore.ALERT
            pickSoundFolder.launch(null)
        })
        root.addView(action("Preview selected secondary alert") { previewSound(AlarmSoundStore.ALERT) })
        root.addView(action("Stop audio preview") { stopPreview() })
        root.addView(space(6))
        root.addView(text(
            "The main alarm selection is used for theft alarms. The secondary alert selection is used for the optional battery-threshold alert.",
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
        val monitorEnabled = monitorPrefs.getBoolean(ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
        root.addView(action(if (monitorEnabled) "Disable background monitoring" else "Enable background monitoring") {
            val enabled = !monitorPrefs.getBoolean(ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
            monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, enabled).apply()
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
                // Explicit owner opt-out stops pending charger countdowns too,
                // but never clears a latched alarm without authentication.
                val disabledState = ProtectionStore(this).update { current ->
                    if (current.mode == ProtectionMode.ALARM_TRIGGERED) current
                    else ProtectionState(ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1)
                }
                if (disabledState.mode != ProtectionMode.ALARM_TRIGGERED) AlarmScheduler.cancel(this)
                stopService(Intent(this, ProtectionMonitorService::class.java))
            } else {
                android.widget.Toast.makeText(this, "Monitoring will resume after owner authentication.", android.widget.Toast.LENGTH_LONG).show()
            }
            render()
        })
        val motionEnabled = monitorPrefs.getBoolean(
            ProtectionMonitorService.KEY_MOTION_ENABLED, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED
        )
        root.addView(action("Independent motion alarm: ${if (motionEnabled) "ON" else "OFF"}") {
            val enabled = !monitorPrefs.getBoolean(
                ProtectionMonitorService.KEY_MOTION_ENABLED, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED
            )
            monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_MOTION_ENABLED, enabled).apply()
            sendBroadcast(Intent(ProtectionMonitorService.ACTION_REFRESH).setPackage(packageName))
            render()
        })
        root.addView(text(
            "Motion is a separate alarm source and can trigger while the charger is still connected. Leave it OFF for charger-removal-only protection.",
            12f, R.color.tas_muted
        ))
        val sensitivity = MotionSensitivity.fromStored(
            monitorPrefs.getString(ProtectionMonitorService.KEY_SENSITIVITY, MotionSensitivity.MEDIUM.name)
        )
        root.addView(action("Motion sensitivity (when enabled): ${sensitivity.name.lowercase()}") {
            val choices = MotionSensitivity.values().map { it.name.lowercase() }.toTypedArray()
            MaterialAlertDialogBuilder(this)
                .setTitle("Motion sensitivity")
                .setSingleChoiceItems(choices, MotionSensitivity.values().indexOf(sensitivity)) { dialog, which ->
                    monitorPrefs.edit().putString(ProtectionMonitorService.KEY_SENSITIVITY, MotionSensitivity.values()[which].name).apply()
                    dialog.dismiss()
                    render()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        val batteryAlertEnabled = monitorPrefs.getBoolean(ProtectionMonitorService.KEY_BATTERY_ALERT_ENABLED, false)
        root.addView(action("Battery threshold alert: ${if (batteryAlertEnabled) "ON" else "OFF"}") {
            val enabled = !batteryAlertEnabled
            monitorPrefs.edit()
                .putBoolean(ProtectionMonitorService.KEY_BATTERY_ALERT_ENABLED, enabled)
                .apply()
            if (enabled) {
                monitorPrefs.edit().putBoolean(ProtectionMonitorService.KEY_ENABLED, true).apply()
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
        val threshold = monitorPrefs.getInt(ProtectionMonitorService.KEY_BATTERY_THRESHOLD, 80)
        root.addView(action("Battery alert threshold: ${threshold}%") {
            val choices = arrayOf("80%", "85%", "90%", "95%", "100%")
            val values = intArrayOf(80, 85, 90, 95, 100)
            MaterialAlertDialogBuilder(this)
                .setTitle("Battery alert threshold")
                .setSingleChoiceItems(choices, values.indexOf(threshold).coerceAtLeast(0)) { dialog, which ->
                    monitorPrefs.edit().putInt(ProtectionMonitorService.KEY_BATTERY_THRESHOLD, values[which]).apply()
                    dialog.dismiss()
                    render()
                }
                .setNegativeButton("Cancel", null)
                .show()
        })
        root.addView(space(22))

        root.addView(text("LOCAL TAMPER HISTORY", 11f, R.color.tas_primary, true))
        root.addView(space(8))
        val historyText = LocalTamperHistory.read(this, 20).ifEmpty {
            listOf("No recorded protection events yet.")
        }.joinToString("\n")
        root.addView(text(historyText, 12f, R.color.tas_text))
        root.addView(space(8))
        root.addView(action("Export local event history (CSV)") {
            exportEvents.launch("thiefalertsequre-events.csv")
        })
        root.addView(action("Clear local event history") {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear local history?")
                .setMessage("This permanently clears the on-device protection event log. It does not change protection state.")
                .setNegativeButton("Keep history", null)
                .setPositiveButton("Clear") { _, _ ->
                    LocalTamperHistory.clear(this)
                    LocalTamperHistory.record(this, "history_cleared", "Owner cleared local event history")
                    render()
                }.show()
        })
        root.addView(space(22))

        root.addView(text("PRIVACY & DATA", 11f, R.color.tas_primary, true))
        root.addView(space(10))
        root.addView(text(
            "No account, analytics, advertising SDK, or app backend is used. Internet access is used only when you manually check for an official GitHub release and download its APK; protection state and personal data are not uploaded. The optional alarm flashlight uses Android's torch API without camera permission and does not capture images or video. Device credentials and biometric templates are handled by Android and are not read by this app.",
            13f, R.color.tas_muted
        ))
        root.addView(space(10))
        root.addView(action("Review privacy notice") {
            getSharedPreferences("privacy_notice_v1", MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        })
        root.addView(space(22))
        root.addView(action("Back to protection dashboard") { finish() })
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
    }

    private fun checkForOfficialUpdate() {
        android.widget.Toast.makeText(this, "Checking official GitHub release…", android.widget.Toast.LENGTH_SHORT).show()
        AppUpdateManager.checkAndDownload(this, ioExecutor) { result ->
            if (isFinishing || isDestroyed) return@checkAndDownload
            when (result) {
                is AppUpdateResult.Ready -> {
                    val notes = result.releaseNotes.ifBlank { "No release notes were provided." }
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Version \${result.versionName} is available")
                        .setMessage(
                            notes + "\n\nAndroid will ask you to confirm installation. " +
                                "Because the package ID and official signing key match, this is an in-place update; your app data is retained."
                        )
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Install update") { _, _ -> beginUpdateInstall(result.apkFile) }
                        .show()
                }
                AppUpdateResult.UpToDate -> android.widget.Toast.makeText(
                    this, "ThiefAlertSeQure is already up to date.", android.widget.Toast.LENGTH_LONG
                ).show()
                is AppUpdateResult.Failed -> MaterialAlertDialogBuilder(this)
                    .setTitle("Update unavailable")
                    .setMessage(result.reason)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun beginUpdateInstall(apkFile: File) {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            android.widget.Toast.makeText(this, "The verified update file is no longer available. Check again.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            pendingUpdateInstall = apkFile
            MaterialAlertDialogBuilder(this)
                .setTitle("Allow Android to install this update")
                .setMessage(
                    "Android requires your approval for installs initiated outside an app store. " +
                        "Allow ThiefAlertSeQure to install this verified official update. You can turn this access off again afterward."
                )
                .setNegativeButton("Cancel") { _, _ -> pendingUpdateInstall = null }
                .setPositiveButton("Open Android setting") { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .show()
            return
        }
        launchAndroidInstaller(apkFile)
    }

    private fun launchAndroidInstaller(apkFile: File) {
        runCatching {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.updates", apkFile)
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            startActivity(installIntent)
        }.onFailure {
            android.widget.Toast.makeText(
                this, "Android could not open the package installer: \${it.message}", android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
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
                    .show()
            }
        }
    }

    private fun isAudioDocument(file: DocumentFile): Boolean {
        if (file.type.orEmpty().startsWith("audio/")) return true
        val name = file.name.orEmpty().lowercase()
        return listOf(".mp3", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".flac", ".3gp", ".amr")
            .any { name.endsWith(it) }
    }

    private fun exportHistory(uri: Uri) {
        runCatching {
            val output = contentResolver.openOutputStream(uri)
                ?: error("Android could not open the selected export destination.")
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.appendLine("event")
                LocalTamperHistory.read(this, 100).asReversed().forEach { event ->
                    writer.append('"')
                    writer.append(event.replace("\"", "\"\""))
                    writer.append('"')
                    writer.appendLine()
                }
            }
        }.onSuccess {
            android.widget.Toast.makeText(this, "Event history exported.", android.widget.Toast.LENGTH_LONG).show()
        }.onFailure {
            android.widget.Toast.makeText(this, "Export failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun previewSound(slot: String) {
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

    private fun stopPreview() {
        runCatching { previewPlayer?.stop() }
        runCatching { previewPlayer?.release() }
        previewPlayer = null
    }

    override fun onDestroy() {
        stopPreview()
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
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
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
            .setPositiveButton("OK", null).show()
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        }
        val info = MaterialButton(this).apply {
            text = "i"; isAllCaps = false; minWidth = dp(44); minHeight = dp(48); cornerRadius = dp(14)
            contentDescription = "Tutorial: $tutorialTitle"
            setOnClickListener {
                MaterialAlertDialogBuilder(this@SettingsActivity)
                    .setTitle(tutorialTitle + " • Android " + Build.VERSION.SDK_INT)
                    .setMessage(androidVersionContext() + "\n\n" + tutorial)
                    .setPositiveButton("Got it", null).show()
            }
        }
        row.addView(launch); row.addView(info, LinearLayout.LayoutParams(dp(48), dp(52)))
        root.addView(row, matchWrap())
    }

    private fun open(primary: Intent, fallback: Intent? = null) {
        if (fallback == null) {
            AndroidDeviceCompatibility.open(this, primary)
        } else {
            AndroidDeviceCompatibility.open(this, primary, fallback)
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
