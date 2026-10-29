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

enum class KeyboardLanguage(val label: String) {
    GERMAN("Deutsch"),
    ENGLISH("English"),
    ;

    fun next(): KeyboardLanguage = entries[(ordinal + 1) % entries.size]
}

enum class KeyboardPage { LETTERS, SYMBOLS, MORE_SYMBOLS }

/** One key of the text keyboard. [width] is relative to a normal letter key. */
sealed interface KeySpec {
    val width: Float get() = 1f

    /** Types [value]; long-press offers [popup] (umlauts, accents, digits, …). */
    data class Text(val value: String, val popup: List<String> = emptyList()) : KeySpec

    data object Shift : KeySpec {
        override val width = 1.5f
    }

    data object Backspace : KeySpec {
        override val width = 1.5f
    }

    data object Enter : KeySpec {
        override val width = 1.5f
    }

    data object Space : KeySpec {
        override val width = 4f
    }

    data object Language : KeySpec

    data object Emoji : KeySpec

    data class Page(val target: KeyboardPage, val label: String) : KeySpec {
        override val width get() = 1.5f
    }
}

/**
 * Key layouts of the text keyboard: German QWERTZ and English/international QWERTY,
 * each with letter, number/symbol and extra symbol pages. Pure Kotlin, unit-tested.
 */
object KeyboardLayouts {

    /** Long-press alternatives for letters (lower case; upper-cased with shift). */
    val ACCENTS: Map<String, List<String>> = mapOf(
        "a" to listOf("ä", "à", "á", "â", "æ", "ã", "å", "ā"),
        "c" to listOf("ç", "ć", "č"),
        "e" to listOf("é", "è", "ê", "ë", "ē", "ę"),
        "i" to listOf("í", "ì", "î", "ï", "ī"),
        "l" to listOf("ł"),
        "n" to listOf("ñ", "ń"),
        "o" to listOf("ö", "ò", "ó", "ô", "œ", "õ", "ø", "ō"),
        "s" to listOf("ß", "ś", "š"),
        "u" to listOf("ü", "ù", "ú", "û", "ū"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
    )

    private val SYMBOL_POPUPS: Map<String, List<String>> = mapOf(
        "." to listOf(",", "?", "!", ":", ";", "…", "-", "@"),
        "," to listOf(";", ":", "'", "\""),
        "-" to listOf("–", "—", "·"),
        "'" to listOf("‘", "’", "‚", "`"),
        "\"" to listOf("“", "”", "„", "«", "»"),
        "?" to listOf("¿"),
        "!" to listOf("¡"),
        "€" to listOf("$", "£", "¥", "¢"),
        "$" to listOf("€", "£", "¥", "¢"),
        "0" to listOf("°", "∅"),
        "%" to listOf("‰"),
    )

    private const val DIGITS = "1234567890"

    /** Top row letters also offer the digit above them, like most phone keyboards. */
    private fun letterRow(letters: String, withDigits: Boolean = false): List<KeySpec> =
        letters.mapIndexed { index, c ->
            val value = c.toString()
            val digit = if (withDigits && index < DIGITS.length) listOf(DIGITS[index].toString()) else emptyList()
            KeySpec.Text(value, digit + ACCENTS[value].orEmpty())
        }

    private fun symbolRow(symbols: List<String>): List<KeySpec> =
        symbols.map { KeySpec.Text(it, SYMBOL_POPUPS[it].orEmpty()) }

    private fun bottomRow(pageKey: KeySpec.Page): List<KeySpec> = listOf(
        pageKey,
        KeySpec.Language,
        KeySpec.Emoji,
        KeySpec.Text(",", SYMBOL_POPUPS[","].orEmpty()),
        KeySpec.Space,
        KeySpec.Text(".", SYMBOL_POPUPS["."].orEmpty()),
        KeySpec.Enter,
    )

    fun rows(language: KeyboardLanguage, page: KeyboardPage): List<List<KeySpec>> = when (page) {
        KeyboardPage.LETTERS -> letters(language)
        KeyboardPage.SYMBOLS -> listOf(
            symbolRow(DIGITS.map { it.toString() }),
            symbolRow(listOf("@", "#", if (language == KeyboardLanguage.GERMAN) "€" else "$", "_", "&", "-", "+", "(", ")", "/")),
            listOf<KeySpec>(KeySpec.Page(KeyboardPage.MORE_SYMBOLS, "=\\<")) +
                symbolRow(listOf("*", "\"", "'", ":", ";", "!", "?")) + KeySpec.Backspace,
            bottomRow(KeySpec.Page(KeyboardPage.LETTERS, "ABC")),
        )
        KeyboardPage.MORE_SYMBOLS -> listOf(
            symbolRow(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")),
            symbolRow(listOf("£", "¥", "$", "¢", "^", "°", "=", "{", "}", "\\")),
            listOf<KeySpec>(KeySpec.Page(KeyboardPage.SYMBOLS, "123")) +
                symbolRow(listOf("%", "©", "®", "™", "✓", "[", "]")) + KeySpec.Backspace,
            bottomRow(KeySpec.Page(KeyboardPage.LETTERS, "ABC")),
        )
    }

    private fun letters(language: KeyboardLanguage): List<List<KeySpec>> = when (language) {
        KeyboardLanguage.GERMAN -> listOf(
            letterRow("qwertzuiopü", withDigits = true),
            letterRow("asdfghjklöä"),
            listOf<KeySpec>(KeySpec.Shift) + letterRow("yxcvbnm") + KeySpec.Backspace,
            bottomRow(KeySpec.Page(KeyboardPage.SYMBOLS, "?123")),
        )
        KeyboardLanguage.ENGLISH -> listOf(
            letterRow("qwertyuiop", withDigits = true),
            letterRow("asdfghjkl"),
            listOf<KeySpec>(KeySpec.Shift) + letterRow("zxcvbnm") + KeySpec.Backspace,
            bottomRow(KeySpec.Page(KeyboardPage.SYMBOLS, "?123")),
        )
    }

    /** Text a key types: upper case with shift, except "ß", which has no common capital. */
    fun applyShift(value: String, upper: Boolean): String =
        if (upper && value != "ß") value.uppercase() else value
}

enum class ShiftMode { OFF, ONCE, LOCKED }

/**
 * Shift key behaviour: tap = next letter upper case, double-tap = caps lock,
 * tap again = off. Auto-capitalisation (start of sentence) never overrides caps lock.
 */
class ShiftController(private val doubleTapMs: Long = 400) {
    var mode: ShiftMode = ShiftMode.OFF
        private set

    private var lastTap = Long.MIN_VALUE / 2

    val isUpper: Boolean get() = mode != ShiftMode.OFF

    fun onShiftTap(now: Long) {
        mode = when (mode) {
            ShiftMode.OFF -> ShiftMode.ONCE
            ShiftMode.ONCE -> if (now - lastTap <= doubleTapMs) ShiftMode.LOCKED else ShiftMode.OFF
            ShiftMode.LOCKED -> ShiftMode.OFF
        }
        lastTap = now
    }

    fun onCharacterTyped() {
        if (mode == ShiftMode.ONCE) mode = ShiftMode.OFF
    }

    fun setAuto(capitalize: Boolean) {
        if (mode != ShiftMode.LOCKED) mode = if (capitalize) ShiftMode.ONCE else ShiftMode.OFF
    }
}
