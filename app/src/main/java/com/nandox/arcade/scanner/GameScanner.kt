package com.nandox.arcade.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.nandox.arcade.core.PlatformClassifier
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.emulator.ArchiveRom
import com.nandox.arcade.emulator.SheetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GameScanner {
    private const val MAX_DEPTH = 6

    private data class Node(val docId: String, val folders: List<String>, val depth: Int)
    private data class Found(val entity: GameEntity, val dirId: String, val ext: String)

    suspend fun scan(context: Context, treeUri: Uri): List<GameEntity> = withContext(Dispatchers.IO) {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val found = mutableListOf<Found>()
        val sheetDirs = mutableSetOf<String>()
        val queue = ArrayDeque<Node>().apply { add(Node(rootId, emptyList(), 0)) }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
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
                    val size = if (it.isNull(3)) null else it.getLong(3)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (node.depth < MAX_DEPTH) queue.add(Node(id, node.folders + name, node.depth + 1))
                        continue
                    }
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    if (ArchiveRom.isArchive(name)) {
                        val inside = runCatching {
                            ArchiveRom.games(context, fileUri, name, node.folders)
                        }.getOrDefault(emptyList())
                        inside.forEach { found += Found(it, "archive:$id", "") }
                        continue
                    }
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in SheetParser.EXTENSIONS) sheetDirs += node.docId
                    val platform = PlatformClassifier.classify(name, node.folders, size) ?: continue
                    found += Found(
                        GameEntity(
                            title = name.substringBeforeLast('.'),
                            platformId = platform,
                            uri = fileUri.toString(),
                        ),
                        node.docId,
                        ext,
                    )
                }
            }
        }
        // as faixas .bin de um .cue/.gdi não são jogos separados
        found.filterNot { it.ext == "bin" && it.dirId in sheetDirs }.map { it.entity }
    }
}
