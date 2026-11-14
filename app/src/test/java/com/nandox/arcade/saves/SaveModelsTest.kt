package com.nandox.arcade.saves

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveModelsTest {
    @Test
    fun classifiesSaveDataAndStates() {
        assertEquals(SaveKind.SAVE_DATA, SaveClassifier.kind("Game.SAV"))
        assertEquals(SaveKind.SAVE_STATE, SaveClassifier.kind("Game.ss0"))
        assertEquals(SaveKind.SAVE_STATE, SaveClassifier.kind("game.state.auto"))
        assertNull(SaveClassifier.kind("game.iso"))
    }

    @Test
    fun backupNameRoundTrips() {
        val name = BackupName.build("mgba", "My__Game.sav", 1_700_000_000_000L)
        val info = BackupName.parse(name)!!
        assertEquals("mgba", info.pluginId)
        assertEquals("My__Game.sav", info.origName)
        assertEquals(1_700_000_000_000L / 1000, info.timestamp / 1000)
    }

    @Test
    fun invalidBackupNamesAreRejected() {
        assertNull(BackupName.parse("foo.sav"))
        assertNull(BackupName.parse("notadate__p__a.sav"))
    }

    @Test
    fun pruneKeepsNewestVersionsOfSameSave() {
        val a = BackupName.build("p", "x.sav", 1_000_000_000_000L)
        val b = BackupName.build("p", "x.sav", 1_100_000_000_000L)
        val c = BackupName.build("p", "x.sav", 1_200_000_000_000L)
        val other = BackupName.build("p", "y.sav", 1_000_000_000_000L)
        assertEquals(listOf(a), BackupPolicy.toPrune(listOf(a, b, c, other), "p", "x.sav", 2))
    }
}
