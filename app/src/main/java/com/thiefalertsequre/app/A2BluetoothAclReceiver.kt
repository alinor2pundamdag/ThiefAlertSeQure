package com.thiefalertsequre.app

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

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
        val enabled = monitorEnabled && alertEnabled && permissionGranted
        return runCatching {
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
}

internal object A2BluetoothReceiverQueuePolicy {
    const val MAX_PENDING_EVENTS = 16
    const val BACKPRESSURE_TIMEOUT_MS = 1_000L

    fun createExecutor(
        queueCapacity: Int = MAX_PENDING_EVENTS,
        backpressureTimeoutMs: Long = BACKPRESSURE_TIMEOUT_MS
    ): java.util.concurrent.ThreadPoolExecutor =
        BoundedSerialExecutor.create(
            queueCapacity = queueCapacity,
            backpressureTimeoutMs = backpressureTimeoutMs,
            threadName = "a2-bluetooth-acl-events"
        )
}

/** Process-lifetime queue shared by manifest receiver instances. */
internal object A2BluetoothReceiverQueue {
    val executor = A2BluetoothReceiverQueuePolicy.createExecutor()
}

/**
 * Receives Android's manifest-exempt ACL broadcasts while the app process is absent.
 * When the foreground service's runtime receiver is ready, that service owns the event.
 */
class A2BluetoothAclReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION", "MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (ProtectionMonitorService.isBluetoothReceiverRegistered) return
        // Capture at broadcast receipt, before async scheduling or trust-store I/O.
        val eventEpochMs = System.currentTimeMillis()
        val eventElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
        val eventBootCount = ProtectionClock.bootCount(context)
        val pendingResult = goAsync()
        try {
            A2BluetoothReceiverQueue.executor.execute {
                try {
                val app = context.applicationContext
                val prefs = app.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
                if (!SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED) ||
                    !SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
                ) return@execute
                if (Build.VERSION.SDK_INT >= 31 &&
                    ContextCompat.checkSelfPermission(app, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    A2BluetoothReceiverController.sync(app)
                    LocalTamperHistory.record(app, "bluetooth_permission_missing", "Trusted Bluetooth separation monitoring paused; grant Bluetooth Connect permission")
                    return@execute
                }
                val device = runCatching {
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                }.getOrNull() ?: return@execute
                val address = device.address ?: return@execute
                if (!TrustedBluetoothRegistry.isTrusted(prefs, address)) return@execute

                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        TrustedBluetoothRegistry.markConnected(prefs, address, true)
                        A2DisconnectScheduler.cancel(app, address)
                        LocalTamperHistory.record(app, "trusted_bluetooth_connected", "A registered trusted Bluetooth device connected while the monitor process was absent")
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val hadBaseline = TrustedBluetoothRegistry.markConnected(prefs, address, false)
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
        } catch (error: Exception) {
            pendingResult.finish()
            LocalTamperHistory.record(
                context, "a2_manifest_receiver_queue_failed",
                error.javaClass.simpleName + ": " + error.message
            )
            ProtectionMonitorRecoveryNotice.show(
                context,
                "A trusted Bluetooth event could not be queued. Open the app to review protection monitoring."
            )
        }
    }
}
