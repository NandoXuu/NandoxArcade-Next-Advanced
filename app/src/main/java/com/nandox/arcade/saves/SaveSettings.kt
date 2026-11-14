package com.nandox.arcade.saves

import android.content.Context
import android.net.Uri

class SaveSettings(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("saves", Context.MODE_PRIVATE)

    var autoBackup: Boolean
        get() = prefs.getBoolean("auto", false)
        set(v) = prefs.edit().putBoolean("auto", v).apply()

    var keepVersions: Int
        get() = prefs.getInt("keep", 5).coerceIn(1, 50)
        set(v) = prefs.edit().putInt("keep", v.coerceIn(1, 50)).apply()

    var destinationUri: String?
        get() = prefs.getString("dest", null)
        set(v) = prefs.edit().apply { if (v == null) remove("dest") else putString("dest", v) }.apply()

    var pendingSession: Boolean
        get() = prefs.getBoolean("pending", false)
        set(v) = prefs.edit().putBoolean("pending", v).apply()

    var lastAuto: String
        get() = prefs.getString("last_auto", "").orEmpty()
        set(v) = prefs.edit().putString("last_auto", v).apply()

    fun location(pluginId: String): String? = prefs.getString("loc_$pluginId", null)

    fun setLocation(pluginId: String, uri: String) = prefs.edit().putString("loc_$pluginId", uri).apply()

    fun locations(): Map<String, String> =
        prefs.all.filterKeys { it.startsWith("loc_") }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix("loc_") to it } }.toMap()

    fun destination(): BackupDestination =
        destinationUri?.let { SafDestination(app, Uri.parse(it)) }
            ?: InternalDestination(java.io.File(app.filesDir, "backups"))
}
