package com.nandox.arcade.saves

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

enum class Outcome { OK, CONFLICT, FAILED }

data class SaveResult(val outcome: Outcome, val message: String)

object SaveService {
    private fun failure(e: Exception, fallback: String): SaveResult =
        SaveResult(Outcome.FAILED, e.message?.takeIf { it.isNotBlank() } ?: fallback)

    suspend fun backup(context: Context, save: SaveFile): SaveResult = withContext(Dispatchers.IO) {
        val settings = SaveSettings(context)
        val dest = settings.destination()
        if (!dest.available()) return@withContext SaveResult(Outcome.FAILED, "Destino de backup indisponível")
        try {
            val input = context.contentResolver.openInputStream(save.uri)
                ?: return@withContext SaveResult(Outcome.FAILED, "Não foi possível ler ${save.name}")
            val name = BackupName.build(save.pluginId, save.name, System.currentTimeMillis())
            input.use { dest.write(name, it) }
            BackupPolicy.toPrune(dest.list().map { it.name }, save.pluginId, save.name, settings.keepVersions)
                .forEach { dest.delete(it) }
            SaveResult(Outcome.OK, "Backup concluído e verificado: ${save.name}")
        } catch (e: IOException) {
            failure(e, "Backup interrompido")
        } catch (e: SecurityException) {
            SaveResult(Outcome.FAILED, "Sem permissão para ler ${save.name}")
        }
    }

    suspend fun writeToLocation(
        context: Context,
        pluginId: String,
        name: String,
        openSource: () -> InputStream?,
        overwriteConfirmed: Boolean,
    ): SaveResult = withContext(Dispatchers.IO) {
        val settings = SaveSettings(context)
        val tree = settings.location(pluginId)?.let(Uri::parse)
            ?: return@withContext SaveResult(Outcome.FAILED, "Defina a pasta de saves deste emulador")
        try {
            val sourceHash = openSource()?.use { FileOps.hash(it) }
                ?: return@withContext SaveResult(Outcome.FAILED, "Não foi possível ler a origem")
            val existing = SaveScanner.scan(context, pluginId, tree).firstOrNull { it.name == name }
            if (existing != null) {
                val currentHash = context.contentResolver.openInputStream(existing.uri)?.use { FileOps.hash(it) }
                if (currentHash == sourceHash) return@withContext SaveResult(Outcome.OK, "Já é idêntico ao arquivo atual")
                if (!overwriteConfirmed) {
                    return@withContext SaveResult(Outcome.CONFLICT, "$name já existe e é diferente")
                }
                val safety = backup(context, existing)
                if (safety.outcome != Outcome.OK) {
                    return@withContext SaveResult(Outcome.FAILED, "Backup de segurança falhou. Nada foi substituído.")
                }
            }
            val targetUri = existing?.uri
                ?: Saf.create(context, tree, Saf.rootId(tree), name, Saf.SAVE_MIME)
                ?: return@withContext SaveResult(Outcome.FAILED, "Não foi possível criar $name")
            val out = context.contentResolver.openOutputStream(targetUri, "wt")
                ?: return@withContext SaveResult(Outcome.FAILED, "Não foi possível gravar $name")
            val written = openSource()?.use { src -> out.use { FileOps.copyWithHash(src, it) } }
                ?: return@withContext SaveResult(Outcome.FAILED, "Não foi possível ler a origem")
            val readBack = context.contentResolver.openInputStream(targetUri)?.use { FileOps.hash(it) }
            if (written != readBack) {
                SaveResult(Outcome.FAILED, "Gravação não confirmada para $name. Restaure o backup de segurança.")
            } else {
                SaveResult(Outcome.OK, "$name gravado e verificado")
            }
        } catch (e: IOException) {
            failure(e, "Operação interrompida")
        } catch (e: SecurityException) {
            SaveResult(Outcome.FAILED, "Sem permissão de gravação na pasta de saves")
        }
    }

    suspend fun restore(context: Context, backupName: String, overwriteConfirmed: Boolean): SaveResult {
        val info = BackupName.parse(backupName) ?: return SaveResult(Outcome.FAILED, "Nome de backup inválido")
        val dest = withContext(Dispatchers.IO) { SaveSettings(context).destination() }
        return writeToLocation(context, info.pluginId, info.origName, { dest.open(backupName) }, overwriteConfirmed)
    }

    suspend fun export(context: Context, save: SaveFile, target: Uri): SaveResult = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(save.uri)
                ?: return@withContext SaveResult(Outcome.FAILED, "Não foi possível ler ${save.name}")
            val out = context.contentResolver.openOutputStream(target, "wt")
                ?: return@withContext SaveResult(Outcome.FAILED, "Não foi possível gravar o destino")
            val written = input.use { src -> out.use { FileOps.copyWithHash(src, it) } }
            val readBack = context.contentResolver.openInputStream(target)?.use { FileOps.hash(it) }
            if (written == readBack) {
                SaveResult(Outcome.OK, "Exportado e verificado: ${save.name}")
            } else {
                SaveResult(Outcome.FAILED, "Exportação não confirmada")
            }
        } catch (e: IOException) {
            failure(e, "Exportação interrompida")
        } catch (e: SecurityException) {
            SaveResult(Outcome.FAILED, "Sem permissão no destino")
        }
    }

    suspend fun autoBackupIfPending(context: Context): SaveResult? {
        val settings = SaveSettings(context)
        if (!settings.autoBackup || !settings.pendingSession) return null
        settings.pendingSession = false
        val dest = withContext(Dispatchers.IO) { settings.destination() }
        var copied = 0
        var failed = 0
        for ((pluginId, uri) in settings.locations()) {
            for (save in SaveScanner.scan(context, pluginId, Uri.parse(uri))) {
                val changed = withContext(Dispatchers.IO) { hasChanged(context, dest, save) }
                if (!changed) continue
                if (backup(context, save).outcome == Outcome.OK) copied++ else failed++
            }
        }
        val result = when {
            failed > 0 -> SaveResult(Outcome.FAILED, "Backup automático falhou em $failed arquivo(s); $copied concluído(s)")
            else -> SaveResult(Outcome.OK, "Backup automático: $copied arquivo(s) novo(s) ou alterado(s)")
        }
        settings.lastAuto = "${System.currentTimeMillis()}|${result.message}"
        return result
    }

    private fun hasChanged(context: Context, dest: BackupDestination, save: SaveFile): Boolean = try {
        val current = context.contentResolver.openInputStream(save.uri)?.use { FileOps.hash(it) }
        val latest = dest.list().map { it.name }
            .mapNotNull { n -> BackupName.parse(n)?.let { n to it } }
            .filter { it.second.pluginId == save.pluginId && it.second.origName == save.name }
            .maxByOrNull { it.second.timestamp }
        val latestHash = latest?.let { l -> dest.open(l.first)?.use { FileOps.hash(it) } }
        current != null && current != latestHash
    } catch (e: IOException) {
        true
    } catch (e: SecurityException) {
        false
    }
}
