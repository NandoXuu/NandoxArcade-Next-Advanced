package com.nandox.arcade.browser

import android.content.Context
import java.net.URLEncoder

enum class SearchEngine(val label: String, val searchUrl: String, val home: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=", "https://duckduckgo.com"),
    GOOGLE("Google", "https://www.google.com/search?q=", "https://www.google.com"),
}

class BrowserSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("browser", Context.MODE_PRIVATE)

    var engine: SearchEngine
        get() = SearchEngine.entries.firstOrNull { it.name == prefs.getString("engine", null) } ?: SearchEngine.GOOGLE
        set(v) = prefs.edit().putString("engine", v.name).apply()
}

object UrlResolver {
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    fun resolve(input: String, engine: SearchEngine = SearchEngine.DUCKDUCKGO): String {
        val text = input.trim()
        if (text.isEmpty()) return search("", engine)
        val lower = text.lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://")) return text
        if (scheme.containsMatchIn(text)) return search(text, engine)
        if (' ' !in text && '.' in text) return "https://$text"
        return search(text, engine)
    }

    private fun search(query: String, engine: SearchEngine) =
        engine.searchUrl + URLEncoder.encode(query, "UTF-8")

    fun isAllowedNavigation(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("https://") || lower.startsWith("http://")
    }
}
