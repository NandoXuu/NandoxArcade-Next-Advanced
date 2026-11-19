package com.nandox.arcade.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager

enum class DownloadState { PENDING, RUNNING, PAUSED, DONE, FAILED }

data class DownloadItem(
    val id: Long,
    val title: String,
    val state: DownloadState,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localUri: String? = null,
) {
    val percent: Int?
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else null
}

class DownloadsRepository(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueue(url: String, fileName: String, mimeType: String?, userAgent: String?): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName)
        if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
        if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
        return manager.enqueue(request)
    }

    fun list(): List<DownloadItem> {
        val cursor = manager.query(DownloadManager.Query()) ?: return emptyList()
        return cursor.use {
            val id = it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val title = it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val status = it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val so = it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val total = it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val local = it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            buildList {
                while (it.moveToNext()) {
                    add(
                        DownloadItem(
                            id = it.getLong(id),
                            title = it.getString(title).orEmpty(),
                            state = when (it.getInt(status)) {
                                DownloadManager.STATUS_PENDING -> DownloadState.PENDING
                                DownloadManager.STATUS_RUNNING -> DownloadState.RUNNING
                                DownloadManager.STATUS_PAUSED -> DownloadState.PAUSED
                                DownloadManager.STATUS_SUCCESSFUL -> DownloadState.DONE
                                else -> DownloadState.FAILED
                            },
                            downloadedBytes = it.getLong(so),
                            totalBytes = it.getLong(total),
                            localUri = it.getString(local),
                        )
                    )
                }
            }.sortedByDescending { d -> d.id }
        }
    }

    fun remove(id: Long) {
        manager.remove(id)
    }
}
