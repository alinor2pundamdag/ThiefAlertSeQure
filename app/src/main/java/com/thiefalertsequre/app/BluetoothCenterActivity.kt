package com.thiefalertsequre.app

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

/** Dedicated, categorized Bluetooth capability center. Inspection is local and read-only. */
class BluetoothCenterActivity : FragmentActivity() {
    private lateinit var root: LinearLayout
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var enableAfterPermission = false
    private var unregisterRangingCallback: (() -> Unit)? = null
    private val connectPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (enableAfterPermission) enableA2() else showTrustedDevicePicker()
        } else render()
    }
    private val rangingPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && Build.VERSION.SDK_INT >= 36) queryRangingCapabilities() else render()
    }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }

    override fun onResume() { super.onResume(); ThemeRuntime.apply(this, window.decorView) }

    private fun render() {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(getColor(R.color.tas_background)) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(30)) }
        scroll.addView(root)
        root.addView(button("‹  Back") { finish() }, matchWrap())
        root.addView(space(18))
        root.addView(label("DEVICE CAPABILITY CENTER", 11f, R.color.tas_primary, true), matchWrap())
        root.addView(space(6))
        root.addView(label("Bluetooth", 30f, R.color.tas_text, true), matchWrap())
        root.addView(space(8))
        root.addView(label("Local controller inspection, Android API availability, peer requirements, and app implementation status. Opening this page does not scan, pair, stream, or modify anything.", 14f, R.color.tas_muted, false), matchWrap())
        root.addView(space(16))

        val snapshot = BluetoothCapabilityCatalog.snapshot(this)
        val adapterState = when {
            !snapshot.adapterAvailable -> "NO ADAPTER"
            !snapshot.connectPermissionGranted -> "CONNECT PERMISSION REQUIRED"
            snapshot.adapterEnabled == true -> "ADAPTER ON"
            snapshot.adapterEnabled == false -> "ADAPTER OFF"
            else -> "STATUS UNKNOWN"
        }
        addCard("THIS DEVICE", listOf(
            "Android ${Build.VERSION.RELEASE} • API ${Build.VERSION.SDK_INT}",
            "Bluetooth Classic: ${if (snapshot.classicHardware) "Detected" else "Not detected"}",
            "Bluetooth LE: ${if (snapshot.bleHardware) "Detected" else "Not detected"}",
            adapterState,
            "Paired devices: ${snapshot.bondedDeviceCount?.toString() ?: "Not checked"}",
            "Controller probes supported: ${snapshot.supportedCount} • Unknown: ${snapshot.unknownCount}"
        ))
        addTrustedDeviceSection()
        if (!snapshot.connectPermissionGranted && Build.VERSION.SDK_INT >= 31) {
            root.addView(button("Grant Bluetooth Connect permission") { connectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }, matchWrap())
            root.addView(space(8))
        }
        root.addView(button("Open Android Bluetooth settings") { AndroidDeviceCompatibility.open(this, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS)) }, matchWrap())
        root.addView(space(8))
        root.addView(button("Refresh device capabilities") { render() }, matchWrap())
        root.addView(space(18))

        val groups = listOf(
            "Radio and connection" to snapshot.items.filter { it.name in listOf("Bluetooth Classic (BR/EDR)", "Bluetooth Low Energy (BLE)", "Bluetooth adapter", "Paired-device inventory", "Trusted-device ACL separation alert (A2)") },
            "Controller and radio features" to snapshot.items.filter { it.name.startsWith("LE ") || it.name.startsWith("Multiple BLE") || it.name.startsWith("BLE offloaded") },
            "Profiles and data transports" to snapshot.items.filter { it.name in listOf("GATT client", "GATT server", "RFCOMM / Serial Port Profile (SPP)", "LE L2CAP Connection-oriented Channels", "A2DP audio profile", "AVRCP media controls", "HFP / HSP hands-free", "HID keyboard / mouse", "PBAP / MAP / OPP / PAN") },
            "Ranging and proximity" to snapshot.items.filter { it.name.contains("RSSI") || it.name.contains("Sounding") || it.name.contains("Direction Finding") || it.name.startsWith("UWB") || it.name.startsWith("Wi-Fi") },
            "Root and restricted operations" to snapshot.items.filter { it.name.startsWith("Root ") || it.name.startsWith("Bluetooth firmware") || it.name.startsWith("Bluetooth profile data") }
        )
        groups.forEach { (title, items) ->
            if (items.isNotEmpty()) {
                root.addView(sectionTitle(title), matchWrap())
                root.addView(space(8))
                items.forEach { addCapabilityItem(it) }
                root.addView(space(12))
            }
        }
        if (Build.VERSION.SDK_INT >= 36) {
            root.addView(sectionTitle("ADVANCED RANGING CHECK"), matchWrap())
            root.addView(space(8))
            addCard("Platform capability check", listOf(
                "Android Ranging API is available from API 36. A platform query can report BLE Channel Sounding, BLE RSSI ranging, UWB, and Wi-Fi NAN RTT.",
                "This check does not start a ranging session or measure distance. A compatible peer, setup, permissions, and hardware are still required."
            ))
            root.addView(button("Check actual ranging hardware capabilities") { checkRangingPermission() }, matchWrap())
            root.addView(space(14))
        }
        root.addView(sectionTitle("ROOT-AWARE DIAGNOSTICS"), matchWrap())
        root.addView(space(8))
        val passive = RootCapabilityInspector.inspectPassive()
        addCard("Passive root indicators", listOf(
            when (passive.status) {
                RootCapabilityInspector.RootStatus.NOT_INDICATED -> "No common indicators found (not proof of unrooted)"
                RootCapabilityInspector.RootStatus.ROOT_INDICATORS_FOUND -> "Possible root indicators found; authorization not verified"
                else -> passive.status.name
            },
            passive.detail,
            "Root is optional. Diagnostics require an explicit action and remain read-only."
        ))
        root.addView(button("Verify root and collect read-only Bluetooth diagnostics") { confirmRootDiagnostics() }, matchWrap())
        root.addView(space(18))
        root.addView(label("Capability labels are conservative. SUPPORTED means the local device/controller reported support; it does not mean ThiefAlertSeQure has implemented a complete user workflow. Peer-dependent profiles require compatible accessories. Unsupported hardware cannot be enabled by software or root.", 12f, R.color.tas_muted, false), matchWrap())
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, scroll)
    }

    private fun addTrustedDeviceSection() {
        val prefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
        val devices = TrustedBluetoothRegistry.read(prefs)
        val enabled = prefs.getBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
        val baselineCount = devices.count { TrustedBluetoothRegistry.isBaselineConnected(prefs, it.address) }
        val monitorEnabled = prefs.getBoolean(ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
        val monitorRunning = runCatching {
            getSystemService(ActivityManager::class.java).getRunningServices(100)
                .any { it.service.className == ProtectionMonitorService::class.java.name }
        }.getOrDefault(false)
        val protectionMode = ProtectionStore(this).read().mode
        addCard("TRUSTED ACCESSORY SEPARATION ALERT (A2)", listOf(
            "Feature: ${if (enabled) "ON" else "OFF"}",
            "Registered trusted accessories: ${devices.size}",
            "Connection baselines confirmed: $baselineCount / ${devices.size}",
            "Monitor setting: ${if (monitorEnabled) "Enabled" else "Disabled by owner"}",
            "Monitor runtime: ${if (monitorRunning) "Running" else "Not confirmed running"}",
            "Protection state: $protectionMode",
            "Monitoring runs only while A2 is ON and the protection monitor service is running. Each registered accessory needs a fresh connection baseline; disconnect starts an 8-second grace period, and reconnecting that accessory cancels its pending alert.",
            "Unregistering removes only this app's trust. Android pairing remains unchanged."
        ))
        root.addView(button("Add trusted paired device") { requestTrustedDevicePicker() }, matchWrap())
        devices.forEach { device ->
            root.addView(space(6))
            root.addView(button("Unregister: ${device.name}") { confirmUnregister(device) }, matchWrap())
        }
        root.addView(space(7))
        root.addView(button(if (enabled) "Disable trusted-device separation alert" else "Enable trusted-device separation alert") { toggleA2() }, matchWrap())
        root.addView(space(14))
    }

    private fun confirmUnregister(device: TrustedBluetoothDevice) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Unregister trusted accessory?")
            .setMessage("${device.name} will no longer be trusted by A2. It will remain paired in Android Bluetooth settings.")
            .setNegativeButton("Keep registered", null)
            .setPositiveButton("Unregister") { _, _ ->
                val prefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
                if (!TrustedBluetoothRegistry.remove(prefs, device.address)) {
                    showResult(
                        "Could not unregister accessory",
                        "The trusted-device registry could not be updated. A2 is disabled if secure storage failed. Android Bluetooth pairing was not changed."
                    )
                }
                render()
            }
            .showThemed(this)
    }

    private fun requestTrustedDevicePicker() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            enableAfterPermission = false
            connectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else showTrustedDevicePicker()
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun showTrustedDevicePicker() {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            showResult("Bluetooth unavailable", "This device does not expose a Bluetooth adapter.")
            return
        }
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (!enabled) {
            showResult("Turn on Bluetooth", "Turn on Bluetooth and pair your trusted accessory in Android Settings, then return here.")
            AndroidDeviceCompatibility.open(this, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            return
        }
        val devices = runCatching { adapter.bondedDevices.toList().sortedBy { it.name ?: it.address } }.getOrDefault(emptyList())
        if (devices.isEmpty()) {
            showResult("No paired accessories", "Pair a watch, earbuds, or another trusted Bluetooth accessory in Android Settings first.")
            AndroidDeviceCompatibility.open(this, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            return
        }
        val labels = devices.map { it.name?.takeIf { name -> name.isNotBlank() } ?: "Bluetooth accessory" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Select trusted paired accessory")
            .setItems(labels) { _, which ->
                val selected = devices[which]
                val prefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
                if (!TrustedBluetoothRegistry.add(
                        prefs,
                        selected.address,
                        selected.name ?: "Bluetooth accessory"
                    )
                ) {
                    showResult(
                        "Could not register accessory",
                        "Secure local storage could not save this trusted accessory. Check device storage/Android Keystore and try again."
                    )
                }
                render()
            }
            .setNegativeButton("Cancel", null)
            .showThemed(this)
    }

    private fun toggleA2() {
        val prefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)) {
            prefs.edit().putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false).apply()
            TrustedBluetoothRegistry.clearBaselines(prefs)
            render()
            return
        }
        if (TrustedBluetoothRegistry.read(prefs).isEmpty()) {
            requestTrustedDevicePicker()
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            enableAfterPermission = true
            connectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        enableA2()
    }

    private fun enableA2() {
        enableAfterPermission = false
        val prefs = getSharedPreferences(ProtectionMonitorService.PREFS, MODE_PRIVATE)
        if (TrustedBluetoothRegistry.read(prefs).isEmpty()) {
            showTrustedDevicePicker()
            return
        }
        if (!prefs.getBoolean(ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)) {
            showResult("Protection monitor is disabled", "Enable the protection monitor from the main dashboard first. This Bluetooth alert uses the existing user-visible monitor and will not silently turn it back on.")
            return
        }
        if (ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED) {
            showResult("Alarm is active", "Do not change Bluetooth alert configuration while the theft alarm is active.")
            return
        }
        prefs.edit().putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, true).apply()
        TrustedBluetoothRegistry.clearBaselines(prefs)
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, ProtectionMonitorService::class.java))
        }.onFailure {
            prefs.edit().putBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false).apply()
            showResult("Could not start monitoring", "${it.javaClass.simpleName}: ${it.message ?: "Android blocked the foreground service."}")
        }
        render()
    }

    private fun addCapabilityItem(item: BluetoothCapabilityCatalog.Item) {
        val card = MaterialCardView(this).apply {
            radius = dp(14).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
            strokeColor = getColor(R.color.tas_surface_variant); setCardBackgroundColor(getColor(R.color.tas_surface))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        content.addView(label(item.name, 15f, R.color.tas_text, true), matchWrap())
        content.addView(space(5))
        content.addView(label(stateLabel(item.state), 10f, stateColor(item.state), true), matchWrap())
        content.addView(space(5))
        content.addView(label(item.detail, 12f, R.color.tas_muted, false), matchWrap())
        card.addView(content); root.addView(card, matchWrap()); root.addView(space(7))
    }

    private fun stateLabel(state: BluetoothCapabilityCatalog.State): String = when (state) {
        BluetoothCapabilityCatalog.State.SUPPORTED_ON_DEVICE -> "SUPPORTED ON THIS DEVICE"
        BluetoothCapabilityCatalog.State.API_PRESENT_CHECK_REQUIRED -> "API PRESENT • HARDWARE / SESSION CHECK REQUIRED"
        BluetoothCapabilityCatalog.State.PEER_REQUIRED -> "COMPATIBLE PEER OR CONNECTION REQUIRED"
        BluetoothCapabilityCatalog.State.PERMISSION_REQUIRED -> "PERMISSION REQUIRED"
        BluetoothCapabilityCatalog.State.ADAPTER_OFF -> "BLUETOOTH ADAPTER IS OFF"
        BluetoothCapabilityCatalog.State.HARDWARE_REQUIRED -> "SPECIAL CONTROLLER / ANTENNA HARDWARE REQUIRED"
        BluetoothCapabilityCatalog.State.ROOT_ONLY -> "OWNER-AUTHORIZED ROOT DIAGNOSTICS ONLY"
        BluetoothCapabilityCatalog.State.NOT_SUPPORTED -> "NOT SUPPORTED OR NOT DETECTED"
        BluetoothCapabilityCatalog.State.NOT_IMPLEMENTED -> "NOT IMPLEMENTED BY THIS APP"
        BluetoothCapabilityCatalog.State.UNKNOWN -> "UNKNOWN • COULD NOT VERIFY"
    }
    private fun stateColor(state: BluetoothCapabilityCatalog.State): Int = when (state) {
        BluetoothCapabilityCatalog.State.SUPPORTED_ON_DEVICE -> R.color.tas_primary
        BluetoothCapabilityCatalog.State.PERMISSION_REQUIRED, BluetoothCapabilityCatalog.State.ADAPTER_OFF, BluetoothCapabilityCatalog.State.UNKNOWN -> R.color.tas_warning
        else -> R.color.tas_muted
    }

    private fun checkRangingPermission() {
        if (Build.VERSION.SDK_INT >= 36) {
            if (checkSelfPermission("android.permission.RANGING") != PackageManager.PERMISSION_GRANTED) rangingPermission.launch("android.permission.RANGING")
            else queryRangingCapabilities()
        }
    }

    @androidx.annotation.RequiresApi(36)
    private fun queryRangingCapabilities() {
        val manager = getSystemService(android.ranging.RangingManager::class.java)
        if (manager == null) { showResult("Ranging service unavailable", "Android did not expose a RangingManager on this device."); return }
        val callback = object : android.ranging.RangingManager.RangingCapabilitiesCallback {
            override fun onRangingCapabilities(capabilities: android.ranging.RangingCapabilities) {
                runCatching { manager.unregisterCapabilitiesCallback(this) }
                unregisterRangingCallback = null
                val availability = capabilities.technologyAvailability
                fun status(id: Int): String = when (availability[id]) {
                    android.ranging.RangingCapabilities.ENABLED -> "SUPPORTED AND ENABLED"
                    android.ranging.RangingCapabilities.NOT_SUPPORTED -> "NOT SUPPORTED"
                    android.ranging.RangingCapabilities.DISABLED_USER -> "DISABLED BY USER"
                    android.ranging.RangingCapabilities.DISABLED_REGULATORY -> "DISABLED BY REGULATORY POLICY"
                    android.ranging.RangingCapabilities.DISABLED_USER_RESTRICTIONS -> "DISABLED BY DEVICE POLICY"
                    null -> "NOT REPORTED"
                    else -> "UNKNOWN"
                }
                val result = buildString {
                    append("Bluetooth Channel Sounding: ${status(android.ranging.RangingManager.BLE_CS)}\n\n")
                    append("Bluetooth RSSI ranging: ${status(android.ranging.RangingManager.BLE_RSSI)}\n\n")
                    append("UWB (not Bluetooth): ${status(android.ranging.RangingManager.UWB)}\n\n")
                    append("Wi-Fi NAN RTT (not Bluetooth): ${status(android.ranging.RangingManager.WIFI_NAN_RTT)}")
                    if (Build.VERSION.SDK_INT >= 37) append("\n\nWi-Fi Proximity Detection (not Bluetooth): ${status(5)}")
                    append("\n\nThis is a local capability query only. A compatible peer and supported ranging session are still required; no distance or direction is measured.")
                }
                runOnUiThread { if (!isFinishing && !isDestroyed) showResult("Ranging capability results", result) }
            }
        }
        unregisterRangingCallback = { runCatching { manager.unregisterCapabilitiesCallback(callback) }; Unit }
        runCatching { manager.registerCapabilitiesCallback(mainExecutor, callback) }
            .onFailure {
                unregisterRangingCallback = null
                showResult("Ranging check unavailable", "${it.javaClass.simpleName}: ${it.message ?: "No platform capability data."}")
            }
    }

    private fun confirmRootDiagnostics() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Run read-only root diagnostics?")
            .setMessage("This runs 'su -c id' only after your confirmation. Your root manager may ask for approval. If authorized, the app reads Bluetooth service and system-property diagnostics. It will not change firmware, pairing, radio power, or system files.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                ioExecutor.execute {
                    val report = RootCapabilityInspector.verifyRootAuthorization()
                    val diagnostics = if (report.isRootAuthorized) RootCapabilityInspector.collectBluetoothDiagnostics() else report.detail
                    val output = buildString {
                        append("Root status: ${report.status}\n${report.detail}")
                        if (report.indicators.isNotEmpty()) append("\n\nIndicators:\n${report.indicators.joinToString("\n")}")
                        append("\n\nREAD-ONLY BLUETOOTH DIAGNOSTICS\n$diagnostics")
                    }.take(24000)
                    runOnUiThread { if (!isFinishing && !isDestroyed) showResult("Device diagnostics", output) }
                }
            }.showThemed(this)
    }

    private fun showResult(title: String, message: String) {
        val body = TextView(this).apply { text = message; textSize = 13f; setTextColor(getColor(R.color.tas_text)); setPadding(dp(18), dp(12), dp(18), dp(12)) }
        val scroll = ScrollView(this).apply { addView(body) }
        MaterialAlertDialogBuilder(this).setTitle(title).setView(scroll).setPositiveButton("Close", null).showThemed(this)
    }

    private fun addCard(title: String, lines: List<String>) {
        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
            strokeColor = getColor(R.color.tas_surface_variant); setCardBackgroundColor(getColor(R.color.tas_surface))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(14), dp(15), dp(14)) }
        content.addView(label(title, 14f, R.color.tas_primary, true), matchWrap())
        lines.forEach { content.addView(space(6)); content.addView(label(it, 13f, R.color.tas_text, false), matchWrap()) }
        card.addView(content); root.addView(card, matchWrap()); root.addView(space(10))
    }
    private fun sectionTitle(value: String) = label(value.uppercase(), 11f, R.color.tas_primary, true)
    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(getColor(color)); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun button(value: String, action: () -> Unit) = MaterialButton(this).apply { text = value; isAllCaps = false; minHeight = dp(48); setOnClickListener { action() } }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        unregisterRangingCallback?.invoke()
        unregisterRangingCallback = null
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
