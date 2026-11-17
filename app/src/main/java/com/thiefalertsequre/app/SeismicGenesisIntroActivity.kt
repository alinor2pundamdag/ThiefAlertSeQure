package com.thiefalertsequre.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import kotlin.math.min

/** Four-stage, nine-second Canvas-rendered first-launch sequence; privacy acknowledgement remains next. */
class SeismicGenesisIntroActivity : FragmentActivity() {
    private var animator: ValueAnimator? = null
    private var toneGenerator: ToneGenerator? = null
    private var progress = 0f
    private lateinit var artwork: GenesisCanvas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val replay = intent.getBooleanExtra(EXTRA_REPLAY, false)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!replay && prefs.getBoolean(KEY_COMPLETED, false)) {
            openMain()
            return
        }
        val root = FrameLayout(this)
        artwork = GenesisCanvas(this)
        root.addView(artwork, FrameLayout.LayoutParams(-1, -1))
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(32), dp(28), dp(28))
        }
        val stageLabel = TextView(this).apply {
            text = "SEISMIC GENESIS"
            textSize = 12f
            letterSpacing = 0.22f
            setTextColor(0xFF9DDFFF.toInt())
            gravity = Gravity.CENTER
            contentDescription = "Seismic Genesis introduction stage"
        }
        overlay.addView(stageLabel, LinearLayout.LayoutParams(-1, -2))
        overlay.addView(TextView(this).apply {
            text = "A smarter shield.\nA safer device."
            textSize = 30f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        overlay.addView(Button(this).apply {
            text = if (replay) "Close intro" else "Skip intro"
            contentDescription = "Skip the introduction and continue to the privacy notice"
            setOnClickListener { complete() }
            minHeight = dp(48)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        window.statusBarColor = 0xFF050B16.toInt()
        window.navigationBarColor = 0xFF050B16.toInt()
        if (AppearanceStudioStore.reduceMotion(this)) {
            progress = 1f
            artwork.progress = progress
            stageLabel.text = "YOUR DEVICE. YOUR CONTROL."
            artwork.invalidate()
        } else {
            startSequence(stageLabel)
        }
    }

    private fun startSequence(stageLabel: TextView) {
        if (AppearanceStudioStore.haptics(this)) runCatching {
            val vibrator = getSystemService(Vibrator::class.java)
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(35, 45))
                else @Suppress("DEPRECATION") vibrator.vibrate(35)
            }
        }
        if (AppearanceStudioStore.audio(this)) runCatching {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 25).also {
                it.startTone(ToneGenerator.TONE_PROP_ACK, 130)
                Handler(Looper.getMainLooper()).postDelayed({
                    runCatching { it.release() }
                    if (toneGenerator === it) toneGenerator = null
                }, 180L)
            }
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = INTRO_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                artwork.progress = progress
                artwork.invalidate()
                stageLabel.text = when ((progress * 4f).toInt().coerceAtMost(3)) {
                    0 -> "01  •  AWAKEN"
                    1 -> "02  •  DETECT"
                    2 -> "03  •  RESPOND"
                    else -> "04  •  PROTECT"
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isFinishing) complete()
                }
            })
            start()
        }
    }

    private fun complete() {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_COMPLETED, true)
            .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
            .commit()
        if (!saved) {
            Toast.makeText(this, "Could not save intro completion. Tap Continue to retry.", Toast.LENGTH_LONG).show()
            return
        }
        openMain()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onDestroy() {
        animator?.cancel()
        animator = null
        runCatching { toneGenerator?.release() }
        toneGenerator = null
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private class GenesisCanvas(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var progress = 0f
        private val colors = IntArray(20) { index ->
            android.graphics.Color.HSVToColor(floatArrayOf((index * 18f) % 360f, 0.58f, 1f))
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(0xFF050B16.toInt())
            val cx = width / 2f
            val cy = height * 0.43f
            val radius = min(width, height) * (0.12f + 0.18f * progress)
            paint.shader = RadialGradient(cx, cy, radius * 2.8f,
                intArrayOf(0xFF173A59.toInt(), 0xFF0B1728.toInt(), 0xFF050B16.toInt()),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = colors[(progress * 20f).toInt().coerceIn(0, 19)]
            canvas.drawCircle(cx, cy, radius, paint)
            paint.strokeWidth = 1.5f
            canvas.drawCircle(cx, cy, radius * 1.28f, paint)
            paint.style = Paint.Style.FILL
            paint.color = 0xFF57C7FF.toInt()
            canvas.drawCircle(cx, cy, 5f + 9f * progress, paint)
        }
    }

    companion object {
        const val EXTRA_REPLAY = "replay_intro"
        private const val PREFS = "batch_three_intro_v1"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_COMPLETED_AT = "completed_at"
        private const val INTRO_DURATION_MS = 9_000L
    }
}
