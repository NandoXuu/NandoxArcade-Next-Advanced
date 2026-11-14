package com.nandox.arcade.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
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
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.emulator.RomStager
import com.nandox.arcade.emulator.StateSlot
import com.nandox.arcade.emulator.StateStore
import com.nandox.arcade.launcher.GameLauncher
import com.nandox.arcade.settings.AppSettings
import com.nandox.arcade.settings.LayeredPrefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

/** Tela do jogo: novo jogo, continuar, configurações e todos os saves com miniatura. */
@Composable
fun GameScreen(
    gameId: Long,
    registry: PluginRegistry,
    onSettings: (Long) -> Unit,
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
    val games by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val game = games.firstOrNull { it.id == gameId }
    if (game == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ScreenHeader("Jogo", onBack = onBack)
            Text("Jogo não encontrado na biblioteca.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val key = remember(game.uri) { RomStager.keyFor(Uri.parse(game.uri)) }
    val slots = remember(key, refresh) { StateStore.slots(context, key) }
    val played = remember(key, refresh) { PlayHistory.find(context, key)?.lastPlayed ?: 0L }
    val platformName = Platforms.byId(game.platformId)?.name ?: game.platformId
    var pendingDelete by remember { mutableStateOf<StateSlot?>(null) }

    fun play(state: File?) {
        if (state != null) PlayHistory.pendingState(context, state) else PlayHistory.clearPending(context)
        val text = GameLauncher.message(GameLauncher.launch(context, registry, game))
        if (text != null) Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    val newest = slots.firstOrNull()
    val auto = slots.firstOrNull { StateStore.isAuto(it.file) }
    val continueSlot = auto ?: newest

    @Composable
    fun Header() {
        Card(colors = arcadeCardColors(), modifier = Modifier.fillMaxWidth()) {
            Column {
                FileThumb(
                    file = PlayHistory.thumbnail(context, key).takeIf { it.isFile } ?: continueSlot?.thumbnail,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    sample = 1,
                ) {
                    PlatformIcon(game.platformId, Modifier.fillMaxWidth().height(96.dp).padding(12.dp))
                }
                Column(Modifier.padding(14.dp)) {
                    Text(game.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "$platformName · " + if (played > 0L) "Última partida: ${formatDate(played)}" else "Ainda não jogado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    @Composable
    fun Actions() {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { play(null) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("NOVO JOGO") }
            FilledTonalButton(
                onClick = { play(continueSlot?.file) },
                enabled = continueSlot != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (continueSlot != null) "CONTINUAR · ${formatDate(continueSlot.file.lastModified())}" else "CONTINUAR (sem save)")
            }
            OutlinedButton(onClick = { onSettings(game.id) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("CONFIGURAÇÕES DO JOGO")
            }
        }
    }

    @Composable
    fun Saves() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SectionTitle("ALL SAVES (${slots.size})")
            if (slots.isEmpty()) {
                Text(
                    "Os saves aparecem aqui. Salve pelo menu de pausa ou apenas saia do jogo: ele salva sozinho.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            slots.forEach { slot ->
                Card(colors = arcadeCardColors(), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FileThumb(
                            file = slot.thumbnail,
                            modifier = Modifier.size(120.dp, 68.dp).clip(MaterialTheme.shapes.small),
                        ) {
                            Text("SEM IMAGEM", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (StateStore.isAuto(slot.file)) "Save automático" else "Save manual",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                formatDate(slot.file.lastModified()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { play(slot.file) }) { Text("Carregar") }
                                TextButton(onClick = { pendingDelete = slot }) { Text("Excluir") }
                            }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            ScreenHeader(title = "Jogo", onBack = onBack)
            if (landscape) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.weight(0.45f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Header()
                        Actions()
                        Spacer(Modifier.height(8.dp))
                    }
                    Column(Modifier.weight(0.55f).verticalScroll(rememberScrollState())) {
                        Saves()
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Header()
                    Actions()
                    Saves()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    pendingDelete?.let { slot ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Excluir save?") },
            text = { Text("O save de ${formatDate(slot.file.lastModified())} será apagado.") },
            confirmButton = {
                TextButton(onClick = {
                    StateStore.delete(slot.file)
                    pendingDelete = null
                    refresh++
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** Configurações só deste jogo: guardam apenas o que for alterado; o resto segue o padrão do app. */
@Composable
fun GameSettingsScreen(gameId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { ArcadeDb.get(context).games() }
    val games by remember { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val game: GameEntity? = games.firstOrNull { it.id == gameId }
    if (game == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { ScreenHeader("Configurações do jogo", onBack = onBack) }
        return
    }
    val key = remember(game.uri) { RomStager.keyFor(Uri.parse(game.uri)) }
    val gameStore = remember(key) { AppSettings.game(context, key) }
    val prefs = remember(key) { LayeredPrefs(AppSettings.global(context), gameStore) }
    var version by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenHeader(
            title = "Configurações do jogo",
            subtitle = game.title,
            onBack = onBack,
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "O que você mudar aqui vale só para este jogo. O resto segue as configurações gerais do app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.runtime.key(version) {
                VideoSettingsPanel(prefs, game.platformId)
            }
            OutlinedButton(
                onClick = {
                    gameStore.clear()
                    version++
                    Toast.makeText(context, "Voltou ao padrão do app", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(vertical = 12.dp),
            ) { Text("Usar o padrão do app neste jogo") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
