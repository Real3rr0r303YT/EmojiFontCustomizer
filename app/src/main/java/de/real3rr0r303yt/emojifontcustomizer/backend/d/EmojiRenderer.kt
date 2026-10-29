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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.os.Build
import java.io.File
import java.util.UUID

/**
 * Backend D: renders emoji from the selected pack, both for the keyboard grid and
 * as PNG images that the keyboard sends to apps.
 */
class EmojiRenderer(private val context: Context, val packFile: File?) {

    /** Lets the keyboard notice when the pack file was replaced. */
    val packModified: Long = packFile?.lastModified() ?: 0L

    val typeface: Typeface? = packFile?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = this@EmojiRenderer.typeface ?: Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }

    private val cache = mutableMapOf<EmojiCategory, List<String>>()

    /** Emoji of [category] that this device can show. */
    fun emojiFor(category: EmojiCategory): List<String> =
        cache.getOrPut(category) { category.codePoints.mapNotNull(::emojiOrNull) }

    private fun emojiOrNull(codePoint: Int): String? {
        if (codePoint in EmojiCatalog.SKIN_TONES) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI)) return null
            return EmojiCatalog.toEmoji(
                codePoint,
                emojiPresentation = UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_PRESENTATION),
            )
        }
        // Android 8.x: no Unicode emoji properties, so ask the font instead.
        return EmojiCatalog.toEmoji(codePoint).takeIf { paint.hasGlyph(it) }
    }

    /**
     * Renders [emoji] into a transparent PNG of [sizePx]² pixels for the target app.
     *
     * Privacy: files get random names (nothing reveals which emoji was sent) and are
     * deleted once older than [SHARED_FILE_TTL_MS], which leaves the receiving app
     * enough time to read them.
     */
    fun renderPng(emoji: String, sizePx: Int = 256): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        deleteOldFiles(dir)
        val file = File(dir, "${UUID.randomUUID()}.png")

        val p = Paint(paint).apply { textSize = sizePx * 0.8f }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val metrics = p.fontMetrics
        Canvas(bitmap).drawText(emoji, sizePx / 2f, sizePx / 2f - (metrics.ascent + metrics.descent) / 2f, p)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    companion object {
        /** Must match res/xml/emoji_paths.xml. */
        const val CACHE_DIR = "emoji"

        const val SHARED_FILE_TTL_MS = 2 * 60 * 1000L

        /** Removes shared emoji images older than [maxAgeMs] (all of them with 0). */
        fun deleteOldFiles(dir: File, maxAgeMs: Long = SHARED_FILE_TTL_MS) {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            dir.listFiles()?.filter { it.isFile && it.lastModified() <= cutoff }?.forEach { it.delete() }
        }

        fun cacheDir(context: Context) = File(context.cacheDir, CACHE_DIR)
    }
}
