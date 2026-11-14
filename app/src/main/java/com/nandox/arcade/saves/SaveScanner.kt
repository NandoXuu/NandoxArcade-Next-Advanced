package com.nandox.arcade.saves

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SaveScanner {
    private const val MAX_DEPTH = 3

    suspend fun scan(context: Context, pluginId: String, tree: Uri): List<SaveFile> =
        withContext(Dispatchers.IO) {
            val found = mutableListOf<SaveFile>()
            val queue = ArrayDeque<Pair<String, Int>>().apply { add(Saf.rootId(tree) to 0) }
            while (queue.isNotEmpty()) {
                val (dir, depth) = queue.removeFirst()
                for (child in Saf.children(context, tree, dir)) {
                    if (child.isDir) {
                        if (depth < MAX_DEPTH) queue.add(child.docId to depth + 1)
                        continue
                    }
                    val kind = SaveClassifier.kind(child.name) ?: continue
                    found += SaveFile(pluginId, child.name, child.uri, child.size, child.modified, kind)
                }
            }
            found
        }
}
