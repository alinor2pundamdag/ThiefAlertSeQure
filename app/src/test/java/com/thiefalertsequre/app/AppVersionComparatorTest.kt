package com.thiefalertsequre.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppVersionComparatorTest {
    @Test fun patchUpdateIsNewer() {
        assertEquals(1, AppVersionComparator.compare("1.1.4", "1.1.3").compareTo(0))
    }

    @Test fun higherMinorVersionIsNewer() {
        assertEquals(1, AppVersionComparator.compare("1.2.0", "1.1.9").compareTo(0))
    }

    @Test fun equalVersionsCompareEqual() {
        assertEquals(0, AppVersionComparator.compare("1.1.3", "1.1.3"))
    }

    @Test fun majorVersionDominatesLowerComponents() {
        assertEquals(1, AppVersionComparator.compare("2.0.0", "1.99.99").compareTo(0))
    }

    @Test fun malformedVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AppVersionComparator.compare("1.1.4-beta", "1.1.3")
        }
    }
}
