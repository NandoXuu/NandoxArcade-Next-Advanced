package com.nandox.arcade.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.saves.BackupName
import com.nandox.arcade.saves.Outcome
import com.nandox.arcade.saves.SaveFile
import com.nandox.arcade.saves.SaveKind
import com.nandox.arcade.saves.SaveResult
import com.nandox.arcade.saves.SaveScanner
import com.nandox.arcade.saves.SaveService
import com.nandox.arcade.saves.SaveSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private class PendingWrite(val title: String, val run: suspend (Boolean) -> SaveResult)

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

private fun formatDate(ts: Long): String =
    if (ts <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ts))

@Composable
fun SaveManagerScreen(registry: PluginRegistry) {
    val context = LocalContext.current
    val settings = remember { SaveSettings(context) }
    val scope = rememberCoroutineScope()

    var auto by remember { mutableStateOf(settings.autoBackup) }
    var keep by remember { mutableIntStateOf(settings.keepVersions) }
    var locations by remember { mutableStateOf(settings.locations()) }
    var destUri by remember { mutableStateOf(settings.destinationUri) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var saves by remember { mutableStateOf<List<SaveFile>>(emptyList()) }
    var backups by remember { mutableStateOf<List<Triple<String, Long, Long>>>(emptyList()) }
    var pending by remember { mutableStateOf<PendingWrite?>(null) }
    var pickingFor by remember { mutableStateOf<String?>(null) }
    var importFor by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<SaveFile?>(null) }

    LaunchedEffect(refresh, locations, destUri) {
        withContext(Dispatchers.IO) {
            val found = locations.flatMap { (id, uri) ->
                runCatching { SaveScanner.scan(context, id, Uri.parse(uri)) }.getOrDefault(emptyList())
            }
            val dest = settings.destination()
            val list = if (dest.available()) dest.list() else emptyList()
            saves = found.sortedByDescending { it.modified }
            backups = list.mapNotNull { f -> BackupName.parse(f.name)?.let { Triple(f.name, f.size, it.timestamp) } }
                .sortedByDescending { it.third }
        }
    }

    fun handle(result: SaveResult, retry: (suspend (Boolean) -> SaveResult)?, title: String) {
        if (result.outcome == Outcome.CONFLICT && retry != null) {
            pending = PendingWrite(title, retry)
        } else {
            message = result.message
            refresh++
        }
    }

    val treeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    val locationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val id = pickingFor
        if (uri != null && id != null) {
            val ok = runCatching { context.contentResolver.takePersistableUriPermission(uri, treeFlags) }.isSuccess
            if (ok) {
                settings.setLocation(id, uri.toString())
                locations = settings.locations()
            } else {
                message = "Não foi possível obter permissão para esta pasta"
            }
        }
        pickingFor = null
    }
    val destPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val ok = runCatching { context.contentResolver.takePersistableUriPermission(uri, treeFlags) }.isSuccess
            if (ok) {
                settings.destinationUri = uri.toString()
                destUri = settings.destinationUri
            } else {
                message = "Não foi possível obter permissão para esta pasta"
            }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val save = exporting
        if (uri != null && save != null) scope.launch { handle(SaveService.export(context, save, uri), null, "") }
        exporting = null
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = importFor
        if (uri != null && id != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            if (name == null) {
                message = "Não foi possível ler o nome do arquivo"
            } else {
                val run: suspend (Boolean) -> SaveResult = { confirmed ->
                    SaveService.writeToLocation(context, id, name, { context.contentResolver.openInputStream(uri) }, confirmed)
                }
                scope.launch { handle(run(false), run, "Importar $name") }
            }
        }
        importFor = null
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Saves e backups", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Save Data é o salvamento do jogo. Save State é a captura completa do emulador. " +
                "Backup é uma cópia de qualquer um deles. O NDXEMULATOR só enxerga pastas que você escolher.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Backup automático ao voltar do emulador", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Copia saves novos ou alterados. Não força o emulador a salvar.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                        Switch(checked = auto, onCheckedChange = { auto = it; settings.autoBackup = it })
                    }
                    val last = settings.lastAuto.split("|", limit = 2)
                    if (last.size == 2) {
                        Text(
                            "Último: ${formatDate(last[0].toLongOrNull() ?: 0)} — ${last[1]}",
                            fontSize = 13.sp,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Versões mantidas: $keep")
                        OutlinedButton(onClick = { keep = (keep - 1).coerceAtLeast(1); settings.keepVersions = keep }) { Text("−") }
                        OutlinedButton(onClick = { keep = (keep + 1).coerceAtMost(50); settings.keepVersions = keep }) { Text("+") }
                    }
                    Text(
                        "Versões mais antigas do mesmo arquivo são apagadas automaticamente.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
            item {
                Card {
                    Text("Destino dos backups", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (destUri == null) "Armazenamento interno do app" else "Pasta externa escolhida",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { destPicker.launch(null) }) { Text("Escolher pasta (USB/SD)") }
                        if (destUri != null) {
                            OutlinedButton(onClick = { settings.destinationUri = null; destUri = null }) { Text("Usar interno") }
                        }
                    }
                }
            }
            items(registry.all.filter { it.packageName != null }, key = { "loc_" + it.id }) { m ->
                Card {
                    Text("Pasta de saves: ${m.name}", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (locations[m.id] != null) "Pasta definida" else "Não definida. Use a pasta de saves do emulador, se ele permitir escolher uma.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickingFor = m.id; locationPicker.launch(null) }) { Text("Escolher pasta") }
                        if (locations[m.id] != null) {
                            OutlinedButton(onClick = { importFor = m.id; importPicker.launch(arrayOf("*/*")) }) { Text("Importar save") }
                        }
                    }
                }
            }
            item { Text("Saves detectados", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            if (saves.isEmpty()) {
                item { Text("Nenhum save encontrado nas pastas definidas.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(saves, key = { it.uri.toString() }) { s ->
                Card {
                    Text(s.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${if (s.kind == SaveKind.SAVE_STATE) "Save State" else "Save Data"} · " +
                            "${registry.all.firstOrNull { it.id == s.pluginId }?.name ?: s.pluginId} · " +
                            "${formatSize(s.size)} · ${formatDate(s.modified)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch { handle(SaveService.backup(context, s), null, "") }
                        }) { Text("Backup") }
                        OutlinedButton(onClick = { exporting = s; exportPicker.launch(s.name) }) { Text("Exportar") }
                    }
                }
            }
            item { Text("Backups", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            if (backups.isEmpty()) {
                item { Text("Nenhum backup neste destino.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(backups, key = { it.first }) { (name, size, ts) ->
                val info = BackupName.parse(name)
                Card {
                    Text(info?.origName ?: name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${registry.all.firstOrNull { it.id == info?.pluginId }?.name ?: info?.pluginId} · " +
                            "${formatSize(size)} · ${formatDate(ts)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    OutlinedButton(onClick = {
                        val run: suspend (Boolean) -> SaveResult = { confirmed -> SaveService.restore(context, name, confirmed) }
                        scope.launch { handle(run(false), run, "Restaurar ${info?.origName}") }
                    }) { Text("Restaurar") }
                }
            }
        }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Substituir arquivo existente?") },
            text = { Text("${p.title}. Um backup de segurança do arquivo atual é criado antes, e a substituição só acontece se esse backup for confirmado.") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch { handle(p.run(true), null, "") }
                }) { Text("Substituir") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}
