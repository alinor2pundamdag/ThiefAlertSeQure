package com.thiefalertsequre.app
import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.app.KeyguardManager
import androidx.core.app.NotificationManagerCompat

enum class SetupState { VERIFIED,ACTION_REQUIRED,NEUTRAL,UNSUPPORTED,UNKNOWN }
data class SetupItem(val id:String,val title:String,val state:SetupState,val detail:String)
data class SetupSnapshot(val items:List<SetupItem>){
 fun dashboardSummary():String{
  val ok=items.count{it.state==SetupState.VERIFIED};val action=items.count{it.state==SetupState.ACTION_REQUIRED};val unknown=items.count{it.state==SetupState.UNKNOWN}
  val rows=items.take(7).joinToString("\n"){ val mark=when(it.state){SetupState.VERIFIED->"✓";SetupState.ACTION_REQUIRED->"!";SetupState.NEUTRAL->"—";SetupState.UNSUPPORTED->"N/A";SetupState.UNKNOWN->"?"};"$mark ${it.title}: ${it.state.name.replace('_',' ')}" }
  return "ANDROID SETUP • $ok verified • $action need attention • $unknown unconfirmed\n$rows\nSee Settings → Verified Android setup status."
 }
}
object SetupStatusSnapshot {
 fun read(c:Context):SetupSnapshot{
  val x=mutableListOf<SetupItem>();val alarm=c.getSystemService(AlarmManager::class.java)
  if(Build.VERSION.SDK_INT>=31){val ok=runCatching{alarm.canScheduleExactAlarms()}.getOrDefault(false);x+=SetupItem("exact","Exact 10-second alarm access",if(ok)SetupState.VERIFIED else SetupState.ACTION_REQUIRED,if(ok)"Android confirms exact-alarm scheduling access." else "Android has not granted exact-alarm special access.")}else x+=SetupItem("exact","Exact alarm access",SetupState.UNSUPPORTED,"No separate access gate on this Android version.")
  val notifs=runCatching{NotificationManagerCompat.from(c).areNotificationsEnabled()}.getOrDefault(false);x+=SetupItem("notifications","App notifications",if(notifs)SetupState.VERIFIED else SetupState.ACTION_REQUIRED,if(notifs)"Android reports notifications enabled." else "Android reports notifications disabled.")
  if(Build.VERSION.SDK_INT>=34){val ok=runCatching{c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()}.getOrDefault(false);x+=SetupItem("fullscreen","Full-screen intent access",if(ok)SetupState.VERIFIED else SetupState.ACTION_REQUIRED,if(ok)"Android confirms access." else "Android does not allow full-screen intents.")}else x+=SetupItem("fullscreen","Full-screen intent access",SetupState.UNSUPPORTED,"No separate access gate on this Android version.")
  if(Build.VERSION.SDK_INT>=23){val ok=runCatching{c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)}.getOrDefault(false);x+=SetupItem("battery","Battery optimization exemption",if(ok)SetupState.VERIFIED else SetupState.ACTION_REQUIRED,if(ok)"Android confirms exemption." else "App remains subject to battery optimization.")}
  val lock=runCatching{c.getSystemService(KeyguardManager::class.java).isDeviceSecure}.getOrDefault(false);x+=SetupItem("lock","Secure device lock",if(lock)SetupState.VERIFIED else SetupState.ACTION_REQUIRED,if(lock)"Android confirms a secure lock." else "No secure PIN, pattern, or password is configured.")
  val perm=Build.VERSION.SDK_INT<31||c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;x+=SetupItem("bt_permission","Bluetooth Connect permission",if(perm)SetupState.VERIFIED else SetupState.ACTION_REQUIRED,if(perm)"Permission granted or not runtime-gated." else "Permission is not granted.")
  val adapter=runCatching{if(perm)c.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled else null}.getOrNull();x+=when(adapter){true->SetupItem("bt","Bluetooth adapter",SetupState.VERIFIED,"Android reports Bluetooth enabled.");false->SetupItem("bt","Bluetooth adapter",SetupState.NEUTRAL,"Bluetooth is off; enable only when using A2.");null->SetupItem("bt","Bluetooth adapter",SetupState.UNKNOWN,"Adapter state could not be verified.")}
  val prefs=c.getSharedPreferences(ProtectionMonitorService.PREFS,0);val enabled=prefs.getBoolean(ProtectionMonitorService.KEY_ENABLED,ProtectionMonitorPolicy.DEFAULT_ENABLED)
  val running=runCatching{c.getSystemService(ActivityManager::class.java).getRunningServices(100).any{it.service.className==ProtectionMonitorService::class.java.name}}.getOrDefault(false)
  x+=when{!enabled->SetupItem("monitor","Protection monitor",SetupState.NEUTRAL,"Monitor is disabled by owner.");running->SetupItem("monitor","Protection monitor",SetupState.VERIFIED,"Android process list confirms the service is running.");else->SetupItem("monitor","Protection monitor",SetupState.UNKNOWN,"Enabled preference exists, but running service is not confirmed.")}
  val a2=prefs.getBoolean(ProtectionMonitorService.KEY_BT_ALERT_ENABLED,false);val trusted=prefs.getString(ProtectionMonitorService.KEY_BT_TRUSTED_NAME,null)
  x+=when{!a2->SetupItem("a2","Bluetooth separation alert",SetupState.NEUTRAL,"Optional A2 is disabled.");trusted.isNullOrBlank()->SetupItem("a2","Bluetooth separation alert",SetupState.ACTION_REQUIRED,"A2 enabled but no accessory selected.");running->SetupItem("a2","Bluetooth separation alert",SetupState.VERIFIED,"Accessory selected and monitor confirmed running.");else->SetupItem("a2","Bluetooth separation alert",SetupState.UNKNOWN,"Monitor service is not confirmed running.")}
  return SetupSnapshot(x)
 }
 fun action(c:Context,id:String):Intent?=when(id){
 "exact"->if(Build.VERSION.SDK_INT>=31)Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${c.packageName}"))else null
 "notifications"->Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.packageName)
 "fullscreen"->if(Build.VERSION.SDK_INT>=34)Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:${c.packageName}"))else null
 "battery"->if(Build.VERSION.SDK_INT>=23)Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:${c.packageName}"))else null
 "lock"->Intent(Settings.ACTION_SECURITY_SETTINGS)
 "bt","bt_permission"->Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
 "monitor","a2"->Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}"))
 else->null}
}
