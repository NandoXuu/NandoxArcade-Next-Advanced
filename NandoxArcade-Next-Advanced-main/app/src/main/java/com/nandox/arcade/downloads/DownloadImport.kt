package com.nandox.arcade.downloads

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object DownloadImport {
    fun gameUri(context: Context, localUri: String?): Uri? {
        val path = localUri?.let { Uri.parse(it).path } ?: return null
        val file = File(path)
        if (!file.isFile) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
    }
}
