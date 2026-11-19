package com.nandox.arcade.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val filtered = games.filter { it.platformId == platformId }
    val unknownCount = games.count { it.platformId == PlatformClassifier.UNKNOWN }
    val history = remember(refresh, games) { PlayHistory.all(context).associateBy { it.key } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenHeader(
            title = platform?.name ?: "Não classificados",
            subtitle = "${filtered.size} jogo(s) · ${sources.size} pasta(s)",
            onBack = onBack,
            trailing = {
                if (platform != null) PlatformIcon(platform.id, Modifier.size(44.dp))
            },
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Importar jogo ou ZIP/7z") }
            OutlinedButton(onClick = onSources) { Text("Pastas") }
            Button(onClick = { scanAll() }, enabled = sources.isNotEmpty() && !scanning) {
                Text(if (scanning) "Escaneando…" else "Escanear")
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        if (unknownCount > 0 && platform != null) {
            Text(
                "$unknownCount jogo(s) sem plataforma definida",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp).clickable(onClick = onOpenUnknown),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            Text(
                "Nenhum jogo aqui. Importe um arquivo (inclusive ZIP ou 7z) ou adicione uma pasta e escaneie.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { g ->
                    val available = statuses[g.sourceId]?.let { it == SourceStatus.AVAILABLE } ?: true
                    val gameKey = remember(g.uri) { RomStager.keyFor(Uri.parse(g.uri)) }
                    val played = history[gameKey]?.lastPlayed ?: 0L
                    Card(
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable {
                            if (!available) {
                                Toast.makeText(context, "Pasta indisponível. Conecte o armazenamento.", Toast.LENGTH_LONG).show()
                            } else {
                                onOpenGame(g.id)
                            }
                        },
                        colors = arcadeCardColors(),
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FileThumb(
                                file = PlayHistory.thumbnail(context, gameKey),
                                modifier = Modifier.size(104.dp, 60.dp).clip(MaterialTheme.shapes.small),
                            ) {
                                PlatformIcon(g.platformId, Modifier.size(104.dp, 60.dp).padding(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (available) g.title else "${g.title} · indisponível",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (played > 0L) {
                                    Text(
                                        "Última partida · " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(played)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
