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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.real3rr0r303yt.emojifontcustomizer.backend.a.ImportedFont
import de.real3rr0r303yt.emojifontcustomizer.backend.c.OverlayCommands
import de.real3rr0r303yt.emojifontcustomizer.backend.c.ShellResult
import de.real3rr0r303yt.emojifontcustomizer.backend.c.ShizukuState
import de.real3rr0r303yt.emojifontcustomizer.router.BackendRouter
import de.real3rr0r303yt.emojifontcustomizer.router.RouteDecision
import de.real3rr0r303yt.emojifontcustomizer.router.RouteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val fonts: List<ImportedFont> = emptyList(),
    val selected: ImportedFont? = null,
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

    init {
        router.start()
        viewModelScope.launch {
            val fonts = withContext(Dispatchers.IO) { router.renderer.loadAll() }
            _ui.update { it.copy(fonts = fonts, selected = fonts.firstOrNull()) }
        }
    }

    override fun onCleared() {
        router.stop()
    }

    fun route(request: RouteRequest): RouteDecision = router.route(request)

    fun select(font: ImportedFont?) = _ui.update { it.copy(selected = font) }

    fun setSampleText(text: String) = _ui.update { it.copy(sampleText = text) }

    fun setFontSize(sp: Float) = _ui.update { it.copy(fontSizeSp = sp) }

    fun setOverlayPackage(pkg: String) = _ui.update { it.copy(overlayPackage = pkg.trim()) }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    fun importFont(uri: Uri) = background("Font imported") {
        val font = router.renderer.import(uri)
        reloadFonts(select = font)
    }

    fun deleteSelected() = background("Font deleted") {
        _ui.value.selected?.let { router.renderer.delete(it) }
        reloadFonts(select = null)
    }

    fun exportPack(target: Uri) = background("Font pack exported") {
        val fonts = _ui.value.selected?.let { listOf(it) } ?: _ui.value.fonts
        router.exporter.exportPack(fonts, packName = fonts.first().name, target = target)
    }

    fun importPack(source: Uri) = background("Font pack imported") {
        val imported = router.exporter.importPack(source)
        reloadFonts(select = imported.first())
    }

    fun requestShizukuPermission() = router.shizuku.requestPermission()

    fun refreshShizuku() = router.shizuku.refresh()

    fun runCommand(build: () -> String) {
        val command = try {
            build()
        } catch (e: IllegalArgumentException) {
            _ui.update { it.copy(message = e.message) }
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

    private suspend fun reloadFonts(select: ImportedFont?) {
        val fonts = router.renderer.loadAll()
        val selected = fonts.firstOrNull { it.file == select?.file } ?: fonts.firstOrNull()
        withContext(Dispatchers.Main) {
            _ui.update { it.copy(fonts = fonts, selected = selected) }
        }
    }

    private fun background(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) { block() }
                success
            } catch (e: Exception) {
                "Error: ${e.message ?: e.javaClass.simpleName}"
            }
            _ui.update { it.copy(message = message) }
        }
    }
}
