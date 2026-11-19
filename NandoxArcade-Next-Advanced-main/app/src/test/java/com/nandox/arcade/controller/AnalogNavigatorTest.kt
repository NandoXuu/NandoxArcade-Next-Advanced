package com.nandox.arcade.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalogNavigatorTest {
    @Test
    fun insideDeadzoneDoesNothing() {
        assertNull(AnalogNavigator().update(0.3f, 0.2f, 0))
    }

    @Test
    fun firesOnceThenThrottlesRepeats() {
        val n = AnalogNavigator(repeatMs = 200)
        assertEquals(InputAction.RIGHT, n.update(0.9f, 0f, 1000))
        assertNull(n.update(0.9f, 0f, 1050))
        assertEquals(InputAction.RIGHT, n.update(0.9f, 0f, 1250))
    }

    @Test
    fun returningToCenterRearms() {
        val n = AnalogNavigator()
        assertEquals(InputAction.UP, n.update(0f, -0.9f, 1000))
        assertNull(n.update(0f, 0f, 1010))
        assertEquals(InputAction.UP, n.update(0f, -0.9f, 1020))
    }

    @Test
    fun directionChangeFiresImmediately() {
        val n = AnalogNavigator()
        n.update(0.9f, 0f, 1000)
        assertEquals(InputAction.DOWN, n.update(0f, 0.9f, 1010))
    }
}
