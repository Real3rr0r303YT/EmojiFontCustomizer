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

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

/**
 * Emoji keyboard. Works without root: when the target app accepts images
 * (`commitContent`), emoji from the selected pack are sent as PNGs so the recipient
 * sees exactly that style; otherwise the plain Unicode emoji is inserted.
 *
 * A clipboard bar above the emoji shows recent clips as chips; tapping one pastes it.
 * The history lives in memory only (see [ClipboardHistory]).
 *
 * The keyboard never reads the text field, and the app has no internet permission.
 */
class EmojiKeyboardService : InputMethodService() {

    private var renderer: EmojiRenderer? = null
    private val adapter = EmojiAdapter()
    private var categoryIndex = 1

    private lateinit var categoryBar: LinearLayout
    private lateinit var statusLabel: TextView
    private lateinit var clipScroll: HorizontalScrollView
    private lateinit var clipBar: LinearLayout

    private var clipboard: ClipboardManager? = null
    private val clipHistory = ClipboardHistory()
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        readClipboard()
        refreshClipBar()
    }

    private val dp get() = resources.displayMetrics.density
    private val night
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val bgColor get() = if (night) Color.rgb(0x1F, 0x1F, 0x1F) else Color.rgb(0xEE, 0xEE, 0xEE)
    private val fgColor get() = if (night) Color.WHITE else Color.BLACK

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreate() {
        super.onCreate()
        clipboard = getSystemService(ClipboardManager::class.java)?.apply {
            addPrimaryClipChangedListener(clipListener)
        }
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        clipHistory.clear()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        clipBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        clipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(clipBar)
        }
        root.addView(
            clipScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        categoryBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(
            HorizontalScrollView(this).apply { addView(categoryBar) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        val grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = (48 * dp).toInt()
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            adapter = this@EmojiKeyboardService.adapter
            setOnItemClickListener { _, _, position, _ -> send(this@EmojiKeyboardService.adapter.getItem(position)) }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (220 * dp).toInt()))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bottom.addView(key("🌐", "Switch keyboard") { switchKeyboard() })
        statusLabel = TextView(this).apply {
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }
        bottom.addView(statusLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(key("⌫", "Delete") { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) })
        root.addView(bottom)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val pack = KeyboardPrefs.emojiPack(this)
        val current = renderer
        if (current == null || current.packFile != pack || current.packModified != (pack?.lastModified() ?: 0L)) {
            renderer = EmojiRenderer(this, pack)
        }
        readClipboard()
        refreshClipBar()
        buildCategoryBar()
        showCategory(categoryIndex)
        statusLabel.text = when {
            pack == null -> "System emoji · pick a pack in the app"
            acceptsImages(info) -> "${pack.nameWithoutExtension} · sends images"
            else -> "${pack.nameWithoutExtension} · app accepts text only"
        }
    }

    /**
     * Android 10+ only lets the focused app or the default keyboard read the clipboard,
     * so reading can fail; that is not an error.
     */
    private fun readClipboard() {
        val clip = runCatching { clipboard?.primaryClip }.getOrNull() ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        val sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        clipHistory.add(text, sensitive, SystemClock.elapsedRealtime())
    }

    private fun refreshClipBar() {
        if (!::clipBar.isInitialized) return
        clipBar.removeAllViews()
        val items = clipHistory.items(SystemClock.elapsedRealtime())
        clipScroll.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        items.forEach { entry ->
            clipBar.addView(chip(entry.label()).apply {
                setOnClickListener { currentInputConnection?.commitText(entry.text, 1) }
                setOnLongClickListener {
                    clipHistory.remove(entry)
                    refreshClipBar()
                    true
                }
            })
        }
        clipBar.addView(key("🗑", "Clear clipboard") {
            clipHistory.clear()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { clipboard?.clearPrimaryClip() }
            }
            refreshClipBar()
        })
    }

    private fun chip(label: String): TextView =
        TextView(this).apply {
            text = label
            contentDescription = "Paste: $label"
            maxLines = 1
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            val padH = (12 * dp).toInt()
            val padV = (8 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(if (night) Color.rgb(0x3A, 0x3A, 0x3A) else Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                val margin = (4 * dp).toInt()
                setMargins(margin, margin, margin, margin)
            }
        }

    private fun buildCategoryBar() {
        categoryBar.removeAllViews()
        val icons = listOf("🕘") + EmojiCatalog.categories.map { it.icon }
        icons.forEachIndexed { index, icon ->
            categoryBar.addView(key(icon, if (index == 0) "Recent" else EmojiCatalog.categories[index - 1].title) {
                showCategory(index)
            })
        }
    }

    /** Index 0 is "recent", then [EmojiCatalog.categories]. */
    private fun showCategory(index: Int) {
        categoryIndex = index
        val r = renderer ?: return
        adapter.update(
            if (index == 0) KeyboardPrefs.recent(this) else r.emojiFor(EmojiCatalog.categories[index - 1]),
            r.typeface,
        )
        for (i in 0 until categoryBar.childCount) {
            categoryBar.getChildAt(i).alpha = if (i == index) 1f else 0.5f
        }
    }

    private fun acceptsImages(info: EditorInfo?): Boolean =
        info != null && EditorInfoCompat.getContentMimeTypes(info).any {
            ClipDescription.compareMimeTypes("image/png", it)
        }

    private fun send(emoji: String) {
        val connection = currentInputConnection ?: return
        val info = currentInputEditorInfo
        KeyboardPrefs.addRecent(this, emoji)
        val r = renderer
        if (r?.packFile != null && acceptsImages(info)) {
            val sent = runCatching {
                val uri = FileProvider.getUriForFile(this, "$packageName.emojiprovider", r.renderPng(emoji))
                val content = InputContentInfoCompat(uri, ClipDescription(emoji, arrayOf("image/png")), null)
                InputConnectionCompat.commitContent(
                    connection,
                    info,
                    content,
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                    null,
                )
            }.getOrDefault(false)
            if (sent) return
        }
        connection.commitText(emoji, 1)
    }

    private fun switchKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && switchToPreviousInputMethod()) return
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    private fun key(label: String, description: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            contentDescription = description
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            minWidth = (48 * dp).toInt()
            minHeight = (44 * dp).toInt()
            setOnClickListener { onClick() }
        }

    private inner class EmojiAdapter : BaseAdapter() {
        private var items: List<String> = emptyList()
        private var typeface: Typeface? = null

        fun update(items: List<String>, typeface: Typeface?) {
            this.items = items
            this.typeface = typeface
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): String = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView as? TextView ?: TextView(this@EmojiKeyboardService).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * dp).toInt())
            }
            view.typeface = typeface ?: Typeface.DEFAULT
            view.text = items[position]
            return view
        }
    }
}
