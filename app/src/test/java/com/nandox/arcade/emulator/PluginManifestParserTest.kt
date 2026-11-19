package com.nandox.arcade.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PluginManifestParserTest {
    @Test
    fun parsesValidExternalManifest() {
        val m = PluginManifestParser.parse(
            """{"id":"x","name":"X","version":"1","type":"external_app","platforms":["psp"],"packageName":"a.b"}"""
        )
        assertEquals(PluginType.EXTERNAL_APP, m.type)
        assertEquals("a.b", m.packageName)
    }

    @Test
    fun rejectsMissingPlatforms() {
        assertThrows(PluginManifestException::class.java) {
            PluginManifestParser.parse(
                """{"id":"x","name":"X","version":"1","type":"external_app","platforms":[],"packageName":"a.b"}"""
            )
        }
    }

    @Test
    fun rejectsNativeCoreWithoutAbis() {
        assertThrows(PluginManifestException::class.java) {
            PluginManifestParser.parse(
                """{"id":"x","name":"X","version":"1","type":"native_core","platforms":["psp"]}"""
            )
        }
    }

    @Test
    fun rejectsInvalidJson() {
        assertThrows(PluginManifestException::class.java) { PluginManifestParser.parse("{") }
    }

    @Test
    fun parsesNativeCoreWithCoreLib() {
        val m = PluginManifestParser.parse(
            """{"id":"c","name":"C","version":"1","type":"native_core","platforms":["n64"],"abis":["arm64-v8a"],"coreLib":"libx_libretro_android.so"}"""
        )
        assertEquals(PluginType.NATIVE_CORE, m.type)
        assertEquals("libx_libretro_android.so", m.coreLib)
    }

    @Test
    fun rejectsNativeCoreWithoutCoreLib() {
        assertThrows(PluginManifestException::class.java) {
            PluginManifestParser.parse(
                """{"id":"c","name":"C","version":"1","type":"native_core","platforms":["n64"],"abis":["arm64-v8a"]}"""
            )
        }
    }
}
