package com.nandox.arcade.saves

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object FileOps {
    private fun hex(digest: MessageDigest) = digest.digest().joinToString("") { "%02x".format(it) }

    fun copyWithHash(input: InputStream, output: OutputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
            output.write(buffer, 0, n)
        }
        output.flush()
        return hex(digest)
    }

    fun hash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return hex(digest)
    }
}
