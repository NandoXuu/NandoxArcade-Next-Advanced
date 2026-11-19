package com.nandox.arcade.emulator

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest

/**
 * Os jogos do catálogo são URIs do SAF (content://...), mas os cores libretro precisam de um caminho
 * de arquivo. Aqui a ROM é copiada para o cache do app. Para discos com índice (.cue/.gdi) as faixas
 * referenciadas na mesma pasta são copiadas junto, mantendo os nomes que o índice espera.
 *
 * Só o jogo atual fica no cache; os outros são apagados. Se já estiver copiado (mesmo tamanho), reaproveita.
 */
object RomStager {
    fun keyFor(uri: Uri): String =
        MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)

    fun stage(context: Context, uri: Uri): File {
        val root = File(context.cacheDir, "roms").apply { mkdirs() }
        val key = keyFor(uri)
        root.listFiles()?.filter { it.name != key }?.forEach { it.deleteRecursively() }
        val dir = File(root, key).apply { mkdirs() }

        val archivedEntry = uri.encodedFragment?.takeIf { it.startsWith("entry=") }
        val sourceUri = if (archivedEntry != null) uri.buildUpon().fragment(null).build() else uri
        val outputName = archivedEntry?.let { Uri.decode(it.substringAfter("entry=")).substringAfterLast('/') }
            ?: displayName(context, uri)
        val main = File(dir, sanitize(outputName))
        if (archivedEntry != null) {
            if (!main.isFile || main.length() == 0L) ArchiveRom.extract(context, uri, main)
        } else {
            copyIfNeeded(context, sourceUri, main)
            stageSheetTracks(context, sourceUri, dir, main, depth = 0)
        }
        return main
    }

    private fun stageSheetTracks(context: Context, uri: Uri, dir: File, sheet: File, depth: Int) {
        val ext = sheet.extension.lowercase()
        if (ext !in SheetParser.EXTENSIONS || depth > 1) return
        val names = SheetParser.referencedFiles(sheet.readText(Charsets.ISO_8859_1), ext)
        if (names.isNotEmpty()) copySiblings(context, uri, dir, names)
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> return sanitize(n) }
        }
        return sanitize(uri.lastPathSegment ?: "game.bin")
    }

    private fun sanitize(name: String): String =
        name.substringAfterLast('/').replace(Regex("[\\\\:*?\"<>|]"), "_").ifBlank { "game.bin" }

    private fun size(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) return it.getLong(0)
        }
        return -1L
    }

    private fun copyIfNeeded(context: Context, src: Uri, dest: File) {
        val expected = size(context, src)
        if (dest.isFile && expected > 0 && dest.length() == expected) return
        val part = File(dest.parentFile, dest.name + ".part")
        val input = context.contentResolver.openInputStream(src)
            ?: throw java.io.IOException("não foi possível abrir ${dest.name}")
        input.use { i -> part.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
        dest.delete()
        if (!part.renameTo(dest)) throw java.io.IOException("falha ao gravar ${dest.name}")
    }

    /** Copia, da mesma pasta do jogo, os arquivos citados no índice. */
    private fun copySiblings(context: Context, gameUri: Uri, dir: File, names: List<String>) {
        val authority = gameUri.authority ?: return
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(gameUri) }.getOrNull() ?: return
        val docId = runCatching { DocumentsContract.getDocumentId(gameUri) }.getOrNull() ?: return
        val parentId = docId.substringBeforeLast('/', "").ifEmpty { return }

        val treeUri = DocumentsContract.buildTreeDocumentUri(authority, treeId)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val wanted = names.associateBy { it.lowercase() }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                val wantedName = wanted[name.lowercase()] ?: continue
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                // grava com o nome exatamente como o índice cita (o Android diferencia maiúsculas)
                copyIfNeeded(context, childUri, File(dir, wantedName))
            }
        }
    }
}
