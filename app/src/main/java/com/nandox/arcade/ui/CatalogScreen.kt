package com.nandox.arcade.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nandox.arcade.catalog.CatalogImporter
import com.nandox.arcade.catalog.CatalogStatus
import com.nandox.arcade.catalog.TitleNormalizer
import com.nandox.arcade.catalog.catalogStatus
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.data.ArcadeDb
import com.nandox.arcade.data.CatalogEntryEntity
import com.nandox.arcade.data.CatalogStateEntity
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.emulator.PluginStore
import com.nandox.arcade.scanner.SourceChecker
import com.nandox.arcade.scanner.SourceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 30

@Composable
fun CatalogScreen(registry: PluginRegistry) {
    val context = LocalContext.current
    val db = remember { ArcadeDb.get(context) }
    val catalog = remember { db.catalog() }
    val gamesDao = remember { db.games() }
    val store = remember { PluginStore(context) }
    val scope = rememberCoroutineScope()

    var ready by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<CatalogEntryEntity>>(emptyList()) }
    var hasMore by remember { mutableStateOf(false) }
    var statuses by remember { mutableStateOf<Map<Long, SourceStatus>>(emptyMap()) }

    val states by remember { catalog.observeStates() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val games by remember { gamesDao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val sources by remember { gamesDao.observeSources() }.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(Unit) {
        CatalogImporter.importIfNeeded(context, catalog)
        ready = true
    }
    LaunchedEffect(sources) {
        statuses = withContext(Dispatchers.IO) {
            sources.associate { it.id to SourceChecker.status(context, Uri.parse(it.uri)) }
        }
    }
    LaunchedEffect(ready, query, platform) {
        if (!ready) return@LaunchedEffect
        val page = catalog.search(platform, TitleNormalizer.likePattern(query), PAGE_SIZE, 0)
        results = page
        hasMore = page.size == PAGE_SIZE
    }

    val acquired = remember(states) { states.filter { it.acquired }.map { it.entryKey }.toSet() }
    val gamesByKey = remember(games) { games.groupBy { TitleNormalizer.matchKey(it.title) } }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Catálogo", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Seleção de jogos conhecidos, não uma lista completa. Não inclui arquivos de jogos. " +
                "\"Adquirido\" é só uma marcação sua de organização.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Buscar jogo") },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val all: List<Pair<String?, String>> = listOf<Pair<String?, String>>(null to "Todas") +
                Platforms.all.map { it.id to it.name }
            all.forEach { (id, name) ->
                if (platform == id) {
                    Button(onClick = { platform = id }) { Text(name) }
                } else {
                    OutlinedButton(onClick = { platform = id }) { Text(name) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (ready && results.isEmpty()) {
            Text("Nenhum jogo encontrado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.id }) { e ->
                val keys = (listOf(e.title) + e.altTitles.split("|")).filter { it.isNotBlank() }
                    .map { TitleNormalizer.matchKey(it) }.toSet()
                val matched = keys.flatMap { gamesByKey[it].orEmpty() }.filter { it.platformId == e.platformId }
                val sourceAvailable = matched.any { g ->
                    statuses[g.sourceId]?.let { it == SourceStatus.AVAILABLE } ?: true
                }
                val isAcquired = e.entryKey in acquired
                val status = catalogStatus(
                    acquired = isAcquired,
                    inLibrary = matched.isNotEmpty(),
                    sourceAvailable = sourceAvailable,
                    hasEmulator = registry.adaptersFor(e.platformId, store).isNotEmpty(),
                )
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(e.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            Platforms.byId(e.platformId)?.name,
                            e.developer.takeIf { it.isNotBlank() },
                            e.year.takeIf { it > 0 }?.toString(),
                        ).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    if (e.genres.isNotBlank()) {
                        Text(e.genres, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Text(
                        when (status) {
                            CatalogStatus.CATALOGED -> "Catalogado"
                            CatalogStatus.ACQUIRED -> "Adquirido (marcado por você)"
                            CatalogStatus.IN_LIBRARY -> "Na biblioteca"
                            CatalogStatus.UNAVAILABLE -> "Indisponível: pasta desconectada"
                            CatalogStatus.NO_EMULATOR -> "Na biblioteca, sem emulador ativo"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                    )
                    if (matched.isEmpty()) {
                        OutlinedButton(onClick = {
                            scope.launch { catalog.setState(CatalogStateEntity(e.entryKey, !isAcquired)) }
                        }) { Text(if (isAcquired) "Desmarcar" else "Marcar como adquirido") }
                    }
                }
            }
            if (hasMore) {
                item(key = "more") {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val page = catalog.search(
                                platform, TitleNormalizer.likePattern(query), PAGE_SIZE, results.size,
                            )
                            results = results + page
                            hasMore = page.size == PAGE_SIZE
                        }
                    }) { Text("Carregar mais") }
                }
            }
        }
    }
}
