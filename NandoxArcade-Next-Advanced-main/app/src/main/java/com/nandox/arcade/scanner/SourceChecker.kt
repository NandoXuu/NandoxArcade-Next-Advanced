package com.nandox.arcade.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

enum class SourceStatus { AVAILABLE, NO_PERMISSION, UNAVAILABLE }

object SourceChecker {
    fun status(context: Context, treeUri: Uri): SourceStatus {
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
        if (!granted) return SourceStatus.NO_PERMISSION
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val reachable = runCatching {
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
            )?.use { true } ?: false
        }.getOrDefault(false)
        return if (reachable) SourceStatus.AVAILABLE else SourceStatus.UNAVAILABLE
    }

    fun displayName(context: Context, treeUri: Uri): String {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val name = runCatching {
            context.contentResolver.query(
                doc,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        return name ?: rootId.substringAfterLast(':').ifBlank { rootId }
    }
}
