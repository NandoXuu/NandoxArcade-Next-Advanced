package com.nandox.arcade.audio

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.WindowManager
import com.nandox.arcade.settings.AppSettings
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.Variable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Acompanha o áudio de uma partida: mede se a emulação mantém a velocidade total (que é o que decide
 * se o som sai contínuo), identifica o gargalo, recupera o que for possível e grava um relatório.
 *
 * Tudo roda na thread principal, exceto a leitura de opções do núcleo (thread própria) e a aplicação
 * de prioridade (feita direto na thread de emulação, pelo id dela).
 */
class AudioSession(
    private val context: Context,
    private val view: GLRetroView,
    private val platformId: String,
    private val gameKey: String?,
    initialPlan: AudioPlan,
    initialSettings: AudioSettings,
    private val caps: AudioCapabilities,
    private val baseline: AudioHistory?,
    private val isAudioWanted: () -> Boolean,
    private val onUpdate: (AudioDiagnostics) -> Unit,
) {
    private companion object {
        const val WARMUP_MS = 8_000L
        const val TICK_MS = 1_000L
        const val MIN_SECONDS_FOR_HISTORY = 20.0
        const val MAX_REPORTS = 8
        val N64_INTEREST = Regex("cpucore|rsp-plugin|rdp-plugin|Thread|Framerate|FrameDuping|virefresh|43screensize|169screensize|aspect")
    }

    @Volatile private var plan = initialPlan
    @Volatile private var settings = initialSettings
    private val handler = Handler(Looper.getMainLooper())
    private val cadence = FrameCadence()
    private val contentFps = AudioTiming.expectedFps(platformId)
    private val displayHz: Double = readDisplayHz()
    private val vsync = AudioTiming.usesVsync(contentFps, displayHz)

    private val events = ArrayDeque<String>()
    @Volatile private var glTid = 0
    @Volatile private var appliedPriority: Int? = null
    private var running = false
    private var warm = false
    private var startMs = 0L
    private var lowSeconds = 0
    private var escalated = false
    private var guardDone = false
    private var coreOptions: List<String> = emptyList()
    private var resyncToken = 0
    private var prevSample: Sample? = null
    private var load = ThreadLoad(null, null)
    private var lastDiag: AudioDiagnostics? = null
    private var reportFile: File? = null

    @Suppress("DEPRECATION")
    private fun readDisplayHz(): Double = runCatching {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate.toDouble()
    }.getOrDefault(60.0)

    private data class Sample(val cpuNs: Long, val waitNs: Long?, val wallNs: Long)

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                step()
            } catch (_: Throwable) {
                // diagnóstico nunca pode derrubar o jogo
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    // ---- ciclo de vida ----

    /** Chamar depois do primeiro quadro: aí a thread de emulação já existe e o core está rodando. */
    fun start() {
        if (running) return
        running = true
        startMs = SystemClock.elapsedRealtime()
        applyCadenceParams()
        // Evento único na thread GL: descobre o id dela e aplica a prioridade pedida pelo plano.
        view.queueEvent {
            val tid = Process.myTid()
            glTid = tid
            plan.threadPriority?.let { wanted -> applyPriority(tid, wanted, "plano") }
        }
        handler.postDelayed(tick, TICK_MS)
        if (settings.coreGuard) guardN64Core()
    }

    /** Chamado a cada quadro entregue pelo core. */
    fun onFrame() {
        if (running) cadence.onFrame(System.nanoTime())
    }

    fun updateSettings(newPlan: AudioPlan, newSettings: AudioSettings) {
        val oldPriority = plan.threadPriority
        plan = newPlan
        settings = newSettings
        applyCadenceParams()
        val tid = glTid
        val wanted = newPlan.threadPriority
        if (tid != 0 && wanted != null && wanted != oldPriority) applyPriority(tid, wanted, "ajuste")
        if (newSettings.coreGuard) guardN64Core()
    }

    /**
     * Descarta o áudio antigo que sobrou no buffer (após carregar save ou retomar), em vez de tocá-lo
     * atrasado. O buffer do backend se esvazia sozinho enquanto o som fica desligado por instantes.
     */
    fun resync(reason: String) {
        if (!running || !settings.resync || !isAudioWanted()) return
        val flushMs = (plan.fifoCapacityMs(contentFps) + 40).toLong()
        val token = ++resyncToken
        view.audioEnabled = false
        note("Áudio ressincronizado ($reason): buffer antigo descartado.")
        handler.postDelayed({
            if (token == resyncToken) view.audioEnabled = isAudioWanted()
        }, flushMs)
    }

    /** Fim da partida (ou saída do app): para o acompanhamento, grava histórico e relatório. */
    fun finish() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        recordHistory()
        writeReport()
    }

    /** Grava o relatório sem parar (o app pode ser encerrado pelo sistema enquanto está em segundo plano). */
    fun checkpoint() {
        if (!running) return
        recordHistory()
        writeReport()
    }

    // ---- medições ----

    private fun applyCadenceParams() {
        cadence.nominalNs = 1e9 / AudioTiming.nominalFps(contentFps, displayHz)
        cadence.stallToleranceNs = plan.stallToleranceMs(contentFps) * 1e6
    }

    private fun step() {
        val nowMs = SystemClock.elapsedRealtime()
        if (!warm && nowMs - startMs >= WARMUP_MS) {
            warm = true
            cadence.reset()
            prevSample = null
        }
        updateLoad()
        val snap = cadence.snapshot()
        val findings = if (warm) {
            AudioDiagnosis.analyze(snap.speedWindow, load, snap, plan, vsync)
        } else {
            listOf(Finding(FindingLevel.INFO, "Aquecendo: os primeiros segundos (carregamento do jogo) não entram na medição."))
        }
        if (warm) recover(snap)
        val diag = AudioDiagnostics(
            effectiveMode = plan.effective,
            bufferMs = plan.bufferLatencyMs(contentFps).toInt(),
            cadence = if (warm) snap else CadenceSnapshot(),
            load = load,
            priorityWanted = plan.threadPriority,
            priorityApplied = appliedPriority,
            displayHz = displayHz.toFloat(),
            vsync = vsync,
            findings = findings,
            events = eventsSnapshot(),
        )
        lastDiag = diag
        onUpdate(diag)
    }

    private fun updateLoad() {
        val sample = readSample() ?: return
        val prev = prevSample
        prevSample = sample
        if (prev == null || sample.wallNs <= prev.wallNs) return
        val dw = (sample.wallNs - prev.wallNs).toDouble()
        val cpu = (sample.cpuNs - prev.cpuNs) / dw
        val wait = if (sample.waitNs != null && prev.waitNs != null) (sample.waitNs - prev.waitNs) / dw else null
        // média móvel curta para não oscilar a cada segundo
        load = ThreadLoad(
            cpu = load.cpu?.let { it * 0.5 + cpu * 0.5 } ?: cpu,
            schedWait = if (wait == null) null else (load.schedWait?.let { it * 0.5 + wait * 0.5 } ?: wait),
        )
    }

    /** Tempo de CPU e de espera no escalonador da thread de emulação, lidos do kernel. */
    private fun readSample(): Sample? {
        val tid = glTid
        if (tid == 0) return null
        val wall = SystemClock.elapsedRealtimeNanos()
        val sched = runCatching { File("/proc/self/task/$tid/schedstat").readText().trim().split(' ') }.getOrNull()
        if (sched != null && sched.size >= 2) {
            val run = sched[0].toLongOrNull()
            val wait = sched[1].toLongOrNull()
            if (run != null && wait != null && (run > 0 || wait > 0)) return Sample(run, wait, wall)
        }
        val stat = runCatching { File("/proc/self/task/$tid/stat").readText() }.getOrNull() ?: return null
        val fields = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
        val utime = fields.getOrNull(11)?.toLongOrNull() ?: return null
        val stime = fields.getOrNull(12)?.toLongOrNull() ?: return null
        return Sample((utime + stime) * 10_000_000L, null, wall) // 100 ticks por segundo
    }

    // ---- recuperação ----

    private fun recover(snap: CadenceSnapshot) {
        if (!settings.recovery) return
        if (snap.frames > 120 && snap.speedWindow < 0.97) lowSeconds++ else if (lowSeconds > 0) lowSeconds--
        val tid = glTid
        val applied = appliedPriority
        if (lowSeconds >= 5 && !escalated && tid != 0 && caps.priorityBoost && (applied == null || applied > AudioPriority.URGENT_AUDIO)) {
            escalated = true
            note("Recuperação: velocidade em ${(snap.speedWindow * 100).toInt()}%; prioridade da emulação elevada ao máximo.")
            applyPriority(tid, AudioPriority.URGENT_AUDIO, "recuperação")
        }
    }

    private fun applyPriority(tid: Int, wanted: Int, origin: String) {
        val result = try {
            Process.setThreadPriority(tid, wanted)
            Process.getThreadPriority(tid)
        } catch (_: Throwable) {
            null
        }
        appliedPriority = result
        note(
            if (result == null) "Prioridade da emulação ($origin): o sistema recusou o pedido de $wanted."
            else "Prioridade da emulação ($origin): pedida $wanted, aplicada $result.",
        )
    }

    /**
     * Nintendo 64: registra as opções do núcleo que mais pesam na CPU e, se o núcleo anunciar que o
     * recompilador dinâmico/RSP HLE existem mas não estão ativos, ativa. Nunca altera resolução nem imagem.
     */
    private fun guardN64Core() {
        if (platformId != "n64" || guardDone) return
        guardDone = true
        Thread {
            try {
                val vars = view.getVariables()
                coreOptions = vars.filter { it.key?.let { k -> N64_INTEREST.containsMatchIn(k) } == true }
                    .map { "${it.key}=${it.value}" }
                val fixes = mutableListOf<Variable>()
                fun want(key: String, value: String) {
                    val v = vars.firstOrNull { it.key == key } ?: return
                    if (v.value == value) return
                    val allowed = (v.description ?: "").substringAfter("; ", "").split('|').map { it.trim() }
                    if (value in allowed) fixes += Variable(key, value)
                }
                want("mupen64plus-cpucore", "dynamic_recompiler")
                want("mupen64plus-rsp-plugin", "hle")
                if (fixes.isNotEmpty()) {
                    view.updateVariables(*fixes.toTypedArray())
                    handler.post { note("Núcleo N64: ${fixes.joinToString { "${it.key}=${it.value}" }} (estava mais lento).") }
                }
            } catch (_: Throwable) {
                // o core pode ainda não ter registrado as opções; o relatório só fica sem essa seção
            }
        }.start()
    }

    @Synchronized
    private fun note(text: String) {
        events.addLast(text)
        while (events.size > 20) events.removeFirst()
    }

    @Synchronized
    private fun eventsSnapshot(): List<String> = events.toList()

    // ---- histórico e relatório ----

    private fun recordHistory() {
        val snap = cadence.snapshot()
        if (!warm || snap.elapsedSec < MIN_SECONDS_FOR_HISTORY) return
        AudioHistoryStore.forContext(context).record(platformId, baseline, snap.speedTotal.toFloat())
    }

    private fun writeReport() {
        runCatching {
            val dir = File(AppSettings.baseDir(context), "diagnostics").apply { mkdirs() }
            val file = reportFile ?: File(
                dir,
                "audio-$platformId-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt",
            ).also { reportFile = it }
            file.writeText(buildReport())
            dir.listFiles { f -> f.name.startsWith("audio-") && f.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_REPORTS)
                ?.forEach { it.delete() }
        }
    }

    fun buildReport(): String {
        val d = lastDiag
        val snap = cadence.snapshot()
        val sb = StringBuilder()
        sb.appendLine("NDXEMULATOR — relatório de áudio")
        sb.appendLine("Console: $platformId   Jogo: ${gameKey ?: "-"}")
        sb.appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("CPU: ${Build.SUPPORTED_ABIS.joinToString()} · ${if (caps.is64Bit) "processo 64 bits" else "processo 32 bits"} · ${Runtime.getRuntime().availableProcessors()} núcleos · TV: ${caps.isTv}")
        sb.appendLine("Saída de áudio: ${caps.outputDevices.joinToString().ifEmpty { "?" }} · ${caps.outputSampleRate ?: "?"} Hz · ${caps.outputFramesPerBuffer ?: "?"} quadros/burst")
        sb.appendLine("Tela: ${"%.2f".format(Locale.US, displayHz)} Hz · sincronia: ${if (vsync) "vsync (tempo esticado)" else "por temporizador"}")
        sb.appendLine()
        sb.appendLine("Modo pedido: ${plan.requested.label} · efetivo: ${plan.effective.label}")
        sb.appendLine("Buffer do backend: ${plan.bufferFrames} quadros ≈ ${plan.bufferLatencyMs(contentFps).toInt()} ms (tolera pausas até esse tempo)")
        sb.appendLine("Prioridade da thread de emulação: pedida ${plan.threadPriority ?: "-"}, aplicada ${appliedPriority ?: "-"}")
        sb.appendLine("Desempenho sustentado: ${plan.sustainedPerformance} · modo jogo da TV: ${plan.minimalPostProcessing} · autosave periódico: ${plan.periodicAutosave}")
        plan.notes.forEach { sb.appendLine("Nota: $it") }
        sb.appendLine()
        sb.appendLine("Medição (${"%.0f".format(Locale.US, snap.elapsedSec)} s, sem o aquecimento)")
        sb.appendLine("Velocidade média: ${(snap.speedTotal * 100).toInt()}% · recente: ${(snap.speedWindow * 100).toInt()}% · ${"%.1f".format(Locale.US, snap.fps)} fps")
        sb.appendLine("Quadros atrasados: ${snap.lateFrames} · perdidos: ${snap.missedFrames} · pior intervalo: ${"%.0f".format(Locale.US, snap.worstWindowMs)} ms · p95: ${"%.1f".format(Locale.US, snap.p95Ms)} ms")
        sb.appendLine("Cortes de áudio prováveis (pausa > buffer): ${snap.probableCuts} · som perdido estimado: ${((1.0 - snap.speedTotal).coerceIn(0.0, 1.0) * 100).toInt()}%")
        d?.load?.let { l ->
            sb.appendLine("Thread de emulação: CPU ${l.cpu?.let { (it * 100).toInt().toString() + "%" } ?: "?"} · espera no escalonador ${l.schedWait?.let { (it * 100).toInt().toString() + "%" } ?: "?"}")
        }
        sb.appendLine("(cortes e som perdido são estimativas: o backend não informa underruns diretamente)")
        sb.appendLine()
        sb.appendLine("Diagnóstico:")
        (d?.findings ?: emptyList()).forEach { sb.appendLine(" - [${it.level}] ${it.text}") }
        if (coreOptions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Opções do núcleo N64:")
            coreOptions.forEach { sb.appendLine(" - $it") }
        }
        val log = eventsSnapshot()
        if (log.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Eventos:")
            log.forEach { sb.appendLine(" - $it") }
        }
        return sb.toString()
    }
}
