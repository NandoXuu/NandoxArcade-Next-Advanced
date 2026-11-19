package com.nandox.arcade.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
    onRecents: () -> Unit,
    onAllGames: () -> Unit,
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

    val platforms = Platforms.all
    var savedIndex by rememberSaveable { mutableIntStateOf(1) }
    val wheel = rememberWheelState(savedIndex)
    LaunchedEffect(wheel) { snapshotFlow { wheel.index }.collect { savedIndex = it } }
    val active = platforms[wheel.index.coerceIn(0, platforms.lastIndex)]
    var menuOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(menuOpen) { if (!menuOpen) runCatching { focus.requestFocus() } }

    fun open() = onOpenLibrary(active.id)

    Box(
        Modifier
            .fillMaxSize()
            .wheelKeys(wheel, onSelect = ::open)
            .focusRequester(focus)
            .focusable(),
    ) {
        ConsoleBackdrop(active.id)
        Column(Modifier.fillMaxSize()) {
            ConsoleTopBar(
                title = active.name,
                leading = Glyph.HOME,
                onLeading = { wheel.select(1) },
            ) {
                Box {
                    RoundIcon(Glyph.MENU) { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        listOf(
                            "Recentes" to onRecents,
                            "Biblioteca" to onAllGames,
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
                RoundIcon(Glyph.GEAR, onClick = onSettings)
            }

            Wheel(
                items = platforms,
                state = wheel,
                onActivate = { onOpenLibrary(it.id) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { platform, index, focusOf ->
                PlatformFace(
                    platform = platform,
                    emulator = registry.all.firstOrNull { platform.id in it.platforms }?.name ?: "Sem emulador",
                    index = index,
                    total = platforms.size,
                    focus = focusOf,
                )
            }

            WheelInfo(active.id) { id ->
                val platform = Platforms.byId(id as String)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        registry.all.firstOrNull { it.platforms.contains(id) }?.name ?: "Sem emulador",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${platform?.maker.orEmpty()} · ${games.count { it.platformId == id }} jogo(s)",
                        fontSize = 12.sp,
                        color = Color(0xFFA4A5AD),
                    )
                }
            }

            ConsoleBottom(
                start = {
                    HintBar(
                        listOf(
                            Hint(null, Glyph.MOVE, "Navegar") { wheel.move(1) },
                            Hint("A", null, "Selecionar") { open() },
                            Hint(null, Glyph.MENU, "Menu") { menuOpen = true },
                        ),
                    )
                },
                end = {
                    val last = recent.firstOrNull()?.first
                    if (last != null) {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(Brush.horizontalGradient(listOf(Color(0xFF9B7BFF), Color(0xFF7457E8))))
                                .clickable { onOpenGame(last.id) }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlyphIcon(Glyph.PLAY, Modifier.width(14.dp).height(14.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Continuar · ${last.title}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 260.dp),
                            )
                        }
                    } else {
                        Text(
                            "UMA COLEÇÃO DE CLÁSSICOS",
                            fontSize = 11.sp,
                            letterSpacing = 0.19.em,
                            color = Color(0x57FFFFFF),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PlatformFace(
    platform: Platform,
    emulator: String,
    index: Int,
    total: Int,
    focus: () -> Float,
) {
    Text(
        "%02d / %02d".format(index + 1, total),
        Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 20.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.16.em,
        color = Color(0x3DFFFFFF),
    )
    PlatformIcon(
        platform.id,
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.76f)
            .fillMaxHeight(0.6f)
            .graphicsLayer {
                val s = 1f + 0.04f * focus()
                scaleX = s
                scaleY = s
            },
    )
    Text(
        platform.name.uppercase(),
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
            .graphicsLayer { alpha = focus() },
        fontSize = 11.sp,
        letterSpacing = 0.17.em,
        color = Color(0x9EFFFFFF),
        maxLines = 1,
    )
}
