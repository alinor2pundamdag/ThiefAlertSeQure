package com.thiefalertsequre
import org.junit.Assert.*
import org.junit.Test
class ThemeStoreTest{
 @Test fun hasSixteenUniqueThemes(){assertEquals(16,ThemeStore.all.size);assertEquals(16,ThemeStore.all.map{it.id}.toSet().size)}
 @Test fun hasEightDarkAndEightLight(){assertEquals(8,ThemeStore.all.count{it.mode=="Dark"});assertEquals(8,ThemeStore.all.count{it.mode=="Light"})}
 @Test fun invalidIdsAreRejected(){assertFalse(ThemeStore.valid("not-a-theme"));assertFalse(ThemeStore.valid(null));assertTrue(ThemeStore.valid(ThemeStore.DEFAULT_ID))}
}
