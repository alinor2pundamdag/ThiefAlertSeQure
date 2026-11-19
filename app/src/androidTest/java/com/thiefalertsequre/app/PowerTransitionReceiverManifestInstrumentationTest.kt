package com.thiefalertsequre.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PowerTransitionReceiverManifestInstrumentationTest {
    @Test fun protectedPowerBroadcastsReachTheManifestReceiverWithoutTheMonitorProcess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(context, PowerTransitionReceiver::class.java)
        val info = context.packageManager.getReceiverInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertTrue("The system power receiver must be enabled", info.enabled)
        assertTrue("The system must be able to wake the manifest receiver", info.exported)

        listOf(Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED).forEach { action ->
            val matches = context.packageManager.queryBroadcastReceivers(
                Intent(action).setPackage(context.packageName), 0
            )
            assertTrue(
                "The manifest must receive $action even when the monitor service is absent",
                matches.any { it.activityInfo.name == PowerTransitionReceiver::class.java.name }
            )
        }
    }
}
