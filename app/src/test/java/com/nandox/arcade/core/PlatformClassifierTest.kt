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

    @Test
    fun nintendo64ExtensionsAllMapToN64() {
        for (ext in listOf("z64", "n64", "v64", "Z64")) {
            assertEquals("n64", PlatformClassifier.classify("Game.$ext", emptyList()))
        }
    }

    @Test
    fun lightConsolesAreClassifiedByExtension() {
        assertEquals("nes", PlatformClassifier.classify("Game.nes", emptyList()))
        assertEquals("snes", PlatformClassifier.classify("Game.sfc", emptyList()))
        assertEquals("snes", PlatformClassifier.classify("Game.SMC", emptyList()))
        assertEquals("gb", PlatformClassifier.classify("Game.gb", emptyList()))
        assertEquals("gb", PlatformClassifier.classify("Game.gbc", emptyList()))
        assertEquals("gba", PlatformClassifier.classify("Game.gba", emptyList()))
        assertEquals("md", PlatformClassifier.classify("Game.md", emptyList()))
        assertEquals("md", PlatformClassifier.classify("Game.gen", emptyList()))
        assertEquals("md", PlatformClassifier.classify("Game.smd", emptyList()))
    }

    @Test
    fun binKeepsBeingPlayStationSoExistingLibrariesDoNotChange() {
        assertEquals("ps1", PlatformClassifier.classify("Game.bin", emptyList()))
    }
}
