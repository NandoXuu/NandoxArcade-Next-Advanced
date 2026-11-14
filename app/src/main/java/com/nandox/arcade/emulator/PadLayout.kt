package com.nandox.arcade.emulator

import android.view.KeyEvent

data class PadButton(val label: String, val keyCode: Int)

/**
 * Controles na tela de cada plataforma.
 *
 * O LibretroDroid mapeia pelo NOME do botão (BUTTON_A -> RetroPad A, que fica à direita; BUTTON_B -> RetroPad B,
 * embaixo; BUTTON_Y à esquerda; BUTTON_X em cima). Os rótulos abaixo seguem o que cada core espera.
 */
data class PadLayout(
    val bottom: PadButton? = null,
    val right: PadButton? = null,
    val left: PadButton? = null,
    val top: PadButton? = null,
    val shouldersLeft: List<PadButton> = emptyList(),
    val shouldersRight: List<PadButton> = emptyList(),
    val dpad: Boolean = false,
    val stick: Boolean = false,
    /** Botões C do N64, enviados como direções do analógico direito. */
    val cButtons: Boolean = false,
    val select: Boolean = true,
    /** Permite alternar entre direcional e analógico no menu (PSP). */
    val canToggleStick: Boolean = false,
) {
    companion object {
        private val L = PadButton("L", KeyEvent.KEYCODE_BUTTON_L1)
        private val R = PadButton("R", KeyEvent.KEYCODE_BUTTON_R1)
        private fun face(label: String, code: Int) = PadButton(label, code)

        private val B = KeyEvent.KEYCODE_BUTTON_B // embaixo
        private val A = KeyEvent.KEYCODE_BUTTON_A // direita
        private val Y = KeyEvent.KEYCODE_BUTTON_Y // esquerda
        private val X = KeyEvent.KEYCODE_BUTTON_X // em cima

        fun forPlatform(id: String): PadLayout = when (id) {
            "n64" -> PadLayout(
                bottom = face("A", B), left = face("B", Y),
                shouldersLeft = listOf(L, PadButton("Z", KeyEvent.KEYCODE_BUTTON_L2)),
                shouldersRight = listOf(R),
                stick = true, cButtons = true, select = false,
            )
            "gba" -> PadLayout(
                bottom = face("B", B), right = face("A", A),
                shouldersLeft = listOf(L), shouldersRight = listOf(R), dpad = true,
            )
            "nds" -> PadLayout(
                bottom = face("B", B), right = face("A", A), left = face("Y", Y), top = face("X", X),
                shouldersLeft = listOf(L), shouldersRight = listOf(R), dpad = true,
            )
            "ps1" -> PadLayout(
                bottom = face("✕", B), right = face("○", A), left = face("□", Y), top = face("△", X),
                shouldersLeft = listOf(PadButton("L1", KeyEvent.KEYCODE_BUTTON_L1), PadButton("L2", KeyEvent.KEYCODE_BUTTON_L2)),
                shouldersRight = listOf(PadButton("R1", KeyEvent.KEYCODE_BUTTON_R1), PadButton("R2", KeyEvent.KEYCODE_BUTTON_R2)),
                dpad = true,
            )
            "psp" -> PadLayout(
                bottom = face("✕", B), right = face("○", A), left = face("□", Y), top = face("△", X),
                shouldersLeft = listOf(L), shouldersRight = listOf(R),
                dpad = true, stick = true, canToggleStick = true,
            )
            "dreamcast" -> PadLayout(
                bottom = face("A", B), right = face("B", A), left = face("X", Y), top = face("Y", X),
                shouldersLeft = listOf(PadButton("LT", KeyEvent.KEYCODE_BUTTON_L2)),
                shouldersRight = listOf(PadButton("RT", KeyEvent.KEYCODE_BUTTON_R2)),
                stick = true, select = false,
            )
            else -> PadLayout(
                bottom = face("B", B), right = face("A", A), dpad = true,
            )
        }
    }
}
