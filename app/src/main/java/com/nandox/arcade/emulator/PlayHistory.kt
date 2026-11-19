package com.nandox.arcade.emulator

import android.content.Context
import android.net.Uri
import com.nandox.arcade.settings.AppSettings
import java.io.File
import org.json.JSONObject

data class PlayedGame(
    val key: String,
    val title: String,
    val platformId: String,
    val uri: String,
    val lastPlayed: Long,
)

/**
 * Histórico de partidas guardado em arquivos (não em SharedPreferences): o jogo roda no processo
 * ":emulator" e o app principal precisa ver o que ele gravou.
 */
object PlayHistory {
    private fun dir(context: Context): File = File(AppSettings.baseDir(context), "history").apply { mkdirs() }

    private fun fileFor(context: Context, key: String) = File(dir(context), "$key.json")

    private fun write(context: Context, key: String, data: JSONObject) {
        val target = fileFor(context, key)
        val part = File(target.parentFile, target.name + ".part")
        part.writeText(data.toString())
        if (!part.renameTo(target)) {
            target.delete()
            part.renameTo(target)
        }
    }

    fun recordLaunch(context: Context, title: String, platformId: String, uri: Uri): String {
        val key = RomStager.keyFor(uri)
        val previous = find(context, key)
        val data = JSONObject()
            .put("title", title)
            .put("platformId", platformId)
            .put("uri", uri.toString())
            // só vira "última partida" quando o jogador sai; antes disso mantém o valor anterior
            .put("lastPlayed", previous?.lastPlayed ?: 0L)
        write(context, key, data)
        return key
    }

    fun touch(context: Context, key: String) {
        val current = fileFor(context, key).takeIf { it.isFile }
            ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } ?: return
        current.put("lastPlayed", System.currentTimeMillis())
        write(context, key, current)
    }

    fun find(context: Context, key: String): PlayedGame? {
        val file = fileFor(context, key).takeIf { it.isFile } ?: return null
        return parse(key, file)
    }

    private fun parse(key: String, file: File): PlayedGame? {
        val data = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        return PlayedGame(
            key = key,
            title = data.optString("title", key),
            platformId = data.optString("platformId"),
            uri = data.optString("uri"),
            lastPlayed = data.optLong("lastPlayed"),
        )
    }

    fun all(context: Context): List<PlayedGame> =
        dir(context).listFiles { f -> f.isFile && f.extension == "json" }.orEmpty()
            .mapNotNull { parse(it.nameWithoutExtension, it) }
            .sortedByDescending { it.lastPlayed }

    fun thumbnail(context: Context, key: String): File =
        File(File(AppSettings.baseDir(context), "last-played"), "$key.jpg").apply { parentFile?.mkdirs() }

    private fun pendingFile(context: Context) = File(AppSettings.baseDir(context), "pending_state.txt")

    fun pendingState(context: Context, file: File) {
        pendingFile(context).writeText(file.absolutePath)
    }

    fun clearPending(context: Context) {
        pendingFile(context).delete()
    }

    fun consumePendingState(context: Context): File? {
        val pending = pendingFile(context).takeIf { it.isFile } ?: return null
        val path = runCatching { pending.readText().trim() }.getOrNull()
        pending.delete()
        return path?.let(::File)?.takeIf { it.isFile }
    }
}

/** Save-states de um jogo: cada um é um .state com uma miniatura .png ao lado. */
object StateStore {
    const val AUTO = "auto"

    fun dir(context: Context, gameKey: String): File =
        File(AppSettings.baseDir(context), "states/$gameKey").apply { mkdirs() }

    fun slots(context: Context, gameKey: String): List<StateSlot> =
        dir(context, gameKey).listFiles { f -> f.isFile && f.extension == "state" }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { state ->
                StateSlot(state, File(state.parentFile, state.nameWithoutExtension + ".png").takeIf { it.isFile })
            }

    fun latest(context: Context, gameKey: String): StateSlot? = slots(context, gameKey).firstOrNull()

    fun isAuto(file: File): Boolean = file.nameWithoutExtension == AUTO

    fun delete(file: File) {
        runCatching {
            file.delete()
            File(file.parentFile, file.nameWithoutExtension + ".png").delete()
        }
    }
}
