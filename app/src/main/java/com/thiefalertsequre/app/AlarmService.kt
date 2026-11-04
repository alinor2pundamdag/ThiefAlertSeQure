package com.thiefalertsequre.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import androidx.core.app.NotificationCompat

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var fallbackTone: ToneGenerator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { /* Keep alarm state active on focus changes. */ }
    private val handler = Handler(Looper.getMainLooper())
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
            stopAlarm()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        if (player == null && fallbackTone == null) startAlarmAudio()
        return START_STICKY
    }

    private fun startAlarmAudio() {
        val audio = getSystemService(AudioManager::class.java)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        }
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
            MediaPlayer.create(this, R.raw.thiefalertsequre_alarm, attributes)
        }.getOrNull()
        if (media == null) {
            startFallbackTone()
            return
        }
        try {
            media.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            media.isLooping = true
            media.setOnErrorListener { failedPlayer, _, _ ->
                runCatching { failedPlayer.release() }
                if (player === failedPlayer) player = null
                startFallbackTone()
                true
            }
            media.start()
            player = media
        } catch (_: Exception) {
            runCatching { media.release() }
            startFallbackTone()
        }
    }

    private fun startFallbackTone() {
        if (fallbackTone != null) return
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
