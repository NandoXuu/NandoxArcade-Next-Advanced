package com.nandox.arcade.audio

import android.content.Context
import com.nandox.arcade.settings.AppSettings
import com.nandox.arcade.settings.KvStore
import java.io.File

/**
 * Guarda os ajustes de áudio por console no mesmo arquivo de configurações do app (KvStore, lido do
 * disco a cada acesso, então vale também para o processo ":emulator").
 *
 * Chaves: audio.<console>.<campo>, ou audio.default.<campo> para o padrão geral.
 * Ordem de busca: ajuste do console > perfil próprio do console (ex.: Nintendo 64) > padrão geral > padrão de fábrica.
 */
class AudioSettingsStore(private val kv: KvStore) {

    companion object {
        const val DEFAULT_SCOPE = "default"
        private val FIELDS = listOf("mode", "recovery", "resync", "autosave", "gamemode", "coreguard", "overlay")

        /** Consoles com perfil próprio: não seguem o padrão geral, só o que foi ajustado para eles. */
        private val PROFILES: Map<String, AudioSettings> = mapOf(
            // O autosave periódico serializa dezenas de MB na thread de emulação do N64 e corta o som.
            "n64" to AudioSettings(periodicAutosave = false),
        )

        fun hasOwnProfile(platformId: String?): Boolean = platformId != null && platformId in PROFILES

        fun forContext(context: Context) = AudioSettingsStore(AppSettings.global(context))
    }

    private fun key(scope: String, field: String) = "audio.$scope.$field"

    private fun lookup(platformId: String?, field: String): String? {
        if (platformId != null) {
            val own = key(platformId, field)
            if (kv.has(own)) return own
            if (hasOwnProfile(platformId)) return null
        }
        val general = key(DEFAULT_SCOPE, field)
        return if (kv.has(general)) general else null
    }

    fun defaultsFor(platformId: String?): AudioSettings = PROFILES[platformId] ?: AudioSettings()

    fun load(platformId: String?): AudioSettings {
        val d = defaultsFor(platformId)
        fun bool(field: String, def: Boolean): Boolean = lookup(platformId, field)?.let { kv.getBoolean(it, def) } ?: def
        val mode = lookup(platformId, "mode")?.let { AudioMode.fromId(kv.getString(it, "")) } ?: d.mode
        return AudioSettings(
            mode = mode,
            recovery = bool("recovery", d.recovery),
            resync = bool("resync", d.resync),
            periodicAutosave = bool("autosave", d.periodicAutosave),
            gameModeDisplay = bool("gamemode", d.gameModeDisplay),
            coreGuard = bool("coreguard", d.coreGuard),
            overlay = bool("overlay", d.overlay),
        )
    }

    /** Grava todos os campos no escopo ([platformId] nulo = padrão geral). */
    fun save(platformId: String?, s: AudioSettings) {
        val scope = platformId ?: DEFAULT_SCOPE
        kv.put(
            key(scope, "mode") to s.mode.id,
            key(scope, "recovery") to s.recovery,
            key(scope, "resync") to s.resync,
            key(scope, "autosave") to s.periodicAutosave,
            key(scope, "gamemode") to s.gameModeDisplay,
            key(scope, "coreguard") to s.coreGuard,
            key(scope, "overlay") to s.overlay,
        )
    }

    fun hasOverride(platformId: String?): Boolean {
        val scope = platformId ?: DEFAULT_SCOPE
        return FIELDS.any { kv.has(key(scope, it)) }
    }

    /** Restaura os padrões de um console (ou do padrão geral). */
    fun reset(platformId: String?) {
        val prefix = "audio.${platformId ?: DEFAULT_SCOPE}."
        kv.removeWhere { it.startsWith(prefix) }
    }

    /** Restaura todos os ajustes de áudio de todos os consoles. */
    fun resetAll() {
        kv.removeWhere { it.startsWith("audio.") }
    }
}

/** Resultado médio de sessões anteriores, por console. Alimenta o modo automático. */
class AudioHistoryStore(private val kv: KvStore) {
    companion object {
        fun forContext(context: Context) =
            AudioHistoryStore(KvStore(File(AppSettings.baseDir(context), "settings/audio-history.json")))
    }

    fun load(platformId: String): AudioHistory? {
        val sessions = kv.getInt("$platformId.sessions", 0)
        if (sessions <= 0) return null
        return AudioHistory(sessions, kv.getFloat("$platformId.speed", 1f))
    }

    /**
     * Registra a velocidade média de uma sessão. Idempotente dentro da mesma sessão: o valor é sempre
     * combinado com o [baseline] lido antes dela começar, e não com o que a própria sessão já gravou.
     */
    fun record(platformId: String, baseline: AudioHistory?, sessionSpeed: Float) {
        val speed = sessionSpeed.coerceIn(0f, 1.2f)
        val blended = if (baseline == null) speed else baseline.avgSpeed * 0.5f + speed * 0.5f
        kv.put(
            "$platformId.sessions" to ((baseline?.sessions ?: 0) + 1),
            "$platformId.speed" to blended,
        )
    }

    fun reset(platformId: String? = null) {
        if (platformId == null) kv.clear() else kv.removeWhere { it.startsWith("$platformId.") }
    }
}
