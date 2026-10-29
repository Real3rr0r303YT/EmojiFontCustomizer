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

class KeyboardLayoutsTest {

    private fun texts(row: List<KeySpec>) = row.filterIsInstance<KeySpec.Text>().map { it.value }

    @Test
    fun germanIsQwertzWithUmlautKeys() {
        val rows = KeyboardLayouts.rows(KeyboardLanguage.GERMAN, KeyboardPage.LETTERS)
        assertEquals("qwertzuiopü", texts(rows[0]).joinToString(""))
        assertEquals("asdfghjklöä", texts(rows[1]).joinToString(""))
        assertEquals("yxcvbnm", texts(rows[2]).joinToString(""))
    }

    @Test
    fun englishIsQwertyWithoutUmlautKeys() {
        val rows = KeyboardLayouts.rows(KeyboardLanguage.ENGLISH, KeyboardPage.LETTERS)
        assertEquals("qwertyuiop", texts(rows[0]).joinToString(""))
        assertEquals("zxcvbnm", texts(rows[2]).joinToString(""))
        assertFalse(rows.flatten().any { it is KeySpec.Text && it.value in setOf("ä", "ö", "ü") })
    }

    @Test
    fun umlautsAndAccentsAreOnLongPress() {
        val keys = KeyboardLayouts.rows(KeyboardLanguage.ENGLISH, KeyboardPage.LETTERS).flatten()
            .filterIsInstance<KeySpec.Text>().associateBy { it.value }
        assertTrue("ä" in keys.getValue("a").popup)
        assertTrue("ö" in keys.getValue("o").popup)
        assertTrue("ü" in keys.getValue("u").popup)
        assertTrue("ß" in keys.getValue("s").popup)
        assertTrue("é" in keys.getValue("e").popup)
        // Top row offers digits first.
        assertEquals("1", keys.getValue("q").popup.first())
        assertEquals("0", keys.getValue("p").popup.first())
    }

    @Test
    fun everyPageHasTheStandardKeys() {
        KeyboardLanguage.entries.forEach { language ->
            KeyboardPage.entries.forEach { page ->
                val keys = KeyboardLayouts.rows(language, page).flatten()
                assertTrue("$language/$page misses backspace", keys.any { it is KeySpec.Backspace })
                assertTrue("$language/$page misses space", keys.any { it is KeySpec.Space })
                assertTrue("$language/$page misses enter", KeySpec.Enter in keys)
                assertTrue("$language/$page misses language", KeySpec.Language in keys)
                assertTrue("$language/$page has no page switch", keys.any { it is KeySpec.Page })
            }
            assertTrue(KeyboardLayouts.rows(language, KeyboardPage.LETTERS).flatten().any { it is KeySpec.Shift })
        }
    }

    @Test
    fun rowsAreBalanced() {
        // Every row of a page fills the same width, except centred shorter letter rows.
        KeyboardLanguage.entries.forEach { language ->
            KeyboardPage.entries.forEach { page ->
                val widths = KeyboardLayouts.rows(language, page).map { row -> row.sumOf { it.width.toDouble() } }
                val max = widths.max()
                widths.forEach { assertTrue("$language/$page row width $it vs $max", max - it <= 1.0) }
                assertEquals("$language/$page bottom row", max, widths.last(), 0.001)
            }
        }
    }

    @Test
    fun spaceBarIsTheWidestKey() {
        KeyboardLanguage.entries.forEach { language ->
            val keys = KeyboardLayouts.rows(language, KeyboardPage.LETTERS).flatten()
            val space = keys.first { it is KeySpec.Space }.width
            assertTrue(keys.filter { it !is KeySpec.Space }.all { it.width < space })
            assertTrue(space >= 4f)
        }
    }

    @Test
    fun pagesLinkToEachOther() {
        fun targets(page: KeyboardPage) = KeyboardLayouts.rows(KeyboardLanguage.GERMAN, page).flatten()
            .filterIsInstance<KeySpec.Page>().map { it.target }.toSet()
        assertEquals(setOf(KeyboardPage.SYMBOLS), targets(KeyboardPage.LETTERS))
        assertEquals(setOf(KeyboardPage.MORE_SYMBOLS, KeyboardPage.LETTERS), targets(KeyboardPage.SYMBOLS))
        assertEquals(setOf(KeyboardPage.SYMBOLS, KeyboardPage.LETTERS), targets(KeyboardPage.MORE_SYMBOLS))
    }

    @Test
    fun shiftUpperCasesButKeepsEszett() {
        assertEquals("Ä", KeyboardLayouts.applyShift("ä", upper = true))
        assertEquals("a", KeyboardLayouts.applyShift("a", upper = false))
        assertEquals("ß", KeyboardLayouts.applyShift("ß", upper = true))
    }

    @Test
    fun languagesCycle() {
        assertEquals(KeyboardLanguage.ENGLISH, KeyboardLanguage.GERMAN.next())
        assertEquals(KeyboardLanguage.GERMAN, KeyboardLanguage.ENGLISH.next())
    }
}

class ShiftControllerTest {

    @Test
    fun tapShiftsNextLetterOnly() {
        val shift = ShiftController()
        shift.onShiftTap(now = 0)
        assertEquals(ShiftMode.ONCE, shift.mode)
        shift.onCharacterTyped()
        assertEquals(ShiftMode.OFF, shift.mode)
    }

    @Test
    fun doubleTapLocksAndTapUnlocks() {
        val shift = ShiftController(doubleTapMs = 400)
        shift.onShiftTap(now = 1_000)
        shift.onShiftTap(now = 1_200)
        assertEquals(ShiftMode.LOCKED, shift.mode)
        shift.onCharacterTyped()
        assertEquals(ShiftMode.LOCKED, shift.mode)
        shift.onShiftTap(now = 5_000)
        assertEquals(ShiftMode.OFF, shift.mode)
    }

    @Test
    fun slowSecondTapTurnsShiftOff() {
        val shift = ShiftController(doubleTapMs = 400)
        shift.onShiftTap(now = 1_000)
        shift.onShiftTap(now = 2_000)
        assertEquals(ShiftMode.OFF, shift.mode)
    }

    @Test
    fun autoCapitalisationNeverOverridesCapsLock() {
        val shift = ShiftController()
        shift.setAuto(capitalize = true)
        assertEquals(ShiftMode.ONCE, shift.mode)
        shift.setAuto(capitalize = false)
        assertEquals(ShiftMode.OFF, shift.mode)
        shift.onShiftTap(now = 0)
        shift.onShiftTap(now = 100)
        shift.setAuto(capitalize = false)
        assertEquals(ShiftMode.LOCKED, shift.mode)
    }
}
