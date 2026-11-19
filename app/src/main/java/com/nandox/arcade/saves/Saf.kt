package com.nandox.arcade.saves

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class SafChild(val docId: String, val name: String, val isDir: Boolean, val size: Long, val modified: Long, val uri: Uri)

object Saf {
    const val BACKUP_MIME = "application/x-nandox-backup"
    const val SAVE_MIME = "application/x-nandox-save"

    fun rootId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)

    fun children(context: Context, tree: Uri, parentDocId: String): List<SafChild> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = runCatching { context.contentResolver.query(uri, projection, null, null, null) }.getOrNull()
            ?: return emptyList()
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    add(
                        SafChild(
                            docId = id,
                            name = it.getString(1) ?: continue,
                            isDir = it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = if (it.isNull(3)) 0 else it.getLong(3),
                            modified = if (it.isNull(4)) 0 else it.getLong(4),
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                        )
                    )
                }
            }
        }
    }

    fun create(context: Context, tree: Uri, parentDocId: String, name: String, mime: String): Uri? =
        runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, parentDocId),
                mime,
                name,
            )
        }.getOrNull()

    fun delete(context: Context, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)
}
