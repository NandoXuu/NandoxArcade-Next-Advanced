package com.nandox.arcade.controller

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyMapTest {
    @Test
    fun defaultsMapGamepadButtons() {
        val m = KeyMap()
        assertEquals(InputAction.CONFIRM, m.actionFor(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(InputAction.BACK, m.actionFor(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(InputAction.UP, m.actionFor(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(m.actionFor(KeyEvent.KEYCODE_BUTTON_X))
    }

    @Test
    fun remapReplacesActionKeys() {
        val m = KeyMap().with(InputAction.CONFIRM, KeyEvent.KEYCODE_BUTTON_X)
        assertEquals(InputAction.CONFIRM, m.actionFor(KeyEvent.KEYCODE_BUTTON_X))
        assertNull(m.actionFor(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test
    fun reassigningKeyRemovesItFromPreviousOverride() {
        val m = KeyMap()
            .with(InputAction.CONFIRM, KeyEvent.KEYCODE_BUTTON_X)
            .with(InputAction.BACK, KeyEvent.KEYCODE_BUTTON_X)
        assertEquals(InputAction.BACK, m.actionFor(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A), m.keysFor(InputAction.CONFIRM))
    }
}
