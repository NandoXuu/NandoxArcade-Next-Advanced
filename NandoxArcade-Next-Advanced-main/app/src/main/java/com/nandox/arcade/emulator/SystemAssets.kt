package com.nandox.arcade.emulator

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

/** Instala, no diretório "system" dos cores, os arquivos que vêm embutidos no APK (ex.: assets do PPSSPP). */
object SystemAssets {
    private const val MARKER = ".nandox_installed"

    fun install(context: Context, systemDir: File) {
        systemDir.mkdirs()
        val bundled = runCatching { context.assets.list("system") }.getOrNull().orEmpty()
        for (name in bundled.filter { it.endsWith(".zip") }) {
            val target = File(systemDir, name.removeSuffix(".zip"))
            if (File(target, MARKER).exists()) continue
            context.assets.open("system/$name").use { unzip(it, systemDir) }
            target.mkdirs()
            File(target, MARKER).writeText("ok")
        }
    }

    private fun unzip(input: java.io.InputStream, dest: File) {
        val root = dest.canonicalPath + File.separator
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(dest, entry.name)
                // evita "zip slip" (entradas com ../ escrevendo fora da pasta)
                if (!out.canonicalPath.startsWith(root)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }
}
