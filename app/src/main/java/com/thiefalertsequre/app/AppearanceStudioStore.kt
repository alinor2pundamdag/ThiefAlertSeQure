package com.thiefalertsequre.app

import android.content.Context
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
    fun iconIndex(context: Context) = prefs(context).getInt(KEY_ICON, 0).coerceIn(0, icons.lastIndex)
    fun backgroundIndex(context: Context) = prefs(context).getInt(KEY_BACKGROUND, 0).coerceIn(0, backgrounds.lastIndex)
    fun animationIndex(context: Context) = prefs(context).getInt(KEY_ANIMATION, 1).coerceIn(0, animations.lastIndex)
    fun reduceMotion(context: Context) = prefs(context).getBoolean(KEY_REDUCE_MOTION, false)
    fun haptics(context: Context) = prefs(context).getBoolean(KEY_HAPTICS, false)
    fun audio(context: Context) = prefs(context).getBoolean(KEY_AUDIO, false)

    fun saveIcon(context: Context, index: Int) = index in icons.indices && prefs(context).edit().putInt(KEY_ICON, index).commit()
    fun saveBackground(context: Context, index: Int) = index in backgrounds.indices && prefs(context).edit().putInt(KEY_BACKGROUND, index).commit()
    fun saveAnimation(context: Context, index: Int) = index in animations.indices && prefs(context).edit().putInt(KEY_ANIMATION, index).commit()
    fun setReduceMotion(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_REDUCE_MOTION, value).commit()
    fun setHaptics(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_HAPTICS, value).commit()
    fun setAudio(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_AUDIO, value).commit()

    fun backgroundDrawable(context: Context, palette: ThemePalette): GradientDrawable {
        val colors = when (backgroundIndex(context)) {
            1 -> intArrayOf(palette.background, blend(palette.background, palette.primary, 0.16f), palette.surface)
            2 -> intArrayOf(0xFF071A24.toInt(), blend(palette.background, 0xFF286B68.toInt(), 0.30f), palette.background)
            3 -> intArrayOf(0xFF24130F.toInt(), blend(palette.background, 0xFF9A4C2A.toInt(), 0.28f), palette.background)
            4 -> intArrayOf(0xFF090C10.toInt(), 0xFF252B33.toInt(), palette.background)
            5 -> intArrayOf(0xFFF7F5F0.toInt(), 0xFFECE8DF.toInt(), 0xFFFFFFFF.toInt())
            6 -> intArrayOf(0xFF0B241D.toInt(), 0xFF1A5140.toInt(), palette.background)
            7 -> intArrayOf(0xFF101112.toInt(), 0xFF292D32.toInt(), 0xFF101112.toInt())
            else -> intArrayOf(palette.background, palette.background)
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = 0f }
    }

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
        image.setImageDrawable(GradientDrawable().apply {
            shape = when (index % 4) {
                0 -> GradientDrawable.OVAL
                1 -> GradientDrawable.RECTANGLE
                2 -> GradientDrawable.RING
                else -> GradientDrawable.RECTANGLE
            }
            cornerRadius = if (index % 4 == 3) 100f else 16f
            setColor(palette.surface)
            setStroke(3, palette.primary)
        })
        image.contentDescription = "Selected app mark: ${icons[index]}"
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
