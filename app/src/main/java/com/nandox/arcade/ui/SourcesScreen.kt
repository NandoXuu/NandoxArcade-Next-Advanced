package com.nandox.arcade.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nandox.arcade.data.ArcadeDb
import com.nandox.arcade.data.SourceEntity
import com.nandox.arcade.scanner.SourceChecker
import com.nandox.arcade.scanner.SourceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SourcesScreen() {
    val context = LocalContext.current
    val dao = remember { ArcadeDb.get(context).games() }
    val scope = rememberCoroutineScope()
    val sources by remember { dao.observeSources() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var pendingRemoval by remember { mutableStateOf<SourceEntity?>(null) }

    val info by produceState(emptyMap<Long, Pair<String, SourceStatus>>(), sources) {
        value = withContext(Dispatchers.IO) {
            sources.associate {
                val uri = Uri.parse(it.uri)
                it.id to (SourceChecker.displayName(context, uri) to SourceChecker.status(context, uri))
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val granted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (granted) scope.launch { dao.insertSource(SourceEntity(uri = uri.toString())) }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Pastas de jogos", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Cartões SD e pendrives USB aparecem no seletor do Android quando conectados.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { picker.launch(null) }) { Text("Adicionar pasta") }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources, key = { it.id }) { src ->
                val (name, status) = info[src.id] ?: ("…" to SourceStatus.AVAILABLE)
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (status) {
                                SourceStatus.AVAILABLE -> "Disponível"
                                SourceStatus.NO_PERMISSION -> "Sem permissão. Adicione a pasta novamente."
                                SourceStatus.UNAVAILABLE -> "Indisponível. Conecte o armazenamento."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    OutlinedButton(onClick = { pendingRemoval = src }) { Text("Remover") }
                }
            }
        }
    }

    pendingRemoval?.let { src ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remover pasta da biblioteca?") },
            text = { Text("Os jogos desta pasta saem da biblioteca. Nenhum arquivo é apagado.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    scope.launch {
                        dao.deleteGamesBySource(src.id)
                        dao.deleteSource(src.id)
                        runCatching {
                            context.contentResolver.releasePersistableUriPermission(
                                Uri.parse(src.uri),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    }
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancelar") } },
        )
    }
}
