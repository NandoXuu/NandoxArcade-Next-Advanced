package com.nandox.arcade.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.data.ArcadeDb
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.emulator.PlayHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenLibrary: (String) -> Unit,
    onOpenGame: (Long) -> Unit,
    onCatalog: () -> Unit,
    onSaves: () -> Unit,
    onBrowser: () -> Unit,
    onDownloads: () -> Unit,
    onPlugins: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dao = remember { ArcadeDb.get(context).games() }
    val games by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val recent: List<Pair<GameEntity, Long>> = remember(games, refresh) {
        val byUri = games.associateBy { it.uri }
        PlayHistory.all(context)
            .filter { it.lastPlayed > 0L }
            .mapNotNull { played -> byUri[played.uri]?.let { it to played.lastPlayed } }
            .take(10)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nandox Arcade", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Escolha uma plataforma para jogar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onSettings) { Text("Ajustes") }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "Catálogo" to onCatalog,
                        "Saves" to onSaves,
                        "Navegador" to onBrowser,
                        "Downloads" to onDownloads,
                        "Emuladores" to onPlugins,
                    ).forEach { (label, action) ->
                        AssistChip(onClick = action, label = { Text(label) })
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("CONTINUAR JOGANDO") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { it.first.id }) { (game, played) ->
                        RecentCard(game, played, onClick = { onOpenGame(game.id) })
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("PLATAFORMAS") }
        items(Platforms.all, key = { it.id }) { platform ->
            val count = games.count { it.platformId == platform.id }
            Card(
                modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { onOpenLibrary(platform.id) },
                colors = arcadeCardColors(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PlatformIcon(platform.id, Modifier.fillMaxWidth().height(84.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        platform.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (count == 1) "1 jogo" else "$count jogos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentCard(game: GameEntity, lastPlayed: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val key = remember(game.uri) { com.nandox.arcade.emulator.RomStager.keyFor(android.net.Uri.parse(game.uri)) }
    val thumb = remember(key, lastPlayed) { PlayHistory.thumbnail(context, key) }
    Card(
        modifier = Modifier.width(190.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = arcadeCardColors(),
    ) {
        Column {
            FileThumb(
                file = thumb,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            ) {
                PlatformIcon(game.platformId, Modifier.fillMaxWidth().height(56.dp).padding(8.dp))
            }
            Column(Modifier.padding(10.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(lastPlayed)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
