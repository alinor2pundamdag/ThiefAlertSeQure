package com.thiefalertsequre.app

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the system-only event emitted after the keyguard has been dismissed.
 * It does not inspect or collect PINs, passwords, patterns, or biometric templates.
 */
class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_PRESENT) return

        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val state = ProtectionStore(context).read()
        if (!ProtectionEngine.canAcceptSystemUnlock(
                state = state,
                deviceHasSecureCredential = keyguard.isDeviceSecure,
                keyguardStillLocked = keyguard.isKeyguardLocked
            )
        ) {
            return
        }

        OwnerAuthenticationCoordinator.complete(context, "android_user_present")
    }
}
