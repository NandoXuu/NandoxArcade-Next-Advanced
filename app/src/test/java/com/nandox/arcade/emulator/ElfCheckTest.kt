package com.nandox.arcade.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElfCheckTest {
    private fun header(elfClass: Int, machine: Int): ByteArray = ByteArray(20).also {
        it[0] = 0x7F
        it[1] = 'E'.code.toByte()
        it[2] = 'L'.code.toByte()
        it[3] = 'F'.code.toByte()
        it[4] = elfClass.toByte()
        it[18] = (machine and 0xFF).toByte()
        it[19] = (machine shr 8).toByte()
    }

    private val tvArm32 = listOf("armeabi-v7a", "armeabi")
    private val arm64 = listOf("arm64-v8a", "armeabi-v7a", "armeabi")

    @Test
    fun recognisesArm32And64() {
        assertEquals(ElfCheck.Arch.ARM32, ElfCheck.archOf(header(1, 0x28)))
        assertEquals(ElfCheck.Arch.ARM64, ElfCheck.archOf(header(2, 0xB7)))
    }

    @Test
    fun otherArchitecturesAndGarbageAreNotArm() {
        assertEquals(ElfCheck.Arch.OTHER, ElfCheck.archOf(header(1, 0x03))) // x86
        assertEquals(ElfCheck.Arch.OTHER, ElfCheck.archOf(header(2, 0x3E))) // x86_64
        assertEquals(ElfCheck.Arch.OTHER, ElfCheck.archOf(header(2, 0x28))) // classe 64 com máquina ARM
        assertEquals(ElfCheck.Arch.NOT_ELF, ElfCheck.archOf(ByteArray(20)))
        assertEquals(ElfCheck.Arch.NOT_ELF, ElfCheck.archOf(ByteArray(4)))
    }

    @Test
    fun arm64LibraryNeverRunsOnArm32Tv() {
        assertFalse(ElfCheck.runsOn(ElfCheck.Arch.ARM64, tvArm32))
        assertTrue(ElfCheck.runsOn(ElfCheck.Arch.ARM32, tvArm32))
    }

    @Test
    fun arm64DeviceRunsBothAndRejectsInvalid() {
        assertTrue(ElfCheck.runsOn(ElfCheck.Arch.ARM64, arm64))
        assertTrue(ElfCheck.runsOn(ElfCheck.Arch.ARM32, arm64))
        assertFalse(ElfCheck.runsOn(ElfCheck.Arch.OTHER, arm64))
        assertFalse(ElfCheck.runsOn(ElfCheck.Arch.NOT_ELF, arm64))
    }
}
