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
 val all=listOf(
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
 fun valid(id:String?)=all.any{it.id==id}
 fun selected(c:Context):String { val id=runCatching{c.getSharedPreferences(PREFS,0).getString(KEY,null)}.getOrNull(); return if(valid(id)) id!! else DEFAULT_ID }
 fun get(c:Context,id:String=selected(c))=all.firstOrNull{it.id==id}?:all.first{it.id==DEFAULT_ID}
 fun save(c:Context,id:String)=valid(id)&&c.getSharedPreferences(PREFS,0).edit().putString(KEY,id).commit()
}
object ThemeRuntime {
 fun apply(a:Activity,root:View,id:String=ThemeStore.selected(a)){
  val p=ThemeStore.get(a,id); a.window.statusBarColor=p.background; a.window.navigationBarColor=p.background
  @Suppress("DEPRECATION") run { a.window.decorView.systemUiVisibility=if(p.mode=="Light") View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0 }
  val decor=a.window.decorView
  val previousId=decor.tag as? String ?: ThemeStore.DEFAULT_ID
  val previous=ThemeStore.get(a,previousId)
  val old=mapOf(previous.primary to p.primary,previous.onPrimary to p.onPrimary,previous.background to p.background,previous.surface to p.surface,previous.variant to p.variant,previous.text to p.text,previous.muted to p.muted,previous.success to p.success,previous.warning to p.warning,previous.danger to p.danger)
  fun walk(v:View){
   if(v is TextView) old[v.currentTextColor]?.let(v::setTextColor)
   if(v is MaterialCardView){old[v.cardBackgroundColor.defaultColor]?.let(v::setCardBackgroundColor);v.strokeColor=p.variant}
   else if(v.background is ColorDrawable) old[(v.background as ColorDrawable).color]?.let(v::setBackgroundColor)
   if(v is MaterialButton){ val tint=v.backgroundTintList?.defaultColor; old[tint]?.let{v.backgroundTintList=ColorStateList.valueOf(it)}; val stroke=v.strokeColor?.defaultColor; old[stroke]?.let{v.strokeColor=ColorStateList.valueOf(it)} }
   if(v is ViewGroup) for(i in 0 until v.childCount) walk(v.getChildAt(i))
  }
  walk(root); root.setBackgroundColor(p.background); decor.tag=id
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
