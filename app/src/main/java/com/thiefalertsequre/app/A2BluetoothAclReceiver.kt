package com.thiefalertsequre.app

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Dynamically enables the manifest ACL listener only while the owner enabled A2 monitoring. */
internal object A2BluetoothReceiverController {
    fun sync(context: Context): Boolean {
        val prefs = context.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val monitorEnabled = SafePreferences.boolean(
            prefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val alertEnabled = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
        val permissionGranted = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        val baselineReady = SafePreferences.boolean(
            prefs, ProtectionMonitorService.KEY_A2_BOOT_BASELINE_READY, false
        )
        val enabled = monitorEnabled && alertEnabled && permissionGranted && baselineReady
        return setComponentEnabled(context, enabled)
    }

    /** Clears old connection evidence before A2 can listen after boot or upgrade. */
    fun ensureBaselineReady(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        if (SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_A2_BOOT_BASELINE_READY, false)) return true
        val cleared = runCatching {
            if (!A2DisconnectScheduler.clearAll(app)) return@runCatching false
            TrustedBluetoothRegistry.clearBaselines(prefs)
            true
        }.getOrDefault(false)
        if (!cleared) {
            setComponentEnabled(app, false)
            LocalTamperHistory.record(app, "a2_boot_baseline_reset_failed", "A2 remains disabled because old Bluetooth connection baselines could not be cleared")
            return false
        }
        val saved = commitWithOneRetry {
            prefs.edit().putBoolean(ProtectionMonitorService.KEY_A2_BOOT_BASELINE_READY, true)
        }
        if (!saved) {
            setComponentEnabled(app, false)
            LocalTamperHistory.record(app, "a2_boot_baseline_marker_failed", "A2 remains disabled because boot-baseline readiness could not be committed")
        }
        return saved
    }

    /**
     * During interrupted configuration recovery, invalidate old-boot A2 state
     * but leave the manifest receiver disabled. Normal monitor startup retries
     * the reset and enables it only after settings recovery succeeds.
     */
    fun deferBaselineResetForBoot(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val markedNotReady = commitWithOneRetry {
            prefs.edit().putBoolean(ProtectionMonitorService.KEY_A2_BOOT_BASELINE_READY, false)
        }
        val receiverDisabled = setComponentEnabled(app, false)
        if (!markedNotReady || !receiverDisabled) {
            LocalTamperHistory.record(
                app, "a2_boot_baseline_marker_failed",
                "A2 remains disabled while settings recovery is pending; readiness marker or manifest receiver disable could not be confirmed"
            )
            return false
        }
        val cleared = runCatching {
            if (!A2DisconnectScheduler.clearAll(app)) return@runCatching false
            TrustedBluetoothRegistry.clearBaselines(prefs)
            true
        }.getOrDefault(false)
        if (!cleared) {
            LocalTamperHistory.record(app, "a2_boot_baseline_reset_failed", "A2 remains disabled until interrupted settings recovery and old-boot baseline cleanup complete")
            return false
        }
        return true
    }

    /** A boot always invalidates prior connected-device evidence before re-enabling A2. */
    fun resetBaselineForBoot(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        // Persist "not ready" before touching receiver state. A racing ACL
        // broadcast will then fail closed even if Android has not disabled the
        // manifest component yet.
        val markedNotReady = commitWithOneRetry {
            prefs.edit().putBoolean(ProtectionMonitorService.KEY_A2_BOOT_BASELINE_READY, false)
        }
        val receiverDisabled = setComponentEnabled(app, false)
        if (!markedNotReady || !receiverDisabled) {
            LocalTamperHistory.record(
                app, "a2_boot_baseline_marker_failed",
                "A2 remains disabled because boot readiness or manifest receiver disable could not be confirmed"
            )
            return false
        }
        val cleared = runCatching {
            if (!A2DisconnectScheduler.clearAll(app)) return@runCatching false
            TrustedBluetoothRegistry.clearBaselines(prefs)
            true
        }.getOrDefault(false)
        if (!cleared) {
            LocalTamperHistory.record(app, "a2_boot_baseline_reset_failed", "A2 receiver stays disabled until old connection baselines can be cleared")
            return false
        }
        val markedReady = commitWithOneRetry {
            prefs.edit().putBoolean(ProtectionMonitorService.KEY_A2_BOOT_BASELINE_READY, true)
        }
        if (!markedReady) {
            LocalTamperHistory.record(app, "a2_boot_baseline_marker_failed", "A2 receiver stays disabled because boot-baseline readiness could not be committed")
            return false
        }
        return sync(app)
    }

    private fun commitWithOneRetry(editorFactory: () -> android.content.SharedPreferences.Editor): Boolean {
        val first = runCatching { editorFactory().commit() }.getOrDefault(false)
        return first || runCatching { editorFactory().commit() }.getOrDefault(false)
    }

    private fun setComponentEnabled(context: Context, enabled: Boolean): Boolean = runCatching {
        val component = ComponentName(context, A2BluetoothAclReceiver::class.java)
        val expectedState = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(
            component, expectedState, PackageManager.DONT_KILL_APP
        )
        context.packageManager.getComponentEnabledSetting(component) == expectedState
    }.getOrElse {
        LocalTamperHistory.record(context, "a2_manifest_receiver_sync_failed", it.javaClass.simpleName + ": " + it.message)
        false
    }
}

/**
 * Receives Android's manifest-exempt ACL broadcasts while the app process is absent.
 * When the foreground service's runtime receiver is ready, that service owns the event.
 */
class A2BluetoothAclReceiver : BroadcastReceiver() {
    private companion object {
        // A bounded worker pool prevents a burst of ACL broadcasts from creating
        // unbounded threads while retaining enough capacity for all trusted devices.
        val receiverExecutor = ThreadPoolExecutor(
            2, 2, 30L, TimeUnit.SECONDS,
            ArrayBlockingQueue(64),
            ThreadPoolExecutor.AbortPolicy()
        )
    }

    @Suppress("DEPRECATION", "MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (ProtectionMonitorService.isBluetoothReceiverRegistered) return
        // Capture at broadcast receipt, before async scheduling or trust-store I/O.
        val eventEpochMs = System.currentTimeMillis()
        val eventElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
        val eventBootCount = ProtectionClock.bootCount(context)
        val pendingResult = goAsync()
        val task = Runnable {
            try {
                val app = context.applicationContext
                if (!ConfigurationBackup.recoverPendingRestore(app)) {
                    LocalTamperHistory.record(
                        app, "configuration_restore_recovery_pending",
                        "Trusted Bluetooth event was ignored until interrupted settings recovery completes"
                    )
                    return@Thread
                }
                val prefs = app.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
                if (!SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_A2_BOOT_BASELINE_READY, false)) {
                    A2BluetoothReceiverController.sync(app)
                    LocalTamperHistory.record(
                        app, "a2_baseline_not_ready",
                        "Manifest Bluetooth event ignored because a verified boot baseline is not available"
                    )
                    return@Thread
                }
                if (!SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED) ||
                    !SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
                ) return@Thread
                if (Build.VERSION.SDK_INT >= 31 &&
                    ContextCompat.checkSelfPermission(app, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    A2BluetoothReceiverController.sync(app)
                    LocalTamperHistory.record(app, "bluetooth_permission_missing", "Trusted Bluetooth separation monitoring paused; grant Bluetooth Connect permission")
                    return@Thread
                }
                val device = runCatching {
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                }.getOrNull() ?: return@Thread
                val address = device.address ?: return@Thread
                if (!TrustedBluetoothRegistry.isTrusted(prefs, address)) return@Thread

                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val update = TrustedBluetoothRegistry.markConnected(prefs, address, true)
                        A2DisconnectScheduler.cancel(app, address)
                        if (!update.persisted) {
                            LocalTamperHistory.record(app, "a2_baseline_update_failed", "Bluetooth connected, but its encrypted connection baseline could not be committed; disconnect detection may require reconnecting the accessory")
                        }
                        LocalTamperHistory.record(app, "trusted_bluetooth_connected", "A registered trusted Bluetooth device connected while the monitor process was absent")
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val update = TrustedBluetoothRegistry.markConnected(prefs, address, false)
                        if (!update.persisted) {
                            LocalTamperHistory.record(app, "a2_baseline_update_failed", "Bluetooth disconnected, but its encrypted connection baseline could not be committed; no unverified separation alarm was scheduled")
                            return@Thread
                        }
                        val hadBaseline = update.hadBaseline
                        if (A2DisconnectPolicy.shouldStartGracePeriod(
                                alertEnabled = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false),
                                trusted = TrustedBluetoothRegistry.isTrusted(prefs, address),
                                hadConnectionBaseline = hadBaseline,
                                protectionMode = ProtectionStore(app).read().mode
                            )
                        ) {
                            val pending = A2DisconnectScheduler.Pending(
                                address = address,
                                eventEpochMs = eventEpochMs,
                                eventElapsedRealtimeMs = eventElapsedRealtimeMs,
                                bootCount = eventBootCount,
                                monitorRevision = TrustedBluetoothRegistry.revision(prefs),
                                deviceRevision = TrustedBluetoothRegistry.deviceRevision(prefs, address)
                            )
                            if (A2DisconnectScheduler.schedule(app, pending)) {
                                LocalTamperHistory.record(app, "trusted_bluetooth_disconnected", "Registered trusted device disconnected while the monitor process was absent; durable 8-second grace deadline scheduled")
                            } else {
                                LocalTamperHistory.record(app, "a2_durable_deadline_unavailable", "A2 disconnect was detected without a foreground service, but the durable grace deadline could not be scheduled")
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                LocalTamperHistory.record(
                    context, "a2_manifest_receiver_error",
                    error.javaClass.simpleName + ": " + error.message
                )
            } finally {
                pendingResult.finish()
            }
        }
        try {
            receiverExecutor.execute(task)
        } catch (_: RejectedExecutionException) {
            pendingResult.finish()
            LocalTamperHistory.recordAsync(
                context.applicationContext,
                "a2_receiver_queue_full",
                "A trusted Bluetooth ACL event was rejected because the bounded receiver queue is full"
            )
        }
    }
}
