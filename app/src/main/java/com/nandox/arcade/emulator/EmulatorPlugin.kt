package com.nandox.arcade.emulator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import java.io.DataInputStream
import java.io.File
import org.json.JSONException
import org.json.JSONObject

class PluginManifestException(message: String) : Exception(message)

enum class PluginType { EXTERNAL_APP, NATIVE_CORE }

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val type: PluginType,
    val platforms: List<String>,
    val packageName: String?,
    val abis: List<String>,
    val validated: Boolean,
    val launchAction: String? = null,
    val launchClass: String? = null,
    val coreLib: String? = null,
)

object PluginManifestParser {
    fun parse(json: String): PluginManifest {
        val o = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw PluginManifestException("JSON inválido")
        }
        fun text(key: String): String =
            o.optString(key).takeIf { it.isNotBlank() }
                ?: throw PluginManifestException("Campo obrigatório ausente: $key")

        val type = when (text("type")) {
            "external_app" -> PluginType.EXTERNAL_APP
            "native_core" -> PluginType.NATIVE_CORE
            else -> throw PluginManifestException("Tipo desconhecido")
        }
        val platforms = o.optJSONArray("platforms")?.let { a -> List(a.length()) { a.getString(it) } }
            ?.takeIf { it.isNotEmpty() }
            ?: throw PluginManifestException("Lista de plataformas vazia")
        val abis = o.optJSONArray("abis")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
        val pkg = o.optString("packageName").takeIf { it.isNotBlank() }
        if (type == PluginType.EXTERNAL_APP && pkg == null) {
            throw PluginManifestException("packageName obrigatório para app externo")
        }
        if (type == PluginType.NATIVE_CORE && abis.isEmpty()) {
            throw PluginManifestException("abis obrigatório para core nativo")
        }
        val coreLib = o.optString("coreLib").takeIf { it.isNotBlank() }
        if (type == PluginType.NATIVE_CORE && coreLib == null) {
            throw PluginManifestException("coreLib obrigatório para core nativo")
        }
        return PluginManifest(
            id = text("id"),
            name = text("name"),
            version = text("version"),
            type = type,
            platforms = platforms,
            packageName = pkg,
            abis = abis,
            validated = o.optBoolean("validated", false),
            launchAction = o.optString("launchAction").takeIf { it.isNotBlank() },
            launchClass = o.optString("launchClass").takeIf { it.isNotBlank() },
            coreLib = coreLib,
        )
    }
}

enum class PluginStatus { READY, APP_NOT_INSTALLED, ABI_INCOMPATIBLE, RUNTIME_UNAVAILABLE, DISABLED }

data class PluginDiagnostic(val manifest: PluginManifest, val status: PluginStatus, val enabled: Boolean)

enum class LaunchResult { STARTED_WITH_GAME, OPENED_APP_ONLY, FAILED }

interface EmulatorAdapter {
    val manifest: PluginManifest
    fun diagnose(context: Context): PluginStatus
    fun launch(context: Context, gameUri: Uri, platformId: String? = null): LaunchResult
}

class ExternalAppAdapter(override val manifest: PluginManifest) : EmulatorAdapter {
    private val pkg = requireNotNull(manifest.packageName)

    override fun diagnose(context: Context): PluginStatus =
        if (context.packageManager.getLaunchIntentForPackage(pkg) != null) {
            PluginStatus.READY
        } else {
            PluginStatus.APP_NOT_INSTALLED
        }

    override fun launch(context: Context, gameUri: Uri, platformId: String?): LaunchResult {
        if (diagnose(context) != PluginStatus.READY) return LaunchResult.FAILED
        val intent = Intent(manifest.launchAction ?: Intent.ACTION_VIEW).apply {
            if (manifest.launchClass != null) component = ComponentName(pkg, manifest.launchClass) else setPackage(pkg)
            setDataAndType(gameUri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(intent) }.isSuccess) return LaunchResult.STARTED_WITH_GAME
        val fallback = context.packageManager.getLaunchIntentForPackage(pkg) ?: return LaunchResult.FAILED
        return if (runCatching { context.startActivity(fallback) }.isSuccess) {
            LaunchResult.OPENED_APP_ONLY
        } else {
            LaunchResult.FAILED
        }
    }
}

/**
 * Core libretro empacotado no APK (jniLibs) e executado dentro do próprio app pelo [EmulatorActivity].
 * Não depende de nenhum emulador externo instalado.
 */
class NativeCoreAdapter(override val manifest: PluginManifest) : EmulatorAdapter {
    private val lib = requireNotNull(manifest.coreLib)

    /**
     * O que decide é o arquivo .so que o Android realmente extraiu para este aparelho: se ele existe e o
     * cabeçalho ELF bate com a arquitetura do aparelho, o core está pronto. O campo "abis" do manifesto
     * só é usado para explicar a ausência do arquivo.
     */
    override fun diagnose(context: Context): PluginStatus {
        val file = File(context.applicationInfo.nativeLibraryDir, lib)
        if (file.exists()) {
            return if (ElfCheck.runsOn(ElfCheck.archOf(file), PluginRegistry.deviceAbis)) {
                PluginStatus.READY
            } else {
                PluginStatus.ABI_INCOMPATIBLE
            }
        }
        return if (manifest.abis.none { it in PluginRegistry.deviceAbis }) {
            PluginStatus.ABI_INCOMPATIBLE
        } else {
            PluginStatus.RUNTIME_UNAVAILABLE
        }
    }

    override fun launch(context: Context, gameUri: Uri, platformId: String?): LaunchResult {
        if (diagnose(context) != PluginStatus.READY) return LaunchResult.FAILED
        val intent = EmulatorActivity.intent(context, lib, gameUri, platformId ?: manifest.platforms.first())
        return if (runCatching { context.startActivity(intent) }.isSuccess) {
            LaunchResult.STARTED_WITH_GAME
        } else {
            LaunchResult.FAILED
        }
    }
}

/** Lê o cabeçalho ELF de um .so para saber se ele é mesmo ARM 32 bits (ARMv7) ou ARM 64 bits (AArch64). */
object ElfCheck {
    enum class Arch { ARM32, ARM64, OTHER, NOT_ELF }

    private const val EM_ARM = 0x28
    private const val EM_AARCH64 = 0xB7

    /** [header] precisa ter pelo menos os 20 primeiros bytes do arquivo. */
    fun archOf(header: ByteArray): Arch {
        if (header.size < 20) return Arch.NOT_ELF
        val magic = header[0].toInt() == 0x7F && header[1].toInt() == 'E'.code &&
            header[2].toInt() == 'L'.code && header[3].toInt() == 'F'.code
        if (!magic) return Arch.NOT_ELF
        val elfClass = header[4].toInt()
        val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
        return when {
            elfClass == 1 && machine == EM_ARM -> Arch.ARM32
            elfClass == 2 && machine == EM_AARCH64 -> Arch.ARM64
            else -> Arch.OTHER
        }
    }

    fun archOf(file: File): Arch = runCatching {
        val header = ByteArray(20)
        DataInputStream(file.inputStream()).use { it.readFully(header) }
        archOf(header)
    }.getOrDefault(Arch.NOT_ELF)

    fun runsOn(arch: Arch, deviceAbis: List<String>): Boolean = when (arch) {
        Arch.ARM32 -> deviceAbis.any { it == "armeabi-v7a" || it == "armeabi" }
        Arch.ARM64 -> "arm64-v8a" in deviceAbis
        else -> false
    }
}

class PluginStore(context: Context) {
    private val prefs = context.getSharedPreferences("plugins", Context.MODE_PRIVATE)

    fun isEnabled(id: String): Boolean = id !in prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun setEnabled(id: String, enabled: Boolean) {
        val disabled = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (enabled) disabled.remove(id) else disabled.add(id)
        prefs.edit().putStringSet(KEY, disabled).apply()
    }

    private companion object {
        const val KEY = "disabled"
    }
}

data class LoadResult(val manifests: List<PluginManifest>, val errors: List<Pair<String, String>>)

object PluginLoader {
    fun load(dir: File): LoadResult {
        val files = dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }.orEmpty()
        return parseAll(files.map { f -> f.name to { f.readText() } })
    }

    fun loadAssets(assets: AssetManager, folder: String): LoadResult {
        val names = runCatching { assets.list(folder) }.getOrNull().orEmpty()
            .filter { it.endsWith(".json") }.sorted()
        return parseAll(names.map { n -> n to { assets.open("$folder/$n").bufferedReader().use { it.readText() } } })
    }

    private fun parseAll(entries: List<Pair<String, () -> String>>): LoadResult {
        val manifests = mutableListOf<PluginManifest>()
        val errors = mutableListOf<Pair<String, String>>()
        for ((name, read) in entries) {
            try {
                manifests += PluginManifestParser.parse(read())
            } catch (e: PluginManifestException) {
                errors += name to (e.message ?: "inválido")
            } catch (e: java.io.IOException) {
                errors += name to "falha de leitura"
            }
        }
        return LoadResult(manifests, errors)
    }
}

class PluginRegistry(manifests: List<PluginManifest>, val rejected: List<Pair<String, String>> = emptyList()) {
    val all: List<PluginManifest> = manifests.distinctBy { it.id }
    private val adapters: List<EmulatorAdapter> = all.map {
        when (it.type) {
            PluginType.EXTERNAL_APP -> ExternalAppAdapter(it)
            PluginType.NATIVE_CORE -> NativeCoreAdapter(it)
        }
    }

    /** Cores nativos vêm primeiro (jogam dentro do app); apps externos ficam como alternativa. */
    fun adaptersFor(platformId: String, store: PluginStore): List<EmulatorAdapter> =
        adapters
            .filter { platformId in it.manifest.platforms && store.isEnabled(it.manifest.id) }
            .sortedBy { if (it.manifest.type == PluginType.NATIVE_CORE) 0 else 1 }

    fun diagnostics(context: Context, store: PluginStore): List<PluginDiagnostic> = all.map { m ->
        val enabled = store.isEnabled(m.id)
        val status = when {
            !enabled -> PluginStatus.DISABLED
            else -> adapters.first { it.manifest.id == m.id }.diagnose(context)
        }
        PluginDiagnostic(m, status, enabled)
    }

    companion object {
        val deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList()

        fun load(context: Context): PluginRegistry {
            val bundled = PluginLoader.loadAssets(context.assets, "plugins")
            val user = PluginLoader.load(File(context.filesDir, "plugins"))
            return PluginRegistry(bundled.manifests + user.manifests, bundled.errors + user.errors)
        }
    }
}
