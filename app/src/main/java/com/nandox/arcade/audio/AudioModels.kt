package com.nandox.arcade.audio

import kotlin.math.abs
import kotlin.math.max

/**
 * Sistema de áudio configurável do NDXEMULATOR.
 *
 * IMPORTANTE: tudo aqui se limita ao que o backend (LibretroDroid 0.13.2 + Oboe) realmente consegue
 * executar. O backend só aceita: fluxo de baixa latência (buffer de 4 quadros) ou estável (8 quadros),
 * mudo/som e velocidade. Reamostragem (linear), taxa de saída (nativa do aparelho) e ganho NÃO são
 * configuráveis por ele, então não são oferecidos como opção.
 */
enum class AudioMode(val id: String, val label: String) {
    AUTO("auto", "Automático"),
    STABLE("stable", "Estável"),
    LOW_LATENCY("low_latency", "Baixa latência"),
    REALTIME("realtime", "Tempo real");

    companion object {
        fun fromId(id: String?): AudioMode? = values().firstOrNull { it.id == id }
    }
}

/** Ajustes de um console (ou o padrão geral). */
data class AudioSettings(
    val mode: AudioMode = AudioMode.AUTO,
    /** Recuperação automática: se o jogo ficar abaixo da velocidade, eleva a prioridade da emulação. */
    val recovery: Boolean = true,
    /** Descarta o áudio antigo do buffer ao carregar save ou retomar o jogo (evita som atrasado). */
    val resync: Boolean = true,
    /** Autosave periódico durante o jogo. Congela a emulação por instantes e pode cortar o áudio. */
    val periodicAutosave: Boolean = true,
    /** Pede à TV o "modo jogo" (menos processamento de imagem, melhor sincronia entre imagem e som). */
    val gameModeDisplay: Boolean = true,
    /** Nintendo 64: confere as opções do núcleo que mais pesam na CPU (recompilador e RSP). */
    val coreGuard: Boolean = true,
    /** Mostra o diagnóstico de áudio sobre a imagem durante o jogo. */
    val overlay: Boolean = false,
)

/** O que o aparelho e o backend conseguem fazer. Só dados; a detecção fica em [AudioCapabilityDetector]. */
data class AudioCapabilities(
    val lowLatencyBackend: Boolean,
    val priorityBoost: Boolean,
    val sustainedPerformance: Boolean,
    val minimalPostProcessing: Boolean,
    val isTv: Boolean,
    val is64Bit: Boolean,
    val outputSampleRate: Int? = null,
    val outputFramesPerBuffer: Int? = null,
    val outputDevices: List<String> = emptyList(),
)

/** Resultado de sessões anteriores deste console: alimenta o modo automático. */
data class AudioHistory(val sessions: Int, val avgSpeed: Float)

object AudioPriority {
    /** android.os.Process.THREAD_PRIORITY_AUDIO */
    const val AUDIO = -16

    /** android.os.Process.THREAD_PRIORITY_URGENT_AUDIO */
    const val URGENT_AUDIO = -19
}

/** O que será de fato aplicado ao abrir o jogo. */
data class AudioPlan(
    val requested: AudioMode,
    val effective: AudioMode,
    val lowLatencyStream: Boolean,
    val threadPriority: Int?,
    val sustainedPerformance: Boolean,
    val minimalPostProcessing: Boolean,
    val periodicAutosave: Boolean,
    val bufferFrames: Int,
    val notes: List<String>,
) {
    /** Latência típica do buffer do backend (ele mira meio buffer cheio). */
    fun bufferLatencyMs(contentFps: Double): Double = max(bufferFrames / contentFps * 1000.0, 32.0)

    /** Quanto tempo o jogo pode ficar sem produzir som antes de o áudio falhar. */
    fun stallToleranceMs(contentFps: Double): Double = bufferLatencyMs(contentFps)

    /** Tempo para esvaziar o buffer inteiro (usado na ressincronização). */
    fun fifoCapacityMs(contentFps: Double): Double = bufferLatencyMs(contentFps) * 2.0
}

object AudioPlanner {
    /** Consoles cujo núcleo costuma ser pesado: o automático nunca usa baixa latência neles. */
    private val HEAVY = setOf("n64", "ps1", "ps2", "psp", "dreamcast", "gamecube", "wii", "3ds", "nds")

    fun resolve(
        settings: AudioSettings,
        caps: AudioCapabilities,
        platformId: String?,
        history: AudioHistory?,
    ): AudioPlan {
        val notes = mutableListOf<String>()
        val heavy = platformId != null && platformId in HEAVY
        val struggling = history != null && history.sessions >= 1 && history.avgSpeed < 0.97f

        var effective = when (settings.mode) {
            AudioMode.AUTO -> when {
                struggling && caps.priorityBoost -> {
                    notes += "Sessões anteriores ficaram abaixo da velocidade total (${(history!!.avgSpeed * 100).toInt()}%): modo tempo real."
                    AudioMode.REALTIME
                }
                heavy -> {
                    notes += "Console pesado: buffer estável para manter o som contínuo."
                    AudioMode.STABLE
                }
                caps.isTv || !caps.is64Bit || !caps.lowLatencyBackend -> {
                    notes += "TV, CPU de 32 bits ou sem suporte a baixa latência: buffer estável."
                    AudioMode.STABLE
                }
                else -> AudioMode.LOW_LATENCY
            }
            else -> settings.mode
        }
        if (effective == AudioMode.LOW_LATENCY && !caps.lowLatencyBackend) {
            notes += "Baixa latência não é suportada neste aparelho; usando Estável."
            effective = AudioMode.STABLE
        }
        if (effective == AudioMode.REALTIME && !caps.priorityBoost) {
            notes += "Este aparelho não permite elevar a prioridade da emulação; usando Estável."
            effective = AudioMode.STABLE
        }

        val priority = when {
            !caps.priorityBoost -> null
            effective == AudioMode.REALTIME -> AudioPriority.URGENT_AUDIO
            else -> AudioPriority.AUDIO
        }
        val lowLatency = effective == AudioMode.LOW_LATENCY
        return AudioPlan(
            requested = settings.mode,
            effective = effective,
            lowLatencyStream = lowLatency,
            threadPriority = priority,
            sustainedPerformance = effective == AudioMode.REALTIME && caps.sustainedPerformance,
            minimalPostProcessing = settings.gameModeDisplay && caps.minimalPostProcessing,
            periodicAutosave = settings.periodicAutosave,
            bufferFrames = if (lowLatency) 4 else 8,
            notes = notes,
        )
    }
}

/** Taxas de quadro esperadas por console (NTSC) e a regra de sincronia do backend. */
object AudioTiming {
    fun expectedFps(platformId: String?): Double = when (platformId) {
        "gb", "gba" -> 59.73
        "nes", "snes", "n64", "md", "ps1" -> 60.0
        else -> 60.0
    }

    /** O LibretroDroid usa vsync (e estica o tempo) quando a tela está a menos de 5 Hz do jogo. */
    fun usesVsync(contentFps: Double, displayHz: Double): Boolean = abs(contentFps - displayHz) < 5.0

    /** Taxa de referência para medir velocidade: a da tela no modo vsync, a do jogo no modo por tempo. */
    fun nominalFps(contentFps: Double, displayHz: Double): Double =
        if (displayHz > 0 && usesVsync(contentFps, displayHz)) displayHz else contentFps
}
