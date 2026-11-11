package com.thiefalertsequre.app

/**
 * Captured pre-enable Device Owner policy values exposed by DevicePolicyManager.
 * On Android 14+, Lock Task getters expose resolved policy rather than per-admin
 * values; multi-admin attribution is therefore not reconstructible. Kept
 * Android-framework-free so rollback mapping can be unit tested.
 */
internal data class ManagedPolicySnapshot(
    val lockTaskPackages: Array<String>,
    val lockTaskFeatures: Int,
    val localRestrictionValues: Map<String, Boolean>,
    val globalRestrictionValues: Map<String, Boolean>
) {
    fun shouldKeepLocalRestrictionEnabled(key: String): Boolean =
        localRestrictionValues[key] == true

    fun shouldKeepGlobalRestrictionEnabled(key: String): Boolean =
        globalRestrictionValues[key] == true
}
