package com.thiefalertsequre.app

/**
 * OEM launch-manager switches are private vendor state. Android exposes no
 * public API to inspect them, so HONOR/Huawei readiness must remain UNKNOWN
 * until the owner checks the settings manually.
 */
internal object OemBackgroundSetupPolicy {
    fun evaluate(manufacturer: String, brand: String): SetupItem? {
        if (AndroidDeviceCompatibility.family(manufacturer, brand) !=
            AndroidDeviceCompatibility.Family.HONOR_HUAWEI
        ) return null

        return SetupItem(
            id = "oem_background",
            title = "HONOR / Huawei background launch controls",
            state = SetupState.UNKNOWN,
            detail = "Manual verification required: Settings → App launch → ThiefAlertSeQure → Manage automatically OFF; enable Auto-launch, Secondary launch, and Run in background. Also check Battery optimization → All apps → ThiefAlertSeQure → Don't allow, lock the app in Recents, and disable Power saving / Ultra power saving for reliability. Android cannot read these vendor switches, so this status is never marked VERIFIED automatically."
        )
    }
}
