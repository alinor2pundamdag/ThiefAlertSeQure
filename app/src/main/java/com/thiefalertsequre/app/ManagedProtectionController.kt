package com.thiefalertsequre.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager

/**
 * Optional, explicitly enabled Android Enterprise / fully-managed-device mode.
 *
 * Before changing Device Owner policies, the controller persists the exact policy
 * values owned by this admin. Disable and failed enable operations restore those
 * values instead of assuming that every policy was previously unset.
 */
object ManagedProtectionController {
    private const val PREFS = "managed_protection_rollback"
    private const val SNAPSHOT_VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_PACKAGES = "lock_task_packages"
    private const val KEY_FEATURES = "lock_task_features"
    private const val KEY_RECOVERY_PENDING = "recovery_pending"

    private val restrictions = listOf(
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_USB_FILE_TRANSFER
    )

    fun isDeviceOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)
            .isDeviceOwnerApp(context.packageName)

    fun hasPendingRollback(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RECOVERY_PENDING, false)

    fun isPolicyConfigured(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28 || !isDeviceOwner(context)) return false
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = admin(context)
        return context.packageName in dpm.getLockTaskPackages(admin) &&
            (dpm.getLockTaskFeatures(admin) and DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS) == 0
    }

    fun enable(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        check(isDeviceOwner(activity)) {
            "Device Owner provisioning is required. Standard device-admin activation is not sufficient."
        }

        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        val admin = admin(activity)
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Do not replace the original baseline when enable is retried or the app
        // is already configured. The first snapshot is the rollback authority.
        if (prefs.contains(KEY_VERSION)) {
            check(!prefs.getBoolean(KEY_RECOVERY_PENDING, false)) {
                "Managed-policy rollback is pending. Disable managed lockdown before enabling it again."
            }
            validateSnapshot(prefs)
        } else {
            val localRestrictionsBefore = dpm.getUserRestrictions(admin)
            val globalRestrictionsBefore = if (Build.VERSION.SDK_INT >= 34) {
                dpm.getUserRestrictionsGlobally()
            } else {
                android.os.Bundle()
            }
            val snapshot = ManagedPolicySnapshot(
                lockTaskPackages = dpm.getLockTaskPackages(admin),
                lockTaskFeatures = dpm.getLockTaskFeatures(admin),
                localRestrictionValues = restrictions.associateWith { key ->
                    localRestrictionsBefore.getBoolean(key, false)
                },
                globalRestrictionValues = restrictions.associateWith { key ->
                    globalRestrictionsBefore.getBoolean(key, false)
                }
            )
            persistSnapshot(prefs, snapshot)
        }

        check(prefs.edit().putBoolean(KEY_RECOVERY_PENDING, true).commit()) {
            "Could not mark managed lockdown as in progress; no Device Owner policy changes were applied."
        }

        try {
            // A process death during policy application must leave a visible,
            // retryable exit path rather than automatically re-entering kiosk.
            // Allow only this app in kiosk mode. Keep Keyguard available; omit GLOBAL_ACTIONS.
            dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD)

            restrictions.forEach { key ->
                if (Build.VERSION.SDK_INT >= 34) {
                    dpm.addUserRestrictionGlobally(key)
                } else {
                    dpm.addUserRestriction(admin, key)
                }
            }

            check(dpm.isLockTaskPermitted(activity.packageName)) {
                "Android did not accept this app in the Lock Task allowlist."
            }
            activity.startLockTask()
            check(prefs.edit().putBoolean(KEY_RECOVERY_PENDING, false).commit()) {
                "Managed policies were applied, but activation status could not be saved; rollback will be attempted."
            }
            return true
        } catch (failure: Throwable) {
            runCatching { prefs.edit().putBoolean(KEY_RECOVERY_PENDING, true).commit() }
            runCatching { activity.stopLockTask() }
            val rollbackFailure = runCatching { restorePersistedSnapshot(activity, dpm, admin) }.exceptionOrNull()
            if (rollbackFailure != null) {
                failure.addSuppressed(rollbackFailure)
                throw IllegalStateException(
                    "Managed lockdown could not be enabled and automatic rollback was incomplete. " +
                        "Keep the app installed and use its managed-lockdown recovery action; " +
                        "do not restart or reset the device until policies are restored.",
                    failure
                )
            }
            throw failure
        }
    }

    fun disable(activity: Activity) {
        check(isDeviceOwner(activity)) {
            "Only the enrolled Device Owner can remove managed policies."
        }

        val dpm = activity.getSystemService(DevicePolicyManager::class.java)
        val admin = admin(activity)
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Legacy installations have no original baseline. Persist a recovery
        // marker before changing navigation or policy so restart cannot re-enter kiosk.
        if (!prefs.contains(KEY_VERSION)) {
            check(prefs.edit().putInt(KEY_VERSION, 0).commit()) {
                "Could not persist the legacy managed-policy recovery marker."
            }
        }
        check(prefs.edit().putBoolean(KEY_RECOVERY_PENDING, true).commit()) {
            "Could not mark managed-policy rollback as pending; no policy restoration was attempted."
        }

        // The pending marker is durable before we exit kiosk, so a process death
        // at any point leaves the app able to reopen the recovery action.
        runCatching { activity.stopLockTask() }
        restorePersistedSnapshot(activity, dpm, admin)
    }

    fun startIfConfigured(activity: Activity) {
        // Never re-enter kiosk automatically while an explicit rollback is pending.
        if (hasPendingRollback(activity)) return
        if (isPolicyConfigured(activity) &&
            activity.getSystemService(DevicePolicyManager::class.java).isLockTaskPermitted(activity.packageName)
        ) {
            runCatching { activity.startLockTask() }
        }
    }

    private fun persistSnapshot(
        prefs: android.content.SharedPreferences,
        snapshot: ManagedPolicySnapshot
    ) {
        val editor = prefs.edit()
            .putInt(KEY_VERSION, SNAPSHOT_VERSION)
            .putStringSet(KEY_PACKAGES, snapshot.lockTaskPackages.toSet())
            .putInt(KEY_FEATURES, snapshot.lockTaskFeatures)
            .putBoolean(KEY_RECOVERY_PENDING, false)
        snapshot.localRestrictionValues.forEach { (key, enabled) ->
            editor.putBoolean("local_restriction:$key", enabled)
        }
        snapshot.globalRestrictionValues.forEach { (key, enabled) ->
            editor.putBoolean("global_restriction:$key", enabled)
        }
        check(editor.commit()) {
            "Could not persist the original managed-device policies; no lockdown changes were applied."
        }
    }

    private fun validateSnapshot(prefs: android.content.SharedPreferences) {
        check(prefs.getInt(KEY_VERSION, -1) == SNAPSHOT_VERSION) {
            "Managed-policy rollback data is invalid. Refusing to apply lockdown over an unreadable recovery snapshot."
        }
        check(prefs.getStringSet(KEY_PACKAGES, null) != null) {
            "Managed-policy rollback package list is missing. Refusing to change device policies."
        }
        check(prefs.contains(KEY_FEATURES) &&
            restrictions.all { prefs.contains("local_restriction:$it") && prefs.contains("global_restriction:$it") }) {
            "Managed-policy rollback data is incomplete. Refusing to change device policies."
        }
    }

    private fun restorePersistedSnapshot(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            validateSnapshot(prefs)
        } catch (invalidSnapshot: Exception) {
            // The exact prior baseline is unrecoverable if local rollback data is
            // corrupt. Because the owner explicitly requested disable, restore
            // navigable Android defaults. The original package/feature baseline
            // cannot be reconstructed from damaged metadata.
            restoreEmergencyDefaults(dpm, admin, prefs, invalidSnapshot)
            return
        }
        val packages = prefs.getStringSet(KEY_PACKAGES, null)
            ?: throw IllegalStateException("Managed-policy rollback package list is missing.")
        val snapshot = ManagedPolicySnapshot(
            lockTaskPackages = packages.toTypedArray(),
            lockTaskFeatures = prefs.getInt(KEY_FEATURES, 0),
            localRestrictionValues = restrictions.associateWith { key ->
                prefs.getBoolean("local_restriction:$key", false)
            },
            globalRestrictionValues = restrictions.associateWith { key ->
                prefs.getBoolean("global_restriction:$key", false)
            }
        )

        val failures = mutableListOf<Throwable>()
        // Attempt every restoration independently so one OEM/API failure does
        // not prevent the remaining restrictions and navigation policy restoring.
        runCatching {
            dpm.setLockTaskFeatures(admin, snapshot.lockTaskFeatures)
        }.onFailure(failures::add)
        restrictions.forEach { key ->
            runCatching {
                // clearUserRestriction clears both local and global values set
                // by this admin on API 34+, so rebuild the exact saved pair.
                dpm.clearUserRestriction(admin, key)
                if (snapshot.shouldKeepLocalRestrictionEnabled(key)) {
                    dpm.addUserRestriction(admin, key)
                }
                if (Build.VERSION.SDK_INT >= 34 && snapshot.shouldKeepGlobalRestrictionEnabled(key)) {
                    dpm.addUserRestrictionGlobally(key)
                }
            }.onFailure(failures::add)
        }
        runCatching {
            dpm.setLockTaskPackages(admin, snapshot.lockTaskPackages)
        }.onFailure(failures::add)

        if (failures.isNotEmpty()) {
            throw IllegalStateException(
                "Managed-policy rollback is incomplete. The saved original policy snapshot was retained " +
                    "so recovery can be retried.",
                failures.first()
            )
        }

        check(prefs.edit().clear().commit()) {
            "Policies were restored, but rollback metadata could not be cleared. Retry disable to verify recovery."
        }
    }

    private fun restoreEmergencyDefaults(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        prefs: android.content.SharedPreferences,
        invalidSnapshot: Exception
    ) {
        val failures = mutableListOf<Throwable>()
        restrictions.forEach { key ->
            runCatching { dpm.clearUserRestriction(admin, key) }.onFailure(failures::add)
        }
        runCatching {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)
        }.onFailure(failures::add)
        runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }.onFailure(failures::add)

        if (failures.isNotEmpty()) {
            throw IllegalStateException(
                "Rollback data is corrupt and emergency policy recovery was incomplete. " +
                    "The app retained recovery metadata; keep the app installed and retry disable.",
                failures.first()
            ).also { it.addSuppressed(invalidSnapshot) }
        }
        check(prefs.edit().clear().commit()) {
            "Emergency policy defaults were restored, but damaged rollback metadata could not be cleared."
        }
    }

    private fun admin(context: Context) =
        ComponentName(context, ThiefAlertAdminReceiver::class.java)
}
