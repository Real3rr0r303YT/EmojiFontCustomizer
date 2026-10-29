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
package de.real3rr0r303yt.emojifontcustomizer.backend.d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {

    @Test
    fun categoriesExcludeSkinTonesAndFlags() {
        val all = EmojiCatalog.categories.flatMap { it.codePoints }
        EmojiCatalog.SKIN_TONES.forEach { assertFalse("skin tone %x offered".format(it), it in all) }
        (0x1F1E6..0x1F1FF).forEach { assertFalse("regional indicator %x offered".format(it), it in all) }
    }

    @Test
    fun categoriesDoNotOverlap() {
        val all = EmojiCatalog.categories.flatMap { it.codePoints }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun commonEmojiAreCovered() {
        val all = EmojiCatalog.categories.flatMap { it.codePoints }.toSet()
        listOf(0x1F600, 0x1F602, 0x1F44D, 0x1F525, 0x1F680, 0x1F92F, 0x1FAE0, 0x2764, 0x2600)
            .forEach { assertTrue("missing %x".format(it), it in all) }
    }

    @Test
    fun toEmojiAddsVariationSelectorForTextDefaultCharacters() {
        assertEquals("😀", EmojiCatalog.toEmoji(0x1F600))
        assertEquals("❤️", EmojiCatalog.toEmoji(0x2764))
        assertEquals("⚽", EmojiCatalog.toEmoji(0x26BD, emojiPresentation = true))
    }
}
