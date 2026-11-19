package com.nandox.arcade.ui

import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.nandox.arcade.core.PlatformClassifier
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.data.ArcadeDb
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.downloads.DownloadImport
import com.nandox.arcade.downloads.DownloadItem
import com.nandox.arcade.downloads.DownloadState
import com.nandox.arcade.downloads.DownloadsRepository
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.launcher.GameLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DownloadsScreen(registry: PluginRegistry) {
    val context = LocalContext.current
    val repo = remember { DownloadsRepository(context) }
    val dao = remember { ArcadeDb.get(context).games() }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }
    var pendingRemoval by remember { mutableStateOf<DownloadItem?>(null) }
    var choosing by remember { mutableStateOf<DownloadItem?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            items = withContext(Dispatchers.IO) { runCatching { repo.list() }.getOrDefault(emptyList()) }
            delay(1000)
        }
    }

    fun addAndPlay(d: DownloadItem, platformId: String) {
        scope.launch {
            val uri = DownloadImport.gameUri(context, d.localUri)
            if (uri == null) {
                Toast.makeText(context, "Arquivo não encontrado", Toast.LENGTH_LONG).show()
                return@launch
            }
            val game = GameEntity(title = d.title.substringBeforeLast('.'), platformId = platformId, uri = uri.toString())
            dao.insertGames(listOf(game))
            val text = GameLauncher.message(GameLauncher.launch(context, registry, game))
            Toast.makeText(
                context,
                text ?: "${game.title} adicionado à biblioteca",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Downloads", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Depois de concluído, toque em Jogar para adicionar à biblioteca e abrir no emulador.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text("Nenhum download.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { d ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (d.state) {
                                DownloadState.PENDING -> "Pendente"
                                DownloadState.RUNNING -> d.percent?.let { "Baixando $it%" } ?: "Baixando"
                                DownloadState.PAUSED -> "Pausado"
                                DownloadState.DONE -> "Concluído"
                                DownloadState.FAILED -> "Falhou"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    if (d.state == DownloadState.DONE) {
                        Button(onClick = {
                            val guess = PlatformClassifier.classify(d.title, emptyList())
                                ?.takeIf { it != PlatformClassifier.UNKNOWN }
                            if (guess != null) addAndPlay(d, guess) else choosing = d
                        }) { Text("Jogar") }
                    }
                    OutlinedButton(onClick = { pendingRemoval = d }) {
                        Text(if (d.state == DownloadState.DONE) "Apagar" else "Cancelar")
                    }
                }
            }
        }
    }

    pendingRemoval?.let { d ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(if (d.state == DownloadState.DONE) "Apagar arquivo?" else "Cancelar download?") },
            text = {
                Text(
                    if (d.state == DownloadState.DONE) {
                        "${d.title}. O arquivo é apagado e o jogo sai da biblioteca."
                    } else {
                        d.title
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    val gameUri = DownloadImport.gameUri(context, d.localUri)?.toString()
                    scope.launch {
                        gameUri?.let { dao.deleteGameByUri(it) }
                        repo.remove(d.id)
                    }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Voltar") } },
        )
    }

    choosing?.let { d ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("Qual é a plataforma?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(d.title, fontSize = 13.sp)
                    Platforms.all.forEach { p ->
                        TextButton(onClick = {
                            choosing = null
                            addAndPlay(d, p.id)
                        }) { Text(p.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = null }) { Text("Cancelar") } },
        )
    }
}
