package com.nandox.arcade.emulator

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginLoaderTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun loadsValidAndIsolatesInvalid() {
        val dir = tmp.newFolder()
        File(dir, "ok.json").writeText(
            """{"id":"a","name":"A","version":"1","type":"external_app","platforms":["gba"],"packageName":"p.q"}"""
        )
        File(dir, "bad.json").writeText("{")
        val r = PluginLoader.load(dir)
        assertEquals(listOf("a"), r.manifests.map { it.id })
        assertEquals(listOf("bad.json"), r.errors.map { it.first })
    }

    @Test
    fun missingDirectoryYieldsEmpty() {
        val r = PluginLoader.load(File(tmp.root, "nope"))
        assertEquals(0, r.manifests.size)
        assertEquals(0, r.errors.size)
    }
}
