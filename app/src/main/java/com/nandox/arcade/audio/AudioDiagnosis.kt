package com.nandox.arcade.audio

enum class FindingLevel { OK, INFO, WARN }

data class Finding(val level: FindingLevel, val text: String)

/** Carga da thread de emulação (GL) medida pelo kernel. Frações de um núcleo de CPU: 1.0 = 100%. */
data class ThreadLoad(val cpu: Double?, val schedWait: Double?)

/** Tudo o que o painel e o overlay mostram. */
data class AudioDiagnostics(
    val effectiveMode: AudioMode,
    val bufferMs: Int,
    val cadence: CadenceSnapshot,
    val load: ThreadLoad,
    val priorityWanted: Int?,
    val priorityApplied: Int?,
    val displayHz: Float,
    val vsync: Boolean,
    val findings: List<Finding>,
    val events: List<String>,
) {
    /** Percentual de som perdido estimado: se o jogo roda a X% da velocidade, falta (1-X) de áudio. */
    val estimatedSilencePct: Int get() = ((1.0 - cadence.speedTotal).coerceIn(0.0, 1.0) * 100).toInt()

    fun overlayText(): String =
        "♪ ${(cadence.speedWindow * 100).toInt()}% · ${cadence.fps.toInt()} fps · cortes ${cadence.probableCuts} · buf ${bufferMs} ms"
}

/**
 * Interpreta as medições e aponta a causa mais provável do áudio lento ou picotado. Não muda nada
 * sozinho: só diz onde está o gargalo.
 */
object AudioDiagnosis {
    fun analyze(speed: Double, load: ThreadLoad, snapshot: CadenceSnapshot, plan: AudioPlan, usesVsync: Boolean): List<Finding> {
        val out = mutableListOf<Finding>()
        if (snapshot.frames < 120) {
            out += Finding(FindingLevel.INFO, "Medindo… jogue por alguns segundos para ter um diagnóstico.")
            return out
        }
        if (speed >= 0.98) {
            out += Finding(FindingLevel.OK, "A emulação está em velocidade total (${(speed * 100).toInt()}%).")
            if (snapshot.probableCuts > 0) {
                out += Finding(
                    FindingLevel.WARN,
                    "Houve ${snapshot.probableCuts} pausa(s) maiores que o buffer de áudio (${plan.bufferFrames} quadros). " +
                        "Costumam vir de autosave, carregamento ou segundo plano; o modo Estável tolera mais.",
                )
            } else {
                out += Finding(FindingLevel.OK, "Nenhum corte de áudio estimado. Se ainda ouvir falhas, o problema está na saída de som da TV/HDMI/Bluetooth.")
            }
            return out
        }
        val pct = (speed * 100).toInt()
        out += Finding(
            FindingLevel.WARN,
            "A emulação roda a $pct% da velocidade. Como o som é gerado junto com cada quadro, ele sai lento e com falhas " +
                "(cerca de ${100 - pct}% do tempo sem áudio), mesmo que a imagem pareça fluida.",
        )
        val cpu = load.cpu
        val wait = load.schedWait
        when {
            cpu != null && cpu >= 0.85 -> out += Finding(
                FindingLevel.WARN,
                "Causa provável: CPU. A thread de emulação usa ${(cpu * 100).toInt()}% de um núcleo; o núcleo do console não " +
                    "consegue produzir quadros a tempo neste aparelho. Reduzir resolução só ajuda se o gargalo também for gráfico.",
            )
            wait != null && wait >= 0.15 -> out += Finding(
                FindingLevel.WARN,
                "Causa provável: concorrência por CPU. A thread de emulação passa ${(wait * 100).toInt()}% do tempo esperando " +
                    "o escalonador. Use o modo Tempo real para elevar a prioridade.",
            )
            cpu != null -> out += Finding(
                FindingLevel.WARN,
                "Causa provável: espera da GPU/tela. A thread de emulação usa só ${(cpu * 100).toInt()}% de CPU; o tempo vai para " +
                    "o desenho da imagem ou para a sincronia da tela" + (if (usesVsync) " (vsync)." else "."),
            )
            else -> out += Finding(FindingLevel.INFO, "Não foi possível medir a carga de CPU da thread de emulação neste aparelho.")
        }
        return out
    }
}
