package com.thiefalertsequre.app

import android.app.Application
import android.util.Log

/** Runs durable preference-restore recovery before activities, services, or receivers use settings. */
class ThiefAlertSeQureApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!ConfigurationBackup.recoverPendingRestore(this)) {
            Log.e(
                "ThiefAlertSeQure",
                "Interrupted configuration restore could not be rolled back; recovery journal retained for retry."
            )
        }
    }
}
