package com.nandox.arcade.audio

import kotlin.math.floor
import kotlin.math.max

/** Resultado de uma janela de medição de cadência de quadros. */
data class CadenceSnapshot(
    val frames: Long = 0,
    val elapsedSec: Double = 0.0,
    val fps: Double = 0.0,
    /** Velocidade de emulação na janela recente (1.0 = 100%). */
    val speedWindow: Double = 1.0,
    /** Velocidade de emulação desde o início da medição. */
    val speedTotal: Double = 1.0,
    val p95Ms: Double = 0.0,
    val worstWindowMs: Double = 0.0,
    val lateFrames: Long = 0,
    val missedFrames: Long = 0,
    /** Pausas na emulação maiores que a folga do buffer de áudio (estimativa de cortes de som). */
    val probableCuts: Long = 0,
)

/**
 * Mede o ritmo real em que a emulação entrega quadros. Como o áudio é produzido junto com cada quadro,
 * emulação abaixo da velocidade total significa som lento e picotado, mesmo com a imagem parecendo fluida.
 */
class FrameCadence(private val windowSize: Int = 300) {
    private val lock = Any()
    private val ring = LongArray(windowSize)
    private var head = 0
    private var count = 0
    private var last = 0L

    private var totalFrames = 0L
    private var totalNs = 0L
    private var late = 0L
    private var missed = 0L
    private var cuts = 0L

    @Volatile var nominalNs: Double = 1_000_000_000.0 / 60.0
    @Volatile var stallToleranceNs: Double = 133_000_000.0

    fun reset() {
        synchronized(lock) {
            head = 0
            count = 0
            last = 0L
            totalFrames = 0
            totalNs = 0
            late = 0
            missed = 0
            cuts = 0
        }
    }

    fun onFrame(nowNs: Long) {
        synchronized(lock) {
            if (last == 0L) {
                last = nowNs
            } else {
                val interval = nowNs - last
                last = nowNs
                if (interval > 0) record(interval)
            }
        }
    }

    private fun record(interval: Long) {
        ring[head] = interval
        head = (head + 1) % windowSize
        if (count < windowSize) count++
        totalFrames++
        totalNs += interval
        val nominal = nominalNs
        if (interval > nominal * 1.5) {
            late++
            missed += max(0.0, floor(interval / nominal) - 1.0).toLong()
        }
        if (interval - nominal > stallToleranceNs) {
            cuts++
        }
    }

    fun snapshot(): CadenceSnapshot = synchronized(lock) {
        if (count == 0 || totalNs == 0L) return@synchronized CadenceSnapshot()
        val nominal = nominalNs
        var windowNs = 0L
        var worst = 0L
        val copy = LongArray(count)
        for (i in 0 until count) {
            val v = ring[i]
            copy[i] = v
            windowNs += v
            if (v > worst) worst = v
        }
        copy.sort()
        val p95 = copy[((count - 1) * 0.95).toInt()]
        CadenceSnapshot(
            frames = totalFrames,
            elapsedSec = totalNs / 1e9,
            fps = count * 1e9 / windowNs,
            speedWindow = count * nominal / windowNs,
            speedTotal = totalFrames * nominal / totalNs,
            p95Ms = p95 / 1e6,
            worstWindowMs = worst / 1e6,
            lateFrames = late,
            missedFrames = missed,
            probableCuts = cuts,
        )
    }
}
