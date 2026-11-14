package com.nandox.arcade.controller

import android.view.KeyEvent

class KeyMap(val overrides: Map<InputAction, Set<Int>> = emptyMap()) {

    fun keysFor(action: InputAction): Set<Int> = overrides[action] ?: defaults.getValue(action)

    fun actionFor(keyCode: Int): InputAction? =
        overrides.entries.firstOrNull { keyCode in it.value }?.key
            ?: InputAction.entries.firstOrNull { it !in overrides && keyCode in defaults.getValue(it) }

    fun with(action: InputAction, keyCode: Int): KeyMap {
        val others = overrides
            .filterKeys { it != action }
            .mapValues { it.value - keyCode }
            .filterValues { it.isNotEmpty() }
        return KeyMap(others + (action to setOf(keyCode)))
    }

    private companion object {
        val defaults: Map<InputAction, Set<Int>> = mapOf(
            InputAction.UP to setOf(KeyEvent.KEYCODE_DPAD_UP),
            InputAction.DOWN to setOf(KeyEvent.KEYCODE_DPAD_DOWN),
            InputAction.LEFT to setOf(KeyEvent.KEYCODE_DPAD_LEFT),
            InputAction.RIGHT to setOf(KeyEvent.KEYCODE_DPAD_RIGHT),
            InputAction.CONFIRM to setOf(
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A,
            ),
            InputAction.BACK to setOf(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE),
        )
    }
}
