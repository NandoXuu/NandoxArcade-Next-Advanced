package com.nandox.arcade.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleNormalizerTest {
    @Test
    fun searchTextIgnoresAccentsAndPunctuation() {
        assertEquals("pokemon diamond", TitleNormalizer.searchText("Pokémon Diamond"))
        assertEquals("zelda s mask", TitleNormalizer.searchText("Zelda's Mask"))
    }

    @Test
    fun likePatternJoinsTokens() {
        assertEquals("%mario%kart%", TitleNormalizer.likePattern("  Mario   KART "))
        assertEquals("%%", TitleNormalizer.likePattern(""))
    }

    @Test
    fun matchKeyDropsRegionTags() {
        assertEquals(
            TitleNormalizer.matchKey("Super Mario Kart"),
            TitleNormalizer.matchKey("Super_Mario_Kart (USA) [!]"),
        )
    }
}
