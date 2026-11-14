package com.nandox.arcade.ui

import android.os.Build
import android.content.IntentFilter
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.nandox.arcade.emulator.PlayHistory
import com.nandox.arcade.emulator.RomStager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import com.nandox.arcade.controller.ControlsController
import com.nandox.arcade.core.PlatformClassifier
import com.nandox.arcade.saves.SaveSettings
import com.nandox.arcade.scanner.GameScanner
import com.nandox.arcade.scanner.SourceChecker
import com.nandox.arcade.scanner.SourceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nandox.arcade.BuildConfig
import com.nandox.arcade.core.Platforms
import com.nandox.arcade.data.ArcadeDb
import com.nandox.arcade.data.GameEntity
import com.nandox.arcade.launcher.GameLauncher
import com.nandox.arcade.emulator.LaunchResult
import com.nandox.arcade.emulator.PluginRegistry
import com.nandox.arcade.emulator.PluginStore
import com.nandox.arcade.emulator.PluginStatus

@Composable
fun ArcadeApp(controls: ControlsController) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val registry = remember { PluginRegistry.load(context) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
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
                    onOpenLibrary = { nav.navigate("library/$it") },
                    onOpenGame = { nav.navigate("game/$it") },
                    onCatalog = { nav.navigate("catalog") },
                    onSaves = { nav.navigate("saves") },
                    onBrowser = { nav.navigate("browser") },
                    onDownloads = { nav.navigate("downloads") },
                    onPlugins = { nav.navigate("plugins") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable(
                "library/{platform}",
                arguments = listOf(navArgument("platform") { type = NavType.StringType }),
            ) {
                LibraryScreen(
                    platformId = it.arguments?.getString("platform").orEmpty(),
                    onOpenGame = { id -> nav.navigate("game/$id") },
                    onOpenUnknown = { nav.navigate("library/${PlatformClassifier.UNKNOWN}") },
                    onSources = { nav.navigate("sources") },
                    onBack = { nav.popBackStack() },
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
