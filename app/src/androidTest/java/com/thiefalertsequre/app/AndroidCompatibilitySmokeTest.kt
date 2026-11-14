package com.thiefalertsequre.app

import android.app.Activity
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Framework-level smoke coverage across Android API 23–37.
 *
 * These tests verify app launch and manifest declarations on the release package.
 * OEM-specific guidance and capability logic have JVM regression coverage; these
 * tests do not simulate OEM battery managers, real accessories, charger hardware,
 * biometric hardware, camera LEDs or force-stop.
 */
@RunWith(AndroidJUnit4::class)
class AndroidCompatibilitySmokeTest {

    @Test
    fun everyInternalScreenCanBeCreatedAndDestroyed() {
        launch(MainActivity::class.java)
        launch(SettingsActivity::class.java)
        launch(ThemeActivity::class.java)
        launch(SetupStatusActivity::class.java)
        launch(BluetoothCenterActivity::class.java)
        launch(DeviceIntelligenceActivity::class.java)
    }

    @Test
    fun requiredPlatformPermissionsAreDeclared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val declared = packageInfo.requestedPermissions.orEmpty().toSet()

        listOf(
            android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.SCHEDULE_EXACT_ALARM,
            android.Manifest.permission.USE_FULL_SCREEN_INTENT,
            android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK,
            android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
            android.Manifest.permission.REQUEST_INSTALL_PACKAGES,
            android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.Manifest.permission.WAKE_LOCK,
            android.Manifest.permission.VIBRATE
        ).forEach { permission ->
            assertTrue("Manifest must declare $permission", permission in declared)
        }
    }

    @Suppress("NewApi", "DEPRECATION")
    @Test
    fun productionPackageIdentityAndComponentExposureStayStable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.thiefalertsequre.app", context.packageName)
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or
                PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        )
        assertTrue("Candidate versionCode must be newer than v1.1.3 code 5", info.versionCode > 5)

        val main = info.activities.orEmpty().single { it.name.endsWith(".MainActivity") }
        assertTrue("Only the launcher activity is exported", main.exported)
        val boot = info.receivers.orEmpty().single { it.name.endsWith(".BootReceiver") }
        val deadline = info.receivers.orEmpty().single { it.name.endsWith(".AlarmDeadlineReceiver") }
        val admin = info.receivers.orEmpty().single { it.name.endsWith(".ThiefAlertAdminReceiver") }
        assertFalse("Boot receiver must not be externally callable", boot.exported)
        assertFalse("Alarm deadline receiver must not be externally callable", deadline.exported)
        assertTrue("Device-admin receiver must retain the Android binding permission", admin.exported)
        assertEquals("android.permission.BIND_DEVICE_ADMIN", admin.permission)

        val alarm = info.services.orEmpty().single { it.name.endsWith(".AlarmService") }
        val monitor = info.services.orEmpty().single { it.name.endsWith(".ProtectionMonitorService") }
        assertFalse("Alarm service must remain private", alarm.exported)
        assertFalse("Monitor service must remain private", monitor.exported)
        val updates = info.providers.orEmpty().single { it.authority == "${context.packageName}.updates" }
        assertFalse("APK FileProvider must remain non-exported", updates.exported)
        assertTrue("APK FileProvider must grant read access only through URI permissions", updates.grantUriPermissions)
    }

    @Suppress("NewApi")
    @Test
    fun foregroundServiceTypesRemainDeclaredWhereSupported() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_SERVICES
        )
        val alarm = info.services.orEmpty().single { it.name.endsWith(".AlarmService") }
        val monitor = info.services.orEmpty().single { it.name.endsWith(".ProtectionMonitorService") }
        if (Build.VERSION.SDK_INT >= 29) {
            assertTrue(
                "Alarm service must declare mediaPlayback FGS type",
                alarm.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK != 0
            )
        }
        if (Build.VERSION.SDK_INT >= 34) {
            assertTrue(
                "Protection monitor must declare specialUse FGS type",
                monitor.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0
            )
        }
    }

    @Test
    fun setupSnapshotCanInspectPermissionsAndSettingsOnEverySupportedApi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = SetupStatusSnapshot.read(context)

        assertTrue("Setup snapshot must contain readiness items", snapshot.items.isNotEmpty())
        listOf("exact", "notifications", "battery", "lock", "monitor").forEach { id ->
            assertTrue("Missing setup status item: $id", snapshot.items.any { it.id == id })
        }
    }

    @Test
    fun alarmDeadlineSchedulingHasACompatibleFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nowWall = System.currentTimeMillis()
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val bootCount = ProtectionClock.bootCount(context)

        AlarmScheduler.schedule(
            context,
            nowWall + 60_000L,
            nowElapsed + 60_000L,
            bootCount
        )
        AlarmScheduler.cancel(context)
    }

    @Test
    fun manufacturerSettingsFallbacksAreConstructible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val families = listOf(
            "samsung", "HONOR", "Huawei", "Xiaomi", "OPPO", "realme",
            "OnePlus", "vivo", "Google", "Motorola", "ASUS", "Sony",
            "Nothing", "TECNO", "Infinix", "ZTE", "Lenovo", "unknown"
        )
        families.forEach { manufacturer ->
            val family = AndroidDeviceCompatibility.family(manufacturer, manufacturer)
            assertNotNull(family)
            val intent = AndroidDeviceCompatibility.launchControlSettingsIntent(
                manufacturer,
                manufacturer,
                context.packageName,
                AndroidDeviceCompatibility.LaunchControl.RUN_IN_BACKGROUND
            )
            assertNotNull(intent.action)
            assertTrue(
                AndroidDeviceCompatibility.backgroundSettingsGuide(manufacturer, manufacturer).isNotBlank()
            )
        }
    }

    @Test
    fun deviceCapabilityInspectionIsSafeOnEverySupportedApi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = BluetoothCompatibility.inspect(context)

        assertEquals(Build.VERSION.SDK_INT, report.apiLevel)
        assertTrue(report.androidVersion.isNotBlank())
        assertNotNull(report.adapterStatus)
        assertNotNull(
            AndroidDeviceCompatibility.backgroundSettingsGuide(
                Build.MANUFACTURER,
                Build.BRAND
            )
        )
    }

    private fun <T : Activity> launch(activityClass: Class<T>) {
        ActivityScenario.launch(activityClass).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull("Activity window must be created: ${activityClass.simpleName}", activity.window)
            }
        }
    }
}
