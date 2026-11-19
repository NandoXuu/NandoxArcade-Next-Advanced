package com.nandox.arcade.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandox.arcade.audio.AudioCapabilityDetector
import com.nandox.arcade.audio.AudioDiagnostics
import com.nandox.arcade.audio.AudioHistoryStore
import com.nandox.arcade.audio.AudioMode
import com.nandox.arcade.audio.AudioPlanner
import com.nandox.arcade.audio.AudioSettingsStore
import com.nandox.arcade.audio.AudioTiming
import com.nandox.arcade.audio.FindingLevel
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.settings.AppSettings
import java.io.File

/** Consoles que rodam dentro do app (núcleos nativos) e, por isso, têm áudio configurável. */
private val AUDIO_PLATFORMS = listOf("n64", "ps1", "snes", "nes", "gba", "gb", "md")

private fun latestReport(context: Context, platformId: String?): File? {
    val dir = File(AppSettings.baseDir(context), "diagnostics")
    return dir.listFiles { f -> f.name.startsWith("audio-") && f.name.endsWith(".txt") && (platformId == null || f.name.startsWith("audio-$platformId-")) }
        ?.maxByOrNull { it.lastModified() }
}

/**
 * Painel de áudio avançado. Só oferece o que o backend realmente executa neste aparelho.
 * [lockedPlatform] fixa o console (uso dentro do jogo); nulo deixa escolher o console ou o padrão geral.
 */
@Composable
fun AudioSettingsPanel(
    lockedPlatform: String? = null,
    diagnostics: AudioDiagnostics? = null,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { AudioSettingsStore.forContext(context) }
    val historyStore = remember { AudioHistoryStore.forContext(context) }
    val caps = remember { AudioCapabilityDetector.detect(context) }
    var scope by remember { mutableStateOf(lockedPlatform) }
    var tick by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val refresh = tick

    val settings = store.load(scope)
    val plan = AudioPlanner.resolve(settings, caps, scope, scope?.let { historyStore.load(it) })
    val fps = AudioTiming.expectedFps(scope)

    fun update(change: (com.nandox.arcade.audio.AudioSettings) -> com.nandox.arcade.audio.AudioSettings) {
        store.save(scope, change(store.load(scope)))
        tick++
        onChanged()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (lockedPlatform == null) {
            ChipSetting(
                title = "Console",
                hint = "Cada console tem o próprio ajuste. O Nintendo 64 tem perfil próprio e não segue o padrão geral.",
                options = listOf("default" to "Padrão geral") + AUDIO_PLATFORMS.map { it to (Platforms.byId(it)?.name ?: it) },
                selected = scope ?: "default",
            ) { scope = if (it == "default") null else it }
        } else {
            Text(
                "Ajustes de ${Platforms.byId(lockedPlatform)?.name ?: lockedPlatform}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val modes = buildList {
            add(AudioMode.AUTO)
            add(AudioMode.STABLE)
            if (caps.lowLatencyBackend) add(AudioMode.LOW_LATENCY)
            if (caps.priorityBoost) add(AudioMode.REALTIME)
        }
        ChipSetting(
            title = "Modo de áudio",
            hint = "Automático: escolhe pelo aparelho, console e histórico. Estável: som contínuo (buffer maior). " +
                (if (caps.lowLatencyBackend) "Baixa latência: resposta mais rápida, mas pode picotar. " else "") +
                (if (caps.priorityBoost) "Tempo real: Estável + prioridade máxima da emulação e desempenho sustentado." else ""),
            options = modes.map { it.id to it.label },
            selected = settings.mode.id,
        ) { id -> AudioMode.fromId(id)?.let { m -> update { s -> s.copy(mode = m) } } }

        Text(
            "Em uso: ${plan.effective.label} · buffer de ${plan.bufferFrames} quadros (≈${plan.bufferLatencyMs(fps).toInt()} ms). " +
                "O modo vale ao abrir o jogo." + plan.notes.joinToString(separator = " ", prefix = if (plan.notes.isEmpty()) "" else " "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SwitchSetting(
            title = "Recuperação automática",
            hint = "Se a emulação cair abaixo da velocidade total, eleva a prioridade dela na hora.",
            checked = settings.recovery,
        ) { v -> update { it.copy(recovery = v) } }

        SwitchSetting(
            title = "Corrigir atraso acumulado",
            hint = "Ao carregar um save ou voltar ao jogo, descarta o som antigo do buffer em vez de tocá-lo atrasado.",
            checked = settings.resync,
        ) { v -> update { it.copy(resync = v) } }

        SwitchSetting(
            title = "Salvar estado automático durante o jogo",
            hint = "A cada minuto congela a emulação por instantes e pode cortar o som. O salvamento ao sair e ao pausar continua sempre ligado.",
            checked = settings.periodicAutosave,
        ) { v -> update { it.copy(periodicAutosave = v) } }

        if (caps.minimalPostProcessing) {
            SwitchSetting(
                title = "Modo jogo da TV (sincronia imagem/som)",
                hint = "Pede à TV para reduzir o processamento da imagem, diminuindo o atraso do vídeo em relação ao som.",
                checked = settings.gameModeDisplay,
            ) { v -> update { it.copy(gameModeDisplay = v) } }
        }

        if (scope == "n64") {
            SwitchSetting(
                title = "Nintendo 64: conferir opções pesadas do núcleo",
                hint = "Registra no relatório o recompilador e o RSP do núcleo e ativa os mais rápidos se estiverem desligados. Não mexe na resolução.",
                checked = settings.coreGuard,
            ) { v -> update { it.copy(coreGuard = v) } }
        }

        SwitchSetting(
            title = "Mostrar diagnóstico na tela",
            hint = "Velocidade da emulação, fps e cortes de áudio sobre a imagem, durante o jogo.",
            checked = settings.overlay,
        ) { v -> update { it.copy(overlay = v) } }

        Spacer(Modifier.height(6.dp))
        Text("Diagnóstico", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        DiagnosticsBlock(context, diagnostics, scope)

        Spacer(Modifier.height(6.dp))
        Text("Neste aparelho", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        val unavailable = buildList {
            if (!caps.lowLatencyBackend) add("baixa latência")
            if (!caps.priorityBoost) add("tempo real (prioridade da emulação)")
            if (!caps.sustainedPerformance) add("desempenho sustentado")
            if (!caps.minimalPostProcessing) add("modo jogo da TV")
        }
        Text(
            buildString {
                append("Saída: ${caps.outputDevices.joinToString().ifEmpty { "não identificada" }}")
                append(" · ${caps.outputSampleRate ?: "?"} Hz nativos")
                append(if (caps.isTv) " · TV" else "")
                append(if (caps.is64Bit) " · 64 bits" else " · 32 bits")
                if (unavailable.isNotEmpty()) append("\nIndisponível aqui: ${unavailable.joinToString()}.")
                append("\nFixo no backend de áudio (não configurável): reamostragem linear, taxa de saída igual à do aparelho, ")
                append("buffer de 8 quadros (estável) ou 4 (baixa latência) e sem ganho de volume. Use o volume da TV ou do aparelho.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                store.reset(scope)
                tick++
                onChanged()
                Toast.makeText(context, "Padrões restaurados", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (scope == null) "Restaurar padrão geral" else "Restaurar padrões deste console") }
        OutlinedButton(
            onClick = {
                store.resetAll()
                historyStore.reset()
                tick++
                onChanged()
                Toast.makeText(context, "Todos os ajustes de áudio restaurados", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restaurar tudo (todos os consoles)") }
    }
}

@Composable
private fun DiagnosticsBlock(context: Context, live: AudioDiagnostics?, scope: String?) {
    if (live != null) {
        val c = live.cadence
        val lines = buildList {
            add("Velocidade: ${(c.speedWindow * 100).toInt()}% (média ${(c.speedTotal * 100).toInt()}%) · ${c.fps.toInt()} fps")
            add("Latência do buffer: ≈${live.bufferMs} ms · modo ${live.effectiveMode.label}")
            add("Quadros atrasados: ${c.lateFrames} · perdidos: ${c.missedFrames} · pior intervalo: ${c.worstWindowMs.toInt()} ms")
            add("Cortes de áudio prováveis: ${c.probableCuts} · som perdido estimado: ${live.estimatedSilencePct}%")
            live.load.cpu?.let { add("Thread de emulação: CPU ${(it * 100).toInt()}%" + (live.load.schedWait?.let { w -> " · espera ${(w * 100).toInt()}%" } ?: "")) }
            add("Tela ${"%.1f".format(live.displayHz)} Hz · ${if (live.vsync) "vsync" else "temporizador"} · prioridade ${live.priorityApplied ?: "-"}")
        }
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
        live.findings.forEach {
            val mark = when (it.level) {
                FindingLevel.OK -> "✓"
                FindingLevel.WARN -> "⚠"
                FindingLevel.INFO -> "ℹ"
            }
            Text("$mark ${it.text}", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val report = remember(scope) { latestReport(context, scope) }
    if (report == null) {
        Text(
            "Ainda não há medições. Jogue um pouco com o diagnóstico ligado: o relatório é salvo ao pausar ou sair do jogo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val text = remember(report) { runCatching { report.readText() }.getOrDefault("") }
    Text("Último relatório: ${report.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(text.lines().take(32).joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    OutlinedButton(
        onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Relatório de áudio", text))
            Toast.makeText(context, "Relatório copiado", Toast.LENGTH_SHORT).show()
        },
    ) { Text("Copiar relatório") }
}

/** Tela cheia do painel de áudio (Configurações → Áudio avançado). */
@Composable
fun AudioSettingsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenHeader("Áudio avançado", "Ajustes independentes por console", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AudioSettingsPanel()
            Spacer(Modifier.height(16.dp))
        }
    }
}
