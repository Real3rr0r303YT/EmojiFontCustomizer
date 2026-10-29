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

import android.content.Context
import java.io.File

/** Settings shared between the app and the keyboard (same process, private storage). */
object KeyboardPrefs {
    private const val NAME = "keyboard"
    private const val KEY_PACK = "emoji_pack"
    private const val KEY_RECENT = "recent"
    private const val SEPARATOR = "\u001F"
    private const val MAX_RECENT = 32

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** The emoji pack the keyboard renders with, or null for system emoji. */
    fun emojiPack(context: Context): File? =
        prefs(context).getString(KEY_PACK, null)?.let(::File)?.takeIf { it.isFile }

    fun setEmojiPack(context: Context, file: File?) {
        prefs(context).edit().apply {
            if (file == null) remove(KEY_PACK) else putString(KEY_PACK, file.absolutePath)
        }.apply()
    }

    fun recent(context: Context): List<String> =
        prefs(context).getString(KEY_RECENT, "").orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }

    fun addRecent(context: Context, emoji: String) {
        val list = (listOf(emoji) + recent(context).filter { it != emoji }).take(MAX_RECENT)
        prefs(context).edit().putString(KEY_RECENT, list.joinToString(SEPARATOR)).apply()
    }
}
