package com.thiefalertsequre.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
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
            startForeground(NOTIFICATION_ID, notification())
            LocalTamperHistory.record(this, "alarm_service_started", "Foreground notification active")
        } catch (error: Exception) {
            LocalTamperHistory.record(this, "alarm_service_start_failed", error.javaClass.simpleName + ": " + error.message)
            stopSelf()
            return START_NOT_STICKY
        }
        if (player == null) startAlarmAudio()
        return START_STICKY
    }

    private fun startAlarmAudio() {
        val soundUri = AlarmSoundStore.uri(this, AlarmSoundStore.ALARM)
        if (soundUri == null) {
            LocalTamperHistory.record(this, "alarm_audio_missing", "No user-selected main alarm sound is configured")
            startVibrationFallback()
            return
        }
        val audio = getSystemService(AudioManager::class.java)
        audioManager = audio
        AlarmVolumeController.raiseToMaximum(this, audio)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = request
            runCatching { audio.requestAudioFocus(request) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { audio.requestAudioFocus(audioFocusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) }
        }

        val media = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(this@AlarmService, soundUri)
                setWakeMode(this@AlarmService, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                setOnPreparedListener { prepared ->
                    if (ProtectionStore(this@AlarmService).read().mode == ProtectionMode.ALARM_TRIGGERED) {
                        runCatching {
                            prepared.start()
                            LocalTamperHistory.record(this@AlarmService, "alarm_audio_playing", "User-selected audio started")
                        }.onFailure {
                            LocalTamperHistory.record(this@AlarmService, "alarm_audio_start_failed", it.javaClass.simpleName + ": " + it.message)
                            startVibrationFallback()
                        }
                    } else {
                        runCatching { prepared.release() }
                        player = null
                    }
                }
                setOnErrorListener { failed, what, extra ->
                    LocalTamperHistory.record(this@AlarmService, "alarm_audio_decode_failed", "MediaPlayer error $what/$extra")
                    runCatching { failed.release() }
                    if (player === failed) player = null
                    startVibrationFallback()
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            LocalTamperHistory.record(this, "alarm_audio_open_failed", it.javaClass.simpleName + ": " + it.message)
        }.getOrNull()

        if (media == null) {
            startVibrationFallback()
            return
        }
        player = media
        LocalTamperHistory.record(this, "alarm_audio_preparing", "Preparing user-selected alarm audio")
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
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
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
