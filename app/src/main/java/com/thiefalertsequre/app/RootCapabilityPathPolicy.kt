package com.thiefalertsequre.app

internal object RootExecutablePathPolicy {
    fun isSafeAbsoluteExecutable(path: String): Boolean =
        path.isNotBlank() && path.startsWith("/") && path.split('/').none { it == ".." }
}
