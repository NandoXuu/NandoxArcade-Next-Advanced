package com.nandox.arcade.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalFx = staticCompositionLocalOf<ScreenFx> { error("ScreenFx ausente") }

@Stable
class ScreenFx(private val scope: CoroutineScope) {
    val dark = Animatable(0f)
    val water = Animatable(0f)
    var busy by mutableStateOf(false)
        private set
    private var awaitingReturn = false
    private var leftApp = false

    fun dip(action: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            dark.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
            action()
            delay(70)
            dark.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
            busy = false
        }
    }

    fun dive(action: () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            coroutineScope {
                launch { water.animateTo(1f, tween(1150, easing = LinearOutSlowInEasing)) }
                launch {
                    delay(260)
                    dark.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
                }
            }
            val revealNow = action()
            water.snapTo(0f)
            if (revealNow) {
                delay(120)
                reveal()
            } else {
                leftApp = false
                awaitingReturn = true
                delay(3500)
                if (awaitingReturn && !leftApp) reveal()
            }
        }
    }

    fun onPause() {
        leftApp = true
    }

    fun onResume() {
        if (awaitingReturn) scope.launch { reveal() }
    }

    private suspend fun reveal() {
        awaitingReturn = false
        dark.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
        busy = false
    }
}

@RequiresApi(33)
class WaterRenderer {
    private val shader = RuntimeShader(SOURCE)

    fun effect(width: Float, height: Float, t: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("t", t)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }

    private companion object {
        const val SOURCE = """
uniform shader content;
uniform float2 size;
uniform float t;

half4 main(float2 p) {
    float2 d = p - size * 0.5;
    float r = length(d);
    float reach = length(size) * 0.5;
    float front = t * reach * 1.15;
    float band = reach * (0.10 + 0.18 * t);
    float u = (r - front) / band;
    float ring = exp(-u * u);
    float trail = smoothstep(front - reach * 0.6, front, r) * step(r, front);
    float life = 1.0 - smoothstep(0.55, 1.0, t);
    float wave = sin(r * 0.055 - t * 26.0) * (ring + trail * 0.32) * life;
    float2 dir = d / max(r, 1.0);
    half4 col = content.eval(p + dir * wave * 26.0);
    float shade = wave * ring * life;
    col.rgb += half3(float3(0.10, 0.18, 0.30) * max(shade, 0.0));
    col.rgb *= half(1.0 - 0.10 * max(-shade, 0.0));
    return col;
}
"""
    }
}
