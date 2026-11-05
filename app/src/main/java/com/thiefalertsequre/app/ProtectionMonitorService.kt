package com.thiefalertsequre.app

import android.app.Notification
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
    private var alertPlayer: MediaPlayer? = null
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val countdownRunnable = Runnable { evaluateCountdown() }
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> handleBatteryChanged(intent)
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
        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            val disabledState = ProtectionStore(this).update { current ->
                if (current.mode == ProtectionMode.ALARM_TRIGGERED) current
                else ProtectionState(ProtectionMode.DISARMED, 0L, System.currentTimeMillis(), 0L, -1)
            }
            if (disabledState.mode != ProtectionMode.ALARM_TRIGGERED) AlarmScheduler.cancel(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val state = ProtectionStore(this).read()
        val enabled = prefs.getBoolean(KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED)
        if (!ProtectionMonitorPolicy.shouldRun(enabled, state.mode)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            isRunning = true
            prefs.edit().putBoolean(KEY_RUNNING, true).apply()
        } catch (error: Exception) {
            LocalTamperHistory.record(this, "monitor_fgs_start_failed", error.javaClass.simpleName + ": " + error.message)
            isRunning = false
            prefs.edit().putBoolean(KEY_RUNNING, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }

        registerSystemReceiver()
        PowerTransitionCoordinator.initializeBaseline(this)
        reconcileStablePowerState()
        syncMotionSensor()
        LocalTamperHistory.record(this, "monitor_started", "Persistent foreground charger monitor active; motion sensing is armed-state only")
        return START_STICKY
    }

    private fun registerSystemReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(ACTION_REFRESH)
        }
        ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
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
        val state = ProtectionStore(this).read()
        if (state.mode == ProtectionMode.ARMED) syncMotionSensor() else unregisterMotionSensor()
        syncDeadlineAlarm(state)
    }

    /** AlarmManager wakes the CPU at the stored deadline; Handler is a low-latency companion. */
    private fun syncDeadlineAlarm(state: ProtectionState) {
        if (ProtectionEngine.shouldScheduleDeadline(state.mode)) {
            AlarmScheduler.schedule(this, state.deadlineEpochMs, state.deadlineElapsedRealtimeMs, state.bootCount)
            scheduleCountdown(state)
        } else {
            AlarmScheduler.cancel(this)
            handler.removeCallbacks(countdownRunnable)
        }
    }

    private fun scheduleCountdown(state: ProtectionState) {
        handler.removeCallbacks(countdownRunnable)
        val remaining = ProtectionEngine.remainingMs(
            state, System.currentTimeMillis(), ProtectionClock.elapsedRealtime(), ProtectionClock.bootCount(this)
        )
        handler.postDelayed(countdownRunnable, remaining.coerceAtLeast(0L))
    }

    private fun evaluateCountdown() {
        val now = System.currentTimeMillis()
        val elapsed = ProtectionClock.elapsedRealtime()
        val boot = ProtectionClock.bootCount(this)
        val connected = ExternalPowerState.readConnected(this)
        if (connected == null) {
            handler.postDelayed(countdownRunnable, POWER_RETRY_MS)
            return
        }
        var transitionedToAlarm = false
        val next = ProtectionStore(this).update { current ->
            val updated = ProtectionEngine.reconcileCountdown(current, connected, now, elapsed, boot)
            transitionedToAlarm = current.mode == ProtectionMode.COUNTDOWN &&
                updated.mode == ProtectionMode.ALARM_TRIGGERED
            updated
        }
        if (transitionedToAlarm && next.mode == ProtectionMode.ALARM_TRIGGERED) {
            AlarmScheduler.cancel(this)
            LocalTamperHistory.record(this, "monitor_countdown_expired", "Validated countdown transitioned to alarm")
            startTheftAlarm()
        } else if (next.mode == ProtectionMode.COUNTDOWN) {
            syncDeadlineAlarm(next)
        } else {
            AlarmScheduler.cancel(this)
            handler.removeCallbacks(countdownRunnable)
            if (next.mode == ProtectionMode.ARMED) syncMotionSensor()
        }
    }

    private fun syncMotionSensor() {
        val mode = ProtectionStore(this).read().mode
        val motionEnabled = prefs.getBoolean(KEY_MOTION_ENABLED, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED)
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
        val motionEnabled = prefs.getBoolean(KEY_MOTION_ENABLED, ProtectionMonitorPolicy.DEFAULT_MOTION_ENABLED)
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
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SENSITIVITY, MotionSensitivity.MEDIUM.name)
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
            if (old.mode == ProtectionMode.ARMED) {
                transitionedByThisCall = true
                ProtectionState(ProtectionMode.ALARM_TRIGGERED, 0L, now, 0L, ProtectionClock.bootCount(this))
            } else old
        }
        if (transitionedByThisCall && next.mode == ProtectionMode.ALARM_TRIGGERED) {
            LocalTamperHistory.record(this, "motion_alarm_triggered", "Motion threshold exceeded; sensitivity=${sensitivity.name}")
            unregisterMotionSensor()
            startTheftAlarm()
        }
    }

    private fun handleBatteryChanged(intent: Intent) {
        if (!prefs.getBoolean(KEY_BATTERY_ALERT_ENABLED, false)) return
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
        val previous = prefs.getInt(KEY_LAST_BATTERY_LEVEL, -1)
        val threshold = prefs.getInt(KEY_BATTERY_THRESHOLD, 80).coerceIn(50, 100)
        if (previous >= 0 && previous < threshold && percent >= threshold &&
            !prefs.getBoolean(KEY_BATTERY_ALERT_FIRED, false)
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
            AlarmScheduler.scheduleImmediateRecovery(this)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Protection monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Visible notification while motion and battery protection monitoring is enabled"
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("ThiefAlertSeQure protection monitor")
            .setContentText("Charger monitoring is active; motion alarm is opt-in; battery alert is optional.")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop monitoring", stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        handler.removeCallbacks(countdownRunnable)
        unregisterMotionSensor()
        if (receiverRegistered) runCatching { unregisterReceiver(systemReceiver) }
        receiverRegistered = false
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
        const val PREFS = "protection_monitor_v1"
        const val KEY_ENABLED = "monitor_enabled"
        const val KEY_RUNNING = "monitor_running"
        const val KEY_SENSITIVITY = "motion_sensitivity"
        const val KEY_MOTION_ENABLED = "motion_enabled"
        const val KEY_BATTERY_ALERT_ENABLED = "battery_alert_enabled"
        const val KEY_BATTERY_THRESHOLD = "battery_threshold"
        private const val KEY_BATTERY_ALERT_FIRED = "battery_alert_fired"
        private const val KEY_LAST_BATTERY_LEVEL = "last_battery_level"
        private const val CHANNEL_ID = "protection_monitor"
        private const val NOTIFICATION_ID = 501
        const val ACTION_STOP = "com.thiefalertsequre.STOP_MONITOR"
        const val ACTION_REFRESH = "com.thiefalertsequre.REFRESH_MONITOR"
        private const val MOTION_COOLDOWN_MS = 5_000L
        private const val POWER_RETRY_MS = 1_000L
    }
}
