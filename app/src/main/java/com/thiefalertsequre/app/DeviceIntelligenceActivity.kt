package com.thiefalertsequre.app

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.app.KeyguardManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.StatFs
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import org.json.JSONObject
import java.util.concurrent.Executors

/** Read-only, on-device capability and readiness report. No hardware identifiers are collected. */
class DeviceIntelligenceActivity : FragmentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var rootStatusView: TextView
    private val rootCheckExecutor = Executors.newSingleThreadExecutor()
    private var rangingStatus: String? = null
    private var stopRangingQuery: (() -> Unit)? = null
    private val exportDiagnostics = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) writeDiagnosticsReport(uri) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }

    override fun onResume() {
        super.onResume()
        PermissionChangeMonitor.check(this)
        // Re-read permissions, platform capabilities and service status after Android Settings.
        if (::root.isInitialized) render()
    }

    private fun render() {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(getColor(R.color.tas_background)) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(30)) }
        scroll.addView(root)
        root.addView(button("‹  Back") { finish() }, matchWrap())
        root.addView(space(18))
        root.addView(label("DEVICE CAPABILITY CENTER", 11f, R.color.tas_primary, true), matchWrap())
        root.addView(space(6))
        root.addView(label("Device Intelligence", 29f, R.color.tas_text, true).markAsPageHeading(), matchWrap())
        root.addView(space(8))
        root.addView(label("A local profile of this phone's hardware, Android platform, resources, sensors, and protection-related system readiness. Refresh anytime; nothing is uploaded.", 14f, R.color.tas_muted, false), matchWrap())
        root.addView(space(16))

        val pm = packageManager
        val manufacturer = Build.MANUFACTURER.ifBlank { "Unknown" }
        val brand = Build.BRAND.ifBlank { "Unknown" }
        val model = Build.MODEL.ifBlank { "Unknown" }
        val family = AndroidDeviceCompatibility.familyLabel(manufacturer, brand)
        addCard("DEVICE IDENTITY", listOf(
            "Manufacturer: $manufacturer",
            "Brand / family: $brand • $family",
            "Model: $model",
            "Device code: ${Build.DEVICE} • Product: ${Build.PRODUCT}",
            "Build fingerprint: withheld (not needed for app capability decisions)"
        ))
        addCard("ANDROID PLATFORM", listOf(
            "Android: ${Build.VERSION.RELEASE} • API ${Build.VERSION.SDK_INT}",
            "Security patch: ${Build.VERSION.SECURITY_PATCH}",
            "Build type: ${Build.TYPE} • Tags: ${Build.TAGS ?: "unknown"}",
            "Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}",
            "App version: ${appVersion()} • target SDK: ${applicationInfo.targetSdkVersion} • min SDK: 26"
        ))

        val memory = getSystemService(ActivityManager::class.java).let { manager ->
            ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        }
        val stat = StatFs(filesDir.absolutePath)
        val totalStorage = stat.totalBytes
        val freeStorage = stat.availableBytes
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) "${(level * 100 / scale)}%" else "Unavailable"
        val plugged = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val batteryStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING || batteryStatus == BatteryManager.BATTERY_STATUS_FULL
        val batteryManager = getSystemService(BatteryManager::class.java)
        val capacity = runCatching { batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }.getOrDefault(-1)
        val power = getSystemService(PowerManager::class.java)
        val thermal = if (Build.VERSION.SDK_INT >= 29) thermalLabel(power.currentThermalStatus) else "Not exposed before API 29"
        addCard("PERFORMANCE AND POWER", listOf(
            "RAM: ${formatBytes(memory.totalMem)} total • ${formatBytes(memory.availMem)} available",
            "Low-memory condition: ${if (memory.lowMemory) "Yes" else "No"}",
            "App data filesystem volume: ${formatBytes(totalStorage)} total • ${formatBytes(freeStorage)} available",
            "CPU logical processors: ${Runtime.getRuntime().availableProcessors()}",
            "Battery: $batteryPercent • ${if (charging || plugged) "External power connected" else "Not charging"}",
            "BatteryManager capacity reading: ${if (capacity in 0..100) "$capacity%" else "Unavailable"}",
            "Battery Saver: ${if (power.isPowerSaveMode) "ON" else "OFF"} • Thermal status: $thermal",
            "App battery optimization exemption: ${if (Build.VERSION.SDK_INT >= 23 && power.isIgnoringBatteryOptimizations(packageName)) "Granted" else "Not granted / not exempt"}"
        ))

        val backgroundRestricted = if (Build.VERSION.SDK_INT >= 28) {
            runCatching { getSystemService(ActivityManager::class.java).isBackgroundRestricted }.getOrNull()
        } else null
        val standbyBucket = if (Build.VERSION.SDK_INT >= 28) {
            runCatching { getSystemService(UsageStatsManager::class.java).appStandbyBucket }.getOrNull()
        } else null
        val backgroundState = when (backgroundRestricted) {
            true -> "Restricted by Android"
            false -> "Not marked restricted by Android"
            null -> "Not exposed on this Android version"
        }
        val standbyState = if (Build.VERSION.SDK_INT >= 28) {
            standbyBucket?.let(::standbyBucketLabel) ?: "Unavailable"
        } else "Not exposed before API 28"
        addCard("OEM AND BACKGROUND EXECUTION", listOf(
            "Manufacturer compatibility family: $family",
            "Background execution restriction: $backgroundState",
            "Device idle / Doze state: ${if (power.isDeviceIdleMode) "Idle" else "Not idle"}",
            "App standby bucket: $standbyState",
            "Battery optimization exemption: ${if (power.isIgnoringBatteryOptimizations(packageName)) "Granted" else "Not granted"}",
            "OEM guidance: ${AndroidDeviceCompatibility.backgroundSettingsGuide(manufacturer, brand)}",
            "These are read-only Android-reported states; OEM firmware may apply additional policies that Android does not expose."
        ))

        val display = windowManager.defaultDisplay
        val metrics = resources.displayMetrics
        val mode = runCatching { display.mode }.getOrNull()
        addCard("DISPLAY", listOf(
            "Resolution: ${metrics.widthPixels} × ${metrics.heightPixels} pixels",
            "Density: ${String.format(Locale.US, "%.2f", metrics.density)}× • ${metrics.densityDpi} dpi",
            "Refresh rate: ${String.format(Locale.US, "%.1f", display.refreshRate)} Hz",
            "Active display mode: ${mode?.physicalWidth ?: metrics.widthPixels} × ${mode?.physicalHeight ?: metrics.heightPixels}"
        ))

        val sensorManager = getSystemService(SensorManager::class.java)
        val sensorChecks = listOf(
            Sensor.TYPE_ACCELEROMETER to "Accelerometer",
            Sensor.TYPE_GYROSCOPE to "Gyroscope",
            Sensor.TYPE_MAGNETIC_FIELD to "Magnetometer / compass",
            Sensor.TYPE_PROXIMITY to "Proximity",
            Sensor.TYPE_LIGHT to "Ambient light",
            Sensor.TYPE_PRESSURE to "Barometer",
            Sensor.TYPE_STEP_COUNTER to "Step counter",
            Sensor.TYPE_STEP_DETECTOR to "Step detector",
            Sensor.TYPE_ROTATION_VECTOR to "Rotation vector",
            Sensor.TYPE_SIGNIFICANT_MOTION to "Significant motion"
        )
        val sensorCount = runCatching { sensorManager.getSensorList(Sensor.TYPE_ALL).size }.getOrDefault(0)
        val sensorLines = sensorChecks.map { (type, name) ->
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor == null) "$name: Not detected"
            else "$name: ${sensor.name} • ${sensor.vendor} • range ${sensor.maximumRange} • resolution ${sensor.resolution} • ${sensor.power} mA"
        } + "Total sensor endpoints reported by Android: $sensorCount"
        addCard("SENSOR CAPABILITIES", sensorLines)

        val features = listOf(
            PackageManager.FEATURE_BLUETOOTH to "Bluetooth Classic",
            PackageManager.FEATURE_BLUETOOTH_LE to "Bluetooth Low Energy",
            PackageManager.FEATURE_WIFI to "Wi-Fi",
            PackageManager.FEATURE_NFC to "NFC",
            PackageManager.FEATURE_LOCATION_GPS to "GNSS / GPS hardware",
            PackageManager.FEATURE_LOCATION_NETWORK to "Network location provider capability",
            PackageManager.FEATURE_TELEPHONY to "Telephony hardware",
            PackageManager.FEATURE_CAMERA to "Rear / general camera hardware",
            PackageManager.FEATURE_CAMERA_FRONT to "Front camera hardware",
            PackageManager.FEATURE_CAMERA_FLASH to "Camera flash / torch hardware",
            PackageManager.FEATURE_FINGERPRINT to "Fingerprint hardware",
            PackageManager.FEATURE_USB_HOST to "USB host",
            PackageManager.FEATURE_USB_ACCESSORY to "USB accessory mode",
            PackageManager.FEATURE_SENSOR_ACCELEROMETER to "Accelerometer feature flag",
            PackageManager.FEATURE_SENSOR_GYROSCOPE to "Gyroscope feature flag",
            PackageManager.FEATURE_SENSOR_COMPASS to "Compass feature flag",
            PackageManager.FEATURE_SENSOR_PROXIMITY to "Proximity sensor feature flag"
        )
        addCard("ANDROID HARDWARE FEATURES", features.map { (feature, name) ->
            "$name: ${if (pm.hasSystemFeature(feature)) "Detected" else "Not declared by device"}"
        })

        val alarmManager = getSystemService(AlarmManager::class.java)
        val exactAlarm = if (Build.VERSION.SDK_INT >= 31) runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false) else true
        val notifications = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val secureLock = getSystemService(KeyguardManager::class.java).isDeviceSecure
        val notificationManager = getSystemService(NotificationManager::class.java)
        val fullScreenIntent = if (Build.VERSION.SDK_INT >= 34) runCatching { notificationManager.canUseFullScreenIntent() }.getOrDefault(false) else true
        val monitorPrefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
        val monitorEnabled = SafePreferences.boolean(monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
        val monitorRunning = SafePreferences.boolean(monitorPrefs, ProtectionMonitorService.KEY_RUNNING, false)
        val protectionMode = ProtectionStore(this).read().mode
        val btConnect = Build.VERSION.SDK_INT < 31 || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val a2Enabled = SafePreferences.boolean(monitorPrefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
        val dashboardEnabled = SafePreferences.boolean(getSharedPreferences(MainActivity.ALARM_DISPLAY_PREFS, MODE_PRIVATE), MainActivity.KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN, false)
        val fullScreenSummary = when {
            !dashboardEnabled -> "Not needed — optional dashboard is OFF"
            Build.VERSION.SDK_INT < 34 -> "Available — no separate access gate on this Android version"
            fullScreenIntent -> "Allowed by Android"
            else -> "Not allowed — normal alarm notification and sound remain available"
        }
        val exactAlarmSummary = when {
            !monitorEnabled -> "Not needed while the protection monitor is disabled"
            Build.VERSION.SDK_INT < 31 -> "Available — no separate access gate on this Android version"
            exactAlarm -> "Available / permitted"
            else -> "Access not granted — precise protection scheduling may be delayed"
        }
        val btConnectSummary = when {
            !a2Enabled -> "Not needed — optional A2 is OFF"
            btConnect -> "Granted / not runtime-gated on this Android version"
            else -> "Not granted — A2 cannot monitor trusted-device separation until granted"
        }
        val rangingSummary = if (Build.VERSION.SDK_INT >= 36) {
            "optional owner-initiated API 36 capability query available; local availability only, with no RANGING permission, peer discovery, or ranging session"
        } else {
            "Not available on this Android version"
        }
        val appReadiness = listOf(
            "Android notifications: ${if (notifications) "Enabled" else "Disabled — protection notifications may be hidden"}",
            "Full-screen alarm intent access: $fullScreenSummary",
            "Exact alarm scheduling: $exactAlarmSummary",
            "Secure screen lock: ${if (secureLock) "Configured" else "Not configured"}",
            "Bluetooth Connect permission: $btConnectSummary",
            "Android ranging capability check: $rangingSummary",
            "Protection monitor setting: ${if (monitorEnabled) "Enabled" else "Disabled"} • Runtime status: ${if (monitorRunning) "Running (last recorded state)" else "Not running (last recorded state)"}",
            "Protection state: $protectionMode",
            "Background guidance family: $family"
        )
        addCard("THIEFALERTSEQuRE READINESS", appReadiness)
        val readinessSignals = buildList {
            add(ReadinessSignal("Protection monitor enabled and last recorded running", monitorEnabled && monitorRunning, 25))
            add(ReadinessSignal("Alarm notifications enabled", notifications, 15))
            add(ReadinessSignal("Secure Android screen lock configured", secureLock, 15))
            if (monitorEnabled) add(ReadinessSignal("Exact alarm scheduling available", exactAlarm, 15))
            if (dashboardEnabled) add(ReadinessSignal("Optional full-screen alarm access available", fullScreenIntent, 10))
            if (a2Enabled) add(ReadinessSignal("Optional Bluetooth Connect permission available", btConnect, 10))
            add(ReadinessSignal("Battery optimization exemption", power.isIgnoringBatteryOptimizations(packageName), 10))
        }
        val readinessScore = ReadinessScorePolicy.calculate(readinessSignals)
        addCard("PROTECTION READINESS SCORE — ${readinessScore.percent}%", listOf(
            "${readinessScore.points} / ${readinessScore.possiblePoints} applicable points",
            if (readinessScore.missing.isEmpty()) "All applicable readiness checks are satisfied."
            else "Needs attention: ${readinessScore.missing.joinToString("; ")}",
            "This is a transparent setup-readiness indicator, not a theft-prevention, security-integrity, or hardware reliability guarantee."
        ))
        if (Build.VERSION.SDK_INT >= 36) {
            root.addView(button("Check Android ranging capabilities (optional)") { requestRangingCapabilityCheck() }, matchWrap())
            root.addView(space(6))
            rangingStatus?.let { addCard("ANDROID RANGING CAPABILITY RESULT", listOf(it)) }
        }
        val rootReport = RootCapabilityInspector.inspectPassive()
        addCard("ROOT / SUPERUSER STATUS", listOf(
            when (rootReport.status) {
                RootCapabilityInspector.RootStatus.NOT_INDICATED -> "PASSIVE CHECK: no common root indicators found. This does not prove the device is unrooted."
                RootCapabilityInspector.RootStatus.ROOT_INDICATORS_FOUND -> "PASSIVE CHECK: possible root indicators found. Active su authorization has not been checked."
                else -> rootReport.status.name
            },
            "Build tags: ${rootReport.buildTags ?: "unknown"}",
            if (rootReport.indicators.isEmpty()) "Indicators: none detected in common locations" else "Indicators:\\n${rootReport.indicators.joinToString("\\n")}",
            "Active root access: NOT CHECKED — tap the button below to execute the read-only su -c id test."
        ))
        rootStatusView = label("ROOT ACCESS: NOT CHECKED", 15f, R.color.tas_warning, true)
        root.addView(rootStatusView, matchWrap())
        root.addView(space(8))
        root.addView(button("Check Root Status (run su -c id)") { confirmRootCheck() }, matchWrap())
        root.addView(space(8))
        addCard("MANUFACTURER BACKGROUND GUIDANCE", listOf(
            AndroidDeviceCompatibility.backgroundSettingsGuide(manufacturer, brand),
            "Manufacturer menus and battery policies vary by model and firmware. Guidance does not silently change system settings."
        ))
        rootButtonRow()

        root.addView(button("Refresh device intelligence") { render() }, matchWrap())
        root.addView(space(8))
        root.addView(button("Export redacted diagnostics report (JSON)") {
            exportDiagnostics.launch("thiefalertsequre-diagnostics.json")
        }, matchWrap())
        root.addView(space(12))
        root.addView(label("Privacy boundary: this report reads public local Android APIs and hardware feature flags. It does not read IMEI, serial number, MAC address, Wi-Fi identifiers, contacts, messages, files, microphone, camera content, or precise location. Results stay on this device.", 12f, R.color.tas_muted, false), matchWrap())
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, scroll)
    }


    private fun writeDiagnosticsReport(uri: android.net.Uri) {
        val report = runCatching {
            val monitorPrefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
            val alarmManager = getSystemService(AlarmManager::class.java)
            val notifications = NotificationManagerCompat.from(this).areNotificationsEnabled()
            val exactAlarm = Build.VERSION.SDK_INT < 31 || runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
            val secureLock = getSystemService(KeyguardManager::class.java).isDeviceSecure
            val state = ProtectionStore(this).read()
            JSONObject().apply {
                put("schemaVersion", 1)
                put("generatedAtEpochMs", System.currentTimeMillis())
                put("appVersion", appVersion())
                put("manufacturer", Build.MANUFACTURER)
                put("brand", Build.BRAND)
                put("model", Build.MODEL)
                put("androidRelease", Build.VERSION.RELEASE)
                put("apiLevel", Build.VERSION.SDK_INT)
                put("targetSdk", applicationInfo.targetSdkVersion)
                put("protectionMode", state.mode.name)
                put("monitorEnabled", SafePreferences.boolean(monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED))
                put("monitorLastRecordedRunning", SafePreferences.boolean(monitorPrefs, ProtectionMonitorService.KEY_RUNNING, false))
                put("notificationsEnabled", notifications)
                put("exactAlarmAvailable", exactAlarm)
                put("secureLockConfigured", secureLock)
                put("backgroundRestricted", if (Build.VERSION.SDK_INT >= 28) {
                    runCatching { getSystemService(ActivityManager::class.java).isBackgroundRestricted }.getOrNull()
                } else JSONObject.NULL)
                put("deviceIdleMode", runCatching { getSystemService(PowerManager::class.java).isDeviceIdleMode }.getOrDefault(false))
                put("appStandbyBucket", if (Build.VERSION.SDK_INT >= 28) {
                    runCatching { standbyBucketLabel(getSystemService(UsageStatsManager::class.java).appStandbyBucket) }.getOrDefault("Unavailable")
                } else "Not exposed before API 28")
                put("recentEventCodes", org.json.JSONArray(LocalTamperHistory.read(this@DeviceIntelligenceActivity, 20).map { it.substringAfter("|").trim().substringBefore("|").take(48) }))
                put("privacyNote", "Redacted local diagnostics only. No location, camera, microphone, credentials, serial, IMEI, MAC, or build fingerprint.")
            }
        }.getOrElse {
            android.widget.Toast.makeText(this, "Could not build diagnostics: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(report.toString(2)) }
                ?: error("Android could not open the selected export destination.")
        }.onSuccess {
            LocalTamperHistory.record(this, "diagnostics_exported", "Owner exported a redacted local diagnostics JSON report")
            android.widget.Toast.makeText(this, "Redacted diagnostics report exported.", android.widget.Toast.LENGTH_LONG).show()
        }.onFailure {
            android.widget.Toast.makeText(this, "Diagnostics export failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun requestRangingCapabilityCheck() {
        if (Build.VERSION.SDK_INT < 36) {
            rangingStatus = "Ranging capability queries require Android 16 (API 36) or newer."
            render()
            return
        }
        queryRangingCapabilities()
    }

    private fun queryRangingCapabilities() {
        if (Build.VERSION.SDK_INT < 36) return
        clearRangingCallback()
        val query = RangingCapabilityQuery(this) { result ->
            rangingStatus = result
            if (!isFinishing && !isDestroyed && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                render()
            }
        }
        stopRangingQuery = { query.stop() }
        rangingStatus = "Querying local ranging capabilities…"
        render()
        query.start()
    }

    private fun clearRangingCallback() {
        stopRangingQuery?.invoke()
        stopRangingQuery = null
    }

    override fun onStop() {
        clearRangingCallback()
        super.onStop()
    }

    private fun confirmRootCheck() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Check root access?")
            .setMessage("This runs the read-only command su -c id. Your Magisk or other superuser manager may display an authorization prompt. The check does not modify files, settings, firmware, or protection behavior. Continue?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Run check") { _, _ ->
                rootStatusView.text = "ROOT ACCESS: CHECKING…"
                rootCheckExecutor.execute {
                    val report = RootCapabilityInspector.verifyRootAuthorization()
                    runOnUiThread {
                        if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@runOnUiThread
                        val status = when (report.status) {
                            RootCapabilityInspector.RootStatus.ROOT_AUTHORIZED -> "ROOT ACCESS GRANTED — UID 0 VERIFIED"
                            RootCapabilityInspector.RootStatus.ROOT_NOT_AUTHORIZED -> "ROOT ACCESS DENIED / NOT GRANTED"
                            RootCapabilityInspector.RootStatus.SU_NOT_AVAILABLE -> if (report.mayBeRooted) "POSSIBLE ROOT — SU NOT AVAILABLE" else "NO ROOT ACCESS DETECTED — SU NOT AVAILABLE (HIDDEN ROOT CANNOT BE RULED OUT)"
                            RootCapabilityInspector.RootStatus.ROOT_INDICATORS_FOUND -> "POSSIBLE ROOT — AUTHORIZATION NOT VERIFIED"
                            RootCapabilityInspector.RootStatus.NOT_INDICATED -> "NO ROOT INDICATORS — NOT PROOF OF NON-ROOT"
                            RootCapabilityInspector.RootStatus.ROOT_CHECK_TIMED_OUT -> "ROOT CHECK TIMED OUT"
                            RootCapabilityInspector.RootStatus.ROOT_CHECK_ERROR -> "ROOT CHECK ERROR"
                        }
                        rootStatusView.text = status
                        val color = if (report.isRootAuthorized) R.color.tas_primary else R.color.tas_warning
                        rootStatusView.setTextColor(getColor(color))
                        val details = buildString {
                            append(report.detail)
                            if (report.indicators.isNotEmpty()) append("\\n\\nPassive indicators:\\n${report.indicators.joinToString("\\n")}")
                            append("\\n\\nBuild tags: ${report.buildTags}")
                            append("\\n\\nThis result describes root authorization available to this app at check time. Root-hiding tools can conceal indicators.")
                        }
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Root status: ${report.status.name}")
                            .setMessage(details)
                            .setPositiveButton("Close", null)
                            .showThemed(this)
                    }
                }
            }.showThemed(this)
    }

    private fun rootButtonRow() {
        root.addView(button("Open Android app settings") {
            AndroidDeviceCompatibility.open(this, AndroidDeviceCompatibility.appInfoIntent(packageName), Intent(android.provider.Settings.ACTION_SETTINGS))
        }, matchWrap())
        root.addView(space(8))
    }

    private fun appVersion(): String = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        "${info.versionName ?: "unknown"} ($code)"
    }.getOrDefault("unknown")

    private fun standbyBucketLabel(bucket: Int): String = when (bucket) {
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "Active"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "Working set"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "Frequent"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "Rare"
        else -> if (Build.VERSION.SDK_INT >= 30 && bucket == UsageStatsManager.STANDBY_BUCKET_RESTRICTED) {
            "Restricted"
        } else {
            "Other / platform-defined ($bucket)"
        }
    }

    private fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown threshold"
        else -> "Unknown"
    }

    private fun formatBytes(value: Long): String {
        if (value < 0) return "Unavailable"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var amount = value.toDouble()
        var index = 0
        while (amount >= 1024 && index < units.lastIndex) { amount /= 1024; index++ }
        return String.format(Locale.US, "%.1f %s", amount, units[index])
    }

    private fun addCard(title: String, lines: List<String>) {
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
            strokeColor = getColor(R.color.tas_surface_variant); setCardBackgroundColor(getColor(R.color.tas_surface))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(14), dp(15), dp(14)) }
        content.addView(label(title, 14f, R.color.tas_primary, true), matchWrap())
        lines.forEach { content.addView(space(7)); content.addView(label(it, 13f, R.color.tas_text, false), matchWrap()) }
        card.addView(content); root.addView(card, matchWrap()); root.addView(space(10))
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(getColor(color)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun button(value: String, action: () -> Unit) = MaterialButton(this).apply { text = value; isAllCaps = false; minHeight = dp(48); setOnClickListener { action() } }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        clearRangingCallback()
        rootCheckExecutor.shutdownNow()
        super.onDestroy()
    }
}
