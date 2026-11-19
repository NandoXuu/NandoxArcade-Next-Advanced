package com.nandox.arcade.emulator

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * ABIs do Android. A comparação é sempre EXATA, sem aliases: "armeabi" (ARMv5, legado) não é
 * "armeabi-v7a", e nenhuma delas é "arm64-v8a". Um aparelho que reporta "armeabi-v7a, armeabi"
 * executa as duas ABIs de 32 bits, mas um core só conta como compatível se alguma ABI que o
 * manifesto declara estiver na lista que o próprio aparelho informa.
 *
 * Este arquivo não depende de classes do Android, para poder ser testado na JVM.
 */
object Abi {
    const val ARMEABI_V7A = "armeabi-v7a"
    const val ARMEABI = "armeabi"
    const val ARM64_V8A = "arm64-v8a"
    const val X86 = "x86"
    const val X86_64 = "x86_64"

    fun normalize(abi: String): String = abi.trim().lowercase()

    /** ABIs declaradas pelo manifesto que o aparelho de fato executa (comparação exata). */
    fun supportedBy(declared: List<String>, device: List<String>): List<String> {
        val deviceSet = device.map(::normalize).toSet()
        return declared.map(::normalize).filter { it in deviceSet }.distinct()
    }

    /** Família de instruções de uma ABI do Android, ou null se for desconhecida. */
    fun family(abi: String): ElfArch? = when (normalize(abi)) {
        ARMEABI_V7A, ARMEABI -> ElfArch.ARM32
        ARM64_V8A -> ElfArch.ARM64
        X86 -> ElfArch.X86
        X86_64 -> ElfArch.X86_64
        else -> null
    }
}

enum class ElfArch { ARM32, ARM64, X86, X86_64, OTHER }

/** Campos do cabeçalho ELF que dizem para qual CPU o binário foi compilado. */
data class ElfInfo(val is64Bit: Boolean, val littleEndian: Boolean, val machine: Int) {
    val arch: ElfArch
        get() = when {
            machine == EM_ARM && !is64Bit -> ElfArch.ARM32
            machine == EM_AARCH64 && is64Bit -> ElfArch.ARM64
            machine == EM_386 && !is64Bit -> ElfArch.X86
            machine == EM_X86_64 && is64Bit -> ElfArch.X86_64
            else -> ElfArch.OTHER
        }

    companion object {
        const val EM_386 = 3
        const val EM_ARM = 40
        const val EM_X86_64 = 62
        const val EM_AARCH64 = 183
    }
}

object Elf {
    private const val HEADER_BYTES = 20

    /** Lê classe (32/64 bits), endianness e e_machine. Devolve null se não for um ELF válido. */
    fun parse(header: ByteArray): ElfInfo? {
        if (header.size < HEADER_BYTES) return null
        val magic = header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()
        if (!magic) return null
        val elfClass = header[4].toInt()
        val data = header[5].toInt()
        if ((elfClass != 1 && elfClass != 2) || (data != 1 && data != 2)) return null
        val little = data == 1
        val b0 = header[18].toInt() and 0xFF
        val b1 = header[19].toInt() and 0xFF
        val machine = if (little) b0 or (b1 shl 8) else (b0 shl 8) or b1
        return ElfInfo(is64Bit = elfClass == 2, littleEndian = little, machine = machine)
    }

    fun read(file: File): ElfInfo? = try {
        RandomAccessFile(file, "r").use { raf ->
            val buffer = ByteArray(HEADER_BYTES)
            raf.readFully(buffer)
            parse(buffer)
        }
    } catch (e: IOException) {
        null
    }
}

enum class CoreCheck { OK, ABI_NOT_SUPPORTED, LIBRARY_MISSING, LIBRARY_INVALID, LIBRARY_WRONG_ARCH }

object CoreCompatibility {
    /**
     * Confere um core nativo sem confiar em nomes de arquivo: o binário que está em nativeLibraryDir
     * precisa ser um ELF da mesma arquitetura do processo e de uma ABI que o manifesto declara.
     *
     * @param declaredAbis ABIs para as quais o manifesto diz que existe binário.
     * @param deviceAbis lista completa que o aparelho informa (Build.SUPPORTED_ABIS).
     * @param processAbis ABIs do processo atual (32 ou 64 bits); a primeira é a ABI primária.
     * @param libraryExists se o arquivo .so existe em nativeLibraryDir.
     * @param elf cabeçalho lido do arquivo; null se ilegível ou se não for ELF.
     */
    fun check(
        declaredAbis: List<String>,
        deviceAbis: List<String>,
        processAbis: List<String>,
        libraryExists: Boolean,
        elf: ElfInfo?,
    ): CoreCheck {
        val usable = Abi.supportedBy(declaredAbis, deviceAbis)
        if (usable.isEmpty()) return CoreCheck.ABI_NOT_SUPPORTED
        if (!libraryExists) return CoreCheck.LIBRARY_MISSING
        if (elf == null) return CoreCheck.LIBRARY_INVALID
        val processFamily = processAbis.firstOrNull()?.let(Abi::family)
        if (processFamily != null && elf.arch != processFamily) return CoreCheck.LIBRARY_WRONG_ARCH
        val declaredFamilies = usable.mapNotNull(Abi::family).toSet()
        if (elf.arch !in declaredFamilies) return CoreCheck.LIBRARY_WRONG_ARCH
        return CoreCheck.OK
    }
}
