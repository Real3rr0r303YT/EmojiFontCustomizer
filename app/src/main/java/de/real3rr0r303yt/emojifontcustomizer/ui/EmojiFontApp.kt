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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.real3rr0r303yt.emojifontcustomizer.MainViewModel
import de.real3rr0r303yt.emojifontcustomizer.UiState
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

private enum class Screen(val title: String) { PREVIEW("Preview"), EXPORT("Export"), SYSTEM("System") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiFontApp(viewModel: MainViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Emoji Font Customizer") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Screen.entries.forEachIndexed { index, screen ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(screen.title) })
                }
            }
            when (Screen.entries[tab]) {
                Screen.PREVIEW -> PreviewScreen(ui, viewModel)
                Screen.EXPORT -> ExportScreen(ui, viewModel)
                Screen.SYSTEM -> SystemScreen(ui, shizuku, viewModel)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Backend A: live preview
// ---------------------------------------------------------------------------

@Composable
private fun PreviewScreen(ui: UiState, viewModel: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::importFont)
    }
    val family = ui.selected?.fontFamily ?: FontFamily.Default

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 64.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        fullWidth {
            RouteBanner(viewModel.route(RouteRequest.PREVIEW))
        }
        fullWidth {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fonts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (ui.selected != null) {
                    IconButton(onClick = viewModel::deleteSelected) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete selected font")
                    }
                }
                Button(onClick = { picker.launch(InAppRendererBackend.PICKER_MIME_TYPES) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import .ttf/.otf")
                }
            }
        }
        fullWidth {
            Column {
                FontOption("System default", selected = ui.selected == null) { viewModel.select(null) }
                ui.fonts.forEach { font ->
                    FontOption(font.name, selected = ui.selected?.file == font.file, family = font.fontFamily) {
                        viewModel.select(font)
                    }
                }
            }
        }
        fullWidth {
            OutlinedTextField(
                value = ui.sampleText,
                onValueChange = viewModel::setSampleText,
                label = { Text("Sample text") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        fullWidth {
            Column {
                Text("Size: ${ui.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelLarge)
                Slider(value = ui.fontSizeSp, onValueChange = viewModel::setFontSize, valueRange = 12f..64f)
            }
        }
        fullWidth {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    ui.sampleText,
                    fontFamily = family,
                    fontSize = ui.fontSizeSp.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        fullWidth {
            Text("Emoji grid", style = MaterialTheme.typography.titleMedium)
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
private fun FontOption(
    label: String,
    selected: Boolean,
    family: FontFamily = FontFamily.Default,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text("$label  Aa 😀", fontFamily = family)
    }
}

// ---------------------------------------------------------------------------
// Backend B: font pack export / import
// ---------------------------------------------------------------------------

@Composable
private fun ExportScreen(ui: UiState, viewModel: MainViewModel) {
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ThemeExportBackend.MIME_TYPE)) { uri ->
            uri?.let(viewModel::exportPack)
        }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPack)
    }
    val exportName = (ui.selected?.name ?: "emoji-fonts") + "-fontpack.zip"

    ScrollColumn {
        RouteBanner(viewModel.route(RouteRequest.EXPORT_PACK))
        Text(
            "Bundles fonts into a portable pack (font-pack.json + fonts/) for OEM theme " +
                "stores, overlay templates or sharing with another device.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (ui.selected != null) "Exports: ${ui.selected.name}" else "Exports: all ${ui.fonts.size} imported fonts",
            style = MaterialTheme.typography.labelLarge,
        )
        Button(onClick = { exportLauncher.launch(exportName) }, enabled = ui.fonts.isNotEmpty()) {
            Text("Export font pack")
        }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf(ThemeExportBackend.MIME_TYPE)) }) {
            Text("Import font pack")
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
// Shared
// ---------------------------------------------------------------------------

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
private fun ScrollColumn(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) { content() }
}
