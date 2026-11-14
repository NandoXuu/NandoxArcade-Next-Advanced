package com.nandox.arcade.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetParserTest {
    @Test
    fun cueListsQuotedAndUnquotedFilesOnce() {
        val cue = """
            FILE "Game (Track 1).bin" BINARY
              TRACK 01 MODE2/2352
                INDEX 01 00:00:00
            FILE Track2.bin BINARY
              TRACK 02 AUDIO
            FILE "Game (Track 1).bin" BINARY
        """.trimIndent()
        assertEquals(listOf("Game (Track 1).bin", "Track2.bin"), SheetParser.referencedFiles(cue, "cue"))
    }

    @Test
    fun cueDropsFolderPrefix() {
        val cue = "FILE \"discs\\\\track01.bin\" BINARY\nFILE \"a/b/track02.bin\" BINARY"
        assertEquals(listOf("track01.bin", "track02.bin"), SheetParser.referencedFiles(cue, "cue"))
    }

    @Test
    fun gdiSkipsTrackCountLine() {
        val gdi = "3\n1 0 4 2352 \"track01.bin\" 0\n2 756 0 2352 track02.raw 0\n3 45000 4 2352 \"track03.bin\" 0\n"
        assertEquals(
            listOf("track01.bin", "track02.raw", "track03.bin"),
            SheetParser.referencedFiles(gdi, "gdi"),
        )
    }

    @Test
    fun otherExtensionsHaveNoReferences() {
        assertEquals(emptyList<String>(), SheetParser.referencedFiles("FILE \"x.bin\"", "iso"))
    }
}
