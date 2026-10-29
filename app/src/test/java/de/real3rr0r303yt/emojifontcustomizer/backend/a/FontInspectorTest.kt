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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class FontInspectorTest {

    /** Builds an sfnt table directory with the given [tags] (table data is not needed). */
    private fun sfnt(version: Int, vararg tags: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(version)
            writeShort(tags.size)
            writeShort(0); writeShort(0); writeShort(0) // searchRange, entrySelector, rangeShift
            tags.forEach { tag ->
                writeBytes(tag)
                writeInt(0); writeInt(0); writeInt(0) // checksum, offset, length
            }
        }
        return bytes.toByteArray()
    }

    private fun ttc(font: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeBytes("ttcf")
            writeInt(0x00010000)
            writeInt(1)
            writeInt(16) // offset of the first font, right after this header
            write(font)
        }
        return bytes.toByteArray()
    }

    @Test
    fun plainTrueTypeIsTextFont() {
        val info = FontInspector.inspect(sfnt(0x00010000, "cmap", "glyf", "head", "loca"))
        assertEquals(FontInfo.Container.TRUETYPE, info.container)
        assertTrue(info.colorTables.isEmpty())
        assertTrue(info.androidRenderable)
        assertEquals(FontKind.TEXT, FontInspector.classify(info, "Roboto.ttf"))
    }

    @Test
    fun cbdtFontIsRenderableEmoji() {
        val info = FontInspector.inspect(sfnt(0x00010000, "CBDT", "CBLC", "cmap", "head"))
        assertEquals(setOf("CBDT"), info.colorTables)
        assertTrue(info.androidRenderable)
        assertEquals(FontKind.EMOJI, FontInspector.classify(info, "iOS-style.ttf"))
    }

    @Test
    fun colrOpenTypeIsRenderableEmoji() {
        val info = FontInspector.inspect(sfnt(0x4F54544F, "CFF ", "COLR", "CPAL", "cmap")) // "OTTO"
        assertEquals(FontInfo.Container.OPENTYPE_CFF, info.container)
        assertTrue(info.androidRenderable)
        assertEquals(FontKind.EMOJI, FontInspector.classify(info, "Twemoji.otf"))
    }

    @Test
    fun appleSbixCollectionIsEmojiButNotRenderable() {
        val info = FontInspector.inspect(ttc(sfnt(0x00010000, "sbix", "cmap", "head", "morx")))
        assertEquals(FontInfo.Container.COLLECTION, info.container)
        assertEquals(setOf("sbix"), info.colorTables)
        assertFalse(info.androidRenderable)
        assertEquals(FontKind.EMOJI, FontInspector.classify(info, "Apple Color Emoji.ttc"))
    }

    @Test
    fun emojiFileNameWinsForPlainFonts() {
        val info = FontInspector.inspect(sfnt(0x00010000, "cmap", "glyf"))
        assertEquals(FontKind.EMOJI, FontInspector.classify(info, "NotoEmoji-Regular.ttf"))
    }

    @Test
    fun garbageIsUnknown() {
        assertEquals(FontInfo.UNKNOWN, FontInspector.inspect(ByteArray(4)))
        assertEquals(FontInfo.UNKNOWN, FontInspector.inspect("PK\u0003\u0004 not a font!".toByteArray()))
        // Table count pointing past the end of the header.
        val truncated = sfnt(0x00010000, "cmap").copyOf(20)
        assertEquals(FontInfo.UNKNOWN, FontInspector.inspect(truncated))
    }
}
