package com.nandox.arcade.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        val text = GameLauncher.launchAndExplain(context, registry, game)
        if (text != null) Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    val newest = slots.firstOrNull()
    val auto = slots.firstOrNull { StateStore.isAuto(it.file) }
    val continueSlot = auto ?: newest

    val savesBytes = slots.sumOf { it.file.length() }
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }

    fun restartSave() {
        slots.forEach { StateStore.delete(it.file) }
        PlayHistory.clearPending(context)
        refresh++
    }

    fun removeGame() {
        scope.launch(Dispatchers.IO) { ArcadeDb.get(context).games().deleteGameByUri(game.uri) }
        onBack()
    }

    @Composable
    fun Header() {
        Card(colors = arcadeCardColors(), modifier = Modifier.fillMaxWidth()) {
            Column {
                FileThumb(
                    file = PlayHistory.thumbnail(context, key).takeIf { it.isFile } ?: continueSlot?.thumbnail,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    sample = 1,
                ) {
                    DefaultCover(Modifier.fillMaxHeight().padding(16.dp))
                }
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(game.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        platformName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Última sessão", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (played > 0L) formatDate(played) else "Nunca jogado",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Tamanho do save", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatBytes(savesBytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Options() {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OptionRow("▶", "Continuar Jogo", "Retome de onde parou", highlighted = true) { play(continueSlot?.file) }
            OptionRow("+", "Novo Jogo", "Inicie do zero") { play(null) }
            OptionRow("↻", "Reiniciar Save", "Apague o progresso atual") { confirm = "restart" }
            OptionRow("⚙", "Configurações do Jogo", "Ajuste as opções do emulador") { onSettings(game.id) }
            OptionRow("✕", "Excluir Jogo", null, danger = true) { confirm = "delete" }
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
                        Modifier.weight(0.42f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Header()
                    }
                    Column(
                        Modifier.weight(0.58f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Options()
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
                    Options()
                    Saves()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    when (confirm) {
        "restart" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Reiniciar save?") },
            text = { Text("Todos os saves deste jogo serão apagados.") },
            confirmButton = {
                TextButton(onClick = { restartSave(); confirm = null }) { Text("Reiniciar") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Excluir jogo?") },
            text = { Text("O jogo sai da biblioteca. Os saves continuam guardados no aparelho.") },
            confirmButton = {
                TextButton(onClick = { confirm = null; removeGame() }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
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

@Composable
private fun OptionRow(
    glyph: String,
    title: String,
    subtitle: String?,
    highlighted: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Card(
        colors = arcadeCardColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                else Modifier,
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(glyph, fontSize = 20.sp, color = if (danger) tint else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = tint)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!danger) Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
