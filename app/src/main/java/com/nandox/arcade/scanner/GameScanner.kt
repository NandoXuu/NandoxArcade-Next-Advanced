package com.nandox.arcade.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.nandox.arcade.core.PlatformClassifier
import com.nandox.arcade.emulator.ArchiveRom
import com.nandox.arcade.data.GameEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GameScanner {
    private const val MAX_DEPTH = 6

    private data class Node(val docId: String, val folders: List<String>, val depth: Int)

    suspend fun scan(context: Context, treeUri: Uri): List<GameEntity> = withContext(Dispatchers.IO) {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val found = mutableListOf<GameEntity>()
        val queue = ArrayDeque<Node>().apply { add(Node(rootId, emptyList(), 0)) }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, node.docId)
            val cursor = runCatching {
                context.contentResolver.query(children, projection, null, null, null)
            }.getOrNull() ?: continue
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val name = it.getString(1) ?: continue
                    val mime = it.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (node.depth < MAX_DEPTH) queue.add(Node(id, node.folders + name, node.depth + 1))
                        continue
                    }
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    if (ArchiveRom.isArchive(name)) {
                        val archiveEntries = runCatching { ArchiveRom.entries(context, fileUri) }.getOrDefault(emptyList())
                        archiveEntries.forEach { entry ->
                            val platform = PlatformClassifier.classify(entry.substringAfterLast('/'), node.folders + entry.substringBeforeLast('/', "").split('/')) ?: return@forEach
                            found += GameEntity(
                                title = entry.substringAfterLast('/').substringBeforeLast('.'),
                                platformId = platform,
                                uri = ArchiveRom.uriForEntry(fileUri, entry).toString(),
                            )
                        }
                        continue
                    }
                    val platform = PlatformClassifier.classify(name, node.folders) ?: continue
                    found += GameEntity(
                        title = name.substringBeforeLast('.'),
                        platformId = platform,
                        uri = fileUri.toString(),
                    )
                }
            }
        }
        found
    }
}
