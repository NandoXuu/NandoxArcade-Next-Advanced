package com.nandox.arcade.core

object PlatformClassifier {
    const val UNKNOWN = "unknown"

    private val aliases = mapOf(
        "ps1" to setOf("psx", "playstation1", "psone"),
        "ps2" to setOf("playstation2"),
        "nds" to setOf("nintendods"),
        "3ds" to setOf("nintendo3ds"),
        "n64" to setOf("nintendo64"),
        "gba" to setOf("gameboyadvance"),
        "gamecube" to setOf("ngc"),
        "nes" to setOf("famicom", "nintendoentertainmentsystem"),
        "snes" to setOf("supernintendo", "superfamicom", "sfc"),
        "gb" to setOf("gameboy", "gameboycolor", "gbc"),
        "md" to setOf("megadrive", "genesis", "segagenesis"),
    )

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun tokens(p: Platform): Set<String> =
        (setOf(normalize(p.id), normalize(p.name)) + aliases[p.id].orEmpty()).filter { it.length >= 3 }.toSet()

    fun classify(fileName: String, folderNames: List<String>): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val candidates = Platforms.all.filter { ext in it.extensions }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first().id

        val segments = (folderNames + fileName.substringBeforeLast('.')).map(::normalize)
        val scored = candidates.map { p ->
            p to tokens(p).filter { t -> segments.any { t in it } }.maxOfOrNull { it.length }.orZero()
        }
        val best = scored.maxOf { it.second }
        val winners = scored.filter { it.second == best }
        return if (best > 0 && winners.size == 1) winners.first().first.id else UNKNOWN
    }

    private fun Int?.orZero() = this ?: 0
}
