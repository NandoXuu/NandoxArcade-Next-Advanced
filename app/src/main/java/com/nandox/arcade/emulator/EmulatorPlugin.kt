package com.nandox.arcade.emulator

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import android.os.Process
import com.nandox.arcade.core.Platforms
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
    /** Menor vem antes quando há mais de um core para a mesma plataforma. */
    val priority: Int = 100,
    /** Versão mínima de OpenGL ES exigida pelo core (ex.: 3.0). */
    val minGlEs: Double? = null,
    /** Preenchido = plugin conhecido, mas desligado de propósito; o texto explica o motivo ao usuário. */
    val disabledReason: String? = null,
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
        val disabledReason = o.optString("disabledReason").takeIf { it.isNotBlank() }
        val coreLib = o.optString("coreLib").takeIf { it.isNotBlank() }
        // Um plugin desligado de propósito só precisa dizer quem é e por que está desligado.
        if (disabledReason == null) {
            if (type == PluginType.EXTERNAL_APP && pkg == null) {
                throw PluginManifestException("packageName obrigatório para app externo")
            }
            if (type == PluginType.NATIVE_CORE && abis.isEmpty()) {
                throw PluginManifestException("abis obrigatório para core nativo")
            }
            if (type == PluginType.NATIVE_CORE && coreLib == null) {
                throw PluginManifestException("coreLib obrigatório para core nativo")
            }
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
            priority = o.optInt("priority", 100),
            minGlEs = if (o.has("minGlEs")) o.optDouble("minGlEs").takeIf { !it.isNaN() } else null,
            disabledReason = disabledReason,
        )
    }
}

enum class PluginStatus {
    READY,
    APP_NOT_INSTALLED,

    /** O binário (ou a ABI declarada) não é da arquitetura que este aparelho executa. */
    ABI_INCOMPATIBLE,

    /** A ABI serve, mas o arquivo do core não está no APK (ou está corrompido). */
    RUNTIME_UNAVAILABLE,
    GPU_INCOMPATIBLE,

    /** Desligado de propósito no manifesto (disabledReason), por exigir hardware que a TV não tem. */
    UNSUPPORTED,
    DISABLED,
}

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
 *
 * O diagnóstico não confia no nome do arquivo: lê o cabeçalho ELF do .so que o Android extraiu para
 * nativeLibraryDir e confere se é da arquitetura do processo (ARM 32 bits x ARM64) e de uma ABI
 * declarada no manifesto. A ABI do aparelho vem de Build.SUPPORTED_ABIS, nunca é presumida.
 */
class NativeCoreAdapter(override val manifest: PluginManifest) : EmulatorAdapter {
    private val lib = requireNotNull(manifest.coreLib)

    // O resultado só depende de arquivos do APK instalado, que não mudam enquanto o app roda.
    @Volatile private var cached: PluginStatus? = null

    override fun diagnose(context: Context): PluginStatus =
        cached ?: compute(context).also { cached = it }

    private fun compute(context: Context): PluginStatus {
        val file = File(context.applicationInfo.nativeLibraryDir, lib)
        val verdict = CoreCompatibility.check(
            declaredAbis = manifest.abis,
            deviceAbis = PluginRegistry.deviceAbis,
            processAbis = PluginRegistry.processAbis,
            libraryExists = file.isFile,
            elf = if (file.isFile) Elf.read(file) else null,
        )
        return when (verdict) {
            CoreCheck.ABI_NOT_SUPPORTED, CoreCheck.LIBRARY_WRONG_ARCH -> PluginStatus.ABI_INCOMPATIBLE
            CoreCheck.LIBRARY_MISSING, CoreCheck.LIBRARY_INVALID -> PluginStatus.RUNTIME_UNAVAILABLE
            CoreCheck.OK ->
                if (GlEs.satisfies(PluginRegistry.glEsVersion(context), manifest.minGlEs)) {
                    PluginStatus.READY
                } else {
                    PluginStatus.GPU_INCOMPATIBLE
                }
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

/** Plugin conhecido mas desligado de propósito (manifesto com disabledReason): nunca abre jogos. */
class UnsupportedAdapter(override val manifest: PluginManifest) : EmulatorAdapter {
    override fun diagnose(context: Context): PluginStatus = PluginStatus.UNSUPPORTED
    override fun launch(context: Context, gameUri: Uri, platformId: String?): LaunchResult = LaunchResult.FAILED
}

class PluginStore(context: Context) {
    private val prefs = context.getSharedPreferences("plugins", Context.MODE_PRIVATE)

    fun isEnabled(id: String): Boolean = id !in prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun setEnabled(id: String, enabled: Boolean) {
        val disabled = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (enabled) disabled.remove(id) else disabled.add(id)
        prefs.edit().putStringSet(KEY, disabled).apply()
    }

    /** Plugin que o usuário escolheu como principal para a plataforma (null = ordem padrão). */
    fun preferredFor(platformId: String): String? = prefs.getString(PREFERRED_PREFIX + platformId, null)

    fun setPreferred(platformId: String, pluginId: String?) {
        prefs.edit().apply {
            if (pluginId == null) remove(PREFERRED_PREFIX + platformId) else putString(PREFERRED_PREFIX + platformId, pluginId)
        }.apply()
    }

    private companion object {
        const val KEY = "disabled"
        const val PREFERRED_PREFIX = "preferred_"
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
        when {
            it.disabledReason != null -> UnsupportedAdapter(it)
            it.type == PluginType.EXTERNAL_APP -> ExternalAppAdapter(it)
            else -> NativeCoreAdapter(it)
        }
    }

    /**
     * Plugins ativos da plataforma, na ordem em que serão tentados: o principal escolhido pelo usuário,
     * depois cores nativos (jogam dentro do app) e por fim apps externos. Plugins desligados de
     * propósito (disabledReason) e os que o usuário desativou ficam de fora.
     */
    fun adaptersFor(platformId: String, store: PluginStore): List<EmulatorAdapter> {
        val enabled = adapters.filter {
            platformId in it.manifest.platforms && it.manifest.disabledReason == null && store.isEnabled(it.manifest.id)
        }
        val order = PluginOrdering.order(enabled.map { it.manifest }, store.preferredFor(platformId)).map { it.id }
        return enabled.sortedBy { order.indexOf(it.manifest.id) }
    }

    /** Subconjunto de [adaptersFor] que realmente consegue abrir jogos neste aparelho agora. */
    fun readyFor(context: Context, platformId: String, store: PluginStore): List<EmulatorAdapter> =
        adaptersFor(platformId, store).filter { it.diagnose(context) == PluginStatus.READY }

    /** Texto do cartão da plataforma: o núcleo que vai abrir os jogos, ou por que não há nenhum. */
    fun summaryFor(context: Context, platformId: String, store: PluginStore): String {
        readyFor(context, platformId, store).firstOrNull()?.let { return it.manifest.name }
        val known = adapters.filter { platformId in it.manifest.platforms }
        return when {
            known.isEmpty() -> "Sem emulador compatível"
            known.all { it.manifest.disabledReason != null } -> "Indisponível neste aparelho"
            else -> "Núcleo indisponível"
        }
    }

    /** Por que nenhum plugin consegue abrir jogos desta plataforma (para mostrar ao usuário). */
    fun explainNoneReady(context: Context, platformId: String, store: PluginStore): String {
        val platformName = Platforms.byId(platformId)?.name ?: platformId
        val problems = mutableListOf<Pair<String, String>>()
        for (adapter in adapters.filter { platformId in it.manifest.platforms }) {
            val status = when {
                adapter.manifest.disabledReason != null -> PluginStatus.UNSUPPORTED
                !store.isEnabled(adapter.manifest.id) -> PluginStatus.DISABLED
                else -> adapter.diagnose(context)
            }
            if (status != PluginStatus.READY) {
                problems += adapter.manifest.name to LaunchAdvice.statusReason(adapter.manifest, status, deviceAbis)
            }
        }
        return LaunchAdvice.noneReady(platformName, problems)
    }

    fun diagnostics(context: Context, store: PluginStore): List<PluginDiagnostic> = all.map { m ->
        val enabled = store.isEnabled(m.id)
        val status = when {
            m.disabledReason != null -> PluginStatus.UNSUPPORTED
            !enabled -> PluginStatus.DISABLED
            else -> adapters.first { it.manifest.id == m.id }.diagnose(context)
        }
        PluginDiagnostic(m, status, enabled && m.disabledReason == null)
    }

    companion object {
        /** Tudo o que o aparelho diz executar (ex.: "armeabi-v7a, armeabi" numa TV de 32 bits). */
        val deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList()

        /** ABIs do processo atual: se ele é de 32 ou de 64 bits decide qual família de .so pode ser carregada. */
        val processAbis: List<String> =
            (if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS).toList()

        fun glEsVersion(context: Context): Int =
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.deviceConfigurationInfo?.reqGlEsVersion ?: 0

        fun load(context: Context): PluginRegistry {
            val bundled = PluginLoader.loadAssets(context.assets, "plugins")
            val user = PluginLoader.load(File(context.filesDir, "plugins"))
            return PluginRegistry(bundled.manifests + user.manifests, bundled.errors + user.errors)
        }
    }
}
