package com.nandox.arcade.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key as keyOf
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nandox.arcade.core.Platforms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

val ConsoleBg = Color(0xFF08090D)
private val KeyPurple = Color(0xFF8050F5)
private val KeyGlyph = Color(0xFF956AFF)
private val PillBg = Color(0xA30D0E13)
private val PillLine = Color(0x1CFFFFFF)

enum class Glyph { HOME, GEAR, BATTERY, LEFT, RIGHT, BACK, MENU, PLAY, MOVE }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier.size(24.dp), tint: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.085f
        val line = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val bold = Stroke(sw * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun pt(x: Float, y: Float) = Offset(w * x, h * y)
        fun tri(a: Offset, b: Offset, c: Offset) = drawPath(
            Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() },
            tint,
        )
        when (glyph) {
            Glyph.LEFT -> drawPath(
                Path().apply { moveTo(w * .62f, h * .2f); lineTo(w * .34f, h * .5f); lineTo(w * .62f, h * .8f) },
                tint, style = bold,
            )
            Glyph.RIGHT -> drawPath(
                Path().apply { moveTo(w * .38f, h * .2f); lineTo(w * .66f, h * .5f); lineTo(w * .38f, h * .8f) },
                tint, style = bold,
            )
            Glyph.BACK -> {
                drawLine(tint, pt(.2f, .5f), pt(.82f, .5f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
                drawPath(
                    Path().apply { moveTo(w * .45f, h * .24f); lineTo(w * .2f, h * .5f); lineTo(w * .45f, h * .76f) },
                    tint, style = bold,
                )
            }
            Glyph.HOME -> {
                drawPath(
                    Path().apply {
                        moveTo(w * .1f, h * .5f); lineTo(w * .5f, h * .14f); lineTo(w * .9f, h * .5f)
                        moveTo(w * .22f, h * .42f); lineTo(w * .22f, h * .84f); lineTo(w * .78f, h * .84f); lineTo(w * .78f, h * .42f)
                        moveTo(w * .42f, h * .84f); lineTo(w * .42f, h * .58f); lineTo(w * .58f, h * .58f); lineTo(w * .58f, h * .84f)
                    },
                    tint, style = line,
                )
            }
            Glyph.GEAR -> {
                val c = center
                drawCircle(tint, w * .14f, c, style = line)
                drawCircle(tint, w * .3f, c, style = line)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                    drawLine(tint, c + dir * (w * .3f), c + dir * (w * .43f), strokeWidth = sw * 1.6f, cap = StrokeCap.Round)
                }
            }
            Glyph.BATTERY -> {
                drawRoundRect(tint, pt(.06f, .3f), Size(w * .76f, h * .4f), CornerRadius(w * .08f), style = line)
                drawRoundRect(tint, pt(.13f, .38f), Size(w * .62f, h * .24f), CornerRadius(w * .03f))
                drawLine(tint, pt(.91f, .43f), pt(.91f, .57f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
            }
            Glyph.MENU -> for (y in listOf(.28f, .5f, .72f)) {
                drawLine(tint, pt(.2f, y), pt(.8f, y), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
            }
            Glyph.PLAY -> tri(pt(.3f, .2f), pt(.82f, .5f), pt(.3f, .8f))
            Glyph.MOVE -> {
                drawLine(tint, pt(.5f, .2f), pt(.5f, .8f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(tint, pt(.2f, .5f), pt(.8f, .5f), strokeWidth = sw, cap = StrokeCap.Round)
                tri(pt(.5f, .04f), pt(.38f, .22f), pt(.62f, .22f))
                tri(pt(.5f, .96f), pt(.38f, .78f), pt(.62f, .78f))
                tri(pt(.04f, .5f), pt(.22f, .38f), pt(.22f, .62f))
                tri(pt(.96f, .5f), pt(.78f, .38f), pt(.78f, .62f))
            }
        }
    }
}

@Composable
fun RoundIcon(glyph: Glyph, size: Dp = 36.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, Modifier.size(size * 0.62f)) }
}

private fun batteryLevel(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    return if (level < 0 || scale <= 0) 100 else level * 100 / scale
}

@Composable
private fun PillDivider() {
    Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(20.dp).background(Color(0x47FFFFFF)))
}

@Composable
fun StatusPill(leading: @Composable RowScope.() -> Unit = {}) {
    val context = LocalContext.current
    var time by remember { mutableStateOf("") }
    var battery by remember { mutableIntStateOf(100) }
    LaunchedEffect(Unit) {
        while (true) {
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            battery = batteryLevel(context)
            delay(15_000)
        }
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(PillBg)
            .border(1.dp, PillLine, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        PillDivider()
        Text(time, fontSize = 14.sp, color = Color(0xFFEEEEEE))
        PillDivider()
        Text("$battery%", fontSize = 14.sp, color = Color(0xFFEEEEEE))
        Spacer(Modifier.width(6.dp))
        GlyphIcon(Glyph.BATTERY, Modifier.size(22.dp), Color(0xFFEEEEEE))
    }
}

@Composable
fun ConsoleTopBar(
    title: String,
    leading: Glyph,
    onLeading: () -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(leading, 42.dp, onLeading)
            Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(28.dp).background(Color(0x47FFFFFF)))
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 3 }) togetherWith fadeOut(tween(140))
                },
                label = "title",
            ) { text ->
                Column(Modifier.width(IntrinsicSize.Max)) {
                    Text(
                        text.uppercase(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.045.em,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(2.dp)
                            .background(Brush.horizontalGradient(listOf(Color(0xE68355FF), Color.Transparent))),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        StatusPill(trailing)
    }
}

class Hint(val letter: String?, val glyph: Glyph?, val label: String, val onClick: () -> Unit)

@Composable
fun HintBar(hints: List<Hint>) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(PillBg)
            .border(1.dp, PillLine, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEachIndexed { i, hint ->
            if (i > 0) Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(24.dp).background(Color(0x38FFFFFF)))
            Row(
                Modifier.clip(CircleShape).clickable(onClick = hint.onClick).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hint.letter != null) {
                    Box(Modifier.size(30.dp).background(KeyPurple, CircleShape), contentAlignment = Alignment.Center) {
                        Text(hint.letter, color = Color(0xFF100B1E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else if (hint.glyph != null) {
                    GlyphIcon(hint.glyph, Modifier.size(28.dp), KeyGlyph)
                }
                Spacer(Modifier.width(9.dp))
                Text(hint.label, fontSize = 13.sp, color = Color(0xFFD9D9DF), maxLines = 1)
            }
        }
    }
}

@Composable
fun ConsoleBottom(start: @Composable () -> Unit, end: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                start()
                end()
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                end()
                start()
            }
        }
    }
}

@Composable
fun ActionChip(label: String, enabled: Boolean = true, accent: Boolean = false, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        Modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .then(
                if (accent) Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF9B7BFF), Color(0xFF7457E8))))
                else Modifier.background(PillBg).border(1.dp, PillLine, shape),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
    }
}

@Composable
fun ConsoleBackdrop(platformId: String?) {
    val blurred = Build.VERSION.SDK_INT >= 31
    Box(Modifier.fillMaxSize().background(ConsoleBg)) {
        Crossfade(platformId, animationSpec = tween(900), modifier = Modifier.fillMaxSize(), label = "backdrop") { id ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (blurred) 0.24f else 0.07f
                        scaleX = 1.7f
                        scaleY = 1.7f
                    }
                    .blur(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (id != null && Platforms.byId(id) != null) {
                    PlatformIcon(id, Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f))
                } else {
                    DefaultCover(Modifier.fillMaxHeight(0.7f))
                }
            }
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x38FFFFFF), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.66f),
                        radius = size.width * 0.34f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x2E305DFF), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.74f),
                        radius = size.width * 0.4f,
                    ),
                )
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color(0xB8050609), 0.22f to Color.Transparent, 0.78f to Color.Transparent, 1f to Color(0xB8050609),
                    ),
                )
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0x99050609), 0.28f to Color.Transparent, 0.65f to Color(0x38050609), 1f to ConsoleBg,
                    ),
                )
            },
        )
    }
}

@Stable
class WheelState(private val scope: CoroutineScope, initial: Int = 0) {
    val pos = Animatable(initial.toFloat())
    var target = initial
        private set
    var count = 0
    private val nearestState = derivedStateOf { pos.value.roundToInt() }
    private var lastDelta = 0f

    val nearest: Int get() = nearestState.value
    val wraps: Boolean get() = count >= 5
    val index: Int
        get() = when {
            count == 0 -> 0
            wraps -> Math.floorMod(nearest, count)
            else -> nearest.coerceIn(0, count - 1)
        }

    fun goTo(slot: Int) {
        val v = if (wraps) slot else slot.coerceIn(0, max(count - 1, 0))
        target = v
        scope.launch { pos.animateTo(v.toFloat(), spring(dampingRatio = 0.88f, stiffness = 190f)) }
    }

    fun move(delta: Int) = goTo(nearest + delta)

    fun select(index: Int) {
        if (count == 0) return
        var diff = index - this.index
        if (wraps) {
            if (diff > count / 2) diff -= count
            if (diff < -count / 2) diff += count
        }
        goTo(nearest + diff)
    }

    fun clamp() {
        if (count > 0 && !wraps) goTo(nearest)
    }

    fun drag(delta: Float) {
        lastDelta = -delta
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val v = pos.value - delta
            pos.snapTo(if (wraps) v else v.coerceIn(0f, max(count - 1, 0).toFloat()))
        }
    }

    fun endDrag() {
        val momentum = (lastDelta * 6f).coerceIn(-1.5f, 1.5f)
        lastDelta = 0f
        goTo((pos.value + momentum).roundToInt())
    }
}

@Composable
fun rememberWheelState(initial: Int = 0): WheelState {
    val scope = rememberCoroutineScope()
    return remember { WheelState(scope, initial) }
}

fun Modifier.wheelKeys(state: WheelState, onSelect: () -> Unit, onBack: (() -> Unit)? = null): Modifier =
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.keyOf) {
            Key.DirectionLeft, Key.DirectionUp, Key.ButtonL1, Key.PageUp -> { state.move(-1); true }
            Key.DirectionRight, Key.DirectionDown, Key.ButtonR1, Key.PageDown -> { state.move(1); true }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> { onSelect(); true }
            Key.ButtonB -> if (onBack != null) { onBack(); true } else false
            else -> false
        }
    }

@Composable
fun <T> Wheel(
    items: List<T>,
    state: WheelState,
    onActivate: (T) -> Unit,
    modifier: Modifier = Modifier,
    face: @Composable BoxScope.(item: T, index: Int, focus: () -> Float) -> Unit,
) {
    state.count = items.size
    val dn = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(items.size) { state.clamp() }
    LaunchedEffect(state) {
        var first = true
        snapshotFlow { state.index }.collect {
            if (!first) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            first = false
        }
    }
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    BoxWithConstraints(modifier) {
        val card = minOf(maxHeight * 0.92f, (maxWidth * 0.23f).coerceIn(220.dp, 370.dp))
        val spacing = maxOf(card * 0.62f, minOf(maxWidth * 0.30f, 430.dp))
        val spacingPx = with(dn) { spacing.toPx() }
        val cardPx = with(dn) { card.toPx() }
        val center = state.nearest
        val slots = if (state.wraps) (center - 3..center + 3).toList() else items.indices.toList()

        Box(
            Modifier.fillMaxSize().pointerInput(spacingPx, items.size) {
                detectHorizontalDragGestures(
                    onDragEnd = { state.endDrag() },
                    onDragCancel = { state.endDrag() },
                ) { change, amount ->
                    change.consume()
                    state.drag(amount / spacingPx)
                }
            },
        ) {
            slots.forEach { slot ->
                val idx = if (state.wraps) Math.floorMod(slot, items.size) else slot
                key(if (state.wraps) slot else idx) {
                    val interaction = remember { MutableInteractionSource() }
                    val focus: () -> Float = { (1f - abs(slot - state.pos.value)).coerceIn(0f, 1f) }
                    val radius = cardPx * 0.1f
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(card)
                            .zIndex(10f - abs(slot - center))
                            .graphicsLayer {
                                val d = slot - pos(state)
                                val ad = abs(d)
                                val far = min(ad, 2f)
                                translationX = d * spacingPx
                                translationY = (if (ad <= 1f) 20f * ad else 20f + 15f * (far - 1f)) * density
                                val s = if (ad <= 1f) 1f - 0.22f * ad else 0.78f - 0.20f * (far - 1f)
                                scaleX = s
                                scaleY = s
                                alpha = when {
                                    ad <= 1f -> 1f - 0.1f * ad
                                    ad <= 2f -> 0.9f - 0.52f * (ad - 1f)
                                    else -> (0.38f - 0.38f * (ad - 2f) / 0.6f).coerceAtLeast(0f)
                                }
                            }
                            .drawBehind {
                                val f = focus()
                                if (f > 0.01f) {
                                    listOf(2f to 0.4f, 7f to 0.17f, 16f to 0.07f).forEach { (grow, a) ->
                                        val g = grow.dp.toPx()
                                        drawRoundRect(
                                            Color.White.copy(alpha = a * f * pulse),
                                            topLeft = Offset(-g, -g),
                                            size = Size(size.width + 2 * g, size.height + 2 * g),
                                            cornerRadius = CornerRadius(radius + g),
                                            style = Stroke(width = max(g, 2.dp.toPx())),
                                        )
                                    }
                                }
                            }
                            .clip(RoundedCornerShape(with(dn) { radius.toDp() }))
                            .background(Brush.linearGradient(listOf(Color(0xFA101116), Color(0xFA040508))))
                            .drawWithContent {
                                drawContent()
                                val f = focus()
                                val w = (1f + 1.2f * f) * density
                                drawRoundRect(
                                    Color.White.copy(alpha = 0.14f + 0.82f * f),
                                    topLeft = Offset(w / 2, w / 2),
                                    size = Size(size.width - w, size.height - w),
                                    cornerRadius = CornerRadius(radius - w / 2),
                                    style = Stroke(w),
                                )
                                drawRect(Color.Black.copy(alpha = (1f - f) * 0.3f))
                            }
                            .clickable(interactionSource = interaction, indication = null) {
                                if (state.nearest == slot) onActivate(items[idx]) else state.goTo(slot)
                            },
                    ) { face(items[idx], idx, focus) }
                }
            }

            WheelArrow(Glyph.LEFT, Modifier.align(Alignment.CenterStart).padding(start = 14.dp)) { state.move(-1) }
            WheelArrow(Glyph.RIGHT, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)) { state.move(1) }
        }
    }
}

private fun pos(state: WheelState): Float = state.pos.value

@Composable
private fun WheelArrow(glyph: Glyph, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .zIndex(30f)
            .size(50.dp)
            .clip(CircleShape)
            .background(Color(0xA30A0B10))
            .border(1.dp, Color(0x1FFFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, Modifier.size(28.dp)) }
}

@Composable
fun WheelInfo(key: Any?, content: @Composable (Any?) -> Unit) {
    Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
        Crossfade(key, animationSpec = tween(260), label = "info") { content(it) }
    }
}

@Composable
fun PlayMark(modifier: Modifier = Modifier) {
    Box(modifier.size(34.dp).background(Color(0x29FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
        GlyphIcon(Glyph.PLAY, Modifier.size(16.dp))
    }
}

@Composable
fun CenterText(text: String, modifier: Modifier = Modifier, size: Int = 13, color: Color = Color(0xFFA4A5AD)) {
    Text(text, modifier, fontSize = size.sp, color = color, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
