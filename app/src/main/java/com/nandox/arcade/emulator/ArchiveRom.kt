package com.nandox.arcade.emulator

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.util.zip.ZipFile

/**
 * Leitor de arquivos compactados. Cada ROM interna é representada por uma URI com
 * fragmento #entry=...; o URI original do SAF continua sendo a fonte do arquivo.
 */
object ArchiveRom {
    private val supported = setOf("zip", "7z")

    fun isArchive(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in supported

    fun entries(context: Context, uri: Uri): List<String> {
        val archive = copyToCache(context, uri)
        return try {
            when (archive.extension.lowercase()) {
                "zip" -> ZipFile(archive).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory && safeEntry(it.name) }
                        .map { it.name }.toList()
                }
                "7z" -> SevenZFile(archive).use { seven ->
                    buildList {
                        while (true) {
                            val entry = seven.nextEntry ?: break
                            if (!entry.isDirectory && safeEntry(entry.name)) add(entry.name)
                        }
                    }
                }
                else -> emptyList()
            }
        } finally {
            archive.delete()
        }
    }

    fun uriForEntry(archiveUri: Uri, entry: String): Uri =
        archiveUri.buildUpon().encodedFragment("entry=${Uri.encode(entry)}").build()

    fun extract(context: Context, source: Uri, destination: File) {
        val fragment = source.encodedFragment ?: throw IllegalArgumentException("ROM compactada sem entrada")
        val entryName = Uri.decode(fragment.substringAfter("entry=", ""))
        require(safeEntry(entryName)) { "Caminho inválido dentro do arquivo" }
        val archiveUri = source.buildUpon().fragment(null).build()
        val archive = copyToCache(context, archiveUri)
        try {
            when (archive.extension.lowercase()) {
                "zip" -> ZipFile(archive).use { zip ->
                    val entry = zip.getEntry(entryName) ?: error("ROM não encontrada no ZIP")
                    zip.getInputStream(entry).use { input -> destination.outputStream().use { output -> input.copyTo(output, 256 * 1024) } }
                }
                "7z" -> SevenZFile(archive).use { seven ->
                    var found = false
                    while (true) {
                        val entry = seven.nextEntry ?: break
                        if (!entry.isDirectory && entry.name == entryName) {
                            destination.outputStream().use { out ->
                                val buffer = ByteArray(256 * 1024)
                                while (true) {
                                    val n = seven.read(buffer)
                                    if (n <= 0) break
                                    out.write(buffer, 0, n)
                                }
                            }
                            found = true
                            break
                        }
                    }
                    check(found) { "ROM não encontrada no 7z" }
                }
            }
        } finally {
            archive.delete()
        }
    }

    private fun safeEntry(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        return normalized.isNotBlank() && !normalized.startsWith("/") &&
            normalized.split('/').none { it == ".." } && normalized.substringAfterLast('/').contains('.')
    }

    private fun copyToCache(context: Context, uri: Uri): File {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "rom.${uri.lastPathSegment?.substringAfterLast('.', "zip") ?: "zip"}"
        val ext = name.substringAfterLast('.', "zip").lowercase()
        require(ext == "zip" || ext == "7z") { "Formato compactado não suportado" }
        val target = File(context.cacheDir, "archive-${System.nanoTime()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
        } ?: error("Não foi possível ler o arquivo compactado")
        return target
    }
}
