package com.nandox.arcade.audio

import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDiagnosisTest {
    private val plan = AudioPlanner.resolve(
        AudioSettings(),
        AudioCapabilities(true, true, false, true, true, false),
        "n64",
        null,
    )

    private fun snap(speed: Double, cuts: Long = 0) =
        CadenceSnapshot(frames = 600, elapsedSec = 10.0, fps = 60 * speed, speedWindow = speed, speedTotal = speed, probableCuts = cuts)

    @Test
    fun slowEmulationWithBusyCpuBlamesCpu() {
        val findings = AudioDiagnosis.analyze(0.6, ThreadLoad(0.97, 0.02), snap(0.6), plan, usesVsync = true)
        assertTrue(findings.any { it.level == FindingLevel.WARN && it.text.contains("CPU") })
    }

    @Test
    fun slowEmulationWithSchedulerWaitSuggestsRealtime() {
        val findings = AudioDiagnosis.analyze(0.8, ThreadLoad(0.5, 0.4), snap(0.8), plan, usesVsync = true)
        assertTrue(findings.any { it.text.contains("Tempo real") })
    }

    @Test
    fun fullSpeedWithoutCutsPointsToOutputDevice() {
        val findings = AudioDiagnosis.analyze(1.0, ThreadLoad(0.5, 0.0), snap(1.0), plan, usesVsync = true)
        assertTrue(findings.all { it.level != FindingLevel.WARN })
        assertTrue(findings.any { it.text.contains("HDMI") })
    }

    @Test
    fun fullSpeedWithCutsWarns() {
        val findings = AudioDiagnosis.analyze(1.0, ThreadLoad(0.5, 0.0), snap(1.0, cuts = 3), plan, usesVsync = true)
        assertTrue(findings.any { it.level == FindingLevel.WARN })
    }

    @Test
    fun fewFramesOnlyReportsMeasuring() {
        val findings = AudioDiagnosis.analyze(1.0, ThreadLoad(null, null), CadenceSnapshot(frames = 10), plan, usesVsync = true)
        assertTrue(findings.single().level == FindingLevel.INFO)
    }
}
