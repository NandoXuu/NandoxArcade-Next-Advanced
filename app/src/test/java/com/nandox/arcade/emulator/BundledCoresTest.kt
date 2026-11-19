package com.nandox.arcade.emulator

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Garante que os manifestos de core nativo e o script de download (scripts/fetch_cores.sh) andam juntos. */
class BundledCoresTest {
    private val pluginsDir = File("src/main/assets/plugins")
    private val script = File("../scripts/fetch_cores.sh")

    @Test
    fun allBundledManifestsAreValid() {
        val result = PluginLoader.load(pluginsDir)
        assertEquals(emptyList<Pair<String, String>>(), result.errors)
        assertEquals(result.manifests.size, result.manifests.map { it.id }.toSet().size)
    }

    @Test
    fun everyNativeCoreIsDownloadedByTheScript() {
        val natives = PluginLoader.load(pluginsDir).manifests.filter { it.type == PluginType.NATIVE_CORE }
        assertTrue("nenhum core nativo declarado", natives.isNotEmpty())
        val text = script.readText()
        for (m in natives) {
            val lib = requireNotNull(m.coreLib)
            assertTrue("coreLib inesperado: $lib", lib.startsWith("lib") && lib.endsWith("_libretro_android.so"))
            val name = lib.removePrefix("lib").removeSuffix("_libretro_android.so")
            assertTrue("fetch_cores.sh não baixa '$name' (plugin ${m.id})", Regex("""(?m)^\s*$name\b""").containsMatchIn(text))
            assertTrue("${m.id} deve declarar arm64-v8a", "arm64-v8a" in m.abis)
        }
    }

    @Test
    fun nativeCoresComeWithEveryPlatformOnlyOnce() {
        val natives = PluginLoader.load(pluginsDir).manifests.filter { it.type == PluginType.NATIVE_CORE }
        val platforms = natives.flatMap { it.platforms }
        assertEquals("duas cores nativos para a mesma plataforma", platforms.size, platforms.toSet().size)
    }
}
