package com.nandox.arcade.launcher

import android.content.Context
import android.net.Uri
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.emulator.LaunchResult
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.emulator.PluginStatus
import com.nandox.arcade.emulator.PluginStore
import com.nandox.arcade.emulator.PlayHistory
import com.nandox.arcade.saves.SaveSettings

object GameLauncher {
    fun launch(context: Context, registry: PluginRegistry, game: GameEntity): LaunchResult {
        val adapter = registry.adaptersFor(game.platformId, PluginStore(context))
            .firstOrNull { it.diagnose(context) == PluginStatus.READY }
        val gameUri = Uri.parse(game.uri)
        PlayHistory.recordLaunch(context, game.title, game.platformId, gameUri)
        val result = adapter?.launch(context, gameUri, game.platformId) ?: LaunchResult.FAILED
        if (result != LaunchResult.FAILED) SaveSettings(context).pendingSession = true
        return result
    }

    fun message(result: LaunchResult): String? = when (result) {
        LaunchResult.STARTED_WITH_GAME -> null
        LaunchResult.OPENED_APP_ONLY -> "Emulador aberto sem o jogo. Abra o arquivo por dentro dele."
        LaunchResult.FAILED -> "Nenhum emulador disponível para este jogo"
    }
}
