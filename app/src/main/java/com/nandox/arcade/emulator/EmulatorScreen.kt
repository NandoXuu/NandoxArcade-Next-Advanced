package com.nandox.arcade.emulator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.swordfish.libretrodroid.GLRetroView

@Composable
fun EmulatorScreen(
    view: GLRetroView?,
    status: String?,
    layout: PadLayout,
    onKey: (keyCode: Int, down: Boolean) -> Unit,
    onMotion: (source: Int, x: Float, y: Float) -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
    onSaveState: () -> Unit,
    onLoadState: (java.io.File) -> Unit,
    onDeleteState: (java.io.File) -> Unit,
    onAudioChange: (Boolean) -> Unit,
    audioEnabled: Boolean,
    onShaderChange: (String) -> Unit,
    shaderMode: String,
    fpsLimit: Int,
    renderScale: Float,
    n64Res43: String,
    n64Res169: String,
    n64Aspect: String,
    onN64ResolutionChange: (String, String) -> Unit,
    onFpsLimitChange: (Int) -> Unit,
    onRenderScaleChange: (Float) -> Unit,
    stateSlots: List<StateSlot>,
    platformId: String,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("emulator_display", android.content.Context.MODE_PRIVATE) }
    var displayMode by remember { mutableStateOf(preferences.getString("mode", "original") ?: "original") }
    var controlScale by remember { mutableStateOf(preferences.getFloat("controlScale", 1f)) }
    var menu by remember { mutableStateOf(false) }
    var showStates by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var stickMode by remember { mutableStateOf(true) }
    var editControls by remember { mutableStateOf(false) }
    var fpsSelection by remember { mutableIntStateOf(fpsLimit) }
    var renderScaleSelection by remember { mutableStateOf(renderScale) }
    var n64Res43Selection by remember { mutableStateOf(n64Res43) }
    var n64Res169Selection by remember { mutableStateOf(n64Res169) }
    var n64AspectSelection by remember { mutableStateOf(n64Aspect) }
    var cPressed by remember { mutableStateOf(emptySet<Char>()) }

    BackHandler { menu = true }

    fun setC(c: Char, down: Boolean) {
        cPressed = if (down) cPressed + c else cPressed - c
        val x = (if ('R' in cPressed) 1f else 0f) - (if ('L' in cPressed) 1f else 0f)
        val y = (if ('D' in cPressed) 1f else 0f) - (if ('U' in cPressed) 1f else 0f)
        onMotion(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, x, y)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (view != null) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ratio = when (displayMode) {
                    "4:3" -> 4f / 3f
                    "16:9" -> 16f / 9f
                    "3:2" -> 3f / 2f
                    "original" -> when (platformId) {
                        "gba" -> 3f / 2f
                        "psp" -> 16f / 9f
                        else -> 4f / 3f
                    }
                    else -> 0f
                }
                val renderWidth = if (ratio == 0f) maxWidth else minOf(maxWidth, maxHeight * ratio)
                val renderHeight = if (ratio == 0f) maxHeight else minOf(maxHeight, maxWidth / ratio)
                val scale = renderScaleSelection.coerceIn(1f, 4f)
                AndroidView(
                    factory = { view },
                    modifier = Modifier
                        .size(renderWidth * scale, renderHeight * scale)
                        .graphicsLayer(scaleX = 1f / scale, scaleY = 1f / scale),
                )
            }
        }

        if (status != null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = Color.White)
                Text(status, color = Color.White)
            }
        }

        if (view != null && showControls) {
            val useStick = if (layout.canToggleStick) stickMode else layout.stick
            val useDpad = if (layout.canToggleStick) !stickMode else layout.dpad
            BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(8.dp),
            ) {
                val w = maxWidth
                val h = maxHeight
                @Composable
                fun place(
                    id: String, label: String, defaultX: Float, defaultY: Float,
                    width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp,
                    content: @Composable () -> Unit,
                ) {
                    PlacedControl(
                        id = id, label = label, defaultX = defaultX, defaultY = defaultY,
                        containerWidth = w, containerHeight = h,
                        width = width, height = height, editing = editControls,
                        preferences = preferences, content = content,
                    )
                }
                if (editControls) {
                    Box(Modifier.align(Alignment.TopCenter).background(Color(0xDD20252D), RoundedCornerShape(10.dp)).clickable { editControls = false }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Arraste os controles · Concluir", color = Color.White)
                    }
                } else {
                    place("menu", "MENU", .46f, .02f, 64.dp, 38.dp) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Color(0x66555555)).clickable { menu = true }, contentAlignment = Alignment.Center) {
                            Text("☰", color = Color.White)
                        }
                    }
                }
                layout.shouldersLeft.forEachIndexed { i, b ->
                    place("shoulder_l_$i", b.label, .02f + i * .10f, .04f, 62.dp, 38.dp) {
                        HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.fillMaxSize(), RoundedCornerShape(12.dp), 14)
                    }
                }
                layout.shouldersRight.forEachIndexed { i, b ->
                    place("shoulder_r_$i", b.label, .78f + i * .10f, .04f, 62.dp, 38.dp) {
                        HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.fillMaxSize(), RoundedCornerShape(12.dp), 14)
                    }
                }
                if (useDpad) place("dpad", "D-PAD", .03f, .62f, 150.dp * controlScale, 150.dp * controlScale) {
                    DPad(size = 150.dp * controlScale, onKey = onKey, modifier = Modifier.fillMaxSize())
                }
                if (useStick) place("stick", "ANALÓGICO", .03f, .62f, 150.dp * controlScale, 150.dp * controlScale) {
                    AnalogStick(size = 150.dp * controlScale, onMove = { x, y -> onMotion(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y) }, modifier = Modifier.fillMaxSize())
                }
                val faceSize = if (listOfNotNull(layout.left, layout.top).isEmpty()) 68.dp else 56.dp
                layout.bottom?.let { b -> place("face_bottom", b.label, .85f, .78f, faceSize * controlScale, faceSize * controlScale) {
                    HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.fillMaxSize())
                } }
                layout.right?.let { b -> place("face_right", b.label, .91f, .68f, faceSize * controlScale, faceSize * controlScale) {
                    HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.fillMaxSize())
                } }
                layout.left?.let { b -> place("face_left", b.label, .78f, .68f, faceSize * controlScale, faceSize * controlScale) {
                    HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.fillMaxSize())
                } }
                layout.top?.let { b -> place("face_top", b.label, .85f, .58f, faceSize * controlScale, faceSize * controlScale) {
                    HoldButton(b.label, { onKey(b.keyCode, it) }, Modifier.fillMaxSize())
                } }
                if (layout.cButtons) {
                    listOf("▲" to 'U', "▼" to 'D', "◀" to 'L', "▶" to 'R').forEachIndexed { i, item ->
                        val positions = listOf(.70f to .59f, .70f to .78f, .65f to .685f, .75f to .685f)
                        place("c_$i", item.first, positions[i].first, positions[i].second, 42.dp * controlScale, 42.dp * controlScale) {
                            HoldButton(item.first, { down ->
                                cPressed = if (down) cPressed + item.second else cPressed - item.second
                                val x = (if ('R' in cPressed) 1f else 0f) - (if ('L' in cPressed) 1f else 0f)
                                val y = (if ('D' in cPressed) 1f else 0f) - (if ('U' in cPressed) 1f else 0f)
                                onMotion(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, x, y)
                            }, Modifier.fillMaxSize(), CircleShape, 14)
                        }
                    }
                }
                if (layout.select) place("select", "SELECT", .38f, .90f, 76.dp * controlScale, 32.dp * controlScale) {
                    HoldButton("SELECT", { onKey(android.view.KeyEvent.KEYCODE_BUTTON_SELECT, it) }, Modifier.fillMaxSize(), RoundedCornerShape(15.dp), 11)
                }
                place("start", "START", .52f, .90f, 76.dp * controlScale, 32.dp * controlScale) {
                    HoldButton("START", { onKey(android.view.KeyEvent.KEYCODE_BUTTON_START, it) }, Modifier.fillMaxSize(), RoundedCornerShape(15.dp), 11)
                }
            }
        }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("Menu") },
            text = {
                Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { menu = false }) { Text("Continuar") }
                    Text("PROPORÇÃO DA TELA", color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    Text("ÁUDIO")
                    Switch(checked = audioEnabled, onCheckedChange = onAudioChange)
                    Text("LIMITE / ATUALIZAÇÃO DA TELA")
                    Text("Solicitação de Hz ao Android (não força o FPS interno do core).", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    listOf(0 to "Automático", 30 to "30 Hz", 60 to "60 Hz", 90 to "90 Hz", 120 to "120 Hz").forEach { (value, label) ->
                        TextButton(onClick = { fpsSelection = value; onFpsLimitChange(value) }) {
                            Text((if (fpsSelection == value) "✓  " else "") + label)
                        }
                    }
                    Text("RESOLUÇÃO DA SUPERFÍCIE")
                    Text("Escala do buffer de saída. A resolução interna do core pode ser fixa.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    listOf(1f to "1× (nativa)", 1.5f to "1,5×", 2f to "2×", 3f to "3×", 4f to "4× (uso alto de GPU)").forEach { (value, label) ->
                        TextButton(onClick = { renderScaleSelection = value; onRenderScaleChange(value) }) {
                            Text((if (renderScale == value) "✓  " else "") + label)
                        }
                    }
                    if (platformId == "n64") {
                        Text("RESOLUÇÃO INTERNA DO NINTENDO 64")
                        Text("Configuração do core Mupen64Plus-Next. É aplicada ao reiniciar o jogo.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        listOf("4:3" to listOf("320x240", "640x480", "960x720", "1280x960", "1600x1200", "1920x1440", "2560x1920", "3840x2880"),
                            "16:9" to listOf("640x360", "960x540", "1280x720", "1920x1080", "2560x1440", "3840x2160")).forEach { (aspect, options) ->
                            Text(if (aspect == "4:3") "Proporção 4:3" else "Proporção 16:9")
                            options.forEach { resolution ->
                                val selected = if (aspect == "4:3") n64Res43Selection else n64Res169Selection
                                TextButton(onClick = {
                                    displayMode = aspect
                                    n64AspectSelection = aspect
                                    if (aspect == "4:3") n64Res43Selection = resolution else n64Res169Selection = resolution
                                    preferences.edit().putString("mode", aspect).apply()
                                    onN64ResolutionChange(aspect, resolution)
                                }) {
                                    Text((if (selected == resolution && n64AspectSelection == aspect) "✓  " else "") + resolution)
                                }
                            }
                        }
                    }
                    Text("SHADER")
                    listOf("default" to "Padrão", "sharp" to "Nítido", "crt" to "CRT", "lcd" to "LCD").forEach { (key, label) ->
                        TextButton(onClick = { onShaderChange(key) }) {
                            Text((if (shaderMode == key) "✓  " else "") + label)
                        }
                    }
                    Text("TAMANHO DOS CONTROLES")
                    Slider(
                        value = controlScale,
                        onValueChange = {
                            controlScale = it
                            preferences.edit().putFloat("controlScale", it).apply()
                        },
                        valueRange = 0.65f..1.35f,
                    )
                    TextButton(onClick = { onSaveState() }) { Text("Salvar estado agora") }
                    TextButton(onClick = { menu = false; showStates = true }) { Text("Carregar um save (${stateSlots.size})") }
                    listOf("original" to "Original", "4:3" to "4:3", "16:9" to "16:9", "3:2" to "3:2", "stretch" to "Esticado / tela cheia").forEach { (key, label) ->
                        TextButton(onClick = {
                            displayMode = key
                            preferences.edit().putString("mode", key).apply()
                        }) {
                            Text((if (displayMode == key) "✓  " else "") + label)
                        }
                    }
                    TextButton(onClick = { editControls = true; menu = false }) { Text("Editar posição dos botões") }
                    TextButton(onClick = {
                        preferences.edit().also { editor ->
                            preferences.all.keys.filter { it.startsWith("control_") }.forEach { editor.remove(it) }
                        }.apply()
                        editControls = true
                        menu = false
                    }) { Text("Restaurar posições dos botões") }
                    TextButton(onClick = { showControls = !showControls; menu = false }) {
                        Text(if (showControls) "Ocultar controles na tela" else "Mostrar controles na tela")
                    }
                    if (layout.canToggleStick) {
                        TextButton(onClick = { stickMode = !stickMode; menu = false }) {
                            Text(if (stickMode) "Usar direcional (D-pad)" else "Usar analógico")
                        }
                    }
                    TextButton(onClick = { menu = false; onReset() }) { Text("Reiniciar jogo") }
                    TextButton(onClick = { menu = false; onExit() }) { Text("Sair do jogo") }
                }
            },
            confirmButton = {},
        )
    }

    if (showStates) {
        AlertDialog(
            onDismissRequest = { showStates = false },
            title = { Text("Saves deste jogo") },
            text = {
                if (stateSlots.isEmpty()) {
                    Text("Ainda não há save-states para este jogo.")
                } else {
                    Column(
                        Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        stateSlots.forEach { slot ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    showStates = false
                                    menu = false
                                    onLoadState(slot.file)
                                }.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val bitmap = remember(slot.thumbnail?.absolutePath) {
                                    slot.thumbnail?.let { BitmapFactory.decodeFile(it.absolutePath) }
                                }
                                if (bitmap != null) {
                                    Image(bitmap.asImageBitmap(), contentDescription = "Miniatura do save", modifier = Modifier.size(104.dp, 62.dp))
                                } else {
                                    Box(Modifier.size(104.dp, 62.dp).background(Color(0xFF292F38)), contentAlignment = Alignment.Center) {
                                        Text("SEM THUMB", color = Color.White)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(java.text.SimpleDateFormat("dd/MM/yyyy  HH:mm", java.util.Locale.getDefault()).format(java.util.Date(slot.file.lastModified())))
                                    Text("Toque para carregar", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onDeleteState(slot.file) }) { Text("Excluir") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStates = false }) { Text("Fechar") } },
        )
    }
}


@Composable
private fun PlacedControl(
    id: String,
    label: String,
    defaultX: Float,
    defaultY: Float,
    containerWidth: androidx.compose.ui.unit.Dp,
    containerHeight: androidx.compose.ui.unit.Dp,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    editing: Boolean,
    preferences: android.content.SharedPreferences,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var x by remember(id) { mutableStateOf(preferences.getFloat("control_$id_x", defaultX).coerceIn(0f, 1f)) }
    var y by remember(id) { mutableStateOf(preferences.getFloat("control_$id_y", defaultY).coerceIn(0f, 1f)) }
    Box(
        Modifier
            .offset(x = containerWidth * x, y = containerHeight * y)
            .size(width, height)
            .then(
                if (editing) Modifier.pointerInput(id, containerWidth, containerHeight) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val cw = with(density) { containerWidth.toPx() }.coerceAtLeast(1f)
                        val ch = with(density) { containerHeight.toPx() }.coerceAtLeast(1f)
                        x = (x + drag.x / cw).coerceIn(0f, 1f)
                        y = (y + drag.y / ch).coerceIn(0f, 1f)
                        preferences.edit().putFloat("control_${id}_x", x).putFloat("control_${id}_y", y).apply()
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (editing) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(0xCC315B83)), contentAlignment = Alignment.Center) {
                Text(label, color = Color.White, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        } else content()
    }
}
