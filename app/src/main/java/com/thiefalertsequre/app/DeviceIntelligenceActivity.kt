package com.thiefalertsequre.app

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.KeyguardManager
import android.content.Context
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
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.util.Locale

/** Read-only, on-device capability and readiness report. No hardware identifiers are collected. */
class DeviceIntelligenceActivity : FragmentActivity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }

    private fun render() {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(getColor(R.color.tas_background)) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(30)) }
        scroll.addView(root)
        root.addView(button("‹  Back to Settings") { finish() }, matchWrap())
        root.addView(space(18))
        root.addView(label("DEVICE CAPABILITY CENTER", 11f, R.color.tas_primary, true), matchWrap())
        root.addView(space(6))
        root.addView(label("Device Intelligence", 29f, R.color.tas_text, true), matchWrap())
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
            "App-visible storage volume: ${formatBytes(totalStorage)} total • ${formatBytes(freeStorage)} available",
            "CPU logical processors: ${Runtime.getRuntime().availableProcessors()}",
            "Battery: $batteryPercent • ${if (charging || plugged) "External power connected" else "Not charging"}",
            "BatteryManager capacity reading: ${if (capacity in 0..100) "$capacity%" else "Unavailable"}",
            "Battery Saver: ${if (power.isPowerSaveMode) "ON" else "OFF"} • Thermal status: $thermal",
            "App battery optimization exemption: ${if (Build.VERSION.SDK_INT >= 23 && power.isIgnoringBatteryOptimizations(packageName)) "Granted" else "Not granted / not exempt"}"
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
            "$name: ${if (sensorManager.getDefaultSensor(type) != null) "Available" else "Not detected"}"
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
            "$name: ${if (pm.hasSystemFeature(feature)) "Detected" else "Not declared by device"}
        })

        val alarmManager = getSystemService(AlarmManager::class.java)
        val exactAlarm = if (Build.VERSION.SDK_INT >= 31) runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false) else true
        val notifications = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val secureLock = getSystemService(KeyguardManager::class.java).isDeviceSecure
        val btConnect = Build.VERSION.SDK_INT < 31 || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val ranging = Build.VERSION.SDK_INT >= 36 && checkSelfPermission("android.permission.RANGING") == PackageManager.PERMISSION_GRANTED
        val appReadiness = listOf(
            "Android notifications: ${if (notifications) "Enabled" else "Disabled"}",
            "Exact alarm scheduling: ${if (exactAlarm) "Available / permitted" else "Access not granted"}",
            "Secure screen lock: ${if (secureLock) "Configured" else "Not configured"}",
            "Bluetooth Connect permission: ${if (btConnect) "Granted / not runtime-gated on this Android version" else "Not granted"}",
            "Android Ranging permission: ${if (Build.VERSION.SDK_INT < 36) "Not applicable on this OS" else if (ranging) "Granted" else "Not granted"}",
            "Background guidance family: $family"
        )
        addCard("THIEFALERTSEQuRE READINESS", appReadiness)
        val rootReport = RootCapabilityInspector.inspectPassive()
        addCard("ROOT INDICATORS (PASSIVE)", listOf(
            when (rootReport.status) {
                RootCapabilityInspector.RootStatus.NOT_INDICATED -> "No common indicators found; this does not prove the device is unrooted."
                RootCapabilityInspector.RootStatus.ROOT_INDICATORS_FOUND -> "Possible root indicators found; authorization has not been verified."
                else -> rootReport.status.name
            },
            "No root command or authorization prompt is started by this page."
        ))
        addCard("MANUFACTURER BACKGROUND GUIDANCE", listOf(
            AndroidDeviceCompatibility.backgroundSettingsGuide(manufacturer, brand),
            "Manufacturer menus and battery policies vary by model and firmware. Guidance does not silently change system settings."
        ))
        rootButtonRow()

        root.addView(button("Refresh device intelligence") { render() }, matchWrap())
        root.addView(space(12))
        root.addView(label("Privacy boundary: this report reads public local Android APIs and hardware feature flags. It does not read IMEI, serial number, MAC address, Wi-Fi identifiers, contacts, messages, files, microphone, camera content, or precise location. Results stay on this device.", 12f, R.color.tas_muted, false), matchWrap())
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
    }

    private fun rootButtonRow() {
        root.addView(button("Open Android app battery settings") {
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
        }, matchWrap())
        root.addView(space(8))
    }

    private fun appVersion(): String = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        "${info.versionName ?: "unknown"} ($code)"
    }.getOrDefault("unknown")

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
    private fun button(value: String, action: () -> Unit) = MaterialButton(this).apply { text = value; isAllCaps = false; setOnClickListener { action() } }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
