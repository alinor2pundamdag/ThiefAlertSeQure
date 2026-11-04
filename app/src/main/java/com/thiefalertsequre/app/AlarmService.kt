package com.thiefalertsequre.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AlarmService : Service() {
    private var tone: ToneGenerator? = null
    private val channelId = "anti_theft_alarm"

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(channelId, "Anti-theft alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Active anti-theft alarm status"
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = ProtectionStore(this).read()
        if (intent?.action == ACTION_STOP_IF_AUTHENTICATED && state.mode == ProtectionMode.DISARMED) {
            stopAlarm(); stopSelf(); return START_NOT_STICKY
        }
        if (state.mode != ProtectionMode.ALARM_TRIGGERED) { stopSelf(); return START_NOT_STICKY }
        startForeground(42, notification())
        if (tone == null) {
            val audio = getSystemService(AudioManager::class.java)
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 100).also {
                it.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 60_000)
            }
        }
        return START_STICKY
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("ThiefAlertSeQure alarm active")
            .setContentText("Authenticate with your device lock or biometrics in the app to stop.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun stopAlarm() { tone?.stopTone(); tone?.release(); tone = null }
    override fun onDestroy() { stopAlarm(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.thiefalertseq ure.START".replace(" ", "")
        const val ACTION_STOP_IF_AUTHENTICATED = "com.thiefalertseq ure.STOP_AUTH".replace(" ", "")
    }
}
