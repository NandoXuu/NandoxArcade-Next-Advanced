package com.nandox.arcade.emulator

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.nandox.arcade.core.PlatformClassifier
import com.nandox.arcade.data.GameEntity
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.channels.SeekableByteChannel

/**
 * Leitor de arquivos compactados (.zip e .7z). Cada ROM interna é representada por uma URI com
 * fragmento #entry=...; o URI original continua sendo a fonte do arquivo.
 *
 * O arquivo é lido por acesso aleatório (só o índice é lido para listar), sem copiar o pacote inteiro
 * para o cache. Só se o provedor não permitir acesso aleatório é que se cai para uma cópia temporária.
 */
object ArchiveRom {
    private val supported = setOf("zip", "7z")

    fun isArchive(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in supported

    private interface Archive : AutoCloseable {
        fun names(): List<String>
        fun copyTo(name: String, out: File)
    }

    private class ZipArchive(private val zip: ZipFile) : Archive {
        override fun names(): List<String> =
            zip.entries.asSequence().filter { !it.isDirectory }.map { it.name }.toList()

        override fun copyTo(name: String, out: File) {
            val entry = zip.getEntry(name) ?: error("Arquivo não encontrado no ZIP: $name")
            zip.getInputStream(entry).use { input ->
                out.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            }
        }

        override fun close() = zip.close()
    }

    private class SevenArchive(private val seven: SevenZFile) : Archive {
        override fun names(): List<String> =
            seven.entries.asSequence().filter { !it.isDirectory }.map { it.name }.toList()

        override fun copyTo(name: String, out: File) {
            val entry = seven.entries.firstOrNull { !it.isDirectory && it.name == name }
                ?: error("Arquivo não encontrado no 7z: $name")
            seven.getInputStream(entry).use { input ->
                out.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            }
        }

        override fun close() = seven.close()
    }

    private fun open(ext: String, channel: SeekableByteChannel): Archive = when (ext) {
        "zip" -> ZipArchive(ZipFile(channel))
        "7z" -> SevenArchive(SevenZFile(channel))
        else -> throw IllegalArgumentException("Formato compactado não suportado")
    }

    private fun <T : Any> withArchive(context: Context, uri: Uri, block: (Archive) -> T): T {
        val ext = displayName(context, uri).substringAfterLast('.', "").lowercase()
        require(ext in supported) { "Formato compactado não suportado" }

        val direct = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.let { pfd ->
                try {
                    val channel = FileInputStream(pfd.fileDescriptor).channel
                    open(ext, channel).use(block)
                } finally {
                    runCatching { pfd.close() }
                }
            }
        }.getOrNull()
        if (direct != null) return direct

        // Provedor sem acesso aleatório: copia o pacote para o cache só durante esta operação.
        val copy = copyToCache(context, uri, ext)
        try {
            return open(ext, RandomAccessFile(copy, "r").channel).use(block)
        } finally {
            copy.delete()
        }
    }

    /** Nomes (como estão no arquivo) de todos os arquivos úteis dentro do pacote. */
    fun entries(context: Context, uri: Uri): List<String> = withArchive(context, uri) { archive ->
        archive.names().filter { safeEntry(it) && !it.replace('\\', '/').startsWith("__MACOSX/") }
    }

    /**
     * Jogos encontrados dentro do pacote. [hint] é a plataforma a usar quando o formato é ambíguo
     * (por exemplo .iso) e nem o nome do pacote nem as pastas dizem qual é.
     */
    fun games(
        context: Context,
        archiveUri: Uri,
        archiveName: String,
        folders: List<String>,
        hint: String? = null,
    ): List<GameEntity> {
        val raw = entries(context, archiveUri)
        val sheetDirs = raw.filter { it.substringAfterLast('.', "").lowercase() in SheetParser.EXTENSIONS }
            .map { it.replace('\\', '/').substringBeforeLast('/', "") }.toSet()
        val archiveFolder = archiveName.substringBeforeLast('.')
        return raw.mapNotNull { entry ->
            val normalized = entry.replace('\\', '/')
            val file = normalized.substringAfterLast('/')
            val dir = normalized.substringBeforeLast('/', "")
            // faixas .bin de um .cue/.gdi não são jogos separados
            if (file.substringAfterLast('.', "").lowercase() == "bin" && dir in sheetDirs) return@mapNotNull null
            val context2 = folders + archiveFolder + dir.split('/').filter { it.isNotEmpty() }
            val classified = PlatformClassifier.classify(file, context2) ?: return@mapNotNull null
            val platform = if (classified == PlatformClassifier.UNKNOWN && hint != null) hint else classified
            GameEntity(
                title = file.substringBeforeLast('.'),
                platformId = platform,
                uri = uriForEntry(archiveUri, entry).toString(),
            )
        }
    }

    fun uriForEntry(archiveUri: Uri, entry: String): Uri =
        archiveUri.buildUpon().encodedFragment("entry=${Uri.encode(entry)}").build()

    /**
     * Extrai a ROM escolhida para [destination]. Para discos com índice (.cue/.gdi), as demais
     * faixas da mesma pasta do pacote vão junto, com os nomes que o índice espera.
     */
    fun extract(context: Context, source: Uri, destination: File) {
        val fragment = source.encodedFragment ?: throw IllegalArgumentException("ROM compactada sem entrada")
        val entryName = Uri.decode(fragment.substringAfter("entry=", ""))
        require(safeEntry(entryName)) { "Caminho inválido dentro do arquivo" }
        val archiveUri = source.buildUpon().fragment(null).build()
        destination.parentFile?.mkdirs()

        withArchive(context, archiveUri) { archive ->
            val wanted = LinkedHashMap<String, File>()
            wanted[entryName] = destination
            if (destination.extension.lowercase() in SheetParser.EXTENSIONS) {
                val dir = entryName.replace('\\', '/').substringBeforeLast('/', "")
                for (name in archive.names()) {
                    if (name == entryName || !safeEntry(name)) continue
                    val normalized = name.replace('\\', '/')
                    if (normalized.substringBeforeLast('/', "") == dir) {
                        wanted[name] = File(destination.parentFile, normalized.substringAfterLast('/'))
                    }
                }
            }
            for ((name, file) in wanted) archive.copyTo(name, file)
        }
    }

    private fun safeEntry(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        return normalized.isNotBlank() && !normalized.startsWith("/") &&
            normalized.split('/').none { it == ".." } && normalized.substringAfterLast('/').contains('.')
    }

    private fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    private fun copyToCache(context: Context, uri: Uri, ext: String): File {
        val target = File(context.cacheDir, "archive-${System.nanoTime()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
        } ?: error("Não foi possível ler o arquivo compactado")
        return target
    }
}
