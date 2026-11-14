package com.nandox.arcade.saves

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream

interface BackupDestination {
    val label: String
    fun available(): Boolean
    fun list(): List<BackupFile>
    fun write(name: String, source: InputStream): String
    fun open(name: String): InputStream?
    fun delete(name: String): Boolean
}

class InternalDestination(private val dir: File) : BackupDestination {
    override val label = "Armazenamento interno do app"

    override fun available(): Boolean = dir.isDirectory || dir.mkdirs()

    override fun list(): List<BackupFile> =
        dir.listFiles { f -> f.isFile && !f.name.endsWith(".part") }
            ?.map { BackupFile(it.name, it.length()) }.orEmpty()

    override fun write(name: String, source: InputStream): String {
        if (!available()) throw IOException("Pasta de backup indisponível")
        val part = File(dir, "$name.part")
        val target = File(dir, name)
        try {
            val written = part.outputStream().use { FileOps.copyWithHash(source, it) }
            val readBack = part.inputStream().use { FileOps.hash(it) }
            if (written != readBack) throw IOException("Verificação do backup falhou")
            if (!part.renameTo(target)) throw IOException("Não foi possível finalizar o backup")
            return written
        } catch (e: IOException) {
            part.delete()
            throw e
        }
    }

    override fun open(name: String): InputStream? =
        File(dir, name).takeIf { it.isFile }?.inputStream()

    override fun delete(name: String): Boolean = File(dir, name).delete()
}

class SafDestination(private val context: Context, private val tree: Uri) : BackupDestination {
    override val label = "Pasta externa escolhida"

    private fun entries() = Saf.children(context, tree, Saf.rootId(tree)).filter { !it.isDir }

    override fun available(): Boolean = runCatching {
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission } &&
            context.contentResolver.query(
                android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, Saf.rootId(tree)),
                null, null, null, null,
            )?.use { true } == true
    }.getOrDefault(false)

    override fun list(): List<BackupFile> = entries().map { BackupFile(it.name, it.size) }

    override fun write(name: String, source: InputStream): String {
        val uri = Saf.create(context, tree, Saf.rootId(tree), name, Saf.BACKUP_MIME)
            ?: throw IOException("Não foi possível criar o arquivo de backup")
        try {
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Não foi possível gravar no destino")
            val written = out.use { FileOps.copyWithHash(source, it) }
            val readBack = context.contentResolver.openInputStream(uri)?.use { FileOps.hash(it) }
                ?: throw IOException("Não foi possível verificar o backup")
            if (written != readBack) throw IOException("Verificação do backup falhou")
            return written
        } catch (e: IOException) {
            Saf.delete(context, uri)
            throw e
        } catch (e: SecurityException) {
            Saf.delete(context, uri)
            throw IOException("Sem permissão de gravação no destino")
        }
    }

    override fun open(name: String): InputStream? =
        entries().firstOrNull { it.name == name }?.let { context.contentResolver.openInputStream(it.uri) }

    override fun delete(name: String): Boolean =
        entries().firstOrNull { it.name == name }?.let { Saf.delete(context, it.uri) } ?: false
}
