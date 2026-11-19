package com.nandox.arcade.controller

import android.view.KeyEvent

enum class InputAction(val label: String, val navKeyCode: Int?) {
    UP("Cima", KeyEvent.KEYCODE_DPAD_UP),
    DOWN("Baixo", KeyEvent.KEYCODE_DPAD_DOWN),
    LEFT("Esquerda", KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT("Direita", KeyEvent.KEYCODE_DPAD_RIGHT),
    CONFIRM("Confirmar", KeyEvent.KEYCODE_DPAD_CENTER),
    BACK("Voltar", null),
}
