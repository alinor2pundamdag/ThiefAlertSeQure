package com.thiefalertsequre.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.database.ContentObserver
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var fallbackTone: ToneGenerator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { /* Keep alarm state active on focus changes. */ }
    private val handler = Handler(Looper.getMainLooper())
    private var alarmVolumeObserver: ContentObserver? = null
    private val repeatFallback = object : Runnable {
        override fun run() {
            if (ProtectionStore(this@AlarmService).read().mode != ProtectionMode.ALARM_TRIGGERED) {
                stopAlarm()
                stopSelf()
                return
            }
            fallbackTone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 60_000)
            handler.postDelayed(this, 60_000L)
        }
    }
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
        enforceAlarmVolumeWhileActive()
        if (player == null && fallbackTone == null) startAlarmAudio()
        return START_STICKY
    }

    /**
     * Keep STREAM_ALARM at its maximum while this foreground alarm is active.
     * The observer reasserts the level if the hardware volume keys lower it.
     */
    private fun enforceAlarmVolumeWhileActive() {
        if (alarmVolumeObserver == null) {
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    if (ProtectionStore(this@AlarmService).read().mode != ProtectionMode.ALARM_TRIGGERED) return
                    val audio = getSystemService(AudioManager::class.java)
                    val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                    val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    if (current < maximum) {
                        runCatching { AlarmVolumeController.raiseToMaximum(this@AlarmService, audio) }
                    }
                }
            }
            alarmVolumeObserver = observer
            runCatching {
                contentResolver.registerContentObserver(
                    Settings.System.getUriFor("volume_alarm"),
                    false,
                    observer
                )
            }.onFailure {
                LocalTamperHistory.record(
                    this,
                    "alarm_volume_observer_unavailable",
                    it.javaClass.simpleName + ": " + it.message
                )
            }
        }
        runCatching {
            AlarmVolumeController.raiseToMaximum(this, getSystemService(AudioManager::class.java))
        }
    }

    private fun startAlarmAudio() {
        val audio = getSystemService(AudioManager::class.java)
        AlarmVolumeController.raiseToMaximum(this, audio)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioManager = audio
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
            val descriptor = resources.openRawResourceFd(R.raw.thiefalertsequre_alarm)
            val prepared = MediaPlayer()
            try {
                prepared.setAudioAttributes(attributes)
                prepared.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            } finally {
                descriptor.close()
            }
            prepared.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            prepared.isLooping = true
            prepared.setOnErrorListener { failedPlayer, _, _ ->
                runCatching { failedPlayer.release() }
                if (player === failedPlayer) player = null
                startFallbackTone()
                true
            }
            prepared.prepare()
            LocalTamperHistory.record(this, "alarm_media_prepared", "Bundled OPUS decoder prepared")
            prepared
        }.onFailure {
            LocalTamperHistory.record(this, "alarm_media_failed", it.javaClass.simpleName + ": " + it.message)
        }.getOrNull()
        if (media == null) {
            startFallbackTone()
            return
        }
        try {
            player = media
            media.start()
            LocalTamperHistory.record(this, "alarm_audio_playing", "Bundled alarm audio started")
        } catch (error: Exception) {
            LocalTamperHistory.record(this, "alarm_audio_start_failed", error.javaClass.simpleName + ": " + error.message)
            runCatching { media.release() }
            player = null
            startFallbackTone()
        }
    }

    private fun startFallbackTone() {
        if (fallbackTone != null) return
        LocalTamperHistory.record(this, "alarm_fallback_started", "Bundled audio unavailable; system alarm tone used")
        fallbackTone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
        handler.removeCallbacks(repeatFallback)
        repeatFallback.run()
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
        alarmVolumeObserver?.let { observer ->
            runCatching { contentResolver.unregisterContentObserver(observer) }
        }
        alarmVolumeObserver = null
        handler.removeCallbacks(repeatFallback)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { fallbackTone?.stopTone() }
        runCatching { fallbackTone?.release() }
        fallbackTone = null
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
