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
package de.real3rr0r303yt.emojifontcustomizer.backend.a

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.InputStream

/** A font file imported into app storage, ready for preview. */
data class ImportedFont(
    val file: File,
    val fontFamily: FontFamily,
) {
    val name: String get() = file.nameWithoutExtension
}

/**
 * Backend A: copies `.ttf` / `.otf` files into private storage and turns them into
 * Compose [FontFamily] instances for the live preview.
 */
class InAppRendererBackend(private val context: Context) {

    val fontDir: File = File(context.filesDir, "fonts").apply { mkdirs() }

    /** Loads every previously imported font. Broken files are skipped. */
    fun loadAll(): List<ImportedFont> =
        fontDir.listFiles { f -> f.isFile && f.extension.lowercase() in FONT_EXTENSIONS }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
            .mapNotNull { runCatching { load(it) }.getOrNull() }

    /** Imports a font picked through the Storage Access Framework. */
    fun import(uri: Uri): ImportedFont {
        val displayName = queryDisplayName(uri) ?: "font_${System.currentTimeMillis()}.ttf"
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open $uri")
        return stream.use { import(displayName, it) }
    }

    /** Imports a font from a raw stream (also used by Backend B when unpacking packs). */
    fun import(fileName: String, input: InputStream): ImportedFont {
        val safeName = sanitize(fileName)
        require(File(safeName).extension.lowercase() in FONT_EXTENSIONS) {
            "Only .ttf and .otf files are supported: $fileName"
        }
        val target = File(fontDir, safeName)
        target.outputStream().use { input.copyTo(it) }
        return try {
            load(target)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    fun delete(font: ImportedFont) {
        font.file.delete()
    }

    private fun load(file: File): ImportedFont {
        val typeface = Typeface.createFromFile(file)
        // createFromFile falls back to the default typeface when the file cannot be parsed.
        require(typeface != Typeface.DEFAULT) { "Not a valid font: ${file.name}" }
        return ImportedFont(file, FontFamily(typeface))
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    companion object {
        val FONT_EXTENSIONS = setOf("ttf", "otf")

        /** MIME types offered to the system file picker. */
        val PICKER_MIME_TYPES = arrayOf(
            "font/ttf",
            "font/otf",
            "font/sfnt",
            "application/x-font-ttf",
            "application/x-font-otf",
            "application/font-sfnt",
            "application/octet-stream",
        )

        fun sanitize(name: String): String =
            name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "font.ttf" }
    }
}
