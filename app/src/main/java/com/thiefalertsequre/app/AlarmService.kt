package com.thiefalertsequre.app

import android.app.Notification
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var secondaryPlayer: MediaPlayer? = null
    private var toneController: AlarmToneController? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null
    private var hapticsActive = false
    private var activeHapticPattern: AlarmHapticPattern? = null
    private var flashController: AlarmFlashController? = null
    private var thermalFlashSuppressed = false
    private var thermalFlashController: ThermalFlashController? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { }
    private val channelId = CHANNEL_ID
    private val volumeHandler = Handler(Looper.getMainLooper())
    private val volumeEnforcement = object : Runnable {
        override fun run() {
            if (ProtectionStore(this@AlarmService).read().mode != ProtectionMode.ALARM_TRIGGERED) return
            AlarmVolumeController.enforceMaximum(getSystemService(AudioManager::class.java))
            volumeHandler.postDelayed(this, VOLUME_ENFORCEMENT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 29) {
            thermalFlashController = runCatching {
                ThermalFlashController(this) { suppressFlash ->
                    thermalFlashSuppressed = suppressFlash
                    if (suppressFlash) {
                        flashController?.stop()
                        flashController = null
                        LocalTamperHistory.record(this, "alarm_flash_thermal_suppressed", "Android thermal status is severe or higher; audio alarm continues")
                    } else {
                        LocalTamperHistory.record(this, "alarm_flash_thermal_resumed", "Android thermal status returned below severe; optional flash may resume")
                        syncAlarmFlash()
                    }
                }.also { thermalFlashSuppressed = it.start() }
            }.getOrNull()
        }
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Anti-theft alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Active ThiefAlertSeQure alarm"
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ProtectionStore(this).read().mode != ProtectionMode.ALARM_TRIGGERED) {
            LocalTamperHistory.record(this, "alarm_service_rejected", "Protection state was not ALARM_TRIGGERED")
            stopAlarm()
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            // Promote immediately before locking the device; lockNow() may
            // turn the display off and must not delay the FGS startup deadline.
            startForeground(NOTIFICATION_ID, notification())
            AlarmRecoveryCoordinator.onServiceStarted(this)
            val alarmStartedAt = ProtectionStore(this).read().changedAtEpochMs
            lockDeviceIfAdminEnabled(alarmStartedAt)
            LocalTamperHistory.record(this, "alarm_service_started", "Foreground notification active; device-lock request issued")
        } catch (error: Exception) {
            LocalTamperHistory.record(this, "alarm_service_start_failed", error.javaClass.simpleName + ": " + error.message)
            AlarmRecoveryCoordinator.onStartFailure(
                this, "Android refused to keep the alarm foreground service running"
            )
            stopSelf()
            return START_NOT_STICKY
        }
        if (player == null && toneController == null) startAlarmAudio()
        syncAlarmFlash()
        syncSecondaryAlarm()
        syncAlarmHaptics()
        volumeHandler.removeCallbacks(volumeEnforcement)
        volumeHandler.post(volumeEnforcement)
        return START_STICKY
    }

    /**
     * The torch is an independent visual alarm output. It starts only after the
     * persisted state has been verified as ALARM_TRIGGERED in onStartCommand.
     * Failure to access a flash unit never blocks or stops alarm audio.
     */
    private fun syncAlarmFlash() {
        if (!thermalFlashSuppressed && AlarmFlashSettings.isEnabled(this) && AlarmFlashSettings.shouldRun(ProtectionStore(this).read().mode)) {
            val controller = flashController ?: AlarmFlashController(this, volumeHandler) { event, detail ->
                LocalTamperHistory.record(this, event, detail)
            }.also { flashController = it }
            controller.start()
        } else {
            flashController?.stop()
            flashController = null
        }
    }

    /** Optional looping secondary alarm; it never replaces or gates the primary alarm. */
    private fun syncSecondaryAlarm() {
        val shouldRun = ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED &&
            AlarmSoundStore.isSecondaryAlarmEnabled(this)
        if (!shouldRun) {
            stopSecondaryAlarm()
            return
        }
        if (secondaryPlayer != null) return
        val uri = AlarmSoundStore.uri(this, AlarmSoundStore.ALERT)
        if (uri == null) {
            LocalTamperHistory.record(this, "secondary_alarm_unavailable", "Secondary alarm is enabled but no owner-selected alert sound is available")
            return
        }
        val media = MediaPlayer()
        secondaryPlayer = media
        try {
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            media.setDataSource(this, uri)
            media.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            media.isLooping = true
            media.setOnPreparedListener { prepared ->
                if (secondaryPlayer === prepared &&
                    ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED &&
                    AlarmSoundStore.isSecondaryAlarmEnabled(this)
                ) {
                    runCatching {
                        prepared.start()
                        LocalTamperHistory.record(this, "secondary_alarm_started", "Owner-enabled secondary alarm is looping independently")
                    }.onFailure {
                        LocalTamperHistory.record(this, "secondary_alarm_start_failed", it.javaClass.simpleName + ": " + it.message)
                        stopSecondaryAlarm()
                    }
                } else {
                    stopSecondaryAlarm()
                }
            }
            media.setOnErrorListener { failed, what, extra ->
                LocalTamperHistory.record(this, "secondary_alarm_playback_failed", "Secondary MediaPlayer error $what/$extra; primary alarm remains active")
                if (secondaryPlayer === failed) stopSecondaryAlarm()
                true
            }
            media.prepareAsync()
            LocalTamperHistory.record(this, "secondary_alarm_preparing", "Preparing independent owner-selected secondary alarm")
        } catch (error: Exception) {
            LocalTamperHistory.record(this, "secondary_alarm_open_failed", error.javaClass.simpleName + ": " + error.message)
            stopSecondaryAlarm()
        }
    }

    private fun stopSecondaryAlarm() {
        val active = secondaryPlayer
        secondaryPlayer = null
        runCatching { active?.stop() }
        runCatching { active?.release() }
    }

    private fun requestToneAudioFocus() {
        val audio = getSystemService(AudioManager::class.java)
        audioManager = audio
        if (!AlarmVolumeController.raiseToMaximum(this, audio)) {
            LocalTamperHistory.record(this, "alarm_volume_raise_unconfirmed", "Built-in tone startup continued, but maximum alarm-stream volume was not confirmed")
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = request
            runCatching { audio.requestAudioFocus(request) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { audio.requestAudioFocus(audioFocusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN) }
        }
    }

    private fun handleTonePresetFailure(error: Throwable) {
        LocalTamperHistory.record(this, "alarm_tone_preset_failed", error.javaClass.simpleName + ": " + error.message)
        volumeHandler.post {
            toneController?.stop()
            toneController = null
            if (ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED && player == null) {
                val defaultUri = Uri.parse("android.resource://$packageName/${R.raw.default_alarm}")
                startAlarmAudio(defaultUri, true, defaultUri)
            }
        }
    }

    private fun startAlarmAudio() {
        val defaultUri = Uri.parse("android.resource://$packageName/${R.raw.default_alarm}")
        val selectedUri = AlarmSoundStore.uri(this, AlarmSoundStore.ALARM)
        if (selectedUri == null) {
            val preset = AlarmSoundStore.selectedBuiltInPreset(this)
            if (preset != AlarmTonePreset.DEFAULT_AUDIO) {
                val controller = AlarmToneController(volumeHandler, preset) { error ->
                    handleTonePresetFailure(error)
                }
                requestToneAudioFocus()
                toneController = controller
                if (controller.start()) {
                    LocalTamperHistory.record(this, "alarm_tone_preset_started", "Built-in alarm tone preset active: ${preset.name}")
                    return
                }
                toneController = null
            }
        }
        startAlarmAudio(selectedUri ?: defaultUri, selectedUri == null, defaultUri)
    }

    private fun startAlarmAudio(soundUri: Uri, usingDefault: Boolean, defaultUri: Uri) {
        val audio = getSystemService(AudioManager::class.java)
        audioManager = audio
        if (!AlarmVolumeController.raiseToMaximum(this, audio)) {
            LocalTamperHistory.record(this, "alarm_volume_raise_unconfirmed", "Alarm audio startup continued, but maximum alarm-stream volume was not confirmed")
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = request
            runCatching { audio.requestAudioFocus(request) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { audio.requestAudioFocus(audioFocusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN) }
        }

        val media = MediaPlayer()
        player = media
        try {
            media.setAudioAttributes(attributes)
            media.setDataSource(this, soundUri)
            media.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            media.isLooping = true
            media.setOnPreparedListener { prepared ->
                if (ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED) {
                    runCatching {
                        prepared.start()
                        LocalTamperHistory.record(
                            this,
                            "alarm_audio_playing",
                            if (usingDefault) "Built-in default alarm started and looping" else "Owner-selected alarm started and looping"
                        )
                    }.onFailure {
                        LocalTamperHistory.record(this, "alarm_audio_start_failed", it.javaClass.simpleName + ": " + it.message)
                        if (!usingDefault) {
                            if (player === prepared) player = null
                            runCatching { prepared.release() }
                            startAlarmAudio(defaultUri, true, defaultUri)
                        } else {
                            if (player === prepared) player = null
                            runCatching { prepared.release() }
                            startVibrationFallback()
                        }
                    }
                } else {
                    if (player === prepared) player = null
                    runCatching { prepared.release() }
                }
            }
            media.setOnErrorListener { failed, what, extra ->
                LocalTamperHistory.record(this, "alarm_audio_decode_failed", "MediaPlayer error $what/$extra")
                if (player === failed) player = null
                runCatching { failed.release() }
                if (ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED && !usingDefault) {
                    startAlarmAudio(defaultUri, true, defaultUri)
                } else if (ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED) {
                    startVibrationFallback()
                }
                true
            }
            media.prepareAsync()
            LocalTamperHistory.record(
                this,
                "alarm_audio_preparing",
                if (usingDefault) "Preparing built-in default alarm" else "Preparing owner-selected alarm override"
            )
        } catch (error: Exception) {
            if (player === media) player = null
            runCatching { media.release() }
            LocalTamperHistory.record(this, "alarm_audio_open_failed", error.javaClass.simpleName + ": " + error.message)
            if (!usingDefault) startAlarmAudio(defaultUri, true, defaultUri) else startVibrationFallback()
        }
    }

    private fun syncAlarmHaptics() {
        val shouldRun = ProtectionStore(this).read().mode == ProtectionMode.ALARM_TRIGGERED &&
            AlarmHapticsPolicy.isEnabled(this)
        if (!shouldRun) {
            runCatching { vibrator?.cancel() }
            vibrator = null
            hapticsActive = false
            activeHapticPattern = null
            return
        }
        val selectedPattern = AlarmHapticsPolicy.readPattern(this)
        if (hapticsActive && activeHapticPattern == selectedPattern) return
        runCatching { vibrator?.cancel() }
        val deviceVibrator = getSystemService(Vibrator::class.java)
        if (deviceVibrator == null || !deviceVibrator.hasVibrator()) {
            LocalTamperHistory.record(this, "alarm_haptics_unavailable", "Selected vibration output is not available; audio alarm remains active")
            vibrator = null
            hapticsActive = false
            activeHapticPattern = null
            return
        }
        runCatching {
            deviceVibrator.vibrate(VibrationEffect.createWaveform(AlarmHapticsPolicy.waveform(selectedPattern), 0))
            vibrator = deviceVibrator
            hapticsActive = true
            activeHapticPattern = selectedPattern
            LocalTamperHistory.record(this, "alarm_haptics_started", "Independent repeating haptic pattern active: ${selectedPattern.name}")
        }.onFailure {
            vibrator = null
            hapticsActive = false
            activeHapticPattern = null
            LocalTamperHistory.record(this, "alarm_haptics_failed", it.javaClass.simpleName + ": " + it.message)
        }
    }

    private fun startVibrationFallback() {
        if (!AlarmHapticsPolicy.isEnabled(this)) {
            LocalTamperHistory.record(this, "alarm_haptics_disabled", "Audio fallback vibration is disabled by the owner")
            return
        }
        syncAlarmHaptics()
        if (!hapticsActive) {
            LocalTamperHistory.record(this, "alarm_no_output_available", "Selected audio failed and no usable vibration output was confirmed")
        }
    }

    private fun notification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, 42, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dashboardPrefs = getSharedPreferences(MainActivity.ALARM_DISPLAY_PREFS, MODE_PRIVATE)
        val dashboardEnabled = SafePreferences.boolean(
            dashboardPrefs, MainActivity.KEY_SHOW_ALARM_SCREEN_OVER_LOCKSCREEN, false
        )
        val fullScreenAccessGranted = Build.VERSION.SDK_INT < 34 ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("ThiefAlertSeQure — Alarm active")
            .setContentText("Open to authenticate with your device lock or supported biometric.")
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (AlarmPresentationPolicy.shouldUseFullScreenIntent(
                dashboardEnabled, Build.VERSION.SDK_INT, fullScreenAccessGranted
            )
        ) {
            // This is the optional, owner-enabled dashboard only. If Android 14+
            // access is denied, retain the normal alarm notification fallback.
            builder.setFullScreenIntent(contentIntent, true)
        }
        return builder.build()
    }

    /** Force the keyguard before presenting the full-screen owner-authentication activity. */
    private fun lockDeviceIfAdminEnabled(alarmStartedAtEpochMs: Long) {
        // Device locking is an optional secondary response. Corrupt preferences,
        // missing admin access, or OEM policy must never stop the audible alarm.
        try {
            val manager = getSystemService(DevicePolicyManager::class.java)
            val keyguard = getSystemService(android.app.KeyguardManager::class.java)
            val admin = ComponentName(this, ThiefAlertAdminReceiver::class.java)
            val lockPrefs = getSharedPreferences("alarm_service_lock_v1", MODE_PRIVATE)
            val previouslyRequested = runCatching {
                SafePreferences.long(lockPrefs, KEY_LOCK_REQUESTED_FOR_ALARM_AT, 0L)
            }.getOrDefault(0L)
            if (alarmStartedAtEpochMs > 0L && previouslyRequested == alarmStartedAtEpochMs) {
                // Service starts may repeat during one alarm episode. Do not re-lock
                // a phone the owner has already unlocked for this same alarm.
                return
            }
            if (!keyguard.isDeviceSecure) {
                LocalTamperHistory.record(this, "automatic_screen_lock_unavailable", "No secure device PIN, pattern or password is configured")
                return
            }
            if (!manager.isAdminActive(admin)) {
                LocalTamperHistory.record(this, "automatic_screen_lock_unavailable", "Device administrator has not been activated")
                return
            }
            manager.lockNow()
            if (alarmStartedAtEpochMs > 0L &&
                !lockPrefs.edit().putLong(KEY_LOCK_REQUESTED_FOR_ALARM_AT, alarmStartedAtEpochMs).commit()
            ) {
                LocalTamperHistory.record(this, "automatic_screen_lock_state_not_saved", "Device was locked, but the once-per-alarm marker could not be persisted")
            }
            LocalTamperHistory.record(this, "automatic_screen_lock_requested", "Screen lock requested once for this alarm episode")
        } catch (error: Exception) {
            // Never let a failed optional lock request abort alarm audio/vibration.
            runCatching {
                LocalTamperHistory.record(this, "automatic_screen_lock_failed", error.javaClass.simpleName + ": " + error.message)
            }
        }
    }

    private fun stopAlarm() {
        stopSecondaryAlarm()
        flashController?.stop()
        flashController = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        hapticsActive = false
        activeHapticPattern = null
        audioManager?.let { manager ->
            if (Build.VERSION.SDK_INT >= 26) {
                audioFocusRequest?.let { request -> runCatching { manager.abandonAudioFocusRequest(request) } }
            } else {
                @Suppress("DEPRECATION")
                runCatching { manager.abandonAudioFocus(audioFocusListener) }
            }
        }
        audioFocusRequest = null
        audioManager = null
        if (ProtectionStore(this).read().mode != ProtectionMode.ALARM_TRIGGERED) {
            val restored = runCatching {
                AlarmVolumeController.restore(this, getSystemService(AudioManager::class.java))
            }.getOrElse { error ->
                LocalTamperHistory.record(
                    this, "alarm_volume_restore_exception",
                    error.javaClass.simpleName + ": " + error.message
                )
                false
            }
            if (!restored) {
                LocalTamperHistory.record(this, "alarm_volume_restore_unconfirmed", "Alarm service stopped without confirming full alarm-volume restoration and snapshot cleanup")
            }
        }
    }

    override fun onDestroy() {
        volumeHandler.removeCallbacks(volumeEnforcement)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalFlashController?.stop()
        }
        thermalFlashController = null
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 42
        const val CHANNEL_ID = "anti_theft_alarm"
        const val ACTION_START = "com.thiefalertsequre.START"
        const val ACTION_STOP_IF_AUTHENTICATED = "com.thiefalertsequre.STOP_AUTH"
        const val ACTION_REFRESH_FLASH = "com.thiefalertsequre.REFRESH_ALARM_FLASH"
        const val ACTION_REFRESH_OUTPUTS = "com.thiefalertsequre.REFRESH_ALARM_OUTPUTS"
        private const val VOLUME_ENFORCEMENT_INTERVAL_MS = 500L
        private const val KEY_LOCK_REQUESTED_FOR_ALARM_AT = "lock_requested_for_alarm_started_at"
    }
}
