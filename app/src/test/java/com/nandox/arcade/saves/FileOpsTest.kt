package com.nandox.arcade.saves

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FileOpsTest {
    private val sha256Abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun copyWithHashCopiesBytesAndReturnsSha256() {
        val out = ByteArrayOutputStream()
        val hash = FileOps.copyWithHash(ByteArrayInputStream("abc".toByteArray()), out)
        assertArrayEquals("abc".toByteArray(), out.toByteArray())
        assertEquals(sha256Abc, hash)
    }

    @Test
    fun hashMatchesCopyHash() {
        assertEquals(sha256Abc, FileOps.hash(ByteArrayInputStream("abc".toByteArray())))
    }
}
