/*
 * Emoji Font Customizer
 * Copyright (C) 2026 Real3rr0r303YT
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package de.real3rr0r303yt.emojifontcustomizer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.real3rr0r303yt.emojifontcustomizer.BuildConfig
import de.real3rr0r303yt.emojifontcustomizer.MainViewModel
import de.real3rr0r303yt.emojifontcustomizer.UiState
import de.real3rr0r303yt.emojifontcustomizer.backend.a.FontKind
import de.real3rr0r303yt.emojifontcustomizer.backend.a.ImportedFont
import de.real3rr0r303yt.emojifontcustomizer.backend.a.InAppRendererBackend
import de.real3rr0r303yt.emojifontcustomizer.backend.b.ThemeExportBackend
import de.real3rr0r303yt.emojifontcustomizer.backend.c.ShizukuBackend
import de.real3rr0r303yt.emojifontcustomizer.backend.c.ShizukuState
import de.real3rr0r303yt.emojifontcustomizer.router.RouteDecision
import de.real3rr0r303yt.emojifontcustomizer.router.RouteRequest

private val SAMPLE_EMOJI = listOf(
    "😀", "😂", "🥹", "😍", "🤩", "😎", "🤔", "😴", "🥳", "😭", "😡", "🤯",
    "👍", "👏", "🙏", "💪", "👀", "🫶", "❤️", "🔥", "✨", "🎉", "💯", "⭐",
    "🐶", "🐱", "🦊", "🐼", "🐸", "🦄", "🌈", "🌸", "🍕", "🍔", "☕", "🍩",
    "⚽", "🎮", "🎧", "📱", "💻", "🚀", "✈️", "🏠", "🌍", "🌙", "☀️", "❄️",
)

/** Free emoji sets whose licenses allow redistribution, linked to their official releases. */
private data class EmojiSource(val name: String, val license: String, val url: String)

private val FREE_EMOJI_SOURCES = listOf(
    EmojiSource("Noto Color Emoji (Google)", "OFL-1.1", "https://github.com/googlefonts/noto-emoji/releases"),
    EmojiSource("Twemoji (Mozilla COLR build)", "CC-BY-4.0", "https://github.com/mozilla/twemoji-colr/releases"),
    EmojiSource("OpenMoji", "CC-BY-SA-4.0", "https://github.com/hfg-gmuend/openmoji/releases"),
)

private enum class Screen(val title: String) {
    FONTS("Fonts"),
    EMOJI("Emoji"),
    EXPORT("Export"),
    SYSTEM("System"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiFontApp(viewModel: MainViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    BackHandler(enabled = showAbout) { showAbout = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showAbout) "Open Source" else "Emoji Font Customizer") },
                navigationIcon = {
                    if (showAbout) {
                        IconButton(onClick = { showAbout = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (BuildConfig.DONATION_URL.isNotBlank()) {
                        IconButton(onClick = { uriHandler.openUri(BuildConfig.DONATION_URL) }) {
                            Icon(Icons.Filled.Favorite, contentDescription = "Donate")
                        }
                    }
                    if (!showAbout) {
                        IconButton(onClick = { showAbout = true }) {
                            Icon(Icons.Filled.Info, contentDescription = "Open source & license")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (showAbout) {
                AboutScreen()
                return@Column
            }
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                Screen.entries.forEachIndexed { index, screen ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(screen.title) })
                }
            }
            when (Screen.entries[tab]) {
                Screen.FONTS -> FontsScreen(ui, viewModel)
                Screen.EMOJI -> EmojiScreen(ui, viewModel)
                Screen.EXPORT -> ExportScreen(ui, viewModel)
                Screen.SYSTEM -> SystemScreen(ui, shizuku, viewModel)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Backend A: text fonts
// ---------------------------------------------------------------------------

@Composable
private fun FontsScreen(ui: UiState, viewModel: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::importFont)
    }

    ScrollColumn {
        RouteBanner(viewModel.route(RouteRequest.PREVIEW))
        SectionHeader("Text fonts") {
            Button(onClick = { picker.launch(InAppRendererBackend.PICKER_MIME_TYPES) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import font")
            }
        }
        FontList(
            items = ui.fonts,
            selected = ui.selectedFont,
            defaultLabel = "System default",
            sample = "Aa Bb 123",
            onSelect = viewModel::selectFont,
            onDelete = viewModel::delete,
        )
        OutlinedTextField(
            value = ui.sampleText,
            onValueChange = viewModel::setSampleText,
            label = { Text("Sample text") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Size: ${ui.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelLarge)
        Slider(value = ui.fontSizeSp, onValueChange = viewModel::setFontSize, valueRange = 12f..64f)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(ui.sampleText, fontFamily = ui.previewFamily, fontSize = ui.fontSizeSp.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Font: ${ui.selectedFont?.name ?: "System"} · Emoji: ${ui.selectedEmoji?.name ?: "System"}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            "Emoji fonts imported here are detected automatically and moved to the Emoji tab.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------------------------------------------------------------------------
// Backend A: emoji packs
// ---------------------------------------------------------------------------

@Composable
private fun EmojiScreen(ui: UiState, viewModel: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::importFont)
    }
    val uriHandler = LocalUriHandler.current
    val family = ui.selectedEmoji?.fontFamily ?: FontFamily.Default

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 64.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        fullWidth {
            SectionHeader("Emoji packs") {
                Button(onClick = { picker.launch(InAppRendererBackend.PICKER_MIME_TYPES) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import emoji")
                }
            }
        }
        fullWidth {
            FontList(
                items = ui.emojiPacks,
                selected = ui.selectedEmoji,
                defaultLabel = "System emoji",
                sample = "😀 👍 ❤️",
                onSelect = viewModel::selectEmoji,
                onDelete = viewModel::delete,
            )
        }
        fullWidth {
            InfoCard("iOS-style emoji") {
                Text(
                    "Apple Color Emoji is proprietary, so it cannot ship with this GPL app. " +
                        "If you have an iOS-style emoji font in an Android format (CBDT/COLR .ttf), " +
                        "import it above – it is recognised as an emoji pack and can be previewed, " +
                        "exported or applied system-wide via the Export tab. Apple's original " +
                        ".ttc (sbix format) cannot be rendered by Android.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        fullWidth {
            InfoCard("Free emoji packs") {
                FREE_EMOJI_SOURCES.forEach { source ->
                    TextButton(onClick = { uriHandler.openUri(source.url) }) {
                        Text("${source.name} · ${source.license}")
                    }
                }
            }
        }
        fullWidth {
            Text("Preview: ${ui.selectedEmoji?.name ?: "System emoji"}", style = MaterialTheme.typography.titleMedium)
        }
        items(SAMPLE_EMOJI) { emoji ->
            Card(Modifier.aspectRatio(1f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emoji, fontFamily = family, fontSize = 30.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun FontList(
    items: List<ImportedFont>,
    selected: ImportedFont?,
    defaultLabel: String,
    sample: String,
    onSelect: (ImportedFont?) -> Unit,
    onDelete: (ImportedFont) -> Unit,
) {
    Column {
        FontOption(defaultLabel, sample, selected = selected == null, onClick = { onSelect(null) })
        items.forEach { font ->
            FontOption(
                label = font.name,
                sample = sample,
                selected = selected?.file == font.file,
                family = font.fontFamily,
                warning = font.warning,
                onClick = { onSelect(font) },
                onDelete = { onDelete(font) },
            )
        }
    }
}

@Composable
private fun FontOption(
    label: String,
    sample: String,
    selected: Boolean,
    family: FontFamily = FontFamily.Default,
    warning: String? = null,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(sample, fontFamily = family, style = MaterialTheme.typography.bodyMedium)
            if (warning != null) {
                Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete $label")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Backend B: packs & modules
// ---------------------------------------------------------------------------

@Composable
private fun ExportScreen(ui: UiState, viewModel: MainViewModel) {
    var exportKind by rememberSaveable { mutableStateOf(FontKind.TEXT) }
    val packLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ThemeExportBackend.MIME_TYPE)) { uri ->
            uri?.let { viewModel.exportPack(exportKind, it) }
        }
    val moduleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ThemeExportBackend.MIME_TYPE)) { uri ->
            uri?.let(viewModel::exportMagiskModule)
        }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPack)
    }
    val emoji = ui.selectedEmoji

    ScrollColumn {
        RouteBanner(viewModel.route(RouteRequest.EXPORT_PACK))
        Text(
            "Packs (font-pack.json + fonts/) work with OEM theme stores, overlay templates " +
                "or another device running this app.",
            style = MaterialTheme.typography.bodyMedium,
        )

        InfoCard("Font pack") {
            Text(
                ui.selectedFont?.let { "Exports: ${it.name}" } ?: "Exports: all ${ui.fonts.size} text fonts",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    exportKind = FontKind.TEXT
                    packLauncher.launch((ui.selectedFont?.name ?: "fonts") + "-fontpack.zip")
                },
                enabled = ui.fonts.isNotEmpty(),
            ) { Text("Export font pack") }
        }

        InfoCard("Emoji pack") {
            Text(
                emoji?.let { "Exports: ${it.name}" } ?: "Exports: all ${ui.emojiPacks.size} emoji packs",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    exportKind = FontKind.EMOJI
                    packLauncher.launch((emoji?.name ?: "emoji") + "-emojipack.zip")
                },
                enabled = ui.emojiPacks.isNotEmpty(),
            ) { Text("Export emoji pack") }
        }

        InfoCard("Apply emoji system-wide (root)") {
            RouteBanner(viewModel.route(RouteRequest.APPLY_EMOJI_SYSTEM))
            Text(
                "Builds a Magisk / KernelSU / APatch module that replaces NotoColorEmoji.ttf " +
                    "(and Samsung/HTC emoji files if present), removes emoji fonts that Google Play " +
                    "updates put in /data/fonts (they override /system on Android 12+) and refreshes " +
                    "the Play services emoji cache used by Gboard and many messengers. " +
                    "Flash it in your root manager and reboot. Apps that bundle their own emoji " +
                    "(e.g. WhatsApp) may keep theirs. Shizuku cannot do this: its shell user " +
                    "cannot write to /system or /data/fonts.",
                style = MaterialTheme.typography.bodySmall,
            )
            emoji?.warning?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { moduleLauncher.launch("${emoji?.name ?: "emoji"}-magisk-module.zip") },
                enabled = emoji != null && emoji.info.androidRenderable,
            ) { Text(if (emoji == null) "Select an emoji pack first" else "Export Magisk module") }
        }

        OutlinedButton(onClick = { importLauncher.launch(arrayOf(ThemeExportBackend.MIME_TYPE)) }) {
            Text("Import font / emoji pack")
        }
    }
}

// ---------------------------------------------------------------------------
// Backend C: Shizuku & shell overlays
// ---------------------------------------------------------------------------

@Composable
private fun SystemScreen(ui: UiState, shizuku: ShizukuState, viewModel: MainViewModel) {
    val context = LocalContext.current

    ScrollColumn {
        RouteBanner(viewModel.route(RouteRequest.APPLY_SYSTEM))
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Shizuku", style = MaterialTheme.typography.titleMedium)
                    Text(describe(shizuku), style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = viewModel::refreshShizuku) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Shizuku status")
                }
            }
        }

        when (shizuku) {
            ShizukuState.NotRunning, ShizukuState.Unsupported -> OutlinedButton(onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage(ShizukuBackend.SHIZUKU_PACKAGE)
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Open / get Shizuku") }

            is ShizukuState.PermissionRequired -> Button(onClick = viewModel::requestShizukuPermission) {
                Text("Grant Shizuku access")
            }

            is ShizukuState.Ready -> OverlayControls(ui, viewModel)
        }
        Text(
            "Font overlays switch the system text font. System emoji cannot be changed via overlays – " +
                "use the Magisk module export in the Export tab.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun OverlayControls(ui: UiState, viewModel: MainViewModel) {
    OutlinedTextField(
        value = ui.overlayPackage,
        onValueChange = viewModel::setOverlayPackage,
        label = { Text("Overlay package") },
        placeholder = { Text("com.android.theme.font.notoserifsource") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::commandList, enabled = !ui.running) { Text("List fonts") }
        OutlinedButton(onClick = viewModel::commandFontDump, enabled = !ui.running) { Text("cmd font dump") }
    }
    InfoCard("System emoji not changing?") {
        Text(
            "Runs a read-only check. If NotoColorEmoji.ttf in /system/fonts still has the stock size, " +
                "the root module is not mounted (on KernelSU/APatch install a mount/overlay module). " +
                "If an emoji font is listed under updated fonts, a Google Play update overrides the " +
                "system font – the module removes it on the next boot.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = viewModel::commandEmojiDiagnostics, enabled = !ui.running) { Text("Diagnose emoji") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::commandEnable, enabled = !ui.running && ui.overlayPackage.isNotEmpty()) {
            Text("Enable")
        }
        OutlinedButton(onClick = viewModel::commandDisable, enabled = !ui.running && ui.overlayPackage.isNotEmpty()) {
            Text("Disable")
        }
    }
    if (ui.running) LinearProgressIndicator(Modifier.fillMaxWidth())

    ui.log.forEach { result ->
        HorizontalDivider()
        SelectionContainer {
            Column {
                Text(
                    "$ ${result.command}  →  exit ${result.exitCode}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (result.stdout.isNotEmpty()) {
                    Text(result.stdout, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                if (result.stderr.isNotEmpty()) {
                    Text(
                        result.stderr,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun describe(state: ShizukuState): String = when (state) {
    ShizukuState.NotRunning -> "Not running – install and start Shizuku to apply fonts system-wide."
    ShizukuState.Unsupported -> "Shizuku version is too old (API v11+ required)."
    is ShizukuState.PermissionRequired ->
        if (state.permanentlyDenied) "Access denied – allow this app in the Shizuku app." else "Running, access not granted yet."
    is ShizukuState.Ready -> "Active · server v${state.serverVersion} · ${state.privilege}"
}

// ---------------------------------------------------------------------------
// Open source / GPL-3.0
// ---------------------------------------------------------------------------

private val THIRD_PARTY = listOf(
    "AndroidX & Jetpack Compose" to "Apache-2.0",
    "Kotlin & kotlinx.coroutines" to "Apache-2.0",
    "Shizuku API (RikkaApps)" to "Apache-2.0",
)

@Composable
private fun AboutScreen() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var licenseText by remember { mutableStateOf<String?>(null) }

    ScrollColumn {
        Text("Emoji Font Customizer", style = MaterialTheme.typography.headlineSmall)
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge)

        InfoCard("Free & open source · GPL-3.0") {
            Text(
                "Copyright (C) 2026 Real3rr0r303YT\n\n" +
                    "This program is free software: you can redistribute it and/or modify it under " +
                    "the terms of the GNU General Public License as published by the Free Software " +
                    "Foundation, either version 3 of the License, or (at your option) any later version.\n\n" +
                    "This program is distributed in the hope that it will be useful, but WITHOUT ANY " +
                    "WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A " +
                    "PARTICULAR PURPOSE. See the GNU General Public License for more details.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { uriHandler.openUri(BuildConfig.SOURCE_URL) }) { Text("Source code") }
                OutlinedButton(onClick = {
                    licenseText = if (licenseText == null) {
                        runCatching { context.assets.open("LICENSE").bufferedReader().use { it.readText() } }
                            .getOrElse { "https://www.gnu.org/licenses/gpl-3.0.html" }
                    } else {
                        null
                    }
                }) { Text(if (licenseText == null) "Full license" else "Hide license") }
            }
        }

        if (BuildConfig.DONATION_URL.isNotBlank()) {
            InfoCard("Support development") {
                Text(
                    "This app is free and stays free. If it helps you, a small donation keeps it going.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = { uriHandler.openUri(BuildConfig.DONATION_URL) }) {
                    Icon(Icons.Filled.Favorite, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Donate")
                }
            }
        }

        InfoCard("Third-party components") {
            THIRD_PARTY.forEach { (name, license) ->
                Text("$name · $license", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Imported fonts and emoji packs keep their own licenses.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        licenseText?.let {
            SelectionContainer {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun RouteBanner(decision: RouteDecision) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (decision.fallback) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(decision.backend.label, style = MaterialTheme.typography.labelLarge)
            Text(decision.reason, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScrollColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        content = content,
    )
}
