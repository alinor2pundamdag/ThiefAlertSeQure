package com.thiefalertsequre.app

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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

internal data class A2BluetoothEventReceipt(
    val eventEpochMs: Long,
    val eventElapsedRealtimeMs: Long,
    val eventUptimeMs: Long,
    val bootCount: Int
) {
    companion object {
        fun capture(context: Context): A2BluetoothEventReceipt = A2BluetoothEventReceipt(
            eventEpochMs = System.currentTimeMillis(),
            eventElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            eventUptimeMs = SystemClock.uptimeMillis(),
            bootCount = runCatching { ProtectionClock.bootCount(context.applicationContext) }.getOrDefault(-1)
        )
    }
}

internal data class A2BluetoothDisconnectCandidate(
    val pending: A2DisconnectScheduler.Pending,
    val eventUptimeMs: Long
)

internal sealed class A2BluetoothEventResult {
    data class Connected(val address: String) : A2BluetoothEventResult()
    data class Disconnected(val candidate: A2BluetoothDisconnectCandidate) : A2BluetoothEventResult()
    object Ignored : A2BluetoothEventResult()
}

/** One state transition path is shared by the runtime and manifest receivers. */
internal object A2BluetoothAclEventProcessor {
    @Suppress("DEPRECATION", "MissingPermission")
    fun process(
        context: Context,
        intent: Intent,
        receipt: A2BluetoothEventReceipt
    ): A2BluetoothEventResult = A2DisconnectStateLock.withLock {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val monitorEnabled = SafePreferences.boolean(
            prefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val alertEnabled = SafePreferences.boolean(prefs, ProtectionMonitorService.KEY_BT_ALERT_ENABLED, false)
        if (!monitorEnabled || !alertEnabled) return@withLock A2BluetoothEventResult.Ignored

        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            A2BluetoothReceiverController.sync(app)
            LocalTamperHistory.record(
                app, "bluetooth_permission_missing",
                "Trusted Bluetooth separation monitoring paused; grant Bluetooth Connect permission"
            )
            return@withLock A2BluetoothEventResult.Ignored
        }

        val device = runCatching {
            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        }.getOrNull() ?: return@withLock A2BluetoothEventResult.Ignored
        val address = device.address ?: return@withLock A2BluetoothEventResult.Ignored
        if (!TrustedBluetoothRegistry.isTrusted(prefs, address)) return@withLock A2BluetoothEventResult.Ignored

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                TrustedBluetoothRegistry.markConnected(prefs, address, true)
                A2DisconnectScheduler.cancel(app, address)
                LocalTamperHistory.record(app, "trusted_bluetooth_connected", "A registered trusted Bluetooth device connected")
                A2BluetoothEventResult.Connected(address)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val hadBaseline = TrustedBluetoothRegistry.markConnected(prefs, address, false)
                if (!A2DisconnectPolicy.shouldStartGracePeriod(
                        alertEnabled = alertEnabled,
                        trusted = TrustedBluetoothRegistry.isTrusted(prefs, address),
                        hadConnectionBaseline = hadBaseline,
                        protectionMode = ProtectionStore(app).read().mode
                    )
                ) return@withLock A2BluetoothEventResult.Ignored

                val pending = A2DisconnectScheduler.Pending(
                    address = address,
                    eventEpochMs = receipt.eventEpochMs,
                    eventElapsedRealtimeMs = receipt.eventElapsedRealtimeMs,
                    bootCount = receipt.bootCount,
                    monitorRevision = TrustedBluetoothRegistry.revision(prefs),
                    deviceRevision = TrustedBluetoothRegistry.deviceRevision(prefs, address)
                )
                if (A2DisconnectScheduler.schedule(app, pending)) {
                    LocalTamperHistory.record(
                        app, "trusted_bluetooth_disconnected",
                        "Registered trusted device disconnected; durable 8-second grace deadline scheduled"
                    )
                } else {
                    LocalTamperHistory.record(
                        app, "a2_durable_deadline_unavailable",
                        "Trusted-device disconnect was detected, but a durable A2 deadline could not be scheduled; only an active service can provide the in-process timer"
                    )
                }
                A2BluetoothEventResult.Disconnected(A2BluetoothDisconnectCandidate(pending, receipt.eventUptimeMs))
            }
            else -> A2BluetoothEventResult.Ignored
        }
    }
}

/** All ACL sources share one serial queue so a service restart cannot reorder a reconnect. */
internal object A2BluetoothEventQueue {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "A2BluetoothAclEvents").apply { isDaemon = true }
    }

    fun execute(task: () -> Unit) {
        executor.execute(task)
    }
}

internal fun enqueueA2BluetoothEvent(
    context: Context,
    intent: Intent,
    receipt: A2BluetoothEventReceipt,
    source: String,
    onResult: ((A2BluetoothEventResult) -> Unit)? = null,
    onFinished: () -> Unit
) {
    val app = context.applicationContext
    val finished = AtomicBoolean(false)
    fun finishOnce() {
        if (finished.compareAndSet(false, true)) runCatching { onFinished() }
    }
    try {
        A2BluetoothEventQueue.execute {
            try {
                val result = A2BluetoothAclEventProcessor.process(app, intent, receipt)
                onResult?.invoke(result)
            } catch (error: Exception) {
                LocalTamperHistory.record(app, "${source}_error", error.javaClass.simpleName + ": " + error.message)
            } finally {
                finishOnce()
            }
        }
    } catch (error: Exception) {
        finishOnce()
        LocalTamperHistory.record(app, "${source}_queue_failed", error.javaClass.simpleName + ": " + error.message)
    }
}

/**
 * The manifest copy remains enabled while A2 is active so process death does not
 * remove ACL coverage. The runtime receiver may see the same broadcast; both
 * copies enter the same serial queue and the registry baseline makes duplicates idempotent.
 */
/**
 * Manifest entry point for Android-protected ACL broadcasts. Exported intentionally so the
 * privileged Bluetooth stack UID can deliver events while the app process is absent. The
 * controller enables this component only while monitoring, A2, and Bluetooth permission are active.
 */
class A2BluetoothAclReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val receipt = A2BluetoothEventReceipt.capture(context)
        val pendingResult = goAsync()
        enqueueA2BluetoothEvent(
            context = context,
            intent = intent,
            receipt = receipt,
            source = "a2_manifest_receiver",
            onFinished = { pendingResult.finish() }
        )
    }
}
