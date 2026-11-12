package com.thiefalertsequre.app
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
class ThemeActivity:FragmentActivity(){
 private var preview=ThemeStore.selected(this);private var mode="Dark"
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);render()}
 override fun onResume(){super.onResume();ThemeRuntime.apply(this,window.decorView,preview)}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun render(){
  val scroll=ScrollView(this).apply{isFillViewport=true};val root=LinearLayout(this).apply{orientation=1;setPadding(dp(20),dp(20),dp(20),dp(28))};scroll.addView(root)
  root.addView(MaterialButton(this).apply{text="‹ Back";setOnClickListener{finish()}})
  root.addView(TextView(this).apply{text="APPEARANCE";textSize=11f;setTextColor(ThemeStore.get(this@ThemeActivity,preview).primary)})
  root.addView(TextView(this).apply{text="Choose your theme";textSize=28f;setTextColor(ThemeStore.get(this@ThemeActivity,preview).text)})
  root.addView(TextView(this).apply{text="Tap a theme to preview it. Apply saves it locally; Cancel leaves your current theme unchanged.";textSize=13f;setTextColor(ThemeStore.get(this@ThemeActivity,preview).muted)})
  val filters=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER}
  listOf("Dark","Light").forEach{v->filters.addView(MaterialButton(this).apply{text=v;setOnClickListener{mode=v;render()}},LinearLayout.LayoutParams(0,dp(48),1f))}
  root.addView(filters)
  val p=ThemeStore.get(this,preview);val card=MaterialCardView(this).apply{radius=dp(16).toFloat();setCardBackgroundColor(p.surface);strokeColor=p.variant;strokeWidth=dp(1)}
  val sample=LinearLayout(this).apply{orientation=1;setPadding(dp(15),dp(15),dp(15),dp(15))}
  sample.addView(TextView(this).apply{text="LIVE PREVIEW • ${p.name}";textSize=12f;setTextColor(p.primary)})
  sample.addView(TextView(this).apply{text="Protection dashboard";textSize=22f;setTextColor(p.text)})
  sample.addView(TextView(this).apply{text="Verified setup • Local protection";textSize=13f;setTextColor(p.muted)})
  sample.addView(MaterialButton(this).apply{text="Protection enabled";backgroundTintList=android.content.res.ColorStateList.valueOf(p.primary);setTextColor(p.onPrimary)})
  card.addView(sample);root.addView(card)
  ThemeStore.all.filter{it.mode==mode}.forEach{t->root.addView(MaterialButton(this).apply{text=(if(preview==t.id)"✓ " else "")+t.name;isAllCaps=false;backgroundTintList=android.content.res.ColorStateList.valueOf(t.surface);setTextColor(t.text);strokeColor=android.content.res.ColorStateList.valueOf(t.primary);strokeWidth=dp(1);setOnClickListener{preview=t.id;render()}},LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(6)})}
  root.addView(MaterialButton(this).apply{text="Apply selected theme";setOnClickListener{if(ThemeStore.save(this@ThemeActivity,preview)){setResult(RESULT_OK);finish()}}})
  root.addView(MaterialButton(this).apply{text="Cancel";setOnClickListener{finish()}})
  setContentView(scroll);ThemeRuntime.apply(this,scroll,preview)
 }
}
