package com.thiefalertsequre.app
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
class SetupStatusActivity:FragmentActivity(){
 private var rendered=false
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);render()}
 override fun onResume(){super.onResume();if(rendered)render() }
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun render(){
  val scroll=ScrollView(this).apply{isFillViewport=true};val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(20),dp(20),dp(28))};scroll.addView(root)
  root.addView(MaterialButton(this).apply{text="‹ Back";setOnClickListener{finish()}})
  root.addView(MaterialButton(this).apply{text="Refresh verified status";setOnClickListener{render()}})
  val p=ThemeStore.get(this)
  root.addView(TextView(this).apply{text="VERIFIED ANDROID SETUP";textSize=11f;setTextColor(p.primary)})
  root.addView(TextView(this).apply{text="Protection readiness";textSize=27f;setTextColor(p.text)})
  root.addView(TextView(this).apply{text="Fresh Android checks. Stored preferences alone never prove that a service is running.";textSize=13f;setTextColor(p.muted)})
  SetupStatusSnapshot.read(this).items.forEach{item->
   val color=when(item.state){SetupState.VERIFIED->p.success;SetupState.ACTION_REQUIRED->p.warning;SetupState.NEUTRAL,SetupState.UNSUPPORTED->p.muted;SetupState.UNKNOWN->p.muted}
   val card=MaterialCardView(this).apply{radius=dp(14).toFloat();setCardBackgroundColor(p.surface);strokeColor=p.variant;strokeWidth=dp(1)}
   val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(12),dp(13),dp(12))}
   body.addView(TextView(this).apply{text=item.title;textSize=16f;setTextColor(p.text)})
   body.addView(TextView(this).apply{text=item.state.name.replace('_',' ');textSize=11f;setTextColor(color)})
   body.addView(TextView(this).apply{text=item.detail;textSize=13f;setTextColor(p.muted)})
   SetupStatusSnapshot.action(this,item.id)?.let{intent->
    val actionLabel=when(item.id){
     "monitor"->"Review monitor settings"
     "a2"->"Manage trusted accessories"
     "bt"->"Open Bluetooth settings"
     "bt_permission"->"Open app permissions"
     else->"Open Android settings"
    }
    body.addView(MaterialButton(this).apply{text=actionLabel;minHeight=dp(48);setOnClickListener{runCatching{startActivity(intent)}}})
   }
   card.addView(body);root.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
  }
  setContentView(scroll);ThemeRuntime.apply(this,scroll);rendered=true
 }
}
