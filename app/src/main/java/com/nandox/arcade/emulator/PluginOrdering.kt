package com.nandox.arcade.emulator

/** Ordem em que os plugins de uma plataforma são tentados ao abrir um jogo. */
object PluginOrdering {
    /**
     * 1º o plugin que o usuário escolheu como principal; 2º cores nativos (rodam dentro do app);
     * 3º apps externos. Dentro de cada grupo vale a "priority" do manifesto (menor vem antes).
     */
    fun order(candidates: List<PluginManifest>, preferredId: String?): List<PluginManifest> =
        candidates.sortedWith(
            compareBy<PluginManifest> { if (it.id == preferredId) 0 else 1 }
                .thenBy { if (it.type == PluginType.NATIVE_CORE) 0 else 1 }
                .thenBy { it.priority }
                .thenBy { it.id },
        )
}

/** Versão de OpenGL ES do aparelho (ConfigurationInfo.reqGlEsVersion: 0x30002 = 3.2). */
object GlEs {
    fun decode(reqGlEsVersion: Int): Double = (reqGlEsVersion ushr 16) + (reqGlEsVersion and 0xFFFF) / 10.0

    /** Versão desconhecida (<= 0) não bloqueia: quem decide é o próprio core ao abrir o jogo. */
    fun satisfies(deviceReqGlEsVersion: Int, minimum: Double?): Boolean =
        minimum == null || deviceReqGlEsVersion <= 0 || decode(deviceReqGlEsVersion) >= minimum
}

/** Mensagens que dizem exatamente por que um jogo não abre, em vez de um erro genérico. */
object LaunchAdvice {
    fun statusReason(manifest: PluginManifest, status: PluginStatus, deviceAbis: List<String>): String = when (status) {
        PluginStatus.READY -> "pronto"
        PluginStatus.APP_NOT_INSTALLED -> "o app não está instalado"
        PluginStatus.ABI_INCOMPATIBLE ->
            "o binário não é compatível com a CPU deste aparelho (${deviceAbis.joinToString()})"
        PluginStatus.RUNTIME_UNAVAILABLE ->
            "o núcleo não está incluído neste APK para a arquitetura do aparelho (${deviceAbis.firstOrNull() ?: "?"})"
        PluginStatus.GPU_INCOMPATIBLE -> "a GPU não oferece OpenGL ES ${manifest.minGlEs ?: "mais recente"}"
        PluginStatus.UNSUPPORTED -> manifest.disabledReason ?: "indisponível neste aparelho"
        PluginStatus.DISABLED -> "desativado em Configurações → Emuladores e plugins"
    }

    /** [problems]: nome do plugin → motivo pelo qual ele não pode abrir o jogo. */
    fun noneReady(platformName: String, problems: List<Pair<String, String>>): String =
        if (problems.isEmpty()) {
            "Nenhum núcleo de $platformName está disponível nesta versão para este aparelho."
        } else {
            "$platformName não pode abrir agora: " + problems.joinToString("; ") { (name, why) -> "$name — $why" } + "."
        }
}
