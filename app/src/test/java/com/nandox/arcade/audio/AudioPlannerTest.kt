package com.nandox.arcade.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlannerTest {
    private val tv32 = AudioCapabilities(
        lowLatencyBackend = true, priorityBoost = true, sustainedPerformance = false,
        minimalPostProcessing = true, isTv = true, is64Bit = false,
    )
    private val phone64 = tv32.copy(isTv = false, is64Bit = true, sustainedPerformance = true)

    @Test
    fun autoUsesStableBufferOnTvArm32() {
        val plan = AudioPlanner.resolve(AudioSettings(), tv32, "n64", null)
        assertEquals(AudioMode.STABLE, plan.effective)
        assertFalse(plan.lowLatencyStream)
        assertEquals(8, plan.bufferFrames)
    }

    @Test
    fun autoUsesLowLatencyOnLightConsoleInModernPhone() {
        val plan = AudioPlanner.resolve(AudioSettings(), phone64, "nes", null)
        assertEquals(AudioMode.LOW_LATENCY, plan.effective)
        assertTrue(plan.lowLatencyStream)
        assertEquals(4, plan.bufferFrames)
    }

    @Test
    fun autoNeverUsesLowLatencyOnHeavyConsole() {
        val plan = AudioPlanner.resolve(AudioSettings(), phone64, "n64", null)
        assertEquals(AudioMode.STABLE, plan.effective)
    }

    @Test
    fun unsupportedLowLatencyFallsBackToStable() {
        val caps = phone64.copy(lowLatencyBackend = false)
        val plan = AudioPlanner.resolve(AudioSettings(mode = AudioMode.LOW_LATENCY), caps, "nes", null)
        assertEquals(AudioMode.STABLE, plan.effective)
        assertTrue(plan.notes.isNotEmpty())
    }

    @Test
    fun unsupportedRealtimeFallsBackToStableWithoutPriority() {
        val caps = phone64.copy(priorityBoost = false)
        val plan = AudioPlanner.resolve(AudioSettings(mode = AudioMode.REALTIME), caps, "snes", null)
        assertEquals(AudioMode.STABLE, plan.effective)
        assertNull(plan.threadPriority)
    }

    @Test
    fun realtimeRaisesPriorityAndSustainedOnlyWhenSupported() {
        val plan = AudioPlanner.resolve(AudioSettings(mode = AudioMode.REALTIME), phone64, "snes", null)
        assertEquals(AudioMode.REALTIME, plan.effective)
        assertEquals(AudioPriority.URGENT_AUDIO, plan.threadPriority)
        assertTrue(plan.sustainedPerformance)
        val tvPlan = AudioPlanner.resolve(AudioSettings(mode = AudioMode.REALTIME), tv32, "snes", null)
        assertFalse(tvPlan.sustainedPerformance)
    }

    @Test
    fun autoEscalatesToRealtimeAfterSlowSessions() {
        val plan = AudioPlanner.resolve(AudioSettings(), tv32, "n64", AudioHistory(sessions = 2, avgSpeed = 0.7f))
        assertEquals(AudioMode.REALTIME, plan.effective)
    }

    @Test
    fun autoStaysStableWhenPreviousSessionsWereFullSpeed() {
        val plan = AudioPlanner.resolve(AudioSettings(), tv32, "n64", AudioHistory(sessions = 3, avgSpeed = 1.0f))
        assertEquals(AudioMode.STABLE, plan.effective)
    }

    @Test
    fun periodicAutosaveFollowsSettings() {
        val off = AudioPlanner.resolve(AudioSettings(periodicAutosave = false), tv32, "n64", null)
        assertFalse(off.periodicAutosave)
    }

    @Test
    fun bufferLatencyMatchesBackendRule() {
        val stable = AudioPlanner.resolve(AudioSettings(mode = AudioMode.STABLE), tv32, "snes", null)
        assertEquals(133.3, stable.bufferLatencyMs(60.0), 0.5)
        val low = AudioPlanner.resolve(AudioSettings(mode = AudioMode.LOW_LATENCY), tv32, "snes", null)
        assertEquals(66.7, low.bufferLatencyMs(60.0), 0.5)
    }

    @Test
    fun vsyncOnlyWhenDisplayIsCloseToContentRate() {
        assertTrue(AudioTiming.usesVsync(60.0, 60.0))
        assertTrue(AudioTiming.usesVsync(59.73, 60.0))
        assertFalse(AudioTiming.usesVsync(50.0, 60.0))
        assertEquals(60.0, AudioTiming.nominalFps(59.73, 60.0), 0.001)
        assertEquals(50.0, AudioTiming.nominalFps(50.0, 60.0), 0.001)
    }
}
