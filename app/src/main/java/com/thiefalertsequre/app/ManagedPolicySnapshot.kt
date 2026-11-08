package com.thiefalertsequre.app

/**
 * Exact pre-enable policy values owned by this Device Owner admin.
 * Kept Android-framework-free so rollback mapping can be unit tested.
 */
internal data class ManagedPolicySnapshot(
    val lockTaskPackages: Array<String>,
    val lockTaskFeatures: Int,
    val restrictionValues: Map<String, Boolean>
) {
    fun shouldKeepRestrictionEnabled(key: String): Boolean =
        restrictionValues[key] == true
}
