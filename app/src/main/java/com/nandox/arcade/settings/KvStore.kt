package com.nandox.arcade.settings

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import java.io.File

/**
 * Armazenamento chave/valor em arquivo JSON. Diferente do SharedPreferences, é lido do disco a cada
 * acesso, então o processo do app e o processo ":emulator" enxergam sempre os mesmos valores.
 */
class KvStore(private val file: File) {
    @Synchronized
    private fun load(): JSONObject =
        runCatching { if (file.isFile) JSONObject(file.readText()) else JSONObject() }.getOrDefault(JSONObject())

    fun has(key: String): Boolean = load().has(key)

    fun getString(key: String, default: String): String =
        load().let { if (it.has(key)) it.optString(key, default) else default }

    fun getFloat(key: String, default: Float): Float =
        load().let { if (it.has(key)) it.optDouble(key, default.toDouble()).toFloat() else default }

    fun getInt(key: String, default: Int): Int =
        load().let { if (it.has(key)) it.optInt(key, default) else default }

    fun getBoolean(key: String, default: Boolean): Boolean =
        load().let { if (it.has(key)) it.optBoolean(key, default) else default }

    @Synchronized
    fun put(vararg pairs: Pair<String, Any?>) {
        val json = load()
        for ((key, value) in pairs) {
            when (value) {
                null -> json.remove(key)
                is Float -> json.put(key, value.toDouble())
                else -> json.put(key, value)
            }
        }
        save(json)
    }

    @Synchronized
    fun removeWhere(predicate: (String) -> Boolean) {
        val json = load()
        json.keys().asSequence().toList().filter(predicate).forEach { json.remove(it) }
        save(json)
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun save(json: JSONObject) {
        file.parentFile?.mkdirs()
        val part = File(file.parentFile, file.name + ".part")
        part.writeText(json.toString())
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }
}

object AppSettings {
    fun baseDir(context: Context): File = context.getExternalFilesDir(null) ?: context.filesDir

    /** Configurações gerais do app (valem para todos os jogos, salvo ajuste individual). */
    fun global(context: Context): KvStore = KvStore(File(baseDir(context), "settings/display.json"))

    /** Ajustes próprios de um jogo; só guarda o que foi alterado em relação ao padrão. */
    fun game(context: Context, gameKey: String): KvStore =
        KvStore(File(baseDir(context), "settings/game-$gameKey.json"))
}

/** Lê do jogo se houver ajuste próprio, senão do padrão do app. Escreve no jogo (ou no padrão, se não houver jogo). */
class LayeredPrefs(val global: KvStore, val game: KvStore?) {
    private fun source(key: String): KvStore = if (game != null && game.has(key)) game else global

    fun getString(key: String, default: String): String = source(key).getString(key, default)
    fun getFloat(key: String, default: Float): Float = source(key).getFloat(key, default)
    fun getInt(key: String, default: Int): Int = source(key).getInt(key, default)
    fun getBoolean(key: String, default: Boolean): Boolean = source(key).getBoolean(key, default)

    fun put(vararg pairs: Pair<String, Any?>) {
        (game ?: global).put(*pairs)
    }
}

/** Esconde (ou mostra) a barra de status e a de navegação do Android. */
object Immersive {
    fun apply(activity: Activity, enabled: Boolean) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}
