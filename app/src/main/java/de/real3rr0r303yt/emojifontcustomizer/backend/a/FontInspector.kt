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

/** Whether a font file is a regular text font or an emoji pack. */
enum class FontKind { TEXT, EMOJI }

/** Result of inspecting a font file's sfnt table directory. */
data class FontInfo(
    val container: Container,
    val tables: Set<String>,
) {
    enum class Container { TRUETYPE, OPENTYPE_CFF, COLLECTION, UNKNOWN }

    /** Color glyph tables found in the font (`CBDT`, `COLR`, `sbix`, `SVG `). */
    val colorTables: Set<String> get() = tables intersect COLOR_TABLES

    /** True if Android can render the color glyphs (CBDT/CBLC bitmaps or COLR vectors). */
    val androidRenderable: Boolean
        get() = colorTables.isEmpty() || "CBDT" in colorTables || "COLR" in colorTables

    companion object {
        val COLOR_TABLES = setOf("CBDT", "COLR", "sbix", "SVG ")
        val UNKNOWN = FontInfo(Container.UNKNOWN, emptySet())
    }
}

/**
 * Reads just enough of a TrueType/OpenType/TTC header to tell emoji fonts from
 * text fonts. Pure Kotlin so it can be unit-tested on the JVM.
 */
object FontInspector {

    /** Bytes callers should pass to [inspect]; table directories are tiny. */
    const val HEADER_BYTES = 64 * 1024

    fun inspect(header: ByteArray): FontInfo {
        if (header.size < 12) return FontInfo.UNKNOWN
        return when (val tag = tagAt(header, 0)) {
            "ttcf" -> {
                // TTC header: tag, version, numFonts, then one offset per font.
                if (header.size < 16 || u32(header, 8) < 1) return FontInfo.UNKNOWN
                val first = u32(header, 12)
                if (first > Int.MAX_VALUE) return FontInfo.UNKNOWN
                val tables = readTables(header, first.toInt()) ?: return FontInfo.UNKNOWN
                FontInfo(FontInfo.Container.COLLECTION, tables)
            }
            "OTTO" -> FontInfo(FontInfo.Container.OPENTYPE_CFF, readTables(header, 0) ?: return FontInfo.UNKNOWN)
            "true", "\u0000\u0001\u0000\u0000" ->
                FontInfo(FontInfo.Container.TRUETYPE, readTables(header, 0) ?: return FontInfo.UNKNOWN)
            else -> FontInfo.UNKNOWN
        }
    }

    /** Emoji if the font carries color glyph tables or is named like an emoji font. */
    fun classify(info: FontInfo, fileName: String): FontKind =
        if (info.colorTables.isNotEmpty() || fileName.contains("emoji", ignoreCase = true)) {
            FontKind.EMOJI
        } else {
            FontKind.TEXT
        }

    private fun readTables(bytes: ByteArray, offset: Int): Set<String>? {
        if (offset < 0 || offset + 12 > bytes.size) return null
        val numTables = u16(bytes, offset + 4)
        val start = offset + 12
        if (numTables == 0 || start + numTables * 16 > bytes.size) return null
        return (0 until numTables).mapTo(mutableSetOf()) { tagAt(bytes, start + it * 16) }
    }

    private fun tagAt(b: ByteArray, at: Int): String =
        String(CharArray(4) { (b[at + it].toInt() and 0xFF).toChar() })

    private fun u16(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)
}
