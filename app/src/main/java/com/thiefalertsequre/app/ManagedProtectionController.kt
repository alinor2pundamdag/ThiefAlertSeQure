package com.thiefalertsequre.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager

/**
 * Optional, explicitly enabled Android Enterprise / fully-managed-device mode.
 * This never attempts to elevate an ordinary app to Device Owner.
 */
object ManagedProtectionController {
    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)
            .isDeviceOwnerApp(context.packageName)

    fun isPolicyConfigured(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28 || !isDeviceOwner(context)) return false
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = admin(context)
        return context.packageName in dpm.getLockTaskPackages(admin) &&
            (dpm.getLockTaskFeatures(admin) and DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS) == 0
    }

    fun enable(activity: Activity): Boolean {
        check(Build.VERSION.SDK_INT >= 28) {
            "Managed Lock Task feature configuration requires Android 9.0 (API 28) or later."
        }
        check(isDeviceOwner(activity)) {
            "Device Owner provisioning is required. Standard device-admin activation is not sufficient."
        }
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        val admin = admin(activity)

        // Allow only this app in kiosk mode. Keep Keyguard available; omit GLOBAL_ACTIONS.
        dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD)

        dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        if (Build.VERSION.SDK_INT >= 28) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
        }

        if (dpm.isLockTaskPermitted(activity.packageName)) {
            activity.startLockTask()
            return true
        }
        return false
    }

    fun disable(activity: Activity) {
        check(isDeviceOwner(activity)) { "Only the enrolled Device Owner can remove managed policies." }
        runCatching { activity.stopLockTask() }
        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        val admin = admin(activity)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        if (Build.VERSION.SDK_INT >= 28) {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
        }
        if (Build.VERSION.SDK_INT >= 28) {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)
        }
        dpm.setLockTaskPackages(admin, emptyArray())
    }

    fun startIfConfigured(activity: Activity) {
        if (isPolicyConfigured(activity) &&
            activity.getSystemService(DevicePolicyManager::class.java).isLockTaskPermitted(activity.packageName)
        ) {
            runCatching { activity.startLockTask() }
        }
    }

    private fun admin(context: Context) =
        ComponentName(context, ThiefAlertAdminReceiver::class.java)
}
