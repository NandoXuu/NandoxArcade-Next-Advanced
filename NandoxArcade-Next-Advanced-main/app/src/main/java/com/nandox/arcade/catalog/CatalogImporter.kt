package com.nandox.arcade.catalog

import android.content.Context
import com.nandox.arcade.data.CatalogDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CatalogImporter {
    private const val ASSET = "catalog/catalog.json"

    suspend fun importIfNeeded(context: Context, dao: CatalogDao): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("catalog", Context.MODE_PRIVATE)
        val parsed = try {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            CatalogParser.parse(text)
        } catch (e: CatalogException) {
            return@withContext false
        } catch (e: java.io.IOException) {
            return@withContext false
        }
        if (parsed.version <= prefs.getInt("version", 0)) return@withContext false
        dao.insertAll(parsed.entries)
        prefs.edit().putInt("version", parsed.version).apply()
        true
    }
}
