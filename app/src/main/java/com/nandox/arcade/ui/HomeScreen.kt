package com.nandox.arcade.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nandox.arcade.core.Platform
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.data.ArcadeDb
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.emulator.PlayHistory
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.emulator.RomStager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HomeSection(val label: String, val glyph: String) {
    INICIO("Início", "⌂"),
    FAVORITOS("Favoritos", "★"),
    RECENTES("Recentes", "◷"),
    BIBLIOTECA("Biblioteca", "▤"),
}

private val accentBrush = Brush.horizontalGradient(listOf(Color(0xFF9B7BFF), Color(0xFF7457E8)))

@Composable
fun HomeScreen(
    registry: PluginRegistry,
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
    }

    var section by remember { mutableStateOf(HomeSection.INICIO) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val q = query.trim()

    Row(Modifier.fillMaxSize()) {
        Sidebar(
            selected = section,
            onSelect = { section = it },
            onSettings = onSettings,
        )
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (section == HomeSection.INICIO) "Emuladores" else section.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Buscar") },
                        modifier = Modifier.width(260.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                CircleButton("⌕") {
                    searching = !searching
                    if (!searching) query = ""
                }
                Spacer(Modifier.width(10.dp))
                Box {
                    CircleButton("⋮") { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        listOf(
                            "Catálogo" to onCatalog,
                            "Navegador" to onBrowser,
                            "Downloads" to onDownloads,
                            "Salvamentos" to onSaves,
                            "Emuladores e plugins" to onPlugins,
                        ).forEach { (label, action) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    menuOpen = false
                                    action()
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            when (section) {
                HomeSection.INICIO -> {
                    val platforms = Platforms.all.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(190.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(platforms, key = { it.id }) { platform ->
                            PlatformCard(
                                platform = platform,
                                emulatorName = registry.all.firstOrNull { platform.id in it.platforms }?.name ?: "Sem emulador",
                                onClick = { onOpenLibrary(platform.id) },
                            )
                        }
                    }
                    SettingsCard(onClick = onSettings)
                }

                HomeSection.FAVORITOS -> EmptyState(
                    "Favoritos em breve",
                    "Em breve você poderá marcar seus jogos favoritos aqui.",
                )

                HomeSection.RECENTES -> {
                    val visibleRecent = recent.filter { q.isEmpty() || it.first.title.contains(q, ignoreCase = true) }
                    if (visibleRecent.isEmpty()) {
                        EmptyState("Nenhum jogo jogado ainda", "Os jogos que você abrir aparecem aqui.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(240.dp),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(visibleRecent, key = { it.first.id }) { (game, played) ->
                                GameCard(game = game, lastPlayed = played, onClick = { onOpenGame(game.id) })
                            }
                        }
                    }
                }

                HomeSection.BIBLIOTECA -> {
                    val visibleGames = games.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
                    if (visibleGames.isEmpty()) {
                        EmptyState("Biblioteca vazia", "Adicione pastas de jogos em Configurações.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(240.dp),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(visibleGames, key = { it.id }) { game ->
                                GameCard(game = game, lastPlayed = null, onClick = { onOpenGame(game.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    selected: HomeSection,
    onSelect: (HomeSection) -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🎮", fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Text("Nandox Arcade", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        HomeSection.values().forEach { item ->
            SidebarItem(item.glyph, item.label, item == selected) { onSelect(item) }
        }
        SidebarItem("⚙", "Configurações", selected = false, onClick = onSettings)
        Spacer(Modifier.weight(1f))
        Text(
            "v1.0.0-beta",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SidebarItem(glyph: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) Modifier.background(accentBrush, RoundedCornerShape(26.dp)) else Modifier
    val tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .then(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, fontSize = 20.sp, color = tint)
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CircleButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PlatformCard(platform: Platform, emulatorName: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        colors = arcadeCardColors(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlatformIcon(platform.id, Modifier.fillMaxWidth().height(100.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                platform.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                emulatorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GameCard(game: GameEntity, lastPlayed: Long?, onClick: () -> Unit) {
    val context = LocalContext.current
    val key = remember(game.uri) { RomStager.keyFor(Uri.parse(game.uri)) }
    val thumb = remember(key, lastPlayed) { PlayHistory.thumbnail(context, key) }
    val platformName = Platforms.byId(game.platformId)?.name ?: game.platformId
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        colors = arcadeCardColors(),
    ) {
        Column {
            FileThumb(
                file = thumb,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            ) {
                PlatformIcon(game.platformId, Modifier.fillMaxWidth().height(64.dp).padding(8.dp))
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    game.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (lastPlayed != null) {
                        "$platformName · ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(lastPlayed))}"
                    } else {
                        platformName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        colors = arcadeCardColors(),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚙", fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Configurações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Personalize sua experiência",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

