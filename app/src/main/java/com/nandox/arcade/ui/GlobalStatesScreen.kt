package com.nandox.arcade.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.emulator.PlayHistory
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.launcher.GameLauncher
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class GlobalState(val file: File, val thumb: File?, val gameTitle: String, val platform: String, val uri: String)

@Composable
fun GlobalStatesScreen(registry: PluginRegistry) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val games = remember(refresh) { PlayHistory.all(context).associateBy { it.key } }
    val states = remember(refresh) {
        val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "states")
        root.listFiles()?.flatMap { dir ->
            dir.listFiles { f -> f.isFile && f.extension == "state" }.orEmpty().map { file ->
                val game = games[dir.name]
                GlobalState(
                    file = file,
                    thumb = File(file.parentFile, file.nameWithoutExtension + ".png").takeIf { it.isFile },
                    gameTitle = game?.title ?: dir.name,
                    platform = game?.platformId.orEmpty(),
                    uri = game?.uri.orEmpty(),
                )
            }
        }.orEmpty().sortedByDescending { it.file.lastModified() }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Todos os save-states", style = MaterialTheme.typography.headlineSmall)
        Text("${states.size} estado(s) salvos em todos os jogos", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (states.isEmpty()) {
            Text("Seus save-states aparecerão aqui depois que você salvar dentro de um jogo.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(states, key = { it.file.absolutePath }) { item ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val bitmap = remember(item.thumb?.absolutePath) { item.thumb?.let { BitmapFactory.decodeFile(it.absolutePath) } }
                        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.size(112.dp, 68.dp))
                        else Box(Modifier.size(112.dp, 68.dp).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                            Text("SEM IMAGEM", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.gameTitle, style = MaterialTheme.typography.titleSmall)
                            Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.file.lastModified())), style = MaterialTheme.typography.bodySmall)
                            if (item.platform.isNotBlank()) Text(item.platform.uppercase(), style = MaterialTheme.typography.labelSmall)
                            if (item.uri.isNotBlank()) TextButton(onClick = {
                                PlayHistory.pendingState(context, item.file)
                                GameLauncher.launchAndExplain(context, registry, GameEntity(
                                    title = item.gameTitle, platformId = item.platform, uri = item.uri,
                                ))?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                            }) { Text("Abrir jogo e carregar") }
                            else Text("Jogo original não localizado", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            item.file.delete()
                            File(item.file.parentFile, item.file.nameWithoutExtension + ".png").delete()
                            refresh++
                        }) { Text("Excluir") }
                    }
                }
            }
        }
    }
}
