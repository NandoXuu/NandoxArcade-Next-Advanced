package com.nandox.arcade.emulator

import android.content.Context
import android.net.Uri
import java.io.File
import org.json.JSONObject

data class PlayedGame(
    val key: String,
    val title: String,
    val platformId: String,
    val uri: String,
    val lastPlayed: Long,
)

object PlayHistory {
    private const val PREFS = "play_history"

    fun recordLaunch(context: Context, title: String, platformId: String, uri: Uri): String {
        val key = RomStager.keyFor(uri)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val data = JSONObject()
            .put("title", title)
            .put("platformId", platformId)
            .put("uri", uri.toString())
            .put("lastPlayed", System.currentTimeMillis())
        prefs.edit().putString(key, data.toString()).apply()
        return key
    }

    fun touch(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(key, null) ?: return
        val data = runCatching { JSONObject(raw) }.getOrNull() ?: return
        data.put("lastPlayed", System.currentTimeMillis())
        prefs.edit().putString(key, data.toString()).apply()
    }

    fun all(context: Context): List<PlayedGame> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, raw) ->
            val rawText = raw as? String ?: return@mapNotNull null
            val data = runCatching { JSONObject(rawText) }.getOrNull() ?: return@mapNotNull null
            PlayedGame(
                key = key,
                title = data.optString("title", key),
                platformId = data.optString("platformId"),
                uri = data.optString("uri"),
                lastPlayed = data.optLong("lastPlayed"),
            )
        }.sortedByDescending { it.lastPlayed }
    }

    fun thumbnail(context: Context, key: String): File =
        File(File(context.getExternalFilesDir(null) ?: context.filesDir, "last-played"), "$key.jpg").apply { parentFile?.mkdirs() }

    fun pendingState(context: Context, file: File) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("pending_state", file.absolutePath).apply()
    }

    fun consumePendingState(context: Context): File? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val path = prefs.getString("pending_state", null) ?: return null
        prefs.edit().remove("pending_state").apply()
        return File(path).takeIf { it.isFile }
    }
}
