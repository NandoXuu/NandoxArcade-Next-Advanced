package com.nandox.arcade.ui

import android.os.Build
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nandox.arcade.controller.ControlsController
import com.nandox.arcade.core.PlatformClassifier
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.emulator.PluginStatus
import com.nandox.arcade.emulator.PluginStore
import kotlin.math.hypot

@Composable
fun ArcadeApp(controls: ControlsController) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val registry = remember { PluginRegistry.load(context) }
    val scope = rememberCoroutineScope()
    val fx = remember { ScreenFx(scope) }
    val water = remember { if (Build.VERSION.SDK_INT >= 33) WaterRenderer() else null }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> fx.onPause()
                Lifecycle.Event.ON_RESUME -> fx.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun go(route: String) = fx.dip { nav.navigate(route) }
    fun play(route: String) = fx.dive { nav.navigate(route); true }

    CompositionLocalProvider(LocalFx provides fx) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = fx.water.value
                        renderEffect = if (Build.VERSION.SDK_INT >= 33 && water != null && t > 0f && t < 1f) water.effect(size.width, size.height, t) else null
                    }
                    .background(MaterialTheme.colorScheme.background)
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                listOf(Color(0x553D2A8C), Color.Transparent),
                                center = Offset(size.width, 0f),
                                radius = size.width * 0.6f,
                            ),
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                listOf(Color(0x3D3D2A8C), Color.Transparent),
                                center = Offset(0f, size.height),
                                radius = size.width * 0.5f,
                            ),
                        )
                    }
                    .safeDrawingPadding(),
            ) {
                NavHost(
                    nav,
                    startDestination = "home",
                    enterTransition = { fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 28 } },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(150)) },
                ) {
                    composable("home") {
                        HomeScreen(
                            registry = registry,
                            onOpenLibrary = { go("library/$it") },
                            onOpenGame = { play("game/$it") },
                            onCatalog = { nav.navigate("catalog") },
                            onSaves = { nav.navigate("saves") },
                            onBrowser = { nav.navigate("browser") },
                            onDownloads = { nav.navigate("downloads") },
                            onPlugins = { nav.navigate("plugins") },
                            onSettings = { nav.navigate("settings") },
                            onRecents = { go("library/$LIB_RECENT") },
                            onAllGames = { go("library/$LIB_ALL") },
                        )
                    }
                    composable(
                        "library/{platform}",
                        arguments = listOf(navArgument("platform") { type = NavType.StringType }),
                    ) {
                        LibraryScreen(
                            platformId = it.arguments?.getString("platform").orEmpty(),
                            onOpenGame = { id -> play("game/$id") },
                            onOpenUnknown = { go("library/${PlatformClassifier.UNKNOWN}") },
                            onSources = { nav.navigate("sources") },
                            onBack = { fx.dip { nav.popBackStack() } },
                        )
                    }
                    composable(
                        "game/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) {
                        GameScreen(
                            gameId = it.arguments?.getLong("id") ?: 0L,
                            registry = registry,
                            onSettings = { id -> nav.navigate("game-settings/$id") },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(
                        "game-settings/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) {
                        GameSettingsScreen(
                            gameId = it.arguments?.getLong("id") ?: 0L,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("saves") { GlobalStatesScreen(registry) }
                    composable("save-files") { SaveManagerScreen(registry) }
                    composable("catalog") { CatalogScreen(registry) }
                    composable("browser") { BrowserScreen(onExit = { nav.popBackStack() }, onDownloads = { nav.navigate("downloads") }) }
                    composable("downloads") { DownloadsScreen(registry) }
                    composable("sources") { SourcesScreen() }
                    composable("plugins") { PluginsScreen(registry) }
                    composable("settings") {
                        AppSettingsScreen(
                            onControls = { nav.navigate("controls") },
                            onPlugins = { nav.navigate("plugins") },
                            onSources = { nav.navigate("sources") },
                            onSaves = { nav.navigate("save-files") },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("controls") { ControlsScreen(controls) }
                }
            }

            if (water == null) {
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val t = fx.water.value
                        if (t > 0f && t < 1f) {
                            val reach = hypot(size.width, size.height) / 2f
                            for (i in 0..2) {
                                val r = (t * 1.15f - i * 0.12f) * reach
                                if (r > 0f) {
                                    drawCircle(
                                        Color(0xFFBFE4FF).copy(alpha = (1f - t) * 0.35f / (i + 1)),
                                        radius = r,
                                        center = center,
                                        style = Stroke(width = 6.dp.toPx() * (i + 1)),
                                    )
                                }
                            }
                        }
                    },
                )
            }

            Box(
                Modifier.fillMaxSize().drawBehind {
                    val a = fx.dark.value
                    if (a > 0f) drawRect(Color.Black.copy(alpha = a))
                },
            )

            if (fx.busy) {
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PluginsScreen(registry: PluginRegistry) {
    val context = LocalContext.current
    val store = remember { PluginStore(context) }
    var diagnostics by remember { mutableStateOf(registry.diagnostics(context, store)) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Plugins de emulação", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "ABI do aparelho: ${PluginRegistry.deviceAbis.joinToString()}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(diagnostics, key = { it.manifest.id }) { d ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    d.manifest.platforms.firstOrNull()?.let { platformId ->
                        PlatformIcon(platformId, Modifier.size(56.dp, 40.dp).padding(end = 12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(d.manifest.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            d.manifest.platforms.mapNotNull { Platforms.byId(it)?.name }.joinToString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        val status = when (d.status) {
                            PluginStatus.READY -> "App instalado"
                            PluginStatus.APP_NOT_INSTALLED -> "App não instalado"
                            PluginStatus.ABI_INCOMPATIBLE -> "ABI incompatível"
                            PluginStatus.RUNTIME_UNAVAILABLE -> "Core não incluído no APK"
                            PluginStatus.DISABLED -> "Desativado"
                        }
                        val validation = if (d.manifest.validated) "validado" else "não validado"
                        Text("$status · $validation", fontSize = 13.sp)
                    }
                    Switch(
                        checked = d.enabled,
                        onCheckedChange = {
                            store.setEnabled(d.manifest.id, it)
                            diagnostics = registry.diagnostics(context, store)
                        },
                    )
                }
            }
            items(registry.rejected, key = { it.first }) { (file, reason) ->
                Text(
                    "Manifesto rejeitado: $file ($reason)",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
