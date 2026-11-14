package com.nandox.arcade.emulator

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

private val IDLE = Color(0x55FFFFFF)
private val PRESSED = Color(0xAAFFFFFF)

/** Botão que fica "apertado" enquanto o dedo estiver sobre ele. Vários podem ser usados ao mesmo tempo. */
@Composable
fun HoldButton(
    label: String,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    fontSize: Int = 18,
) {
    val callback by rememberUpdatedState(onChange)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(shape)
            .background(if (pressed) PRESSED else IDLE)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    callback(true)
                    try {
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        pressed = false
                        callback(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
    }
}

private fun directions(pos: Offset, w: Float, h: Float): Set<Int> {
    val dx = (pos.x - w / 2f) / (w / 2f)
    val dy = (pos.y - h / 2f) / (h / 2f)
    val out = mutableSetOf<Int>()
    if (hypot(dx, dy) < 0.18f) return out
    if (dx < -0.35f) out += KeyEvent.KEYCODE_DPAD_LEFT
    if (dx > 0.35f) out += KeyEvent.KEYCODE_DPAD_RIGHT
    if (dy < -0.35f) out += KeyEvent.KEYCODE_DPAD_UP
    if (dy > 0.35f) out += KeyEvent.KEYCODE_DPAD_DOWN
    return out
}

/** Direcional digital em cruz, com diagonais. */
@Composable
fun DPad(size: Dp, onKey: (Int, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onKey)
    var active by remember { mutableStateOf(emptySet<Int>()) }

    Box(
        modifier
            .size(size)
            .pointerInput(Unit) {
                fun commit(next: Set<Int>) {
                    val prev = active
                    (prev - next).forEach { callback(it, false) }
                    (next - prev).forEach { callback(it, true) }
                    active = next
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = this.size.width.toFloat()
                    val h = this.size.height.toFloat()
                    commit(directions(down.position, w, h))
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val p = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                            if (p.pressed) commit(directions(p.position, w, h))
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        commit(emptySet())
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = this.size.minDimension / 3f
            val c = this.size.minDimension
            val r = CornerRadius(t / 3f)
            drawRoundRect(IDLE, Offset(t, 0f), Size(t, c), r)
            drawRoundRect(IDLE, Offset(0f, t), Size(c, t), r)
            if (KeyEvent.KEYCODE_DPAD_UP in active) drawRoundRect(PRESSED, Offset(t, 0f), Size(t, t), r)
            if (KeyEvent.KEYCODE_DPAD_DOWN in active) drawRoundRect(PRESSED, Offset(t, 2 * t), Size(t, t), r)
            if (KeyEvent.KEYCODE_DPAD_LEFT in active) drawRoundRect(PRESSED, Offset(0f, t), Size(t, t), r)
            if (KeyEvent.KEYCODE_DPAD_RIGHT in active) drawRoundRect(PRESSED, Offset(2 * t, t), Size(t, t), r)
        }
    }
}

/** Analógico virtual: x e y de -1 a 1 (y positivo = para baixo, como no MotionEvent). */
@Composable
fun AnalogStick(size: Dp, onMove: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onMove)
    var knob by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .size(size)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val radius = this.size.width / 2f

                    fun update(p: Offset) {
                        var x = (p.x - radius) / radius
                        var y = (p.y - radius) / radius
                        val len = hypot(x, y)
                        if (len > 1f) {
                            x /= len
                            y /= len
                        }
                        knob = Offset(x, y)
                        callback(x, y)
                    }

                    update(down.position)
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val p = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                            if (p.pressed) update(p.position)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        knob = Offset.Zero
                        callback(0f, 0f)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            drawCircle(Color(0x33FFFFFF), radius)
            drawCircle(IDLE, radius * 0.42f, center + Offset(knob.x * radius * 0.55f, knob.y * radius * 0.55f))
        }
    }
}
