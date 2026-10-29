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

/** One entry of the keyboard's clipboard bar. */
data class ClipEntry(val text: String, val sensitive: Boolean, val addedAt: Long) {

    /** Single-line, shortened label; sensitive entries are never shown in clear text. */
    fun label(maxLength: Int = 28): String {
        if (sensitive) return "•••••• (sensitive)"
        val line = text.trim().replace(Regex("\\s+"), " ")
        return if (line.length <= maxLength) line else line.take(maxLength - 1) + "…"
    }
}

/**
 * In-memory clipboard history for the keyboard. Nothing is written to disk, so the
 * history is gone when the keyboard process stops. Sensitive clips (passwords,
 * flagged by the copying app) expire after [sensitiveTtlMs].
 *
 * Pure Kotlin with an explicit clock so it can be unit-tested.
 */
class ClipboardHistory(
    private val maxEntries: Int = 5,
    private val sensitiveTtlMs: Long = 60_000,
) {
    private val entries = ArrayDeque<ClipEntry>()

    /** Adds [text] at the front; an existing identical entry moves to the front. */
    fun add(text: String, sensitive: Boolean, now: Long) {
        if (text.isBlank()) return
        entries.removeAll { it.text == text }
        entries.addFirst(ClipEntry(text, sensitive, now))
        while (entries.size > maxEntries) entries.removeLast()
    }

    /** Current entries, newest first, without expired sensitive clips. */
    fun items(now: Long): List<ClipEntry> {
        entries.removeAll { it.sensitive && now - it.addedAt >= sensitiveTtlMs }
        return entries.toList()
    }

    fun remove(entry: ClipEntry) {
        entries.remove(entry)
    }

    fun clear() = entries.clear()
}
