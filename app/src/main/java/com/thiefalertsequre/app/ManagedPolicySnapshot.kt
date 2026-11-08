package com.thiefalertsequre.app

/**
 * Exact pre-enable Device Owner policy values.
 * Kept Android-framework-free so rollback mapping can be unit tested.
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
