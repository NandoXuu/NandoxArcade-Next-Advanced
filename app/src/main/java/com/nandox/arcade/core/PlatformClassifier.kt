package com.nandox.arcade.core

object PlatformClassifier {
    const val UNKNOWN = "unknown"

    /**
     * Um .bin sem pista alguma (pasta ou nome) e maior que isso é imagem de disco (PlayStation);
     * até isso cabe numa ROM de Mega Drive (a maior, Super Street Fighter II, tem 5 MiB).
     */
    const val GENESIS_MAX_BIN_BYTES = 8L * 1024 * 1024

    /** ".md" também é a extensão de textos em Markdown; ROMs de Mega Drive têm pelo menos 32 KiB. */
    const val GENESIS_MIN_MD_BYTES = 32L * 1024
    private val documentationNames = setOf("readme", "license", "licence", "changelog", "contributing", "notes", "todo")

    private val aliases = mapOf(
        "ps1" to setOf("psx", "playstation1", "psone"),
        "ps2" to setOf("playstation2"),
        "psp" to setOf("playstationportable"),
        "nds" to setOf("nintendods"),
        "3ds" to setOf("nintendo3ds"),
        "n64" to setOf("nintendo64"),
        "gba" to setOf("gameboyadvance"),
        "gamecube" to setOf("ngc"),
        "nes" to setOf("famicom", "nintendoentertainmentsystem"),
        "snes" to setOf("supernintendo", "supernes", "superfamicom", "sfc"),
        "gb" to setOf("gameboy"),
        "gbc" to setOf("gameboycolor"),
        "genesis" to setOf("megadrive", "segagenesis", "segamegadrive"),
    )

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun tokens(p: Platform): Set<String> =
        (setOf(normalize(p.id), normalize(p.name)) + aliases[p.id].orEmpty()).filter { it.length >= 3 }.toSet()

    /** Extensão (sem ponto, em minúsculas) de um nome de arquivo. */
    fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

    /** True se alguma plataforma conhece a extensão. Maiúsculas e minúsculas não importam. */
    fun isSupportedExtension(ext: String): Boolean {
        val e = ext.removePrefix(".").lowercase()
        return Platforms.all.any { e in it.extensions }
    }

    /**
     * Plataforma de um arquivo: pela extensão; se ela for ambígua (.bin, .iso, .chd), pelas pistas
     * de pasta/nome; e, só para .bin sem nenhuma pista, pelo tamanho ([sizeBytes], quando conhecido).
     * Devolve null se nenhuma plataforma usa a extensão e [UNKNOWN] se ela é ambígua e nada decide.
     */
    fun classify(fileName: String, folderNames: List<String>, sizeBytes: Long? = null): String? {
        val ext = extensionOf(fileName)
        if (ext == "md" && isMarkdownText(fileName, sizeBytes)) return null
        val candidates = Platforms.all.filter { ext in it.extensions }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first().id

        val segments = (folderNames + fileName.substringBeforeLast('.')).map(::normalize)
        val scored = candidates.map { p ->
            p to tokens(p).filter { t -> segments.any { t in it } }.maxOfOrNull { it.length }.orZero()
        }
        val best = scored.maxOf { it.second }
        val winners = scored.filter { it.second == best }
        if (best > 0 && winners.size == 1) return winners.first().first.id

        if (ext == "bin" && sizeBytes != null && sizeBytes > 0) {
            val bySize = if (sizeBytes <= GENESIS_MAX_BIN_BYTES) "genesis" else "ps1"
            if (candidates.any { it.id == bySize }) return bySize
        }
        return UNKNOWN
    }

    private fun isMarkdownText(fileName: String, sizeBytes: Long?): Boolean =
        fileName.substringBeforeLast('.').trim().lowercase() in documentationNames ||
            (sizeBytes != null && sizeBytes in 0 until GENESIS_MIN_MD_BYTES)

    private fun Int?.orZero() = this ?: 0
}
