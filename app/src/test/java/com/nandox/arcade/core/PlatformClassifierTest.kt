package com.nandox.arcade.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformClassifierTest {
    @Test
    fun uniqueExtensionNeedsNoFolder() {
        assertEquals("gba", PlatformClassifier.classify("Game.GBA", emptyList()))
    }

    @Test
    fun unrelatedExtensionIsIgnored() {
        assertNull(PlatformClassifier.classify("foto.jpg", listOf("psp")))
    }

    @Test
    fun ambiguousExtensionUsesFolderHint() {
        assertEquals("psp", PlatformClassifier.classify("a.iso", listOf("Roms", "PSP")))
        assertEquals("ps2", PlatformClassifier.classify("a.iso", listOf("PlayStation 2")))
        assertEquals("wii", PlatformClassifier.classify("a.iso", listOf("Wii")))
    }

    @Test
    fun ambiguousWithoutHintIsUnknown() {
        assertEquals(PlatformClassifier.UNKNOWN, PlatformClassifier.classify("a.iso", listOf("Downloads")))
    }
}
