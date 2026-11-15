package com.thiefalertsequre.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootExecutablePathPolicyTest {
    @Test fun acceptsOnlyAbsoluteNonTraversalExecutablePaths() {
        assertTrue(RootExecutablePathPolicy.isSafeAbsoluteExecutable("/system/bin/su"))
        assertTrue(RootExecutablePathPolicy.isSafeAbsoluteExecutable("/vendor/bin/su"))
        assertFalse(RootExecutablePathPolicy.isSafeAbsoluteExecutable("su"))
        assertFalse(RootExecutablePathPolicy.isSafeAbsoluteExecutable("../su"))
        assertFalse(RootExecutablePathPolicy.isSafeAbsoluteExecutable("/system/../data/local/su"))
    }
}
