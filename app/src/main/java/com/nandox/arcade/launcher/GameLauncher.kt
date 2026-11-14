package com.nandox.arcade.launcher

import android.content.Context
import android.net.Uri
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.emulator.EmulatorAdapter
import com.nandox.arcade.emulator.LaunchResult
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.emulator.PluginStatus
import com.nandox.arcade.emulator.PluginStore
import com.nandox.arcade.emulator.PlayHistory
import com.nandox.arcade.saves.SaveSettings

object GameLauncher {
    private const val DEFAULT_FAILURE = "Nenhum emulador disponível para este jogo"

    /** Motivo real da última falha de [launch]; usado por [message] para não mostrar um aviso genérico/falso. */
    @Volatile
    private var lastFailure: String? = null

    fun launch(context: Context, registry: PluginRegistry, game: GameEntity): LaunchResult {
        val candidates = registry.adaptersFor(game.platformId, PluginStore(context))
        val adapter = candidates.firstOrNull { it.diagnose(context) == PluginStatus.READY }
        val gameUri = Uri.parse(game.uri)
        PlayHistory.recordLaunch(context, game.title, game.platformId, gameUri)
        val result = adapter?.launch(context, gameUri, game.platformId) ?: LaunchResult.FAILED
        lastFailure = when {
            result != LaunchResult.FAILED -> null
            adapter != null -> "Não foi possível abrir ${adapter.manifest.name}"
            else -> describeFailure(context, candidates, game.platformId)
        }
        if (result != LaunchResult.FAILED) SaveSettings(context).pendingSession = true
        return result
    }

    private fun describeFailure(context: Context, candidates: List<EmulatorAdapter>, platformId: String): String {
        val platform = Platforms.byId(platformId)?.name ?: platformId
        if (candidates.isEmpty()) return "Nenhum emulador ativo para $platform"
        val reasons = candidates.map { it.manifest.name to it.diagnose(context) }
        return reasons.joinToString("; ") { (name, status) ->
            when (status) {
                PluginStatus.ABI_INCOMPATIBLE -> "$name não é compatível com este aparelho (${PluginRegistry.deviceAbis.joinToString()})"
                PluginStatus.RUNTIME_UNAVAILABLE -> "$name não está incluído neste APK para este aparelho"
                PluginStatus.APP_NOT_INSTALLED -> "$name não está instalado"
                PluginStatus.DISABLED -> "$name está desativado"
                PluginStatus.READY -> "$name está pronto"
            }
        }
    }

    fun message(result: LaunchResult): String? = when (result) {
        LaunchResult.STARTED_WITH_GAME -> null
        LaunchResult.OPENED_APP_ONLY -> "Emulador aberto sem o jogo. Abra o arquivo por dentro dele."
        LaunchResult.FAILED -> lastFailure ?: DEFAULT_FAILURE
    }
}
