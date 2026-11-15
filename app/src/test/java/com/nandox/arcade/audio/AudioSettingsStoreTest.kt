package com.nandox.arcade.audio

import com.nandox.arcade.settings.KvStore
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioSettingsStoreTest {
    private lateinit var file: File
    private lateinit var store: AudioSettingsStore

    @Before
    fun setUp() {
        file = File.createTempFile("audio-settings", ".json").also { it.delete() }
        store = AudioSettingsStore(KvStore(file))
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun n64HasOwnProfileWithoutPeriodicAutosave() {
        assertFalse(store.load("n64").periodicAutosave)
        assertTrue(store.load("snes").periodicAutosave)
    }

    @Test
    fun generalDefaultAppliesToOtherConsolesButNotToN64Profile() {
        store.save(null, AudioSettings(mode = AudioMode.STABLE, recovery = false))
        assertEquals(AudioMode.STABLE, store.load("snes").mode)
        assertFalse(store.load("snes").recovery)
        assertEquals(AudioMode.AUTO, store.load("n64").mode)
        assertTrue(store.load("n64").recovery)
    }

    @Test
    fun consoleSettingsAreIndependent() {
        store.save("snes", AudioSettings(mode = AudioMode.LOW_LATENCY))
        store.save("n64", AudioSettings(mode = AudioMode.REALTIME, periodicAutosave = true))
        assertEquals(AudioMode.LOW_LATENCY, store.load("snes").mode)
        assertEquals(AudioMode.REALTIME, store.load("n64").mode)
        assertTrue(store.load("n64").periodicAutosave)
        assertEquals(AudioMode.AUTO, store.load("gba").mode)
    }

    @Test
    fun resetRestoresDefaultsOnlyForThatConsole() {
        store.save("snes", AudioSettings(mode = AudioMode.STABLE))
        store.save("n64", AudioSettings(mode = AudioMode.REALTIME, periodicAutosave = true))
        store.reset("n64")
        assertEquals(AudioMode.AUTO, store.load("n64").mode)
        assertFalse(store.load("n64").periodicAutosave)
        assertEquals(AudioMode.STABLE, store.load("snes").mode)
        assertFalse(store.hasOverride("n64"))
        assertTrue(store.hasOverride("snes"))
    }

    @Test
    fun resetAllKeepsOtherSettingsKeys() {
        val kv = KvStore(file)
        kv.put("outraChave" to "valor")
        val s = AudioSettingsStore(kv)
        s.save("snes", AudioSettings(mode = AudioMode.STABLE))
        s.resetAll()
        assertEquals(AudioMode.AUTO, s.load("snes").mode)
        assertEquals("valor", kv.getString("outraChave", ""))
    }

    @Test
    fun historyAveragesSessionsAndResets() {
        val hist = AudioHistoryStore(KvStore(File.createTempFile("audio-history", ".json").also { it.delete() }))
        assertEquals(null, hist.load("n64"))
        hist.record("n64", null, 0.6f)
        val first = hist.load("n64")!!
        assertEquals(1, first.sessions)
        hist.record("n64", first, 1.0f)
        assertEquals(0.8f, hist.load("n64")!!.avgSpeed, 0.001f)
        hist.reset("n64")
        assertEquals(null, hist.load("n64"))
    }
}
