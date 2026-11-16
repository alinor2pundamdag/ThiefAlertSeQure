package com.thiefalertsequre.app

import android.app.Activity
import android.media.AudioManager
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Framework-level smoke coverage across the Android API 26–37 emulator matrix.
 *
 * These tests verify app launch and manifest declarations on the release package.
 * OEM-specific guidance and capability logic have JVM regression coverage; these
 * tests do not simulate OEM battery managers, real accessories, charger hardware,
 * biometric hardware, camera LEDs or force-stop.
 */
@RunWith(AndroidJUnit4::class)
class AndroidCompatibilitySmokeTest {
    private lateinit var appContext: Context
    private lateinit var monitorSnapshot: SharedPreferencesSnapshot
    private lateinit var historySnapshot: SharedPreferencesSnapshot
    private lateinit var stateSnapshot: SharedPreferencesSnapshot
    private lateinit var corruptionSnapshot: SharedPreferencesSnapshot
    private lateinit var transitionSnapshot: SharedPreferencesSnapshot
    private lateinit var schedulerSnapshot: SharedPreferencesSnapshot
    private lateinit var volumeSnapshot: SharedPreferencesSnapshot
    private var monitorWasRunning = false
    private var monitorWasEnabled = false
    private var safeToLaunchScreens = false
    private var originalAlarmStreamVolume = 0

    @Before
    fun preserveOwnerSettingsAndServiceState() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        monitorSnapshot = SharedPreferencesSnapshot(appContext, ProtectionMonitorService.PREFS)
        historySnapshot = SharedPreferencesSnapshot(appContext, "local_tamper_history_v1")
        stateSnapshot = SharedPreferencesSnapshot(appContext, "protection_state_v1")
        corruptionSnapshot = SharedPreferencesSnapshot(appContext, "protection_state_corrupt_backup_v1")
        transitionSnapshot = SharedPreferencesSnapshot(appContext, "charger_transition_v2")
        schedulerSnapshot = SharedPreferencesSnapshot(appContext, "alarm_scheduler_v1")
        volumeSnapshot = SharedPreferencesSnapshot(appContext, "alarm_volume_restore_v1")
        val monitorPrefs = appContext.getSharedPreferences(ProtectionMonitorService.PREFS, Context.MODE_PRIVATE)
        val statePrefs = appContext.getSharedPreferences("protection_state_v1", Context.MODE_PRIVATE)
        val schedulerPrefs = appContext.getSharedPreferences("alarm_scheduler_v1", Context.MODE_PRIVATE)
        monitorWasEnabled = SafePreferences.boolean(
            monitorPrefs, ProtectionMonitorService.KEY_ENABLED, ProtectionMonitorPolicy.DEFAULT_ENABLED
        )
        monitorWasRunning = ProtectionMonitorService.isRunning
        originalAlarmStreamVolume = appContext.getSystemService(AudioManager::class.java)
            .getStreamVolume(AudioManager.STREAM_ALARM)

        val mode = when (val stored = SafePreferences.readString(statePrefs, "mode", null)) {
            is PreferenceRead.Value -> stored.value ?: ProtectionMode.DISARMED.name
            is PreferenceRead.Corrupt -> "CORRUPT"
        }
        val alarmToken = SafePreferences.readString(schedulerPrefs, "last_token", null)
        val noAlarmToken = alarmToken is PreferenceRead.Value && alarmToken.value == null
        val a2Deadlines = SafePreferences.readString(
            monitorPrefs, "trusted_bluetooth_pending_disconnects_v1", null
        )
        val noA2Deadlines = a2Deadlines is PreferenceRead.Value && a2Deadlines.value == null
        safeToLaunchScreens = mode in setOf(ProtectionMode.DISARMED.name, ProtectionMode.ARMED.name) &&
            noAlarmToken && noA2Deadlines &&
            !AlarmScheduler.hasLegacyPendingIntent(appContext) &&
            !ManagedProtectionController.isDeviceOwner(appContext)
    }

    @After
    fun restoreOwnerSettingsAndServiceState() {
        stateSnapshot.restore()
        corruptionSnapshot.restore()
        transitionSnapshot.restore()
        schedulerSnapshot.restore()
        volumeSnapshot.restore()
        monitorSnapshot.restore()
        runCatching {
            appContext.getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmStreamVolume, 0)
        }
        if (monitorWasRunning && monitorWasEnabled && !ProtectionMonitorService.isRunning) {
            runCatching {
                ContextCompat.startForegroundService(
                    appContext, Intent(appContext, ProtectionMonitorService::class.java)
                )
            }
        } else if (!monitorWasRunning) {
            appContext.stopService(Intent(appContext, ProtectionMonitorService::class.java))
        }
        // The emulator is disposable. Do not revoke a runtime permission here:
        // PackageManager kills the target process on permission revocation, which also
        // terminates AndroidJUnitRunner before the remaining instrumentation tests run.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        historySnapshot.restore()
    }

    @Test
    fun everyInternalScreenCanBeCreatedAndDestroyed() {
        assumeTrue(
            "Skip activity smoke launch while an alarm/countdown, scheduled deadline, pending A2 event, or Device Owner policy is active",
            safeToLaunchScreens
        )
        grantNotificationPermissionForTest()
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
        listOf("exact", "notifications", "alarm_channel", "fullscreen", "battery", "lock", "monitor", "bt", "a2").forEach { id ->
            assertTrue("Missing setup status item: $id", snapshot.items.any { it.id == id })
        }
    }

    @Test
    fun ownerAuthenticationSelectionUsesActualDeviceCapabilities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val deviceCredentialAvailable = context.getSystemService(android.app.KeyguardManager::class.java)
            .isDeviceSecure
        val strongBiometricAvailable = deviceCredentialAvailable &&
            androidx.biometric.BiometricManager.from(context)
                .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

        val selected = OwnerAuthenticationPolicy.select(
            strongBiometricAvailable = strongBiometricAvailable,
            deviceCredentialAvailable = deviceCredentialAvailable
        )
        val expected = when {
            strongBiometricAvailable -> OwnerAuthenticationMethod.BIOMETRIC
            deviceCredentialAvailable -> OwnerAuthenticationMethod.DEVICE_CREDENTIAL
            else -> OwnerAuthenticationMethod.UNAVAILABLE
        }
        assertEquals("Authentication must follow the methods configured on this Android device", expected, selected)
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

    private fun grantNotificationPermissionForTest() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            runShell("pm grant ${appContext.packageName} android.permission.POST_NOTIFICATIONS")
        }
    }

    private fun runShell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private fun <T : Activity> launch(activityClass: Class<T>) {
        ActivityScenario.launch(activityClass).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull("Activity window must be created: ${activityClass.simpleName}", activity.window)
            }
        }
    }
}
