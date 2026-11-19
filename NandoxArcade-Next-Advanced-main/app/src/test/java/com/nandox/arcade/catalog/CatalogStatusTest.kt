package com.nandox.arcade.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogStatusTest {
    @Test
    fun notInLibraryDependsOnAcquired() {
        assertEquals(CatalogStatus.CATALOGED, catalogStatus(false, false, true, true))
        assertEquals(CatalogStatus.ACQUIRED, catalogStatus(true, false, true, true))
    }

    @Test
    fun inLibraryPrioritizesUnavailableThenNoEmulator() {
        assertEquals(CatalogStatus.UNAVAILABLE, catalogStatus(true, true, false, false))
        assertEquals(CatalogStatus.NO_EMULATOR, catalogStatus(false, true, true, false))
        assertEquals(CatalogStatus.IN_LIBRARY, catalogStatus(false, true, true, true))
    }
}
