package com.nandox.arcade.controller

import kotlin.math.abs

class AnalogNavigator(private val deadzone: Float = 0.6f, private val repeatMs: Long = 200) {
    private var active: InputAction? = null
    private var lastFiredAt = 0L

    fun update(x: Float, y: Float, nowMs: Long): InputAction? {
        val direction = when {
            abs(x) >= deadzone && abs(x) >= abs(y) -> if (x > 0) InputAction.RIGHT else InputAction.LEFT
            abs(y) >= deadzone -> if (y > 0) InputAction.DOWN else InputAction.UP
            else -> null
        }
        if (direction == null) {
            active = null
            return null
        }
        if (direction != active || nowMs - lastFiredAt >= repeatMs) {
            active = direction
            lastFiredAt = nowMs
            return direction
        }
        return null
    }
}
