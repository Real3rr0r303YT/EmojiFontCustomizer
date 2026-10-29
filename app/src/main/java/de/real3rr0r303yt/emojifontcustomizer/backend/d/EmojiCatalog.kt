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

/** A keyboard page: Unicode blocks whose emoji are offered together. */
data class EmojiCategory(val title: String, val icon: String, val ranges: List<IntRange>) {
    val codePoints: List<Int> get() = ranges.flatMap { it.toList() }
}

/**
 * Code point ranges the emoji keyboard offers. Each candidate is still checked
 * against Unicode emoji properties / the font on the device before it is shown.
 * Pure Kotlin so it can be unit-tested on the JVM.
 */
object EmojiCatalog {

    /** Skin tone modifiers; they only make sense combined with another emoji. */
    val SKIN_TONES = 0x1F3FB..0x1F3FF

    val categories: List<EmojiCategory> = listOf(
        EmojiCategory("Smileys", "😀", listOf(0x1F600..0x1F64F)),
        EmojiCategory("People & more", "🤩", listOf(0x1F900..0x1F9FF)),
        EmojiCategory("Nature & objects", "🌸", listOf(0x1F300..0x1F3FA, 0x1F400..0x1F5FF)),
        EmojiCategory("Travel", "🚀", listOf(0x1F680..0x1F6FF)),
        EmojiCategory("New", "🫠", listOf(0x1FA70..0x1FAFF)),
        EmojiCategory("Symbols", "❤️", listOf(0x2600..0x27BF, 0x2B00..0x2BFF)),
    )

    /** Variation selector 16: requests the colorful emoji presentation of a character. */
    const val VS16 = "️"

    /**
     * Text for [codePoint]. Characters that default to text presentation (most of the
     * BMP symbols like ☀ or ❤) get [VS16] so they render as emoji.
     */
    fun toEmoji(codePoint: Int, emojiPresentation: Boolean = codePoint > 0xFFFF): String =
        String(Character.toChars(codePoint)) + if (emojiPresentation) "" else VS16
}
