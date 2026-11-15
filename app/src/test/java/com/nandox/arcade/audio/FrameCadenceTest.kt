package com.nandox.arcade.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCadenceTest {
    private fun feed(c: FrameCadence, frames: Int, intervalNs: Long, startNs: Long = 1_000_000_000L): Long {
        var t = startNs
        repeat(frames) {
            c.onFrame(t)
            t += intervalNs
        }
        return t
    }

    @Test
    fun fullSpeedReportsOneHundredPercent() {
        val c = FrameCadence()
        feed(c, 200, 16_666_667L)
        val s = c.snapshot()
        assertEquals(1.0, s.speedWindow, 0.01)
        assertEquals(0L, s.probableCuts)
        assertEquals(0L, s.lateFrames)
    }

    @Test
    fun halfSpeedReportsFiftyPercent() {
        val c = FrameCadence()
        feed(c, 200, 33_333_333L)
        val s = c.snapshot()
        assertEquals(0.5, s.speedWindow, 0.01)
        assertEquals(0.5, s.speedTotal, 0.01)
        assertTrue(s.lateFrames > 0)
    }

    @Test
    fun longStallCountsAsProbableCut() {
        val c = FrameCadence()
        val t = feed(c, 60, 16_666_667L)
        c.onFrame(t + 400_000_000L)
        val s = c.snapshot()
        assertEquals(1L, s.probableCuts)
        assertTrue(s.missedFrames >= 20)
    }

    @Test
    fun resetClearsMeasurements() {
        val c = FrameCadence()
        feed(c, 100, 33_333_333L)
        c.reset()
        assertEquals(0L, c.snapshot().frames)
    }
}
