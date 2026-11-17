package com.thiefalertsequre.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView

/** Persisted Appearance Studio choices. All choices remain local to this installation. */
object AppearanceStudioStore {
    private const val PREFS = "appearance_studio_v1"
    private const val KEY_ICON = "icon_variant"
    private const val KEY_BACKGROUND = "background_variant"
    private const val KEY_ANIMATION = "button_animation"
    private const val KEY_REDUCE_MOTION = "reduce_motion"
    private const val KEY_HAPTICS = "intro_haptics"
    private const val KEY_AUDIO = "intro_audio"

    val icons = listOf(
        "Shield", "Crest", "Hexagon", "Sentinel", "Fortress",
        "Pulse", "Beacon", "Guardian", "Aegis", "Monogram",
        "Orbit", "Prism", "Vector", "Classic", "Minimal"
    )
    val backgrounds = listOf("Midnight", "Ocean", "Aurora", "Ember", "Graphite", "Paper", "Mint", "Monochrome")
    val animations = listOf("Static", "Press Scale", "Soft Fade", "Lift", "Pulse")
    val iconGlyphs = listOf("⬟", "♜", "⬢", "◈", "▣", "ϟ", "◉", "✦", "⬡", "T", "◎", "◇", "➤", "盾", "●")

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun iconIndex(context: Context) = SafePreferences.int(prefs(context), KEY_ICON, 0).coerceIn(0, icons.lastIndex)
    fun backgroundIndex(context: Context) = SafePreferences.int(prefs(context), KEY_BACKGROUND, 0).coerceIn(0, backgrounds.lastIndex)
    fun animationIndex(context: Context) = SafePreferences.int(prefs(context), KEY_ANIMATION, 1).coerceIn(0, animations.lastIndex)
    fun reduceMotion(context: Context) = SafePreferences.boolean(prefs(context), KEY_REDUCE_MOTION, false)
    fun haptics(context: Context) = SafePreferences.boolean(prefs(context), KEY_HAPTICS, false)
    fun audio(context: Context) = SafePreferences.boolean(prefs(context), KEY_AUDIO, false)

    fun saveIcon(context: Context, index: Int) = index in icons.indices && prefs(context).edit().putInt(KEY_ICON, index).commit()
    fun saveBackground(context: Context, index: Int) = index in backgrounds.indices && prefs(context).edit().putInt(KEY_BACKGROUND, index).commit()
    fun saveAnimation(context: Context, index: Int) = index in animations.indices && prefs(context).edit().putInt(KEY_ANIMATION, index).commit()
    fun setReduceMotion(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_REDUCE_MOTION, value).commit()
    fun setHaptics(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_HAPTICS, value).commit()
    fun setAudio(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_AUDIO, value).commit()

    fun backgroundDrawable(context: Context, palette: ThemePalette): GradientDrawable {
        val colors = when (backgroundIndex(context)) {
            1 -> intArrayOf(palette.background, blend(palette.background, palette.primary, 0.10f), palette.surface)
            2 -> intArrayOf(palette.background, blend(palette.background, palette.success, 0.10f), palette.background)
            3 -> intArrayOf(palette.background, blend(palette.background, palette.warning, 0.08f), palette.surface)
            4 -> intArrayOf(palette.background, palette.variant, palette.surface)
            5 -> if (palette.mode == "Light")
                intArrayOf(0xFFF7F5F0.toInt(), 0xFFECE8DF.toInt(), 0xFFFFFFFF.toInt())
            else intArrayOf(0xFF211F1A.toInt(), 0xFF302D26.toInt(), palette.background)
            6 -> if (palette.mode == "Light")
                intArrayOf(0xFFE8F4EE.toInt(), 0xFFD8EDE2.toInt(), palette.background)
            else intArrayOf(0xFF0B241D.toInt(), 0xFF1A5140.toInt(), palette.background)
            7 -> intArrayOf(palette.background, blend(palette.background, palette.muted, 0.08f), palette.background)
            else -> intArrayOf(palette.background, palette.background)
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = 0f }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun applyButtonAnimation(context: Context, view: View) {
        if (view.getTag(R.id.batch_three_animation_bound) == true) return
        view.setTag(R.id.batch_three_animation_bound, true)
        view.setOnTouchListener { target, event ->
            if (reduceMotion(context)) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> when (animationIndex(context)) {
                    1 -> target.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90L).start()
                    2 -> target.animate().alpha(0.72f).setDuration(90L).start()
                    3 -> target.animate().translationY(-2f).setDuration(90L).start()
                    4 -> target.animate().scaleX(1.025f).scaleY(1.025f).setDuration(110L).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f).translationY(0f).setDuration(120L).start()
            }
            false
        }
    }

    fun applyIconPreview(context: Context, image: ImageView, palette: ThemePalette) {
        val index = iconIndex(context)
        image.setImageDrawable(AppearanceMarkDrawable(index, palette))
        image.contentDescription = "Selected app mark: ${icons[index]}"
    }

    private class AppearanceMarkDrawable(
        private val variant: Int,
        private val palette: ThemePalette
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val b = bounds
            val side = minOf(b.width(), b.height()).toFloat()
            if (side <= 0f) return
            val save = canvas.save()
            canvas.translate(b.left + (b.width() - side) / 2f, b.top + (b.height() - side) / 2f)
            canvas.scale(side / 100f, side / 100f)
            paint.style = Paint.Style.FILL
            paint.color = palette.surface
            canvas.drawRoundRect(RectF(2f, 2f, 98f, 98f), 22f, 22f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = palette.primary
            val path = Path()
            when (variant) {
                0, 13 -> { // Shield / classic shield
                    path.moveTo(50f, 10f); path.lineTo(84f, 23f); path.lineTo(80f, 59f)
                    path.cubicTo(77f, 76f, 62f, 86f, 50f, 92f)
                    path.cubicTo(38f, 86f, 23f, 76f, 20f, 59f); path.lineTo(16f, 23f); path.close()
                    canvas.drawPath(path, paint)
                    if (variant == 13) { path.reset(); path.moveTo(33f, 51f); path.lineTo(45f, 63f); path.lineTo(68f, 39f); canvas.drawPath(path, paint) }
                }
                1 -> { // Crest
                    path.moveTo(18f, 30f); path.lineTo(30f, 38f); path.lineTo(38f, 20f)
                    path.lineTo(50f, 36f); path.lineTo(62f, 20f); path.lineTo(70f, 38f); path.lineTo(82f, 30f)
                    path.lineTo(76f, 75f); path.lineTo(24f, 75f); path.close(); canvas.drawPath(path, paint)
                }
                2 -> { // Hexagon
                    for (i in 0..5) { val a = Math.PI / 3.0 * i - Math.PI / 6.0; val x=50f+34f*kotlin.math.cos(a).toFloat(); val y=50f+34f*kotlin.math.sin(a).toFloat(); if(i==0) path.moveTo(x,y) else path.lineTo(x,y) }
                    path.close(); canvas.drawPath(path, paint)
                }
                3, 7 -> { // Sentinel / guardian eye
                    canvas.drawCircle(50f, 50f, 31f, paint)
                    canvas.drawCircle(50f, 50f, 10f, paint)
                    if (variant == 3) canvas.drawLine(50f, 8f, 50f, 25f, paint)
                }
                4 -> { // Fortress
                    canvas.drawRect(22f, 35f, 78f, 82f, paint)
                    canvas.drawRect(16f, 22f, 34f, 43f, paint)
                    canvas.drawRect(43f, 14f, 57f, 35f, paint)
                    canvas.drawRect(66f, 22f, 84f, 43f, paint)
                }
                5 -> { // Pulse
                    path.moveTo(12f, 52f); path.lineTo(31f, 52f); path.lineTo(41f, 29f)
                    path.lineTo(54f, 73f); path.lineTo(65f, 44f); path.lineTo(74f, 52f); path.lineTo(88f, 52f)
                    canvas.drawPath(path, paint)
                }
                6 -> { // Beacon
                    canvas.drawCircle(50f, 58f, 15f, paint)
                    canvas.drawLine(50f, 10f, 50f, 29f, paint)
                    canvas.drawLine(20f, 26f, 33f, 38f, paint)
                    canvas.drawLine(80f, 26f, 67f, 38f, paint)
                }
                8 -> { // Aegis diamond
                    path.moveTo(50f, 10f); path.lineTo(86f, 50f); path.lineTo(50f, 90f); path.lineTo(14f, 50f); path.close(); canvas.drawPath(path, paint)
                }
                9 -> { // Monogram T
                    paint.textSize = 68f; paint.textAlign = Paint.Align.CENTER; paint.style = Paint.Style.FILL
                    canvas.drawText("T", 50f, 74f, paint); paint.style = Paint.Style.STROKE
                }
                10 -> { // Orbit
                    canvas.drawCircle(50f, 50f, 26f, paint); canvas.drawOval(12f, 34f, 88f, 66f, paint)
                    paint.style = Paint.Style.FILL; canvas.drawCircle(76f, 30f, 7f, paint); paint.style = Paint.Style.STROKE
                }
                11 -> { // Prism
                    path.moveTo(50f, 12f); path.lineTo(86f, 80f); path.lineTo(14f, 80f); path.close()
                    canvas.drawPath(path, paint); canvas.drawLine(50f, 12f, 50f, 80f, paint)
                }
                12 -> { // Vector
                    path.moveTo(16f, 50f); path.lineTo(68f, 50f); path.moveTo(52f, 32f); path.lineTo(72f, 50f); path.lineTo(52f, 68f)
                    canvas.drawPath(path, paint)
                }
                else -> { // Minimal
                    paint.style = Paint.Style.FILL; canvas.drawCircle(50f, 50f, 22f, paint)
                }
            }
            canvas.restoreToCount(save)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android graphics API")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun blend(first: Int, second: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = (first shr shift) and 0xFF
            val b = (second shr shift) and 0xFF
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
