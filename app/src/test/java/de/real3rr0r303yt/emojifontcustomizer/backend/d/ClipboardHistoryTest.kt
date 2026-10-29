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

class ClipboardHistoryTest {

    @Test
    fun newestFirstAndDeduplicated() {
        val history = ClipboardHistory()
        history.add("a", sensitive = false, now = 0)
        history.add("b", sensitive = false, now = 1)
        history.add("a", sensitive = false, now = 2)
        assertEquals(listOf("a", "b"), history.items(now = 3).map { it.text })
    }

    @Test
    fun keepsAtMostMaxEntries() {
        val history = ClipboardHistory(maxEntries = 3)
        (1..5).forEach { history.add("clip $it", sensitive = false, now = it.toLong()) }
        assertEquals(listOf("clip 5", "clip 4", "clip 3"), history.items(now = 6).map { it.text })
    }

    @Test
    fun ignoresBlankText() {
        val history = ClipboardHistory()
        history.add("   ", sensitive = false, now = 0)
        assertTrue(history.items(now = 0).isEmpty())
    }

    @Test
    fun sensitiveClipsExpire() {
        val history = ClipboardHistory(sensitiveTtlMs = 60_000)
        history.add("hunter2", sensitive = true, now = 0)
        history.add("hello", sensitive = false, now = 0)
        assertEquals(2, history.items(now = 59_999).size)
        assertEquals(listOf("hello"), history.items(now = 60_000).map { it.text })
        // Normal clips do not expire.
        assertEquals(listOf("hello"), history.items(now = 10_000_000).map { it.text })
    }

    @Test
    fun labelsAreShortSingleLineAndMaskSensitive() {
        assertEquals("line one line two", ClipEntry("line one\n  line two", false, 0).label())
        assertEquals("abcdefghi…", ClipEntry("abcdefghijklmnop", false, 0).label(maxLength = 10))
        val masked = ClipEntry("hunter2", true, 0).label()
        assertFalse(masked.contains("hunter2"))
    }

    @Test
    fun removeAndClear() {
        val history = ClipboardHistory()
        history.add("a", sensitive = false, now = 0)
        history.add("b", sensitive = false, now = 0)
        history.remove(history.items(now = 0).first())
        assertEquals(listOf("a"), history.items(now = 0).map { it.text })
        history.clear()
        assertTrue(history.items(now = 0).isEmpty())
    }
}
