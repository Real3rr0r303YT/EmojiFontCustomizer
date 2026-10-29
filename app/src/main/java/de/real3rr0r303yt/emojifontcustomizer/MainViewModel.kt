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
package de.real3rr0r303yt.emojifontcustomizer

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.real3rr0r303yt.emojifontcustomizer.backend.a.FontKind
import de.real3rr0r303yt.emojifontcustomizer.backend.a.ImportedFont
import de.real3rr0r303yt.emojifontcustomizer.backend.c.OverlayCommands
import de.real3rr0r303yt.emojifontcustomizer.backend.c.ShellResult
import de.real3rr0r303yt.emojifontcustomizer.backend.c.ShizukuState
import de.real3rr0r303yt.emojifontcustomizer.router.BackendRouter
import de.real3rr0r303yt.emojifontcustomizer.router.RouteDecision
import de.real3rr0r303yt.emojifontcustomizer.router.RouteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val fonts: List<ImportedFont> = emptyList(),
    val emojiPacks: List<ImportedFont> = emptyList(),
    val selectedFont: ImportedFont? = null,
    val selectedEmoji: ImportedFont? = null,
    /** Selected text font with the selected emoji pack as fallback. */
    val previewFamily: FontFamily = FontFamily.Default,
    val sampleText: String = "The quick brown fox 🦊 jumps over the lazy dog 🐶",
    val fontSizeSp: Float = 28f,
    val overlayPackage: String = "",
    val log: List<ShellResult> = emptyList(),
    val running: Boolean = false,
    val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val router = BackendRouter(application)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val shizukuState: StateFlow<ShizukuState> = router.shizukuState

    private var previewJob: Job? = null

    init {
        router.start()
        background(success = null) { reload() }
    }

    override fun onCleared() {
        router.stop()
    }

    fun route(request: RouteRequest): RouteDecision = router.route(request)

    fun selectFont(font: ImportedFont?) {
        _ui.update { it.copy(selectedFont = font) }
        updatePreview()
    }

    fun selectEmoji(pack: ImportedFont?) {
        _ui.update { it.copy(selectedEmoji = pack) }
        updatePreview()
    }

    fun setSampleText(text: String) = _ui.update { it.copy(sampleText = text) }

    fun setFontSize(sp: Float) = _ui.update { it.copy(fontSizeSp = sp) }

    fun setOverlayPackage(pkg: String) = _ui.update { it.copy(overlayPackage = pkg.trim()) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    fun showMessage(message: String) = _ui.update { it.copy(message = message) }

    /** Imports a font file; emoji packs are detected automatically. */
    fun importFont(uri: Uri) = background(success = null) {
        val font = router.renderer.import(uri)
        reload(select = font)
        val what = if (font.kind == FontKind.EMOJI) "Emoji pack" else "Font"
        "$what imported: ${font.name}" + (font.warning?.let { "\n$it" } ?: "")
    }

    fun delete(font: ImportedFont) = background("Deleted ${font.name}") {
        router.renderer.delete(font)
        reload()
    }

    /** Exports the selected font/emoji pack of [kind], or all of that kind if none is selected. */
    fun exportPack(kind: FontKind, target: Uri) = background("Pack exported") {
        val state = _ui.value
        val (selected, all) = when (kind) {
            FontKind.TEXT -> state.selectedFont to state.fonts
            FontKind.EMOJI -> state.selectedEmoji to state.emojiPacks
        }
        val fonts = selected?.let { listOf(it) } ?: all
        router.exporter.exportPack(fonts, packName = fonts.first().name, target = target)
    }

    fun exportMagiskModule(target: Uri) = background("Magisk/KernelSU module exported – flash it in your root manager") {
        val emoji = _ui.value.selectedEmoji ?: throw IllegalStateException("Select an emoji pack first.")
        router.exporter.exportMagiskModule(emoji, BuildConfig.VERSION_NAME, target)
    }

    fun importPack(source: Uri) = background(success = null) {
        val imported = router.exporter.importPack(source)
        reload(select = imported.first())
        "Imported ${imported.size} file(s) from pack"
    }

    fun requestShizukuPermission() = router.shizuku.requestPermission()

    fun refreshShizuku() = router.shizuku.refresh()

    fun runCommand(build: () -> String) {
        val command = try {
            build()
        } catch (e: IllegalArgumentException) {
            showMessage(e.message ?: "Invalid input")
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(running = true) }
            val result = try {
                router.shizuku.execute(command)
            } catch (e: Exception) {
                ShellResult(command, -1, "", e.message ?: e.javaClass.simpleName)
            }
            _ui.update { it.copy(running = false, log = listOf(result) + it.log.take(19)) }
        }
    }

    fun commandList() = runCommand { OverlayCommands.listFontOverlays() }

    fun commandEnable() = runCommand { OverlayCommands.enableExclusive(_ui.value.overlayPackage) }

    fun commandDisable() = runCommand { OverlayCommands.disable(_ui.value.overlayPackage) }

    fun commandFontDump() = runCommand { OverlayCommands.fontDump() }

    fun commandEmojiDiagnostics() = runCommand { OverlayCommands.emojiDiagnostics() }

    /** Reloads both lists from storage; [select] becomes the selection of its kind. */
    private suspend fun reload(select: ImportedFont? = null) {
        val all = router.renderer.loadAll()
        val fonts = all.filter { it.kind == FontKind.TEXT }
        val emoji = all.filter { it.kind == FontKind.EMOJI }
        withContext(Dispatchers.Main) {
            _ui.update { state ->
                fun pick(list: List<ImportedFont>, current: ImportedFont?, kind: FontKind): ImportedFont? {
                    val wanted = if (select?.kind == kind) select else current
                    return list.firstOrNull { it.file == wanted?.file }
                        ?: if (select?.kind == kind) list.firstOrNull() else null
                }
                state.copy(
                    fonts = fonts,
                    emojiPacks = emoji,
                    selectedFont = pick(fonts, state.selectedFont, FontKind.TEXT),
                    selectedEmoji = pick(emoji, state.selectedEmoji, FontKind.EMOJI),
                )
            }
            updatePreview()
        }
    }

    private fun updatePreview() {
        val state = _ui.value
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val family = withContext(Dispatchers.IO) {
                router.renderer.previewFamily(state.selectedFont, state.selectedEmoji)
            }
            _ui.update { it.copy(previewFamily = family) }
        }
    }

    /** Runs [block] off the main thread; its returned text (or [success]) is shown as a snackbar. */
    private fun background(success: String?, block: suspend () -> Any?) {
        viewModelScope.launch {
            val message = try {
                val result = withContext(Dispatchers.IO) { block() }
                (result as? String) ?: success
            } catch (e: Exception) {
                "Error: ${e.message ?: e.javaClass.simpleName}"
            }
            if (message != null) showMessage(message)
        }
    }
}
