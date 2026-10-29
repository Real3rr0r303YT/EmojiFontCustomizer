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
package de.real3rr0r303yt.emojifontcustomizer.backend.b

import android.content.Context
import android.net.Uri
import de.real3rr0r303yt.emojifontcustomizer.backend.a.ImportedFont
import de.real3rr0r303yt.emojifontcustomizer.backend.a.InAppRendererBackend
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backend B: bundles imported fonts into a portable font pack (`.zip`) and reads
 * such packs back in.
 *
 * Pack layout:
 * ```
 * font-pack.json      manifest (name, version, files with SHA-256)
 * fonts/<file>.ttf    the font files
 * ```
 * The layout is deliberately simple so OEM theme tools or Magisk/overlay
 * templates can consume it without this app.
 */
class ThemeExportBackend(
    private val context: Context,
    private val renderer: InAppRendererBackend,
) {

    /** Writes a pack containing [fonts] to [target] (a SAF document Uri). */
    fun exportPack(fonts: List<ImportedFont>, packName: String, target: Uri) {
        require(fonts.isNotEmpty()) { "No fonts to export." }
        val out = context.contentResolver.openOutputStream(target)
            ?: throw IllegalArgumentException("Cannot write $target")
        ZipOutputStream(out.buffered()).use { zip ->
            val files = JSONArray()
            fonts.forEach { font ->
                files.put(
                    JSONObject()
                        .put("path", "$FONT_DIR${font.file.name}")
                        .put("name", font.name)
                        .put("sha256", sha256(font.file))
                        .put("size", font.file.length()),
                )
                zip.putNextEntry(ZipEntry("$FONT_DIR${font.file.name}"))
                font.file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("formatVersion", FORMAT_VERSION)
                .put("name", packName)
                .put("createdAt", System.currentTimeMillis())
                .put("files", files)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString(2).toByteArray())
            zip.closeEntry()
        }
    }

    /** Imports every font from a pack and returns them. */
    fun importPack(source: Uri): List<ImportedFont> {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IllegalArgumentException("Cannot open $source")
        val imported = mutableListOf<ImportedFont>()
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val name = entry.name
                if (!entry.isDirectory && name.startsWith(FONT_DIR) &&
                    File(name).extension.lowercase() in InAppRendererBackend.FONT_EXTENSIONS
                ) {
                    // Only the base name is used, so "../" entries cannot escape fontDir.
                    imported += renderer.import(File(name).name, zip)
                }
                zip.closeEntry()
            }
        }
        require(imported.isNotEmpty()) { "The pack does not contain any .ttf/.otf fonts." }
        return imported
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val FORMAT = "emojifontcustomizer.fontpack"
        const val FORMAT_VERSION = 1
        const val MANIFEST = "font-pack.json"
        const val FONT_DIR = "fonts/"
        const val MIME_TYPE = "application/zip"
    }
}
