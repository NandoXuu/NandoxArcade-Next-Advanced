package com.nandox.arcade.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nandox.arcade.core.PlatformClassifier
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.data.ArcadeDb
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.emulator.ArchiveRom
import com.nandox.arcade.emulator.PlayHistory
import com.nandox.arcade.emulator.RomStager
import com.nandox.arcade.scanner.GameScanner
import com.nandox.arcade.scanner.SourceChecker
import com.nandox.arcade.scanner.SourceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val LIB_ALL = "_all"
const val LIB_RECENT = "_recent"

@Composable
fun LibraryScreen(
    platformId: String,
    onOpenGame: (Long) -> Unit,
    onOpenUnknown: () -> Unit,
    onSources: () -> Unit,
    onBack: () -> Unit,
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
    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            message = null
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
            val (added, skipped) = withContext(Dispatchers.IO) {
                var a = 0
                var s = 0
                for (uri in uris) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                    if (name == null) {
                        s++
                        continue
                    }
                    if (ArchiveRom.isArchive(name)) {
                        val inside = runCatching {
                            ArchiveRom.games(context, uri, name, emptyList(), hint = Platforms.byId(platformId)?.id)
                        }.getOrDefault(emptyList())
                        if (inside.isEmpty()) {
                            s++
                        } else {
                            val ids = dao.insertGames(inside)
                            a += ids.count { it != -1L }
                            s += ids.count { it == -1L }
                        }
                        continue
                    }
                    val hint = listOfNotNull(Platforms.byId(platformId)?.name)
                    val target = PlatformClassifier.classify(name, hint)
                        ?.takeIf { it != PlatformClassifier.UNKNOWN }
                        ?: Platforms.byId(platformId)?.id
                    if (target == null) {
                        s++
                        continue
                    }
                    val ids = dao.insertGames(
                        listOf(GameEntity(title = name.substringBeforeLast('.'), platformId = target, uri = uri.toString())),
                    )
                    if (ids.firstOrNull() != -1L) a++ else s++
                }
                a to s
            }
            message = "$added jogo(s) importado(s)" + if (skipped > 0) ", $skipped ignorado(s)" else ""
        }
    }

    val platform = Platforms.byId(platformId)
    val history = remember(refresh, games) { PlayHistory.all(context).associateBy { it.key } }
    val filtered = remember(games, platformId, history) {
        when (platformId) {
            LIB_ALL -> games
            LIB_RECENT -> games
                .mapNotNull { g ->
                    val played = history[RomStager.keyFor(Uri.parse(g.uri))]?.lastPlayed ?: 0L
                    if (played > 0L) g to played else null
                }
                .sortedByDescending { it.second }
                .map { it.first }
            else -> games.filter { it.platformId == platformId }
        }
    }
    val unknownCount = games.count { it.platformId == PlatformClassifier.UNKNOWN }
    val title = platform?.name ?: when (platformId) {
        LIB_ALL -> "Biblioteca"
        LIB_RECENT -> "Recentes"
        else -> "Não classificados"
    }

    val wheel = rememberWheelState(0)
    val active = filtered.getOrNull(wheel.index)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun available(g: GameEntity) = statuses[g.sourceId]?.let { it == SourceStatus.AVAILABLE } ?: true

    fun activate(g: GameEntity) {
        if (!available(g)) {
            Toast.makeText(context, "Pasta indisponível. Conecte o armazenamento.", Toast.LENGTH_LONG).show()
        } else {
            onOpenGame(g.id)
        }
    }

    BackHandler(onBack = onBack)

    Box(
        Modifier
            .fillMaxSize()
            .wheelKeys(wheel, onSelect = { active?.let(::activate) }, onBack = onBack)
            .focusRequester(focus)
            .focusable(),
    ) {
        ConsoleBackdrop(active?.platformId ?: platform?.id)
        Column(Modifier.fillMaxSize()) {
            ConsoleTopBar(title = title, leading = Glyph.BACK, onLeading = onBack) {
                Text(
                    "${filtered.size} jogo(s) · ${sources.size} pasta(s)",
                    fontSize = 13.sp,
                    color = Color(0xFFEEEEEE),
                    maxLines = 1,
                )
            }

            if (filtered.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
                    CenterText(
                        "Nenhum jogo aqui. Importe um arquivo (inclusive ZIP ou 7z) ou adicione uma pasta e escaneie.",
                        size = 14,
                    )
                }
            } else {
                Wheel(
                    items = filtered,
                    state = wheel,
                    onActivate = ::activate,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { game, index, focusOf ->
                    GameFace(game, index, filtered.size, available(game), focusOf)
                }
            }

            WheelInfo(message ?: active?.id) {
                val msg = message
                val game = filtered.firstOrNull { g -> g.id == it }
                if (msg != null) {
                    Text(msg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC9B8FF))
                } else if (game != null) {
                    val played = history[RomStager.keyFor(Uri.parse(game.uri))]?.lastPlayed ?: 0L
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            if (available(game)) game.title else "${game.title} · indisponível",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            (Platforms.byId(game.platformId)?.name ?: game.platformId) +
                                if (played > 0L) " · Última partida " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(played)) else "",
                            fontSize = 12.sp,
                            color = Color(0xFFA4A5AD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            ConsoleBottom(
                start = {
                    HintBar(
                        listOf(
                            Hint(null, Glyph.MOVE, "Navegar") { wheel.move(1) },
                            Hint("A", null, "Jogar") { active?.let(::activate) },
                            Hint("B", null, "Voltar", onBack),
                        ),
                    )
                },
                end = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("Importar") { importer.launch(arrayOf("*/*")) }
                        ActionChip("Pastas", onClick = onSources)
                        ActionChip(
                            if (scanning) "Escaneando…" else "Escanear",
                            enabled = sources.isNotEmpty() && !scanning,
                            accent = true,
                        ) { scanAll() }
                        if (unknownCount > 0 && platform != null) {
                            ActionChip("$unknownCount sem plataforma", onClick = onOpenUnknown)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun BoxScope.GameFace(
    game: GameEntity,
    index: Int,
    total: Int,
    available: Boolean,
    focus: () -> Float,
) {
    val context = LocalContext.current
    val key = remember(game.uri) { RomStager.keyFor(Uri.parse(game.uri)) }
    val thumb = remember(key) { PlayHistory.thumbnail(context, key) }
    val hue = remember(game.title) { Math.floorMod(game.title.hashCode(), 360).toFloat() }
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color.hsv(hue, 0.42f, 0.46f), Color(0xFF11131C))),
        ),
    )
    FileThumb(thumb, Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                game.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.04).em,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000)),
        ),
    )
    Text(
        "%02d / %02d".format(index + 1, total),
        Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 18.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.16.em,
        color = Color(0x80FFFFFF),
    )
    PlatformIcon(game.platformId, Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 18.dp).size(52.dp, 30.dp))
    PlayMark(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp).graphicsLayer { alpha = focus() })
    if (!available) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            Text("INDISPONÍVEL", fontSize = 11.sp, letterSpacing = 0.2.em, fontWeight = FontWeight.Bold)
        }
    }
}
