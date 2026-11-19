package com.nandox.arcade.catalog

import com.nandox.arcade.core.Platforms
import com.nandox.arcade.data.CatalogEntryEntity
import org.json.JSONException
import org.json.JSONObject

class CatalogException(message: String) : Exception(message)

data class ParsedCatalog(val version: Int, val entries: List<CatalogEntryEntity>)

object CatalogParser {
    fun parse(json: String): ParsedCatalog {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw CatalogException("JSON inválido")
        }
        val version = root.optInt("version", -1)
        if (version < 1) throw CatalogException("Versão ausente")
        val array = root.optJSONArray("entries") ?: throw CatalogException("Lista de entradas ausente")
        val keys = HashSet<String>()
        val entries = List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val title = o.optString("title").takeIf { it.isNotBlank() }
                ?: throw CatalogException("Entrada $i sem título")
            val platform = o.optString("platform")
            if (Platforms.byId(platform) == null) throw CatalogException("Plataforma inválida em \"$title\"")
            val key = "$platform:${TitleNormalizer.searchText(title).replace(' ', '-')}"
            if (!keys.add(key)) throw CatalogException("Entrada duplicada: $title")
            val alts = o.optJSONArray("altTitles")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
            val genres = o.optJSONArray("genres")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
            CatalogEntryEntity(
                entryKey = key,
                title = title,
                platformId = platform,
                searchText = TitleNormalizer.searchText((listOf(title) + alts).joinToString(" ")),
                altTitles = alts.joinToString("|"),
                developer = o.optString("developer"),
                year = o.optInt("year", 0),
                genres = genres.joinToString(", "),
            )
        }
        return ParsedCatalog(version, entries)
    }
}
