package com.nandox.arcade.core

data class Platform(
    val id: String,
    val name: String,
    val maker: String,
    val extensions: Set<String>,
)

object Platforms {
    val all: List<Platform> = listOf(
        Platform("ps1", "PlayStation", "Sony", setOf("cue", "bin", "chd", "pbp")),
        Platform("ps2", "PlayStation 2", "Sony", setOf("iso", "chd", "cso")),
        Platform("psp", "PSP", "Sony", setOf("iso", "cso", "pbp")),
        Platform("nds", "Nintendo DS", "Nintendo", setOf("nds")),
        Platform("3ds", "Nintendo 3DS", "Nintendo", setOf("3ds", "cia", "cci")),
        Platform("n64", "Nintendo 64", "Nintendo", setOf("z64", "n64", "v64")),
        Platform("dreamcast", "Dreamcast", "Sega", setOf("gdi", "cdi", "chd")),
        Platform("gba", "Game Boy Advance", "Nintendo", setOf("gba")),
        Platform("gamecube", "GameCube", "Nintendo", setOf("gcm", "rvz", "iso")),
        Platform("wii", "Wii", "Nintendo", setOf("wbfs", "rvz", "iso")),
    )

    fun byId(id: String): Platform? = all.firstOrNull { it.id == id }
}
