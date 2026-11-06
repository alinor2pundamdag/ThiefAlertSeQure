package com.thiefalertsequre.app

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles system keyguard dismissal while AlarmService is running in the foreground.
 * ACTION_USER_PRESENT is a keyguard-gone signal, not an API exposing the credential type.
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
        ) return

        OwnerAuthenticationCoordinator.complete(context, "android_user_present")
    }
}
