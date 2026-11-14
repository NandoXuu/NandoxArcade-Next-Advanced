package com.nandox.arcade.ui

import android.os.Build
import android.content.IntentFilter
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.nandox.arcade.emulator.PlayHistory
import com.nandox.arcade.emulator.RomStager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import com.nandox.arcade.controller.ControlsController
import com.nandox.arcade.core.PlatformClassifier
import com.nandox.arcade.saves.SaveSettings
import com.nandox.arcade.scanner.GameScanner
import com.nandox.arcade.scanner.SourceChecker
import com.nandox.arcade.scanner.SourceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nandox.arcade.BuildConfig
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.data.ArcadeDb
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.launcher.GameLauncher
import com.nandox.arcade.emulator.LaunchResult
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.emulator.PluginStore
import com.nandox.arcade.emulator.PluginStatus

@Composable
fun ArcadeApp(controls: ControlsController) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val registry = remember { PluginRegistry.load(context) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        NavHost(
            nav,
            startDestination = "home",
            enterTransition = { fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 28 } },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(150)) },
        ) {
            composable("home") {
                HomeScreen(
                    onOpenLibrary = { nav.navigate("library/$it") },
                    onCatalog = { nav.navigate("catalog") },
                    onSaves = { nav.navigate("saves") },
                    onBrowser = { nav.navigate("browser") },
                    onDownloads = { nav.navigate("downloads") },
                    onPlugins = { nav.navigate("plugins") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable(
                "library/{platform}",
                arguments = listOf(navArgument("platform") { type = NavType.StringType }),
            ) {
                LibraryScreen(
                    platformId = it.arguments?.getString("platform").orEmpty(),
                    registry = registry,
                    onOpenUnknown = { nav.navigate("library/${PlatformClassifier.UNKNOWN}") },
                    onSources = { nav.navigate("sources") },
                )
            }
            composable("saves") { GlobalStatesScreen(registry) }
            composable("save-files") { SaveManagerScreen(registry) }
            composable("catalog") { CatalogScreen(registry) }
            composable("browser") { BrowserScreen(onExit = { nav.popBackStack() }, onDownloads = { nav.navigate("downloads") }) }
            composable("downloads") { DownloadsScreen(registry) }
            composable("sources") { SourcesScreen() }
            composable("plugins") { PluginsScreen(registry) }
            composable("settings") {
                SettingsScreen(
                    onControls = { nav.navigate("controls") },
                    onPlugins = { nav.navigate("plugins") },
                    onSources = { nav.navigate("sources") },
                    onSaves = { nav.navigate("save-files") },
                )
            }
            composable("controls") { ControlsScreen(controls) }
        }
    }
}

@Composable
private fun HomeScreen(
    onOpenLibrary: (String) -> Unit,
    onCatalog: () -> Unit,
    onSaves: () -> Unit,
    onBrowser: () -> Unit,
    onDownloads: () -> Unit,
    onPlugins: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val platforms = Platforms.all
    var selected by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    var clockText by remember { mutableStateOf("--:--") }
    var batteryPercent by remember { mutableIntStateOf(-1) }
    var batteryCharging by remember { mutableStateOf(false) }
    var homeVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        homeVisible = true
        while (true) {
            clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            delay(30_000)
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(selected) {
        listState.animateScrollToItem(selected)
    }

    val active = platforms[selected]
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF09090D), Color(0xFF11101A), Color(0xFF08080C)),
            ),
        ),
    ) {
        // Soft ambient lighting behind the selected platform.
        Box(
            Modifier.align(Alignment.Center)
                .size(330.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x553F1B70), Color(0x22301A55), Color.Transparent),
                    ),
                    RoundedCornerShape(50),
                ),
        )

        val entranceAlpha by animateFloatAsState(
            targetValue = if (homeVisible) 1f else 0f,
            animationSpec = tween(520),
            label = "home-entrance",
        )
        Column(
            Modifier.fillMaxSize().graphicsLayer { alpha = entranceAlpha }
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "NANDOX",
                        color = Color(0xFFB99AFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp,
                    )
                    Text(
                        "ARCADE",
                        color = Color(0xFFF5F1FF),
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(clockText, color = Color(0xFFF0E9FF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            (if (batteryCharging) "⚡ " else "▰ ") + if (batteryPercent >= 0) "$batteryPercent%" else "--",
                            color = Color(0xFFBDA4E9), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text("GAME CENTER  •  ${selected + 1}/${platforms.size}", color = Color(0xFF777184), fontSize = 9.sp, letterSpacing = 1.3.sp)
                }
            }

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "ESCOLHA SUA PLATAFORMA",
                    color = Color(0xFF8F879F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.4.sp,
                )
                Spacer(Modifier.height(20.dp))

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardWidth = 236.dp
                    LazyRow(
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp),
                        ),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(292.dp)
                            .focusRequester(focus)
                            .focusable()
                            .onKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (e.key) {
                                    Key.DirectionRight -> {
                                        selected = (selected + 1).coerceAtMost(platforms.lastIndex); true
                                    }
                                    Key.DirectionLeft -> {
                                        selected = (selected - 1).coerceAtLeast(0); true
                                    }
                                    Key.DirectionCenter, Key.Enter, Key.ButtonA -> {
                                        onOpenLibrary(platforms[selected].id); true
                                    }
                                    else -> false
                                }
                            },
                    ) {
                        items(platforms.size) { i ->
                            val p = platforms[i]
                            val isSelected = i == selected
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0.82f,
                                animationSpec = tween(360),
                                label = "platform-scale",
                            )
                            val alpha by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0.43f,
                                animationSpec = tween(360),
                                label = "platform-alpha",
                            )
                            Column(
                                Modifier
                                    .width(cardWidth)
                                    .height(270.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        this.alpha = alpha
                                    }
                                    .shadow(
                                        elevation = if (isSelected) 24.dp else 8.dp,
                                        shape = RoundedCornerShape(32.dp),
                                        ambientColor = Color(0x665D36A0),
                                        spotColor = Color(0x995D36A0),
                                    )
                                    .background(
                                        Brush.linearGradient(
                                            if (isSelected) listOf(
                                                Color(0xFF292039), Color(0xFF171421), Color(0xFF111018),
                                            ) else listOf(Color(0xFF22202A), Color(0xFF14131A)),
                                        ),
                                        RoundedCornerShape(32.dp),
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        brush = Brush.linearGradient(
                                            if (isSelected) listOf(Color(0xFFB68AFF), Color(0xFF7042B8))
                                            else listOf(Color(0xFF393442), Color(0xFF24212B)),
                                        ),
                                        shape = RoundedCornerShape(32.dp),
                                    )
                                    .clickable {
                                        if (isSelected) onOpenLibrary(p.id) else selected = i
                                    }
                                    .padding(17.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        p.maker.uppercase(),
                                        color = if (isSelected) Color(0xFFC9A9FF) else Color(0xFF898392),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                    )
                                    Text(
                                        if (isSelected) "●" else "○",
                                        color = if (isSelected) Color(0xFFC39AFF) else Color(0xFF68616F),
                                        fontSize = 12.sp,
                                    )
                                }

                                // Artwork slot: the matching emulator/platform artwork can be
                                // dropped into drawable-nodpi later without changing the carousel.
                                Box(
                                    Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    if (isSelected) Color(0x665F3A91) else Color(0x332D2938),
                                                    Color.Transparent,
                                                ),
                                            ),
                                            RoundedCornerShape(20.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        platformMark(p.id),
                                        color = if (isSelected) Color(0xFFE7D8FF) else Color(0xFF8E879A),
                                        fontSize = 58.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = (-3).sp,
                                    )
                                }

                                Column {
                                    Text(
                                        p.name,
                                        color = if (isSelected) Color.White else Color(0xFFB0AAB8),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (isSelected) "Pressione A para explorar" else p.maker,
                                        color = if (isSelected) Color(0xFFB99AFF) else Color(0xFF77717F),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(9.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    platforms.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(width = if (i == selected) 22.dp else 5.dp, height = 5.dp)
                                .background(
                                    if (i == selected) Color(0xFFB58AFF) else Color(0xFF494452),
                                    RoundedCornerShape(50),
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(15.dp))
                Text(
                    active.name.uppercase(),
                    color = Color(0xFFF0E8FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "${active.maker}  •  ${active.extensions.sorted().joinToString(" / ").uppercase()}",
                    color = Color(0xFF898294),
                    fontSize = 11.sp,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onOpenLibrary(active.id) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text("ABRIR BIBLIOTECA  →", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    listOf(
                        "Catálogo" to onCatalog,
                        "Saves" to onSaves,
                        "Navegador" to onBrowser,
                        "Downloads" to onDownloads,
                        "Emuladores" to onPlugins,
                        "Ajustes" to onSettings,
                    ).forEach { (label, action) ->
                        OutlinedButton(
                            onClick = action,
                            shape = RoundedCornerShape(12.dp),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
                Text(
                    "←  →  NAVEGAR     •     A  SELECIONAR",
                    color = Color(0xFF696273),
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun platformMark(id: String): String = when (id) {
    "ps1" -> "PS"
    "ps2" -> "PS2"
    "psp" -> "PSP"
    "nds" -> "DS"
    "3ds" -> "3DS"
    "n64" -> "N64"
    "dreamcast" -> "DC"
    "gba" -> "GBA"
    "gamecube" -> "GC"
    "wii" -> "Wii"
    else -> "NX"
}

@Composable
private fun LibraryScreen(
    platformId: String,
    registry: PluginRegistry,
    onOpenUnknown: () -> Unit,
    onSources: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var historyRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) historyRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val dao = remember { ArcadeDb.get(context).games() }
    val store = remember { PluginStore(context) }
    val scope = rememberCoroutineScope()
    val games by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val sources by remember { dao.observeSources() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var statuses by remember { mutableStateOf<Map<Long, SourceStatus>>(emptyMap()) }
    LaunchedEffect(sources) {
        statuses = withContext(Dispatchers.IO) {
            sources.associate { it.id to SourceChecker.status(context, Uri.parse(it.uri)) }
        }
    }

    fun scanAll() {
        scope.launch {
            scanning = true
            var added = 0
            for (src in dao.sources()) {
                val found = GameScanner.scan(context, Uri.parse(src.uri)).map { it.copy(sourceId = src.id) }
                added += dao.insertGames(found).count { it != -1L }
            }
            scanning = false
            message = "$added jogo(s) novo(s) adicionado(s)"
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            var added = 0
            var skipped = 0
            for (uri in uris) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                val hint = listOfNotNull(Platforms.byId(platformId)?.name)
                val target = name?.let { PlatformClassifier.classify(it, hint) }
                    ?.takeIf { it != PlatformClassifier.UNKNOWN }
                    ?: Platforms.byId(platformId)?.id
                if (name == null || target == null) {
                    skipped++
                    continue
                }
                val ids = dao.insertGames(
                    listOf(GameEntity(title = name.substringBeforeLast('.'), platformId = target, uri = uri.toString())),
                )
                if (ids.firstOrNull() != -1L) added++ else skipped++
            }
            message = "$added jogo(s) importado(s)" + if (skipped > 0) ", $skipped ignorado(s)" else ""
        }
    }

    val platform = Platforms.byId(platformId)
    val filtered = games.filter { it.platformId == platformId }
    val unknownCount = games.count { it.platformId == PlatformClassifier.UNKNOWN }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(platform?.name ?: "Não classificados", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Importar jogo") }
            OutlinedButton(onClick = onSources) { Text("Pastas") }
            Button(onClick = { scanAll() }, enabled = sources.isNotEmpty() && !scanning) {
                Text(if (scanning) "Escaneando…" else "Escanear")
            }
        }
        Text(
            "${sources.size} pasta(s) configurada(s)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        message?.let { Text(it, fontSize = 13.sp) }
        if (unknownCount > 0 && platform != null) {
            Text(
                "$unknownCount jogo(s) sem plataforma definida",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onOpenUnknown),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            Text(
                "Nenhum jogo aqui. Adicione uma pasta e escaneie.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { g ->
                    val available = statuses[g.sourceId]?.let { it == SourceStatus.AVAILABLE } ?: true
                    val gameKey = remember(g.uri) { RomStager.keyFor(Uri.parse(g.uri)) }
                    val cover = remember(gameKey, historyRefresh) { PlayHistory.thumbnail(context, gameKey).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) } }
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .clickable {
                                if (!available) {
                                    Toast.makeText(context, "Pasta indisponível. Conecte o armazenamento.", Toast.LENGTH_LONG).show()
                                    return@clickable
                                }
                                val text = GameLauncher.message(GameLauncher.launch(context, registry, g))
                                if (text != null) Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (cover != null) Image(cover.asImageBitmap(), contentDescription = "Última partida de ${g.title}", modifier = Modifier.size(104.dp, 62.dp))
                        else Box(Modifier.size(104.dp, 62.dp).background(Color(0xFF242424), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Text("ARCADE", color = Color.White, fontSize = 11.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (available) g.title else "${g.title} · indisponível",
                                color = if (available) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val played = PlayHistory.all(context).firstOrNull { it.key == gameKey }?.lastPlayed ?: 0L
                            if (played > 0L) Text("Última partida · " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(played)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginsScreen(registry: PluginRegistry) {
    val context = LocalContext.current
    val store = remember { PluginStore(context) }
    var diagnostics by remember { mutableStateOf(registry.diagnostics(context, store)) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Plugins de emulação", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "ABI do aparelho: ${PluginRegistry.deviceAbis.joinToString()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(diagnostics, key = { it.manifest.id }) { d ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.manifest.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            d.manifest.platforms.mapNotNull { Platforms.byId(it)?.name }.joinToString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        val status = when (d.status) {
                            PluginStatus.READY -> "App instalado"
                            PluginStatus.APP_NOT_INSTALLED -> "App não instalado"
                            PluginStatus.ABI_INCOMPATIBLE -> "ABI incompatível"
                            PluginStatus.RUNTIME_UNAVAILABLE -> "Core não incluído no APK"
                            PluginStatus.DISABLED -> "Desativado"
                        }
                        val validation = if (d.manifest.validated) "validado" else "não validado"
                        Text("$status · $validation", fontSize = 13.sp)
                    }
                    Switch(
                        checked = d.enabled,
                        onCheckedChange = {
                            store.setEnabled(d.manifest.id, it)
                            diagnostics = registry.diagnostics(context, store)
                        },
                    )
                }
            }
            items(registry.rejected, key = { it.first }) { (file, reason) ->
                Text(
                    "Manifesto rejeitado: $file ($reason)",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onControls: () -> Unit,
    onPlugins: () -> Unit,
    onSources: () -> Unit,
    onSaves: () -> Unit,
) {
    val context = LocalContext.current
    var showDeviceInfo by remember { mutableStateOf(true) }
    var compactStatus by remember { mutableStateOf(false) }
    val battery = remember { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val batteryText = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "Indisponível"

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0A10), Color(0xFF15111E), Color(0xFF0A090E))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactStatus) 9.dp else 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("CENTRAL DO SISTEMA", color = Color(0xFFB99AFF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
            Text("Configurações", color = Color(0xFFF5F0FF), fontSize = 29.sp, fontWeight = FontWeight.Black)
            Text("Personalize e gerencie seu Nandox Arcade.", color = Color(0xFF9991A5), fontSize = 13.sp)
        }
        SettingsGroup(title = "GERENCIAMENTO") {
            SettingsAction("Controles e gamepad", "Mapeamento de botões e navegação", "⌘", onControls)
            SettingsAction("Emuladores e plugins", "Estado, compatibilidade e integrações", "◈", onPlugins)
            SettingsAction("Pastas e armazenamento", "Locais de jogos e permissões SAF", "▤", onSources)
            SettingsAction("Dados salvos", "Saves, backups e gerenciamento", "▣", onSaves)
        }
        SettingsGroup(title = "INTERFACE") {
            SettingsToggle("Informações do dispositivo", "Exibir detalhes técnicos nesta tela", showDeviceInfo) { showDeviceInfo = it }
            SettingsToggle("Layout compacto", "Reduzir espaçamentos nesta tela", compactStatus) { compactStatus = it }
        }
        if (showDeviceInfo) {
            SettingsGroup(title = "INFORMAÇÕES DO DISPOSITIVO") {
                SettingsInfo("Versão do aplicativo", BuildConfig.VERSION_NAME)
                SettingsInfo("Versão do Android", "${Build.VERSION.RELEASE}  •  API ${Build.VERSION.SDK_INT}")
                SettingsInfo("Arquiteturas", PluginRegistry.deviceAbis.joinToString())
                SettingsInfo("Bateria", batteryText)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("NANDOX ARCADE  •  BUILD ${BuildConfig.VERSION_NAME}", color = Color(0xFF6F6879), fontSize = 9.sp, letterSpacing = 1.5.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(25.dp), ambientColor = Color(0x553F2863), spotColor = Color(0x553F2863))
            .background(Brush.linearGradient(listOf(Color(0xFF211B2B), Color(0xFF15121C))), RoundedCornerShape(25.dp))
            .border(1.dp, Color(0xFF393044), RoundedCornerShape(25.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = Color(0xFFB99AFF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
        content()
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, symbol: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF292333), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(39.dp).background(Color(0xFF45335F), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text(symbol, color = Color(0xFFE2D0FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color(0xFFF2ECFA), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFF9990A5), fontSize = 10.sp)
        }
        Text("›", color = Color(0xFFB99AFF), fontSize = 24.sp)
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color(0xFFF0EAF8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF9990A5), fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsInfo(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Color(0xFF9990A5), fontSize = 11.sp)
        Text(value, color = Color(0xFFE8DFFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
