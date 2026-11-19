package com.nandox.arcade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nandox.arcade.settings.LayeredPrefs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipSetting(
    title: String,
    hint: String? = null,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
fun SwitchSetting(title: String, hint: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val ASPECT_OPTIONS = listOf(
    "original" to "Original",
    "4:3" to "4:3",
    "16:9" to "16:9",
    "3:2" to "3:2",
    "fill" to "Preencher (corta bordas)",
    "stretch" to "Esticado (tela cheia)",
)

private val SHADER_OPTIONS = listOf("default" to "Padrão", "sharp" to "Nítido", "crt" to "CRT", "lcd" to "LCD")
private val REFRESH_OPTIONS = listOf("0" to "Automático", "30" to "30 Hz", "60" to "60 Hz", "90" to "90 Hz", "120" to "120 Hz")
private val SCALE_OPTIONS = listOf("1" to "1× (nativa)", "1.5" to "1,5×", "2" to "2×", "3" to "3×", "4" to "4×")

private val N64_43 = listOf("320x240", "640x480", "960x720", "1280x960", "1600x1200", "1920x1440", "2560x1920", "3840x2880")
private val N64_169 = listOf("640x360", "960x540", "1280x720", "1920x1080", "2560x1440", "3840x2160")
private val PSP_RES = listOf("480x272", "960x544", "1440x816", "1920x1088", "2880x1632", "3840x2176")
private val DC_RES = listOf("640x480", "960x720", "1280x960", "1920x1440", "2560x1920", "3840x2880")

private fun resOptions(list: List<String>) = list.map { it to it.replace('x', '×') }

/**
 * Opções de vídeo e áudio. Serve para as configurações gerais do app, para as de um jogo e para o
 * menu de pausa. [platformId] nulo mostra as opções de todas as plataformas.
 */
@Composable
fun VideoSettingsPanel(prefs: LayeredPrefs, platformId: String?, onChange: (String) -> Unit = {}) {
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val refresh = tick
    fun set(key: String, value: Any?) {
        prefs.put(key to value)
        tick++
        onChange(key)
    }

    SwitchSetting("Áudio", checked = prefs.getBoolean("audio", true)) { set("audio", it) }

    ChipSetting(
        title = "Tamanho da imagem",
        hint = "Original mantém a proporção do console. Esticado preenche a tela inteira.",
        options = ASPECT_OPTIONS,
        selected = prefs.getString("mode", "original"),
    ) { set("mode", it) }

    ChipSetting(
        title = "Filtro / shader",
        options = SHADER_OPTIONS,
        selected = prefs.getString("shader", "default"),
    ) { set("shader", it) }

    ChipSetting(
        title = "Taxa de atualização da tela",
        hint = "Pede essa taxa ao Android. Não trava o FPS interno do jogo.",
        options = REFRESH_OPTIONS,
        selected = prefs.getInt("fpsLimit", 0).toString(),
    ) { set("fpsLimit", it.toInt()) }

    ChipSetting(
        title = "Qualidade da imagem (escala de renderização)",
        hint = "Valores altos deixam a imagem mais nítida, mas pesam na GPU.",
        options = SCALE_OPTIONS,
        selected = prefs.getFloat("renderScale", 1f).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() },
    ) { set("renderScale", it.toFloat()) }

    if (platformId == null || platformId == "n64") {
        ChipSetting(
            title = "Nintendo 64 · proporção interna",
            options = listOf("4:3" to "4:3", "16:9" to "16:9"),
            selected = prefs.getString("n64Aspect", "4:3"),
        ) { set("n64Aspect", it) }
        val n64Aspect = prefs.getString("n64Aspect", "4:3")
        if (n64Aspect == "4:3") {
            ChipSetting(
                title = "Nintendo 64 · resolução interna (4:3)",
                hint = "Resolução real do core. Aplica ao reiniciar o jogo.",
                options = resOptions(N64_43),
                selected = prefs.getString("n64Res43", "640x480"),
            ) { set("n64Res43", it) }
        } else {
            ChipSetting(
                title = "Nintendo 64 · resolução interna (16:9)",
                hint = "Resolução real do core. Aplica ao reiniciar o jogo.",
                options = resOptions(N64_169),
                selected = prefs.getString("n64Res169", "1280x720"),
            ) { set("n64Res169", it) }
        }
    }
    if (platformId == null || platformId == "psp") {
        ChipSetting(
            title = "PSP · resolução interna",
            hint = "Aplica ao reiniciar o jogo.",
            options = resOptions(PSP_RES),
            selected = prefs.getString("res_psp", "480x272"),
        ) { set("res_psp", it) }
    }
    if (platformId == null || platformId == "dreamcast") {
        ChipSetting(
            title = "Dreamcast · resolução interna",
            hint = "Aplica ao reiniciar o jogo.",
            options = resOptions(DC_RES),
            selected = prefs.getString("res_dreamcast", "640x480"),
        ) { set("res_dreamcast", it) }
    }
}
