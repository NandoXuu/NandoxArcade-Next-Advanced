package com.nandox.arcade.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogParserTest {
    private fun doc(entries: String) = """{"version":1,"entries":[$entries]}"""

    @Test
    fun parsesEntryAndBuildsKey() {
        val c = CatalogParser.parse(
            doc("""{"title":"Pokémon Emerald","platform":"gba","developer":"Game Freak","year":2004,"genres":["RPG"]}""")
        )
        assertEquals(1, c.version)
        assertEquals("gba:pokemon-emerald", c.entries.single().entryKey)
        assertEquals("pokemon emerald", c.entries.single().searchText)
    }

    @Test
    fun altTitlesAreSearchable() {
        val c = CatalogParser.parse(
            doc("""{"title":"A","platform":"gba","altTitles":["Outro Nome"]}""")
        )
        assertEquals("a outro nome", c.entries.single().searchText)
    }

    @Test
    fun rejectsUnknownPlatform() {
        assertThrows(CatalogException::class.java) {
            CatalogParser.parse(doc("""{"title":"A","platform":"zzz"}"""))
        }
    }

    @Test
    fun rejectsDuplicates() {
        assertThrows(CatalogException::class.java) {
            CatalogParser.parse(
                doc("""{"title":"A","platform":"gba"},{"title":"a","platform":"gba"}""")
            )
        }
    }

    @Test
    fun rejectsMissingVersion() {
        assertThrows(CatalogException::class.java) { CatalogParser.parse("""{"entries":[]}""") }
    }
}
