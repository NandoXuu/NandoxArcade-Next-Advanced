package com.nandox.arcade.saves

import android.net.Uri
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class SaveKind { SAVE_DATA, SAVE_STATE }

data class SaveFile(
    val pluginId: String,
    val name: String,
    val uri: Uri,
    val size: Long,
    val modified: Long,
    val kind: SaveKind,
)

data class BackupFile(val name: String, val size: Long)

data class BackupInfo(val timestamp: Long, val pluginId: String, val origName: String)

object SaveClassifier {
    private val dataExt = setOf("sav", "srm", "eep", "fla", "sra", "mpk", "dsv", "mcr", "mcd", "gci")
    private val stateExt = setOf("state", "sstate", "ppst", "oops")
    private val numbered = Regex("^(st|ss)\\d+$")

    fun kind(name: String): SaveKind? {
        val lower = name.lowercase()
        val ext = lower.substringAfterLast('.', "")
        return when {
            ext in stateExt || numbered.matches(ext) || ".state" in lower -> SaveKind.SAVE_STATE
            ext in dataExt -> SaveKind.SAVE_DATA
            else -> null
        }
    }
}

object BackupName {
    private const val SEP = "__"

    private fun formatter() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    fun build(pluginId: String, origName: String, timestamp: Long): String =
        formatter().format(Date(timestamp)) + SEP + pluginId + SEP + origName

    fun parse(name: String): BackupInfo? {
        val parts = name.split(SEP, limit = 3)
        if (parts.size != 3 || parts[1].isBlank() || parts[2].isBlank()) return null
        val ts = try {
            formatter().parse(parts[0])?.time
        } catch (e: ParseException) {
            null
        } ?: return null
        return BackupInfo(ts, parts[1], parts[2])
    }
}

object BackupPolicy {
    fun toPrune(names: List<String>, pluginId: String, origName: String, keep: Int): List<String> =
        names.mapNotNull { n -> BackupName.parse(n)?.let { n to it } }
            .filter { it.second.pluginId == pluginId && it.second.origName == origName }
            .sortedByDescending { it.second.timestamp }
            .drop(keep.coerceAtLeast(1))
            .map { it.first }
}
