package com.nandox.arcade.launcher

import android.content.Context
import android.net.Uri
import com.nandox.arcade.core.PlatformClassifier
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.emulator.EmulatorAdapter
import com.nandox.arcade.emulator.LaunchResult
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.emulator.PluginStore
import com.nandox.arcade.emulator.PlayHistory
import com.nandox.arcade.saves.SaveSettings

object GameLauncher {
    /** Resultado da escolha do emulador: um adaptador pronto ou o motivo exato de não haver nenhum. */
    private sealed interface Choice {
        data class Ready(val adapter: EmulatorAdapter) : Choice
        data class Blocked(val reason: String) : Choice
    }

    private fun choose(context: Context, registry: PluginRegistry, game: GameEntity): Choice {
        val platform = Platforms.byId(game.platformId)
        if (game.platformId == PlatformClassifier.UNKNOWN || platform == null) {
            return Choice.Blocked(
                "Plataforma de \"${game.title}\" não identificada. " +
                    "Defina o console em Biblioteca → Não classificados.",
            )
        }
        val ready = registry.readyFor(context, game.platformId, PluginStore(context)).firstOrNull()
        return if (ready != null) {
            Choice.Ready(ready)
        } else {
            Choice.Blocked(registry.explainNoneReady(context, game.platformId, PluginStore(context)))
        }
    }

    fun launch(context: Context, registry: PluginRegistry, game: GameEntity): LaunchResult =
        launchDetailed(context, registry, game).first

    /** Abre o jogo. Devolve null se abriu; senão, o texto exato do motivo para mostrar ao usuário. */
    fun launchAndExplain(context: Context, registry: PluginRegistry, game: GameEntity): String? {
        val (result, reason) = launchDetailed(context, registry, game)
        return reason ?: message(result)
    }

    private fun launchDetailed(context: Context, registry: PluginRegistry, game: GameEntity): Pair<LaunchResult, String?> {
        val adapter = when (val choice = choose(context, registry, game)) {
            is Choice.Blocked -> return LaunchResult.FAILED to choice.reason
            is Choice.Ready -> choice.adapter
        }
        val gameUri = Uri.parse(game.uri)
        PlayHistory.recordLaunch(context, game.title, game.platformId, gameUri)
        val result = adapter.launch(context, gameUri, game.platformId)
        if (result != LaunchResult.FAILED) SaveSettings(context).pendingSession = true
        val reason = if (result == LaunchResult.FAILED) "Não foi possível iniciar ${adapter.manifest.name}." else null
        return result to reason
    }

    fun message(result: LaunchResult): String? = when (result) {
        LaunchResult.STARTED_WITH_GAME -> null
        LaunchResult.OPENED_APP_ONLY -> "Emulador aberto sem o jogo. Abra o arquivo por dentro dele."
        LaunchResult.FAILED -> "Não foi possível iniciar o jogo."
    }
}
