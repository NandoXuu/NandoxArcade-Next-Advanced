package com.nandox.arcade.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlResolverTest {
    @Test
    fun keepsHttpUrls() {
        assertEquals("https://example.com/a", UrlResolver.resolve(" https://example.com/a "))
    }

    @Test
    fun addsHttpsToBareHost() {
        assertEquals("https://example.com", UrlResolver.resolve("example.com"))
    }

    @Test
    fun plainTextBecomesSearch() {
        assertEquals("https://duckduckgo.com/?q=jogos+gba", UrlResolver.resolve("jogos gba"))
    }

    @Test
    fun dangerousSchemesBecomeSearch() {
        assertTrue(UrlResolver.resolve("javascript:alert(1)").startsWith("https://duckduckgo.com/?q="))
        assertTrue(UrlResolver.resolve("file:///sdcard/a").startsWith("https://duckduckgo.com/?q="))
    }

    @Test
    fun navigationOnlyAllowsHttp() {
        assertTrue(UrlResolver.isAllowedNavigation("https://a.com"))
        assertFalse(UrlResolver.isAllowedNavigation("intent://x"))
        assertFalse(UrlResolver.isAllowedNavigation("file:///a"))
    }
}

class UrlResolverEngineTest {
    @Test
    fun googleEngineBuildsGoogleSearch() {
        assertEquals(
            "https://www.google.com/search?q=jogos+gba",
            UrlResolver.resolve("jogos gba", SearchEngine.GOOGLE),
        )
    }
}
