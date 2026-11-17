package com.thiefalertsequre.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Functional catalog for local icon, background, animation, audio and intro preferences. */
class AppearanceStudioOptionsActivity : FragmentActivity() {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun params(top: Int = 8) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val palette = ThemeStore.get(this)
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }
        scroll.addView(root)
        root.addView(MaterialButton(this).apply {
            text = "‹  Appearance Studio"
            isAllCaps = false
            minHeight = dp(48)
            setOnClickListener { finish() }
        }, params(0))
        root.addView(TextView(this).apply {
            text = "PERSONALIZATION"
            textSize = 11f
            setTextColor(palette.primary)
        }, params(18))
        root.addView(TextView(this).apply {
            text = "Make it yours"
            textSize = 28f
            setTextColor(palette.text)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, params(4))
        root.addView(TextView(this).apply {
            text = "Selections are saved locally. Protection state, alarm behavior and owner authentication are never changed by appearance controls."
            textSize = 13f
            setTextColor(palette.muted)
        }, params(6))

        choiceButton("App mark • ${AppearanceStudioStore.icons[AppearanceStudioStore.iconIndex(this)]}", AppearanceStudioStore.icons) {
            AppearanceStudioStore.saveIcon(this, it)
            render()
        }
        choiceButton("Background • ${AppearanceStudioStore.backgrounds[AppearanceStudioStore.backgroundIndex(this)]}", AppearanceStudioStore.backgrounds) {
            AppearanceStudioStore.saveBackground(this, it)
            render()
        }
        choiceButton("Button animation • ${AppearanceStudioStore.animations[AppearanceStudioStore.animationIndex(this)]}", AppearanceStudioStore.animations) {
            AppearanceStudioStore.saveAnimation(this, it)
            render()
        }

        val presets = listOf(AlarmTonePreset.SIREN, AlarmTonePreset.PULSE, AlarmTonePreset.DOUBLE, AlarmTonePreset.URGENT, AlarmTonePreset.CONTINUOUS)
        val current = AlarmSoundStore.selectedBuiltInPreset(this)
        choiceButton("Built-in alarm sound • ${current.label}", presets.map { it.label }) { index ->
            AlarmSoundStore.setBuiltInPreset(this, presets[index])
            render()
        }

        toggleButton("Reduced motion", AppearanceStudioStore.reduceMotion(this)) {
            AppearanceStudioStore.setReduceMotion(this, it)
            render()
        }
        toggleButton("Intro haptics", AppearanceStudioStore.haptics(this)) {
            AppearanceStudioStore.setHaptics(this, it)
            render()
        }
        toggleButton("Intro audio", AppearanceStudioStore.audio(this)) {
            AppearanceStudioStore.setAudio(this, it)
            render()
        }
        root.addView(MaterialButton(this).apply {
            text = "Replay Seismic Genesis intro"
            isAllCaps = false
            minHeight = dp(52)
            setOnClickListener {
                startActivity(Intent(this@AppearanceStudioOptionsActivity, SeismicGenesisIntroActivity::class.java)
                    .putExtra(SeismicGenesisIntroActivity.EXTRA_REPLAY, true))
            }
        }, params(18))
        setContentView(scroll)
        AndroidDeviceCompatibility.applySystemBarInsets(scroll)
        ThemeRuntime.apply(this, scroll)
    }

    private fun choiceButton(title: String, choices: List<String>, onChoice: (Int) -> Unit) {
        root.addView(MaterialButton(this).apply {
            text = title
            isAllCaps = false
            minHeight = dp(52)
            setOnClickListener {
                MaterialAlertDialogBuilder(this@AppearanceStudioOptionsActivity)
                    .setTitle(title.substringBefore(" •"))
                    .setSingleChoiceItems(choices.toTypedArray(), -1) { dialog, which ->
                        onChoice(which)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .showThemed(this@AppearanceStudioOptionsActivity)
            }
        }, params())
    }

    private fun toggleButton(title: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
        root.addView(MaterialButton(this).apply {
            text = "$title: ${if (enabled) "ON" else "OFF"}"
            isAllCaps = false
            minHeight = dp(52)
            setOnClickListener { onChange(!enabled) }
        }, params())
    }
}
