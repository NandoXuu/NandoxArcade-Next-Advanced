package com.nandox.arcade.emulator

/** Lê índices de disco (.cue / .gdi) e devolve os nomes dos arquivos de faixa que eles referenciam. */
object SheetParser {
    private val CUE_FILE = Regex("""(?im)^\s*FILE\s+(?:"([^"]+)"|(\S+))""")
    private val GDI_TRACK = Regex("""^\s*\d+\s+\d+\s+\d+\s+\d+\s+(?:"([^"]+)"|(\S+))""")

    val EXTENSIONS: Set<String> = setOf("cue", "gdi")

    fun referencedFiles(sheet: String, extension: String): List<String> {
        val raw: Sequence<String> = when (extension.lowercase()) {
            "cue" -> CUE_FILE.findAll(sheet).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            "gdi" -> sheet.lineSequence().drop(1)
                .mapNotNull { GDI_TRACK.find(it) }
                .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            else -> emptySequence()
        }
        return raw
            .map { it.substringAfterLast('/').substringAfterLast('\\') }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }
}
