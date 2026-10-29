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
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.InputStream
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily

/** A font file imported into app storage, ready for preview. */
data class ImportedFont(
    val file: File,
    val kind: FontKind,
    val info: FontInfo,
    val typeface: Typeface,
    val fontFamily: FontFamily,
) {
    val name: String get() = file.nameWithoutExtension

    /** Shown in the UI when the font will not render properly on Android. */
    val warning: String?
        get() = if (!info.androidRenderable) {
            "Uses ${info.colorTables.joinToString()} color glyphs (Apple format) – Android cannot render these. " +
                "Use a CBDT or COLR build instead."
        } else {
            null
        }
}

/**
 * Backend A: copies `.ttf` / `.otf` / `.ttc` files into private storage, sorts them
 * into text fonts and emoji packs, and turns them into Compose [FontFamily] instances
 * for the live preview.
 */
class InAppRendererBackend(private val context: Context) {

    private val fontDir: File = File(context.filesDir, "fonts").apply { mkdirs() }
    private val emojiDir: File = File(context.filesDir, "emoji").apply { mkdirs() }

    private fun dirFor(kind: FontKind) = if (kind == FontKind.EMOJI) emojiDir else fontDir

    /** Loads every previously imported font and emoji pack. Broken files are skipped. */
    fun loadAll(): List<ImportedFont> =
        FontKind.entries.flatMap { kind ->
            dirFor(kind).listFiles { f -> f.isFile && f.extension.lowercase() in FONT_EXTENSIONS }
                .orEmpty()
                .sortedBy { it.name.lowercase() }
                .mapNotNull { runCatching { load(it, kind) }.getOrNull() }
        }

    /** Imports a font picked through the Storage Access Framework. */
    fun import(uri: Uri): ImportedFont {
        val displayName = queryDisplayName(uri) ?: "font_${System.currentTimeMillis()}.ttf"
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open $uri")
        return stream.use { import(displayName, it) }
    }

    /**
     * Imports a font from a raw stream (also used by Backend B when unpacking packs).
     * The kind is detected from the font tables unless [forceKind] is given.
     */
    fun import(fileName: String, input: InputStream, forceKind: FontKind? = null): ImportedFont {
        val safeName = sanitize(fileName)
        require(File(safeName).extension.lowercase() in FONT_EXTENSIONS) {
            "Only .ttf, .otf and .ttc files are supported: $fileName"
        }
        val temp = File(context.cacheDir, "import_$safeName")
        try {
            temp.outputStream().use { input.copyTo(it) }
            val kind = forceKind ?: FontInspector.classify(inspect(temp), safeName)
            val target = File(dirFor(kind), safeName)
            // Re-importing a file under the other kind must not leave a stale copy behind.
            FontKind.entries.forEach { File(dirFor(it), safeName).delete() }
            temp.copyTo(target, overwrite = true)
            return try {
                load(target, kind)
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        } finally {
            temp.delete()
        }
    }

    fun delete(font: ImportedFont) {
        font.file.delete()
    }

    /**
     * Builds the preview family: [text] for letters, [emoji] for emoji, system fonts
     * for everything else. Font fallback chains need Android 10+; older versions
     * preview the text font alone (or the emoji pack when no text font is chosen).
     */
    fun previewFamily(text: ImportedFont?, emoji: ImportedFont?): FontFamily {
        if (text == null && emoji == null) return FontFamily.Default
        if (emoji == null) return text!!.fontFamily
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val chain = listOfNotNull(text, emoji).map {
                    PlatformFontFamily.Builder(PlatformFont.Builder(it.file).build()).build()
                }
                val builder = Typeface.CustomFallbackBuilder(chain.first())
                chain.drop(1).forEach { builder.addCustomFallback(it) }
                return FontFamily(builder.setSystemFallback("sans-serif").build())
            }
        }
        return (text ?: emoji).fontFamily
    }

    private fun inspect(file: File): FontInfo {
        val header = ByteArray(minOf(file.length(), FontInspector.HEADER_BYTES.toLong()).toInt())
        file.inputStream().use { input ->
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n < 0) break
                read += n
            }
        }
        return FontInspector.inspect(header)
    }

    private fun load(file: File, kind: FontKind): ImportedFont {
        val typeface = Typeface.createFromFile(file)
        // createFromFile falls back to the default typeface when the file cannot be parsed.
        require(typeface != Typeface.DEFAULT) { "Not a valid font: ${file.name}" }
        return ImportedFont(file, kind, inspect(file), typeface, FontFamily(typeface))
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    companion object {
        val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")

        /** MIME types offered to the system file picker. */
        val PICKER_MIME_TYPES = arrayOf(
            "font/ttf",
            "font/otf",
            "font/collection",
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
