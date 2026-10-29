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

/**
 * Settings shared between the app and the keyboard (same process, private storage).
 *
 * Only configuration is stored (emoji pack, layout language). Nothing about what is
 * typed, pasted or picked is written to disk: recent emoji live in memory only.
 */
object KeyboardPrefs {
    private const val NAME = "keyboard"
    private const val KEY_PACK = "emoji_pack"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_HEIGHT = "height_scale"

    /** Range of the keyboard height setting (1.0 = default). */
    val HEIGHT_RANGE = 0.85f..1.4f

    /** Stored recent emoji in versions up to 0.4.0; removed on first use. */
    private const val LEGACY_KEY_RECENT = "recent"
    private const val MAX_RECENT = 32

    private val recentEmoji = ArrayDeque<String>()

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).also {
        if (it.contains(LEGACY_KEY_RECENT)) it.edit().remove(LEGACY_KEY_RECENT).apply()
    }

    /** The emoji pack the keyboard renders with, or null for system emoji. */
    fun emojiPack(context: Context): File? =
        prefs(context).getString(KEY_PACK, null)?.let(::File)?.takeIf { it.isFile }

    fun setEmojiPack(context: Context, file: File?) {
        prefs(context).edit().apply {
            if (file == null) remove(KEY_PACK) else putString(KEY_PACK, file.absolutePath)
        }.apply()
    }

    fun language(context: Context): KeyboardLanguage =
        prefs(context).getString(KEY_LANGUAGE, null)
            ?.let { name -> KeyboardLanguage.entries.firstOrNull { it.name == name } }
            ?: KeyboardLanguage.GERMAN

    fun setLanguage(context: Context, language: KeyboardLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    fun heightScale(context: Context): Float =
        prefs(context).getFloat(KEY_HEIGHT, 1f).coerceIn(HEIGHT_RANGE)

    fun setHeightScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_HEIGHT, scale.coerceIn(HEIGHT_RANGE)).apply()
    }

    /** Recently used emoji, newest first; kept in memory only. */
    fun recent(): List<String> = recentEmoji.toList()

    fun addRecent(emoji: String) {
        recentEmoji.remove(emoji)
        recentEmoji.addFirst(emoji)
        while (recentEmoji.size > MAX_RECENT) recentEmoji.removeLast()
    }
}
