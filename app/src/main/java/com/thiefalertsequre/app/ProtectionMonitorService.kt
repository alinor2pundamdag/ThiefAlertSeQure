package com.thiefalertsequre.app

import android.app.Notification
import android.bluetooth.BluetoothDevice
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-visible, persistent charger monitor. It remains active while DISARMED,
 * ARMED or COUNTDOWN so plug/unplug transitions are detected without an Activity.
 * Motion sensing is still registered only while protection is ARMED.
 */
class ProtectionMonitorService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private val gravity = FloatArray(3)
    private var gravityInitialized = false
    private var samplesAboveThreshold = 0
    private var lastMotionTriggerElapsed = 0L
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var sensorRegistered = false
    private var receiverRegistered = false
    private var powerReceiverRegistered = false
    private var bluetoothReceiverRegistered = false
    private var stopRetryRunnable: Runnable? = null
    private val powerTransitionReceiver = PowerTransitionReceiver()
    private val pendingBluetoothRunnables = mutableMapOf<String, Runnable>()
    private val bluetoothSeparationReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION", "MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            // Anchor the grace deadline to broadcast receipt, not the time spent
            // validating encrypted trust state and protection guards.
            if (!SafePreferences.boolean(prefs, KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)) return
            val eventUptimeMs = android.os.SystemClock.uptimeMillis()
            val eventElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime()
            val eventEpochMs = System.currentTimeMillis()
            if (!SafePreferences.boolean(prefs, KEY_BT_ALERT_ENABLED, false)) return
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                A2BluetoothReceiverController.sync(context)
                LocalTamperHistory.record(context, "bluetooth_permission_missing", "Trusted Bluetooth disconnect monitoring paused; grant Bluetooth Connect permission")
                return
            }
            val device = runCatching { intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) }.getOrNull() ?: return
            val address = device.address ?: return
            if (!TrustedBluetoothRegistry.isTrusted(prefs, address)) return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    TrustedBluetoothRegistry.markConnected(prefs, address, true)
                    pendingBluetoothRunnables.remove(address)?.let(handler::removeCallbacks)
                    A2DisconnectScheduler.cancel(context, address)
                    LocalTamperHistory.record(context, "trusted_bluetooth_connected", "A registered trusted Bluetooth device connected")
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val hadConnectionBaseline = TrustedBluetoothRegistry.markConnected(prefs, address, false)
                    if (A2DisconnectPolicy.shouldStartGracePeriod(
                            alertEnabled = SafePreferences.boolean(prefs, KEY_BT_ALERT_ENABLED, false),
                            trusted = TrustedBluetoothRegistry.isTrusted(prefs, address),
                            hadConnectionBaseline = hadConnectionBaseline,
                            protectionMode = ProtectionStore(context).read().mode
                        )
                    ) {
                        pendingBluetoothRunnables.remove(address)?.let(handler::removeCallbacks)
                        val monitorRevision = TrustedBluetoothRegistry.revision(prefs)
                        val deviceRevision = TrustedBluetoothRegistry.deviceRevision(prefs, address)
                        val runnable = Runnable {
                            pendingBluetoothRunnables.remove(address)
                            val stillTrusted = TrustedBluetoothRegistry.isTrusted(prefs, address)
                            val pendingDisconnectCurrent = A2DisconnectPolicy.isPendingDisconnectCurrent(
                                monitorRevision,
                                TrustedBluetoothRegistry.revision(prefs),
                                deviceRevision,
                                TrustedBluetoothRegistry.deviceRevision(prefs, address)
                            )
                            val stillDisconnected = !TrustedBluetoothRegistry.isBaselineConnected(prefs, address)
                            if (A2DisconnectPolicy.shouldTriggerAlarm(
                                    alertEnabled = SafePreferences.boolean(prefs, KEY_BT_ALERT_ENABLED, false),
                                    trusted = stillTrusted,
                                    pendingDisconnectCurrent = pendingDisconnectCurrent,
                                    stillDisconnected = stillDisconnected,
                                    protectionMode = ProtectionStore(this@ProtectionMonitorService).read().mode
                                )
                            ) {
                                val now = System.currentTimeMillis()
                                var transitioned = false
                                val update = ProtectionStore(this@ProtectionMonitorService).updatePersisted { current ->
                                    val next = ProtectionEngine.triggerFromArmed(
                                        current, now, ProtectionClock.bootCount(this@ProtectionMonitorService)
                                    )
                                    if (current.mode == ProtectionMode.ARMED && next.mode == ProtectionMode.ALARM_TRIGGERED) {
                                        transitioned = true
                                    }
                                    next
                                }
                                val next = update.state
                                if (transitioned && update.persisted && next.mode == ProtectionMode.ALARM_TRIGGERED) {
                                    LocalTamperHistory.record(this@ProtectionMonitorService, "trusted_bluetooth_separation_alarm", "A registered trusted Bluetooth device remained disconnected beyond grace period")
                                    unregisterMotionSensor()
                                    startTheftAlarm()
                                }
                            }
                        }
                        pendingBluetoothRunnables[address] = runnable
                        val durableScheduled = A2DisconnectScheduler.schedule(
                            this@ProtectionMonitorService,
                            A2DisconnectScheduler.Pending(
                                address = address,
                                eventEpochMs = eventEpochMs,
                                eventElapsedRealtimeMs = eventElapsedRealtimeMs,
                                bootCount = ProtectionClock.bootCount(this@ProtectionMonitorService),
                                monitorRevision = monitorRevision,
                                deviceRevision = deviceRevision
                            )
                        )
                        if (!durableScheduled) {
                            LocalTamperHistory.record(context, "a2_durable_deadline_unavailable", "Using the in-process grace timer because the durable A2 deadline could not be scheduled")
                        }
                        handler.postAtTime(
                            runnable,
                            A2DisconnectPolicy.graceDeadlineUptimeMs(eventUptimeMs, BLUETOOTH_DISCONNECT_GRACE_MS)
                        )
                        LocalTamperHistory.record(context, "trusted_bluetooth_disconnected", "Registered trusted device disconnected; 8-second grace period started")
                    }
                }
            }
        }
    }
    private var alertPlayer: MediaPlayer? = null
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val countdownValidationRunning = AtomicBoolean(false)
    @Volatile private var countdownExpected: ProtectionState? = null
    private val countdownRunnable = Runnable { countdownExpected?.let(::evaluateCountdown) }
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> handleBatteryChanged(intent)
                Intent.ACTION_SHUTDOWN -> {
                    // ACTION_SHUTDOWN is delivered only to dynamically registered receivers
                    // on Android 9+. This records an orderly system shutdown observation;
                    // it cannot block shutdown or detect hardware-forced power-off.
                    val mode = ProtectionStore(this@ProtectionMonitorService).read().mode
                    val userspaceOnly = intent.getBooleanExtra(
                        "android.intent.extra.SHUTDOWN_USERSPACE_ONLY", false
                    )
                    LocalTamperHistory.record(
                        this@ProtectionMonitorService,
                        "orderly_shutdown_observed",
                        "System delivered ACTION_SHUTDOWN; protection mode=$mode; userspaceOnly=$userspaceOnly"
                    )
                }
                ACTION_REFRESH -> refreshProtectionState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_STOP &&
            SafePreferences.boolean(prefs, KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
        ) {
            stopRetryRunnable?.let(handler::removeCallbacks)
            stopRetryRunnable = null
        }
        val stopPreferenceAlreadyCommitted =
            intent?.getBooleanExtra(EXTRA_STOP_PREFERENCE_COMMITTED, false) == true
        val monitorEnabled = SafePreferences.boolean(
            prefs, KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        val applyStopRequest = ProtectionMonitorPolicy.shouldApplyStopRequest(
            stopPreferenceAlreadyCommitted, monitorEnabled
        )
        if (intent?.action == ACTION_STOP && !applyStopRequest) {
            LocalTamperHistory.record(
                this, "stale_monitor_stop_ignored",
                "A newer owner re-enable setting superseded an older monitor stop request"
            )
        }
        if (intent?.action == ACTION_STOP && applyStopRequest) {
            if (!stopPreferenceAlreadyCommitted && !prefs.edit().putBoolean(KEY_ENABLED, false).commit()) {
                LocalTamperHistory.record(
                    this, "monitor_disable_preference_persist_failed",
                    "Android did not confirm that the monitor OFF preference was saved; stop request will be retried"
                )
                ProtectionMonitorRecoveryNotice.show(this, "The monitor could not confirm its OFF setting. Retrying without changing the saved protection state.")
                scheduleStopRetry(intent, flags, startId)
                return START_STICKY
            }

            val store = ProtectionStore(this)
            val current = store.read()
            val update = if (current.mode == ProtectionMode.ALARM_TRIGGERED) {
                ProtectionStoreUpdateResult(current, true)
            } else {
                store.updatePersisted {
                    ProtectionEngine.disableMonitoring(it, System.currentTimeMillis())
                }
            }
            if (!update.persisted) {
                LocalTamperHistory.record(
                    this, "monitor_disable_state_persist_failed",
                    "Protection state was not durably disarmed; the existing deadline and alarm state remain authoritative"
                )
                ProtectionMonitorRecoveryNotice.show(this, "Protection could not be safely disabled because its state was not saved. Retrying; any existing countdown remains active.")
                if (update.state.mode == ProtectionMode.COUNTDOWN) AlarmScheduler.scheduleRetry(this, 1_000L)
                scheduleStopRetry(intent, flags, startId)
                return START_STICKY
            }

            if (update.state.mode != ProtectionMode.ALARM_TRIGGERED) {
                AlarmScheduler.cancel(this)
                PowerTransitionCoordinator.resetBaselineToCurrentPower(this, "Protection monitor disabled by owner")
            }
            val deadlinesCleared = A2DisconnectScheduler.clearAll(this)
            val baselinesCleared = runCatching { TrustedBluetoothRegistry.clearBaselines(prefs); true }.getOrDefault(false)
            A2BluetoothReceiverController.sync(this)
            if (!deadlinesCleared || !baselinesCleared) {
                LocalTamperHistory.record(
                    this, "monitor_disable_cleanup_incomplete",
                    "Monitor is OFF, but trusted-device deadline or baseline cleanup could not be confirmed"
                )
                ProtectionMonitorRecoveryNotice.show(this, "Monitoring is OFF, but trusted-device cleanup needs attention. Reopen Bluetooth settings to review it.")
            }
            stopSelf()
            return START_NOT_STICKY
        }
        val state = ProtectionStore(this).read()
        if (state.mode == ProtectionMode.ALARM_TRIGGERED) {
            runCatching {
                ContextCompat.startForegroundService(
                    this, Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_START)
                )
            }.onFailure {
                LocalTamperHistory.record(this, "latched_alarm_restore_failed", it.javaClass.simpleName + ": " + it.message)
                AlarmRecoveryCoordinator.onStartFailure(
                    this, "The latched alarm needs the app opened to resume playback"
                )
            }
            stopSelf()
            return START_NOT_STICKY
        }
        val enabled = SafePreferences.boolean(prefs, KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
        if (!ProtectionMonitorPolicy.shouldRun(enabled, state.mode)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            isRunning = true
            prefs.edit().putBoolean(KEY_RUNNING, true).apply()
            ProtectionMonitorRecoveryNotice.cancel(this)
        } catch (error: Exception) {
            LocalTamperHistory.record(this, "monitor_fgs_start_failed", error.javaClass.simpleName + ": " + error.message)
            isRunning = false
            prefs.edit().putBoolean(KEY_RUNNING, false).apply()
            ProtectionMonitorRecoveryNotice.show(this, "Android refused to start the protection monitor foreground service")
            stopSelf()
            return START_NOT_STICKY
        }

        registerSystemReceiver()
        registerPowerTransitionReceiver()
        registerBluetoothSeparationReceiver()
        PowerTransitionCoordinator.initializeBaseline(this)
        PowerTransitionReceiver.enqueuePendingRecovery(this)
        reconcileStablePowerState()
        syncMotionSensor()
        LocalTamperHistory.record(this, "monitor_started", "Persistent foreground charger monitor active; motion sensing is armed-state only")
        return START_STICKY
    }

    private fun scheduleStopRetry(intent: Intent?, flags: Int, startId: Int) {
        stopRetryRunnable?.let(handler::removeCallbacks)
        val retry = Runnable {
            stopRetryRunnable = null
            // A newer owner action that re-enabled monitoring supersedes this retry.
            if (!SafePreferences.boolean(prefs, KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)) {
                onStartCommand(intent, flags, startId)
            }
        }
        stopRetryRunnable = retry
        handler.postDelayed(retry, POWER_RETRY_MS)
    }

    private fun registerSystemReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SHUTDOWN)
            addAction(ACTION_REFRESH)
        }
        ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    /**
     * Power connected/disconnected are implicit broadcasts. Register them at runtime
     * from this persistent foreground service; a manifest-only receiver is not
     * reliable for apps targeting Android 8+.
     */
    private fun registerPowerTransitionReceiver() {
        if (powerReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // These actions are protected system broadcasts; third-party apps cannot send them.
        ContextCompat.registerReceiver(
            this, powerTransitionReceiver, filter, ContextCompat.RECEIVER_EXPORTED
        )
        powerReceiverRegistered = true
        LocalTamperHistory.record(this, "power_event_receiver_registered", "Runtime charger connect/disconnect receiver active")
    }

    private fun registerBluetoothSeparationReceiver() {
        if (bluetoothReceiverRegistered) return
        if (!SafePreferences.boolean(prefs, KEY_BT_ALERT_ENABLED, false)) {
            A2BluetoothReceiverController.sync(this)
            return
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            A2BluetoothReceiverController.sync(this)
            LocalTamperHistory.record(this, "bluetooth_permission_missing", "Trusted Bluetooth separation alert cannot monitor without Bluetooth Connect permission")
            return
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, bluetoothSeparationReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        bluetoothReceiverRegistered = true
        isBluetoothReceiverRegistered = true
        A2BluetoothReceiverController.sync(this)
        LocalTamperHistory.record(this, "trusted_bluetooth_receiver_registered", "Trusted Bluetooth separation monitoring active; manifest receiver covers process-death ACL events")
    }

    private fun reconcileStablePowerState() {
        // Service startup/restart cannot synthesize a physical unplug transition.
        PowerTransitionCoordinator.initializeBaseline(this)
        if (ExternalPowerState.readConnected(this) == true) {
            prefs.edit().putBoolean(KEY_BATTERY_ALERT_FIRED, false)
                .putInt(KEY_LAST_BATTERY_LEVEL, -1).apply()
        }
        val state = ProtectionStore(this).read()
        if (state.mode == ProtectionMode.ARMED) syncMotionSensor() else unregisterMotionSensor()
        syncDeadlineAlarm(state)
    }

    /** Refresh sensor/deadline projections after the coordinator changes persisted state. */
    private fun refreshProtectionState() {
        BatchThreeUiBridge.refresh(this)
        val state = ProtectionStore(this).read()
        if (isRunning) {
            runCatching {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification())
            }
        }
        if (state.mode == ProtectionMode.ALARM_TRIGGERED) {
            startTheftAlarm()
            return
        }
        if (state.mode == ProtectionMode.ARMED) syncMotionSensor() else unregisterMotionSensor()
        syncDeadlineAlarm(state)
    }

    /** AlarmManager wakes the CPU at the stored deadline; Handler is a low-latency companion. */
    private fun syncDeadlineAlarm(state: ProtectionState) {
        when (state.mode) {
            ProtectionMode.COUNTDOWN -> {
                if (!AlarmScheduler.schedule(
                        this, state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount
                    )
                ) {
                    AlarmScheduler.scheduleRetry(this, 1_000L)
                }
                scheduleCountdown(state)
            }
            ProtectionMode.ALARM_TRIGGERED -> {
                // The AlarmManager recovery callback is the only restart path if
                // Android/OEM stops the alarm service. Never cancel it here.
                countdownExpected = null
                handler.removeCallbacks(countdownRunnable)
                unregisterMotionSensor()
            }
            ProtectionMode.ARMED, ProtectionMode.DISARMED -> {
                countdownExpected = null
                AlarmScheduler.cancel(this)
                handler.removeCallbacks(countdownRunnable)
            }
        }
    }

    private fun scheduleCountdown(state: ProtectionState) {
        countdownExpected = state
        handler.removeCallbacks(countdownRunnable)
        val remaining = ProtectionEngine.remainingMs(
            state, System.currentTimeMillis(), ProtectionClock.elapsedRealtime(), ProtectionClock.bootCount(this)
        )
        handler.postDelayed(countdownRunnable, remaining.coerceAtLeast(0L))
    }

    /** The Handler is only a timer; charger validation runs off the main thread. */
    private fun evaluateCountdown(expected: ProtectionState) {
        if (!countdownValidationRunning.compareAndSet(false, true)) return
        Thread {
            try {
                // The unplug transition was already confirmed by the coordinator's
                // five-sample debounce before COUNTDOWN was persisted. At the deadline,
                // read the live state once so validation does not add another ~800 ms
                // to the user's ten-second grace period. Unknown remains fail-safe.
                val connected = ExternalPowerState.readConnected(this)
                if (connected == null) {
                    handler.post {
                        if (isExpectedCountdownCurrent(expected)) {
                            handler.postDelayed(countdownRunnable, POWER_RETRY_MS)
                        }
                    }
                    return@Thread
                }

                val now = System.currentTimeMillis()
                val elapsed = ProtectionClock.elapsedRealtime()
                val boot = ProtectionClock.bootCount(this)
                var callbackStillCurrent = false
                var transitionedToAlarm = false
                val next = ProtectionStore(this).update { current ->
                    if (!ProtectionEngine.isCurrentDeadline(
                            current, expected.deadlineEpochMs,
                            expected.deadlineElapsedRealtimeMs, expected.bootCount
                        )
                    ) return@update current
                    callbackStillCurrent = true
                    val updated = ProtectionEngine.reconcileCountdown(current, connected, now, elapsed, boot)
                    transitionedToAlarm = current.mode == ProtectionMode.COUNTDOWN &&
                        updated.mode == ProtectionMode.ALARM_TRIGGERED
                    updated
                }
                if (!callbackStillCurrent) return@Thread

                handler.post {
                    if (transitionedToAlarm && next.mode == ProtectionMode.ALARM_TRIGGERED) {
                        countdownExpected = null
                        AlarmScheduler.cancel(this)
                        LocalTamperHistory.record(this, "monitor_countdown_expired", "Stable charger samples and current deadline transitioned to alarm")
                        startTheftAlarm()
                    } else if (next.mode == ProtectionMode.COUNTDOWN) {
                        syncDeadlineAlarm(next)
                    } else if (next.mode == ProtectionMode.ALARM_TRIGGERED) {
                        countdownExpected = null
                        handler.removeCallbacks(countdownRunnable)
                        unregisterMotionSensor()
                    } else {
                        countdownExpected = null
                        AlarmScheduler.cancel(this)
                        handler.removeCallbacks(countdownRunnable)
                        if (next.mode == ProtectionMode.ARMED) syncMotionSensor()
                    }
                }
            } catch (error: Exception) {
                LocalTamperHistory.record(this, "monitor_countdown_validation_failed", error.javaClass.simpleName + ": " + error.message)
                handler.post {
                    if (isExpectedCountdownCurrent(expected)) {
                        handler.postDelayed(countdownRunnable, POWER_RETRY_MS)
                    }
                }
            } finally {
                countdownValidationRunning.set(false)
            }
        }.start()
    }

    private fun isExpectedCountdownCurrent(expected: ProtectionState): Boolean {
        val current = ProtectionStore(this).read()
        return ProtectionEngine.isCurrentDeadline(
            current, expected.deadlineEpochMs,
            expected.deadlineElapsedRealtimeMs, expected.bootCount
        )
    }

    private fun syncMotionSensor() {
        val mode = ProtectionStore(this).read().mode
        val motionEnabled = SafePreferences.boolean(prefs, KEY_MOTION_ENABLED, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED)
        if (!ProtectionMonitorPolicy.shouldMonitorMotion(motionEnabled, mode)) {
            unregisterMotionSensor()
            return
        }
        if (sensorRegistered) return
        val sensor = accelerometer
        if (sensor == null) {
            LocalTamperHistory.record(this, "motion_sensor_unavailable", "No accelerometer is available")
            return
        }
        sensorRegistered = sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
        if (!sensorRegistered) LocalTamperHistory.record(this, "motion_sensor_register_failed")
    }

    private fun unregisterMotionSensor() {
        if (sensorRegistered) sensorManager?.unregisterListener(this)
        sensorRegistered = false
        gravityInitialized = false
        samplesAboveThreshold = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        if (!SafePreferences.boolean(prefs, KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)) {
            unregisterMotionSensor()
            return
        }
        val motionEnabled = SafePreferences.boolean(prefs, KEY_MOTION_ENABLED, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED)
        val mode = ProtectionStore(this).read().mode
        if (!ProtectionMonitorPolicy.shouldMonitorMotion(motionEnabled, mode)) {
            unregisterMotionSensor()
            return
        }
        val values = event.values
        if (!gravityInitialized) {
            for (i in 0..2) gravity[i] = values[i]
            gravityInitialized = true
            return
        }
        val alpha = 0.8f
        for (i in 0..2) gravity[i] = alpha * gravity[i] + (1f - alpha) * values[i]
        val x = values[0] - gravity[0]
        val y = values[1] - gravity[1]
        val z = values[2] - gravity[2]
        val sensitivity = MotionSensitivity.fromStored(
            SafePreferences.string(
            getSharedPreferences(PREFS, Context.MODE_PRIVATE), KEY_SENSITIVITY, MotionSensitivity.MEDIUM.name
        ) ?: MotionSensitivity.MEDIUM.name
        )
        if (MotionDetection.exceedsThreshold(x, y, z, sensitivity)) {
            samplesAboveThreshold++
            val elapsed = android.os.SystemClock.elapsedRealtime()
            if (samplesAboveThreshold >= 2 && elapsed - lastMotionTriggerElapsed >= MOTION_COOLDOWN_MS) {
                lastMotionTriggerElapsed = elapsed
                triggerMotionAlarm(sensitivity)
            }
        } else {
            samplesAboveThreshold = 0
        }
    }

    private fun triggerMotionAlarm(sensitivity: MotionSensitivity) {
        val now = System.currentTimeMillis()
        var transitionedByThisCall = false
        val next = ProtectionStore(this).update { old ->
            val next = ProtectionEngine.triggerFromArmed(
                old, now, ProtectionClock.bootCount(this)
            )
            if (old.mode == ProtectionMode.ARMED && next.mode == ProtectionMode.ALARM_TRIGGERED) {
                transitionedByThisCall = true
            }
            next
        }
        if (transitionedByThisCall && next.mode == ProtectionMode.ALARM_TRIGGERED) {
            LocalTamperHistory.record(this, "motion_alarm_triggered", "Motion threshold exceeded; sensitivity=${sensitivity.name}")
            unregisterMotionSensor()
            startTheftAlarm()
        }
    }

    private fun handleBatteryChanged(intent: Intent) {
        if (!SafePreferences.boolean(prefs, KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)) return
        if (!SafePreferences.boolean(prefs, KEY_BATTERY_ALERT_ENABLED, false)) return
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (!plugged) {
            // A new charge session gets its own one-shot threshold alert.
            prefs.edit().putBoolean(KEY_BATTERY_ALERT_FIRED, false)
                .putInt(KEY_LAST_BATTERY_LEVEL, -1).apply()
            return
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val percent = (level * 100f / scale).toInt()
        val previous = SafePreferences.int(prefs, KEY_LAST_BATTERY_LEVEL, -1)
        val threshold = SafePreferences.int(prefs, KEY_BATTERY_THRESHOLD, 80).coerceIn(50, 100)
        if (previous >= 0 && previous < threshold && percent >= threshold &&
            !SafePreferences.boolean(prefs, KEY_BATTERY_ALERT_FIRED, false)
        ) {
            prefs.edit().putBoolean(KEY_BATTERY_ALERT_FIRED, true).putInt(KEY_LAST_BATTERY_LEVEL, percent).apply()
            LocalTamperHistory.record(this, "battery_threshold_reached", "Battery crossed configured threshold: ${threshold}%")
            playBatteryAlert()
        } else {
            prefs.edit().putInt(KEY_LAST_BATTERY_LEVEL, percent).apply()
        }
    }

    private fun playBatteryAlert() {
        val uri = AlarmSoundStore.uri(this, AlarmSoundStore.ALERT)
        if (uri == null) {
            LocalTamperHistory.record(this, "battery_alert_sound_missing", "No secondary alert audio selected")
            vibrateOnce()
            return
        }
        runCatching {
            MediaPlayer().apply {
                alertPlayer = this
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@ProtectionMonitorService, uri)
                setOnPreparedListener { runCatching { it.start() }.onFailure { _ -> vibrateOnce() } }
                setOnCompletionListener { releaseAlertPlayer() }
                setOnErrorListener { _, what, extra ->
                    LocalTamperHistory.record(this@ProtectionMonitorService, "battery_alert_audio_failed", "MediaPlayer error $what/$extra")
                    releaseAlertPlayer()
                    vibrateOnce()
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            LocalTamperHistory.record(this, "battery_alert_audio_open_failed", it.javaClass.simpleName + ": " + it.message)
            vibrateOnce()
        }
    }

    private fun releaseAlertPlayer() {
        runCatching { alertPlayer?.release() }
        alertPlayer = null
    }

    private fun vibrateOnce() {
        val deviceVibrator = getSystemService(Vibrator::class.java) ?: return
        if (!deviceVibrator.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                deviceVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 200, 350), -1))
            } else {
                @Suppress("DEPRECATION")
                deviceVibrator.vibrate(longArrayOf(0, 350, 200, 350), -1)
            }
        }
    }

    private fun startTheftAlarm() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_START)
            )
        }.onFailure {
            LocalTamperHistory.record(this, "monitor_alarm_service_start_failed", it.javaClass.simpleName + ": " + it.message)
            AlarmRecoveryCoordinator.onStartFailure(
                this, "Android deferred background alarm playback; the latched alarm remains active"
            )
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Protection monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Visible notification while charger, motion, trusted Bluetooth, and optional battery protection monitoring is enabled"
                    setSound(null, null)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 501, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 502, Intent(this, ProtectionMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mode = ProtectionStore(this).read().mode
        val statusText = when (mode) {
            ProtectionMode.DISARMED -> "Monitor available • protection is not armed"
            ProtectionMode.ARMED -> "Protected • charger monitoring is armed"
            ProtectionMode.COUNTDOWN -> "Warning • charger-removal countdown active"
            ProtectionMode.ALARM_TRIGGERED -> "Alarm active • owner authentication required"
        }
        val statusColor = when (mode) {
            ProtectionMode.DISARMED -> ThemeStore.get(this).muted
            ProtectionMode.ARMED -> ThemeStore.get(this).success
            ProtectionMode.COUNTDOWN -> ThemeStore.get(this).warning
            ProtectionMode.ALARM_TRIGGERED -> ThemeStore.get(this).danger
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setColor(statusColor)
            .setContentTitle("ThiefAlertSeQure • " + mode.name.replace('_', ' '))
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                statusText + ". Charger monitoring is local. Motion and trusted Bluetooth alerts remain optional."
            ))
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop monitoring", stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        stopRetryRunnable?.let(handler::removeCallbacks)
        stopRetryRunnable = null
        handler.removeCallbacks(countdownRunnable)
        unregisterMotionSensor()
        if (receiverRegistered) runCatching { unregisterReceiver(systemReceiver) }
        receiverRegistered = false
        if (powerReceiverRegistered) runCatching { unregisterReceiver(powerTransitionReceiver) }
        powerReceiverRegistered = false
        pendingBluetoothRunnables.values.forEach(handler::removeCallbacks)
        pendingBluetoothRunnables.clear()
        isBluetoothReceiverRegistered = false
        if (bluetoothReceiverRegistered) runCatching { unregisterReceiver(bluetoothSeparationReceiver) }
        bluetoothReceiverRegistered = false
        releaseAlertPlayer()
        isRunning = false
        prefs.edit().putBoolean(KEY_RUNNING, false).apply()
        LocalTamperHistory.record(this, "monitor_stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        @Volatile var isBluetoothReceiverRegistered: Boolean = false
        const val PREFS = "protection_monitor_v1"
        const val KEY_ENABLED = "monitor_enabled"
        const val KEY_RUNNING = "monitor_running"
        const val KEY_SENSITIVITY = "motion_sensitivity"
        const val KEY_MOTION_ENABLED = "motion_enabled"
        const val KEY_BATTERY_ALERT_ENABLED = "battery_alert_enabled"
        const val KEY_BATTERY_THRESHOLD = "battery_threshold"
        const val KEY_BT_ALERT_ENABLED = "trusted_bluetooth_alert_enabled"
        const val KEY_BT_TRUSTED_ADDRESS = "trusted_bluetooth_address"
        const val KEY_BT_TRUSTED_NAME = "trusted_bluetooth_name"
        const val KEY_BT_BASELINE_CONNECTED = "trusted_bluetooth_baseline_connected"
        private const val KEY_BATTERY_ALERT_FIRED = "battery_alert_fired"
        private const val KEY_LAST_BATTERY_LEVEL = "last_battery_level"
        private const val CHANNEL_ID = "protection_monitor"
        private const val NOTIFICATION_ID = 501
        const val ACTION_STOP = "com.thiefalertsequre.STOP_MONITOR"
        const val EXTRA_STOP_PREFERENCE_COMMITTED = "stop_preference_committed"
        const val ACTION_REFRESH = "com.thiefalertsequre.REFRESH_MONITOR"
        private const val MOTION_COOLDOWN_MS = 5_000L
        private const val POWER_RETRY_MS = 1_000L
        const val BLUETOOTH_DISCONNECT_GRACE_MS = 8_000L
    }
}
