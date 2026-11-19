package com.nandox.arcade.ui

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nandox.arcade.BuildConfig
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.settings.AppSettings
import com.nandox.arcade.settings.Immersive
import com.nandox.arcade.settings.LayeredPrefs

@Composable
fun AppSettingsScreen(
    onControls: () -> Unit,
    onPlugins: () -> Unit,
    onSources: () -> Unit,
    onSaves: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { AppSettings.global(context) }
    val prefs = remember { LayeredPrefs(store, null) }
    var immersive by remember { mutableStateOf(store.getBoolean("immersive", true)) }
    var controlScale by remember { mutableFloatStateOf(store.getFloat("controlScale", 1f)) }
    var controlOpacity by remember { mutableFloatStateOf(store.getFloat("controlOpacity", 0.85f)) }

    val battery = remember { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val batteryText = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "Indisponível"

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenHeader("Configurações", "Padrões válidos para todos os jogos", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard("Tela e áudio") {
                VideoSettingsPanel(prefs, null)
            }
            SettingsCard("Interface") {
                SwitchSetting(
                    title = "Modo imersivo",
                    hint = "Esconde a barra de status e a de navegação do Android. Deslize da borda para vê-las.",
                    checked = immersive,
                ) {
                    immersive = it
                    store.put("immersive" to it)
                    (context as? Activity)?.let { activity -> Immersive.apply(activity, it) }
                }
            }
            SettingsCard("Controles na tela") {
                Text("Tamanho geral: ${(controlScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = controlScale,
                    onValueChange = { controlScale = it },
                    onValueChangeFinished = { store.put("controlScale" to controlScale) },
                    valueRange = 0.6f..1.6f,
                )
                Text("Opacidade: ${(controlOpacity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = controlOpacity,
                    onValueChange = { controlOpacity = it },
                    onValueChangeFinished = { store.put("controlOpacity" to controlOpacity) },
                    valueRange = 0.25f..1f,
                )
                Text(
                    "Para mudar a posição e o tamanho de cada botão, abra o menu durante o jogo em Controles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsCard("Gerenciar") {
                SettingsLink("Controles e gamepad", "Mapeamento de botões e navegação", onControls)
                SettingsLink("Emuladores", "Estado e compatibilidade dos cores", onPlugins)
                SettingsLink("Pastas de jogos", "Locais de jogos e permissões", onSources)
                SettingsLink("Dados salvos", "Saves, backups e gerenciamento", onSaves)
            }
            SettingsCard("Sobre") {
                InfoLine("Versão do app", BuildConfig.VERSION_NAME)
                InfoLine("Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                InfoLine("Arquiteturas", PluginRegistry.deviceAbis.joinToString())
                InfoLine("Bateria", batteryText)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(colors = arcadeCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
