package com.thiefalertsequre.app
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class ThemePalette(val id:String,val name:String,val mode:String,val primary:Int,val onPrimary:Int,val background:Int,val surface:Int,val variant:Int,val text:Int,val muted:Int,val success:Int,val warning:Int,val danger:Int)
object ThemeStore {
 const val DEFAULT_ID="midnight"; private const val PREFS="tas_theme_preferences_v1"; private const val KEY="selected_theme_id"
 private fun color(hex:String):Int {
  val value=hex.removePrefix("#")
  require(value.length==6) { "Theme colors must be six-digit RGB values" }
  return (0xFF shl 24) or
   (value.substring(0,2).toInt(16) shl 16) or
   (value.substring(2,4).toInt(16) shl 8) or
   value.substring(4,6).toInt(16)
 }
 private fun p(id:String,n:String,m:String,a:String,on:String,b:String,s:String,v:String,t:String,mu:String,ok:String,w:String,d:String)=ThemePalette(id,n,m,color(a),color(on),color(b),color(s),color(v),color(t),color(mu),color(ok),color(w),color(d))
 private val base=listOf(
 p("midnight","Midnight","Dark","#57C7FF","#002B43","#07111F","#111F31","#1A2B40","#F2F7FC","#A8B8CA","#43D6A1","#FFC66D","#FF647C"),
 p("ocean","Ocean","Dark","#42B8E8","#062B3A","#061722","#0B2635","#12394A","#EAF8FF","#9BBAC8","#52D6A7","#F5C66B","#FF7185"),
 p("forest","Forest","Dark","#75D69A","#12331F","#0B1711","#14271B","#203B2A","#F0FFF4","#AAC6B1","#6FE0A0","#E9C66C","#FF7882"),
 p("amethyst","Amethyst","Dark","#C4A1FF","#28164B","#130D20","#211832","#302346","#F7F0FF","#C0B3D1","#70DDB0","#F3C56F","#FF718F"),
 p("graphite","Graphite","Dark","#D0D7E2","#202832","#111418","#1B2027","#292F38","#F4F6F8","#ADB6C2","#6ED6A5","#E8C16C","#FF7180"),
 p("ember","Ember","Dark","#FFAB70","#49220B","#1B100C","#2A1B15","#3D291F","#FFF4EC","#D2B6A5","#7BDEA8","#FFD078","#FF7180"),
 p("royal","Royal","Dark","#91A9FF","#182B68","#0B1024","#151D3A","#202D50","#F0F3FF","#B1BCDF","#68D6A8","#F0C66D","#FF7188"),
 p("mono","Monochrome","Dark","#E5E7EB","#202124","#101112","#1C1D1F","#2B2D30","#F7F7F7","#B9BBBF","#85D6AC","#E8C875","#FF7A85"),
 p("cloud","Cloud","Light","#176B9A","#FFFFFF","#F3F7FB","#FFFFFF","#E7EEF5","#172635","#526477","#167A55","#956000","#B4233C"),
 p("paper","Paper","Light","#345D85","#FFFFFF","#F7F5F0","#FFFFFF","#ECE8DF","#25231F","#68645C","#167A55","#956000","#B4233C"),
 p("mint","Mint","Light","#147D68","#FFFFFF","#EFF8F4","#FFFFFF","#DDEFE7","#18352D","#547269","#167A55","#956000","#B4233C"),
 p("lavender","Lavender","Light","#6650A4","#FFFFFF","#F5F1FB","#FFFFFF","#E9E1F4","#2D2440","#716681","#167A55","#956000","#B4233C"),
 p("rose","Rose","Light","#A23D62","#FFFFFF","#FFF3F6","#FFFFFF","#F5DFE7","#40232E","#80616D","#167A55","#956000","#B4233C"),
 p("sand","Sand","Light","#79552B","#FFFFFF","#FAF5EB","#FFFFFF","#EFE4D1","#382D20","#786B59","#167A55","#956000","#B4233C"),
 p("sky","Sky","Light","#1769AA","#FFFFFF","#EFF7FF","#FFFFFF","#DCEBFA","#183047","#58728A","#167A55","#956000","#B4233C"),
 p("contrast","High Contrast","Light","#003B70","#FFFFFF","#FFFFFF","#FFFFFF","#E5E5E5","#000000","#333333","#006B3C","#805000","#A40019"))
 val all = base + generatedPalettes()
 private fun generatedPalettes(): List<ThemePalette> = (0 until 104).map { index ->
  val hue = (index % 13) * (360f / 13f)
  val light = index % 8 >= 4
  val variant = index % 4
  val saturation = 0.35f + variant * 0.10f
  val primaryLightness = if (light) 0.05f + variant * 0.025f else 0.62f + variant * 0.06f
  fun hsl(h: Float, s: Float, l: Float): Int {
   val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
   val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
   val m = l - c / 2f
   val rgb = when {
    h < 60f -> Triple(c, x, 0f)
    h < 120f -> Triple(x, c, 0f)
    h < 180f -> Triple(0f, c, x)
    h < 240f -> Triple(0f, x, c)
    h < 300f -> Triple(x, 0f, c)
    else -> Triple(c, 0f, x)
   }
   val red = ((rgb.first + m) * 255f).toInt().coerceIn(0, 255)
   val green = ((rgb.second + m) * 255f).toInt().coerceIn(0, 255)
   val blue = ((rgb.third + m) * 255f).toInt().coerceIn(0, 255)
   return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
  }
  val primary = hsl(hue, saturation, primaryLightness)
  val background = if (light) 0xFFF7F8FA.toInt() else 0xFF080D16.toInt()
  val surface = if (light) 0xFFFFFFFF.toInt() else 0xFF121B29.toInt()
  val variantColor = if (light) 0xFFE7ECF2.toInt() else 0xFF202D3E.toInt()
  val textColor = if (light) 0xFF17212B.toInt() else 0xFFF3F6FA.toInt()
  val mutedColor = if (light) 0xFF526171.toInt() else 0xFFB2BECC.toInt()
  ThemePalette(
   "spectrum_${index + 17}", "Spectrum ${index + 17}", if (light) "Light" else "Dark",
   primary, if (light) 0xFFFFFFFF.toInt() else 0xFF07111F.toInt(),
   background, surface, variantColor, textColor, mutedColor,
   if (light) 0xFF146B48.toInt() else 0xFF43D6A1.toInt(),
   if (light) 0xFF805000.toInt() else 0xFFFFC66D.toInt(),
   if (light) 0xFFA40019.toInt() else 0xFFFF647C.toInt()
  )
 }
 fun valid(id:String?)=all.any{it.id==id}
 fun selected(c:Context):String { val id=SafePreferences.string(c.getSharedPreferences(PREFS,0),KEY,null); return if(valid(id)) id!! else DEFAULT_ID }
 fun get(c:Context,id:String=selected(c))=all.firstOrNull{it.id==id}?:all.first{it.id==DEFAULT_ID}
 fun save(c:Context,id:String)=valid(id)&&c.getSharedPreferences(PREFS,0).edit().putString(KEY,id).commit()
}
internal object ThemeColorMapping {
    fun colors(source: ThemePalette, target: ThemePalette): Map<Int, Int> = mapOf(
        source.primary to target.primary,
        source.onPrimary to target.onPrimary,
        source.background to target.background,
        source.surface to target.surface,
        source.variant to target.variant,
        source.text to target.text,
        source.muted to target.muted,
        source.success to target.success,
        source.warning to target.warning,
        source.danger to target.danger
    )

    fun paletteColors(palette: ThemePalette): Set<Int> = setOf(
        palette.primary, palette.onPrimary, palette.background, palette.surface,
        palette.variant, palette.text, palette.muted, palette.success,
        palette.warning, palette.danger
    )

    /** Preserve colors already authored for the destination palette. */
    fun remap(color: Int, source: ThemePalette, target: ThemePalette): Int {
        if (color in paletteColors(target)) return color
        return colors(source, target)[color] ?: color
    }
}

object ThemeRuntime {
 fun apply(
  a:Activity,
  root:View,
  id:String=ThemeStore.selected(a),
  initialSourceId:String=ThemeStore.DEFAULT_ID
 ){
  val p=ThemeStore.get(a,id); a.window.statusBarColor=p.background; a.window.navigationBarColor=p.background
  @Suppress("DEPRECATION") run { a.window.decorView.systemUiVisibility=if(p.mode=="Light") View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0 }
  val decor=a.window.decorView
  fun walk(v:View){
   val appliedId=v.getTag(R.id.theme_palette_applied) as? String
   val source=ThemeStore.get(a,appliedId ?: initialSourceId)
   if(v is TextView) {
    val color=v.currentTextColor
    val mapped=ThemeColorMapping.remap(color,source,p)
    if(mapped!=color) v.setTextColor(mapped)
   }
   if(v is MaterialCardView){
    val color=v.cardBackgroundColor.defaultColor
    val mapped=ThemeColorMapping.remap(color,source,p)
    if(mapped!=color) v.setCardBackgroundColor(mapped)
    v.strokeColor=p.variant
   } else if(v.background is ColorDrawable) {
    val color=(v.background as ColorDrawable).color
    val mapped=ThemeColorMapping.remap(color,source,p)
    if(mapped!=color) v.setBackgroundColor(mapped)
   }
   if(v is MaterialButton){
    val tint=v.backgroundTintList?.defaultColor
    tint?.let { color ->
     val mapped=ThemeColorMapping.remap(color,source,p)
     if(mapped!=color) v.backgroundTintList=ColorStateList.valueOf(mapped)
    }
    val stroke=v.strokeColor?.defaultColor
    stroke?.let { color ->
     val mapped=ThemeColorMapping.remap(color,source,p)
     if(mapped!=color) v.strokeColor=ColorStateList.valueOf(mapped)
    }
    AppearanceStudioStore.applyButtonAnimation(a, v)
   }
   v.setTag(R.id.theme_palette_applied,id)
   if(v is ViewGroup) for(i in 0 until v.childCount) walk(v.getChildAt(i))
  }
  walk(root); root.background=AppearanceStudioStore.backgroundDrawable(a,p)
  root.setTag(R.id.theme_palette_applied,id); decor.tag=id
 }

 fun applyDialog(activity:Activity,dialog:AlertDialog){
  val p=ThemeStore.get(activity)
  val density=activity.resources.displayMetrics.density
  dialog.window?.setBackgroundDrawable(ColorDrawable(p.surface))
  val dialogRoot=dialog.window?.decorView
  fun recolor(view:View){
   if(view is TextView) view.setTextColor(p.text)
   if(view is MaterialCardView){view.setCardBackgroundColor(p.surface);view.strokeColor=p.variant}
   if(view is ViewGroup) for(index in 0 until view.childCount) recolor(view.getChildAt(index))
  }
  if(dialogRoot!=null) recolor(dialogRoot)
  val minimumButtonHeight=(48f*density).toInt()
  dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply{setTextColor(p.primary);minHeight=minimumButtonHeight}
  dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply{setTextColor(p.muted);minHeight=minimumButtonHeight}
  dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply{setTextColor(p.muted);minHeight=minimumButtonHeight}
 }
}

fun MaterialAlertDialogBuilder.showThemed(activity:Activity):AlertDialog{
 val dialog=create()
 dialog.setOnShowListener{ThemeRuntime.applyDialog(activity,dialog)}
 dialog.show()
 return dialog
}
