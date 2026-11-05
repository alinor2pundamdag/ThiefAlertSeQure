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
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { }
    private val channelId = "anti_theft_alarm"

    override fun onCreate() {
        super.onCreate()
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
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            lockDeviceIfAdminEnabled()
            startForeground(NOTIFICATION_ID, notification())
            LocalTamperHistory.record(this, "alarm_service_started", "Foreground notification active")
        } catch (error: Exception) {
            LocalTamperHistory.record(this, "alarm_service_start_failed", error.javaClass.simpleName + ": " + error.message)
            AlarmRecoveryNotice.show(this, "Android refused to keep the alarm foreground service running")
            stopSelf()
            return START_NOT_STICKY
        }
        if (player == null) startAlarmAudio()
        return START_STICKY
    }

    private fun startAlarmAudio() {
        val defaultUri = Uri.parse("android.resource://$packageName/${R.raw.default_alarm}")
        val selectedUri = AlarmSoundStore.uri(this, AlarmSoundStore.ALARM)
        startAlarmAudio(selectedUri ?: defaultUri, selectedUri == null, defaultUri)
    }

    private fun startAlarmAudio(soundUri: Uri, usingDefault: Boolean, defaultUri: Uri) {
        val audio = getSystemService(AudioManager::class.java)
        audioManager = audio
        AlarmVolumeController.raiseToMaximum(this, audio)
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

    private fun startVibrationFallback() {
        val deviceVibrator = getSystemService(Vibrator::class.java) ?: return
        if (!deviceVibrator.hasVibrator()) {
            LocalTamperHistory.record(this, "alarm_no_output_available", "Selected audio unavailable and device has no vibrator")
            return
        }
        vibrator = deviceVibrator
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                deviceVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 250, 700, 250), 0))
            } else {
                @Suppress("DEPRECATION")
                deviceVibrator.vibrate(longArrayOf(0, 700, 250, 700, 250), 0)
            }
            LocalTamperHistory.record(this, "alarm_vibration_fallback", "Vibration active because selected audio could not play")
        }.onFailure {
            LocalTamperHistory.record(this, "alarm_vibration_failed", it.javaClass.simpleName + ": " + it.message)
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
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("ThiefAlertSeQure — Alarm active")
            .setContentText("Open to authenticate with your device lock or supported biometric.")
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /** Force the keyguard before presenting the full-screen owner-authentication activity. */
    private fun lockDeviceIfAdminEnabled() {
        val manager = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, ThiefAlertAdminReceiver::class.java)
        if (!getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure) {
            LocalTamperHistory.record(this, "automatic_screen_lock_unavailable", "No secure device PIN, pattern or password is configured")
            return
        }
        if (!manager.isAdminActive(admin)) {
            LocalTamperHistory.record(this, "automatic_screen_lock_unavailable", "Device administrator has not been activated")
            return
        }
        runCatching { manager.lockNow() }
            .onFailure { LocalTamperHistory.record(this, "automatic_screen_lock_failed", it.javaClass.simpleName + ": " + it.message) }
    }

    private fun stopAlarm() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
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
            runCatching { AlarmVolumeController.restore(this, getSystemService(AudioManager::class.java)) }
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 42
        const val ACTION_START = "com.thiefalertsequre.START"
        const val ACTION_STOP_IF_AUTHENTICATED = "com.thiefalertsequre.STOP_AUTH"
    }
}
