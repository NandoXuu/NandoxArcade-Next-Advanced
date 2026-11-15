package com.nandox.arcade.emulator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.view.PixelCopy
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nandox.arcade.audio.AudioCapabilityDetector
import com.nandox.arcade.audio.AudioDiagnostics
import com.nandox.arcade.audio.AudioHistoryStore
import com.nandox.arcade.audio.AudioPlan
import com.nandox.arcade.audio.AudioPlanner
import com.nandox.arcade.audio.AudioSession
import com.nandox.arcade.audio.AudioSettings
import com.nandox.arcade.audio.AudioSettingsStore
import com.nandox.arcade.ui.ArcadeTheme
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Roda um core libretro (empacotado em jniLibs) dentro do app, via LibretroDroid.
 *
 * Roda em processo separado (":emulator") para que uma falha nativa de um core não derrube o app,
 * e o processo é encerrado ao sair do jogo, porque cores libretro guardam estado estático e não
 * costumam sobreviver bem a serem carregados duas vezes no mesmo processo.
 */
class EmulatorActivity : ComponentActivity() {
    private var retroView: GLRetroView? = null
    private var sramFile: File? = null
    @Volatile private var frameReady = false
    @Volatile private var pendingStateApplied = false

    private var view by mutableStateOf<GLRetroView?>(null)
    private var status by mutableStateOf<String?>("Preparando jogo…")
    private var activeGameKey: String? = null
    private var stateSlots by mutableStateOf<List<StateSlot>>(emptyList())
    private var audioEnabled by mutableStateOf(true)
    private var shaderMode by mutableStateOf("default")
    private var padActive by mutableStateOf(false)
    private var frameSpeed by mutableStateOf(1)
    private var audioSession: AudioSession? = null
    private var audioPlan: AudioPlan? = null
    private var audioSettings = AudioSettings()
    private var audioDiagnostics by mutableStateOf<AudioDiagnostics?>(null)
    private var audioOverlay by mutableStateOf<String?>(null)
    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    private val autoSaveTick = object : Runnable {
        override fun run() {
            autoSaveInBackground()
            autoSaveHandler.postDelayed(this, AUTO_SAVE_MS)
        }
    }

    /**
     * O autosave periódico serializa e grava o estado (dezenas de MB no N64). Na thread principal isso
     * congelava a imagem e esvaziava o buffer de áudio a cada minuto; por isso roda em segundo plano.
     * O autosave de onPause continua síncrono, porque a thread GL é pausada logo em seguida.
     */
    private fun autoSaveInBackground() {
        if (!frameReady || retroView == null) return
        if (audioPlan?.periodicAutosave == false) return
        if (!autoSaveBusy.compareAndSet(false, true)) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                autoSaveNow()
            } finally {
                autoSaveBusy.set(false)
            }
        }
    }

    /** Resolução interna padrão do N64: leve em aparelhos 32 bits (ex.: Android TV ARM32), mais nítida em 64 bits. */
    private fun defaultN64Res43(): String = if (Process.is64Bit()) "640x480" else "320x240"
    private fun defaultN64Res169(): String = if (Process.is64Bit()) "1280x720" else "640x360"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersive()

        val coreLib = intent.getStringExtra(EXTRA_CORE_LIB)
        val gameUri = intent.getStringExtra(EXTRA_GAME_URI)?.let(Uri::parse)
        if (coreLib == null || gameUri == null) {
            finish()
            return
        }
        val layout = PadLayout.forPlatform(intent.getStringExtra(EXTRA_PLATFORM).orEmpty())

        setContent {
            ArcadeTheme {
                EmulatorScreen(
                    view = view,
                    status = status,
                    layout = layout,
                    padActive = padActive,
                    onTouchScreen = { padActive = false },
                    onKey = { code, down -> sendKey(code, down) },
                    onMotion = { source, x, y -> retroView?.sendMotionEvent(source, x, y) },
                    onReset = { retroView?.reset() },
                    onExit = { autoSaveNow(); captureLastPlayed { saveStateSnapshot { finish() } } },
                    onSaveState = { saveStateSnapshot() },
                    onLoadState = { loadState(it) },
                    onDeleteState = { deleteState(it) },
                    onAudioChange = { enabled ->
                        retroView?.audioEnabled = enabled
                        audioEnabled = enabled
                        getSharedPreferences("emulator_display", MODE_PRIVATE).edit().putBoolean("audio", enabled).apply()
                    },
                    audioEnabled = audioEnabled,
                    onShaderChange = { mode ->
                        retroView?.shader = when (mode) {
                            "crt" -> ShaderConfig.CRT
                            "lcd" -> ShaderConfig.LCD
                            "sharp" -> ShaderConfig.Sharp
                            else -> ShaderConfig.Default
                        }
                        shaderMode = mode
                        getSharedPreferences("emulator_display", MODE_PRIVATE).edit().putString("shader", mode).apply()
                    },
                    shaderMode = shaderMode,
                    fpsLimit = getSharedPreferences("emulator_display", MODE_PRIVATE).getInt("fpsLimit", 0),
                    renderScale = getSharedPreferences("emulator_display", MODE_PRIVATE).getFloat("renderScale", 1f),
                    n64Res43 = getSharedPreferences("emulator_display", MODE_PRIVATE).getString("n64Res43", defaultN64Res43()) ?: defaultN64Res43(),
                    n64Res169 = getSharedPreferences("emulator_display", MODE_PRIVATE).getString("n64Res169", defaultN64Res169()) ?: defaultN64Res169(),
                    n64Aspect = getSharedPreferences("emulator_display", MODE_PRIVATE).getString("n64Aspect", "4:3") ?: "4:3",
                    onN64ResolutionChange = { aspect, resolution ->
                        getSharedPreferences("emulator_display", MODE_PRIVATE).edit()
                            .putString(if (aspect == "4:3") "n64Res43" else "n64Res169", resolution)
                            .putString("n64Aspect", aspect).apply()
                        Toast.makeText(this, "Resolução do N64 salva. Reinicie o jogo para aplicar.", Toast.LENGTH_LONG).show()
                    },
                    onFpsLimitChange = { fps ->
                        getSharedPreferences("emulator_display", MODE_PRIVATE).edit().putInt("fpsLimit", fps).apply()
                        window.attributes = window.attributes.apply { preferredRefreshRate = fps.toFloat() }
                    },
                    onRenderScaleChange = { scale ->
                        getSharedPreferences("emulator_display", MODE_PRIVATE).edit().putFloat("renderScale", scale).apply()
                    },
                    frameSpeed = frameSpeed,
                    onFrameSpeedChange = { changeSpeed(it) },
                    stateSlots = stateSlots,
                    platformId = intent.getStringExtra(EXTRA_PLATFORM).orEmpty(),
                    audioOverlay = audioOverlay,
                    audioDiagnostics = audioDiagnostics,
                    onAudioSettingsChanged = { onAudioSettingsChanged() },
                )
            }
        }

        lifecycleScope.launch {
            val rom = try {
                withContext(Dispatchers.IO) {
                    SystemAssets.install(applicationContext, systemDir())
                    RomStager.stage(applicationContext, gameUri)
                }
            } catch (e: Exception) {
                fail("Não foi possível abrir o jogo: ${e.message ?: "erro de leitura"}")
                return@launch
            }
            startCore(coreLib, rom, RomStager.keyFor(gameUri))
        }
    }

    private fun startCore(coreLib: String, rom: File, gameKey: String) {
        activeGameKey = gameKey
        refreshStateSlots()
        val lib = File(applicationInfo.nativeLibraryDir, coreLib)
        if (!lib.exists()) {
            fail("Core não encontrado no APK: $coreLib")
            return
        }
        val saves = File(baseDir(), "saves").apply { mkdirs() }
        val sram = File(saves, "$gameKey.srm").also { sramFile = it }

        val platform = intent.getStringExtra(EXTRA_PLATFORM).orEmpty()
        val caps = AudioCapabilityDetector.detect(this)
        val settings = AudioSettingsStore.forContext(this).load(platform)
        val baseline = AudioHistoryStore.forContext(this).load(platform)
        val plan = AudioPlanner.resolve(settings, caps, platform, baseline)
        audioSettings = settings
        audioPlan = plan
        applyDisplayHints(plan)

        val data = GLRetroViewData(this).apply {
            coreFilePath = lib.absolutePath
            gameFilePath = rom.absolutePath
            systemDirectory = systemDir().absolutePath
            savesDirectory = saves.absolutePath
            // Baixa latência usa buffer de só 4 quadros: em TV/HDMI e CPU ARM32 que não segura velocidade total ele
            // estoura e o som picota. O plano de áudio escolhe 4 ou 8 quadros conforme o modo, o console e o aparelho.
            preferLowLatencyAudio = plan.lowLatencyStream
            skipDuplicateFrames = true
            if (sram.isFile) saveRAMState = runCatching { sram.readBytes() }.getOrNull()
            if (intent.getStringExtra(EXTRA_PLATFORM) == "n64") {
                val prefs = getSharedPreferences("emulator_display", MODE_PRIVATE)
                variables = arrayOf(
                    Variable("mupen64plus-43screensize", prefs.getString("n64Res43", defaultN64Res43())),
                    Variable("mupen64plus-169screensize", prefs.getString("n64Res169", defaultN64Res169())),
                    Variable("mupen64plus-aspect", prefs.getString("n64Aspect", "4:3")),
                )
            }
        }
        val v = GLRetroView(this, data)
        val displayPrefs = getSharedPreferences("emulator_display", MODE_PRIVATE)
        audioEnabled = displayPrefs.getBoolean("audio", true)
        shaderMode = displayPrefs.getString("shader", "default") ?: "default"
        val fpsTarget = displayPrefs.getInt("fpsLimit", 0)
        window.attributes = window.attributes.apply { preferredRefreshRate = fpsTarget.toFloat() }
        v.audioEnabled = audioEnabled
        v.shader = when (shaderMode) {
            "crt" -> ShaderConfig.CRT
            "lcd" -> ShaderConfig.LCD
            "sharp" -> ShaderConfig.Sharp
            else -> ShaderConfig.Default
        }
        val session = AudioSession(
            context = this,
            view = v,
            platformId = platform,
            gameKey = gameKey,
            initialPlan = plan,
            initialSettings = settings,
            caps = caps,
            baseline = baseline,
            isAudioWanted = { audioEnabled },
            onUpdate = { diag ->
                audioDiagnostics = diag
                audioOverlay = if (audioSettings.overlay) diag.overlayText() else null
            },
        )
        audioSession = session
        lifecycle.addObserver(v)
        lifecycleScope.launch { v.getGLRetroErrors().collect { fail(errorText(it)) } }
        lifecycleScope.launch {
            v.getGLRetroEvents().collect {
                if (it is GLRetroView.GLRetroEvents.FrameRendered) {
                    frameReady = true
                    session.onFrame()
                    if (!pendingStateApplied) {
                        pendingStateApplied = true
                        // o valor setado antes da criação do core nativo pode não ter efeito; reaplica agora
                        v.audioEnabled = audioEnabled
                        PlayHistory.consumePendingState(this@EmulatorActivity)?.let { state ->
                            runCatching { v.unserializeState(state.readBytes()) }
                        }
                        session.start()
                    }
                }
            }
        }
        retroView = v
        view = v
        status = null
    }

    private fun errorText(code: Int): String = when (code) {
        GLRetroView.ERROR_LOAD_LIBRARY -> "Falha ao carregar o core do emulador"
        GLRetroView.ERROR_LOAD_GAME -> "O core não conseguiu abrir este jogo (arquivo inválido ou BIOS ausente)"
        GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "A GPU deste aparelho não é compatível com este core"
        else -> "Erro no emulador"
    }

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun baseDir(): File = getExternalFilesDir(null) ?: filesDir

    /** BIOS e arquivos de sistema dos cores ficam aqui: Android/data/<pacote>/files/system */
    private fun systemDir(): File = File(baseDir(), "system").apply { mkdirs() }

    private val AUTO_SAVE_MS = 60_000L

    private fun sendKey(code: Int, down: Boolean) {
        retroView?.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code)
    }

    private fun stateDirectory(): File {
        val key = activeGameKey ?: "unknown"
        return File(baseDir(), "states/$key").apply { mkdirs() }
    }

    private fun refreshStateSlots() {
        val dir = stateDirectory()
        stateSlots = dir.listFiles { file -> file.isFile && file.extension == "state" }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { state -> StateSlot(state, File(state.parentFile, state.nameWithoutExtension + ".png").takeIf { it.isFile }) }
    }

    private fun saveStateSnapshot(onComplete: (() -> Unit)? = null) {
        val v = retroView ?: run { onComplete?.invoke(); return }
        lifecycleScope.launch {
            val stateFile = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = v.serializeState()
                    check(bytes.isNotEmpty()) { "Este core não oferece save-state neste jogo" }
                    val target = File(stateDirectory(), "slot-${System.currentTimeMillis()}.state")
                    val part = File(target.parentFile, target.name + ".part")
                    part.writeBytes(bytes)
                    check(part.renameTo(target)) { "Não foi possível gravar o save-state" }
                    target
                }
            }.getOrElse {
                Toast.makeText(this@EmulatorActivity, it.message ?: "Falha ao salvar estado", Toast.LENGTH_LONG).show()
                onComplete?.invoke()
                return@launch
            }
            captureStateThumbnail(v, stateFile) {
                refreshStateSlots()
                Toast.makeText(this@EmulatorActivity, "Estado salvo", Toast.LENGTH_SHORT).show()
                onComplete?.invoke()
            }
        }
    }

    private fun captureStateThumbnail(v: GLRetroView, stateFile: File, done: () -> Unit) {
        val width = v.width
        val height = v.height
        if (width <= 0 || height <= 0) { done(); return }
        val bitmap = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
            ?: run { done(); return }
        runCatching {
            PixelCopy.request(v, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    runCatching {
                        val thumb = File(stateFile.parentFile, stateFile.nameWithoutExtension + ".png")
                        FileOutputStream(thumb).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
                bitmap.recycle()
                done()
            }, Handler(Looper.getMainLooper()))
        }.onFailure {
            bitmap.recycle()
            done()
        }
    }

    private fun deleteState(file: File) {
        runCatching {
            file.delete()
            File(file.parentFile, file.nameWithoutExtension + ".png").delete()
        }
        refreshStateSlots()
    }

    private fun loadState(file: File) {
        val v = retroView ?: return
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            val loaded = bytes?.let { runCatching { v.unserializeState(it) }.getOrDefault(false) } ?: false
            if (loaded) audioSession?.resync("save carregado")
            Toast.makeText(this@EmulatorActivity, if (loaded) "Save carregado" else "Não foi possível carregar este save", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveSram() {
        val v = retroView ?: return
        val file = sramFile ?: return
        if (!frameReady) return
        runCatching {
            val bytes = v.serializeSRAM()
            if (bytes.isEmpty()) return@runCatching
            val part = File(file.parentFile, file.name + ".part")
            part.writeBytes(bytes)
            file.delete()
            part.renameTo(file)
        }
    }

    private fun captureLastPlayed(done: (() -> Unit)? = null) {
        val v = retroView
        val key = activeGameKey
        if (v == null || key == null || v.width <= 0 || v.height <= 0) { done?.invoke(); return }
        val bitmap = runCatching { Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888) }.getOrNull()
            ?: run { done?.invoke(); return }
        runCatching {
            PixelCopy.request(v, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    runCatching {
                        val target = PlayHistory.thumbnail(this, key)
                        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                        PlayHistory.touch(this, key)
                    }
                }
                bitmap.recycle()
                done?.invoke()
            }, Handler(Looper.getMainLooper()))
        }.onFailure {
            bitmap.recycle()
            done?.invoke()
        }
    }

    override fun onResume() {
        super.onResume()
        autoSaveHandler.postDelayed(autoSaveTick, AUTO_SAVE_MS)
        audioSession?.resync("jogo retomado")
    }

    override fun onPause() {
        autoSaveHandler.removeCallbacks(autoSaveTick)
        autoSaveNow()
        // SRAM precisa ser lida antes de a thread GL ser pausada.
        saveSram()
        captureLastPlayed()
        audioSession?.checkpoint()
        super.onPause()
    }

    @Synchronized
    private fun autoSaveNow() {
        val v = retroView ?: return
        if (!frameReady) return
        runCatching {
            val bytes = v.serializeState()
            if (bytes.isEmpty()) return
            val dir = stateDirectory()
            val target = File(dir, "auto.state")
            val part = File(dir, "auto.state.part")
            part.writeBytes(bytes)
            if (target.exists()) target.delete()
            part.renameTo(target)
        }
    }

    /** O painel de áudio mudou: relê os ajustes do console e aplica o que vale com o jogo aberto. */
    private fun onAudioSettingsChanged() {
        val platform = intent.getStringExtra(EXTRA_PLATFORM).orEmpty()
        val caps = AudioCapabilityDetector.detect(this)
        val settings = AudioSettingsStore.forContext(this).load(platform)
        val plan = AudioPlanner.resolve(settings, caps, platform, AudioHistoryStore.forContext(this).load(platform))
        audioSettings = settings
        audioPlan = plan
        applyDisplayHints(plan)
        audioSession?.updateSettings(plan, settings)
        if (!settings.overlay) audioOverlay = null
    }

    private fun applyDisplayHints(plan: AudioPlan) {
        runCatching { window.setSustainedPerformanceMode(plan.sustainedPerformance) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { window.setPreferMinimalPostProcessing(plan.minimalPostProcessing) }
        }
    }

    private fun changeSpeed(speed: Int) {
        frameSpeed = speed
        retroView?.frameSpeed = speed
        Toast.makeText(this, speedMessage(speed), Toast.LENGTH_LONG).show()
    }

    private fun speedMessage(speed: Int): String = when (speed) {
        2 -> "Ei, aproveita o caminho também 🙃"
        4 -> "Ei cara, você talvez deveria prestar a atenção na história 👀"
        else -> "Velocidade normal"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            audioSession?.finish()
            Process.killProcess(Process.myPid())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    // ---- controle físico (Bluetooth/USB) ----

    private fun isPadSource(source: Int): Boolean =
        source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD) != 0

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val padKey = KeyEvent.isGamepadButton(code) ||
            code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
        if (retroView != null && padKey && isPadSource(event.source)) {
            padActive = true
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) sendKey(code, true)
                KeyEvent.ACTION_UP -> sendKey(code, false)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val v = retroView
        if (v != null && ev.action == MotionEvent.ACTION_MOVE && ev.source and InputDevice.SOURCE_JOYSTICK != 0) {
            padActive = true
            v.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_DPAD,
                ev.getAxisValue(MotionEvent.AXIS_HAT_X), ev.getAxisValue(MotionEvent.AXIS_HAT_Y),
            )
            v.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                ev.getAxisValue(MotionEvent.AXIS_X), ev.getAxisValue(MotionEvent.AXIS_Y),
            )
            v.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                ev.getAxisValue(MotionEvent.AXIS_Z), ev.getAxisValue(MotionEvent.AXIS_RZ),
            )
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    companion object {
        private const val EXTRA_CORE_LIB = "coreLib"
        private const val EXTRA_GAME_URI = "gameUri"
        private const val EXTRA_PLATFORM = "platformId"

        fun intent(context: Context, coreLib: String, gameUri: Uri, platformId: String): Intent =
            Intent(context, EmulatorActivity::class.java).apply {
                putExtra(EXTRA_CORE_LIB, coreLib)
                putExtra(EXTRA_GAME_URI, gameUri.toString())
                putExtra(EXTRA_PLATFORM, platformId)
                // o Uri é lido pelo processo ":emulator"; o acesso à árvore do SAF já foi concedido ao app
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
