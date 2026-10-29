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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

/**
 * The app's keyboard: a full text keyboard (German QWERTZ / English QWERTY with
 * numbers, symbols, umlauts and accents), an emoji panel rendered with the selected
 * emoji pack, and a clipboard panel.
 *
 * Emoji: when the target app accepts images (`commitContent`), emoji from the pack are
 * sent as PNGs so the recipient sees exactly that style; otherwise the Unicode emoji
 * is inserted.
 *
 * Privacy: everything stays on the device. The app has no internet permission. There
 * are no suggestions, no dictionary and no learning. Typed text is never stored or read
 * back; auto-capitalisation only asks the editor for its caps flag. Clipboard history
 * and recent emoji live in memory only.
 */
class EmojiKeyboardService : InputMethodService() {

    private enum class Panel { LETTERS, EMOJI, CLIPBOARD }

    private var panel = Panel.LETTERS
    private var page = KeyboardPage.LETTERS
    private var language = KeyboardLanguage.GERMAN
    private val shift = ShiftController()
    private var renderer: EmojiRenderer? = null
    private var emojiCategory = 1

    private var clipboard: ClipboardManager? = null
    private val clipHistory = ClipboardHistory()
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        readClipboard()
        refreshClips()
    }

    private var inputView: View? = null
    private lateinit var tabs: List<Pair<Panel, TextView>>
    private lateinit var clipChips: LinearLayout
    private lateinit var letterPanel: LinearLayout
    private lateinit var emojiPanel: LinearLayout
    private lateinit var clipPanelScroll: ScrollView
    private lateinit var clipPanel: LinearLayout
    private lateinit var emojiCategoryBar: LinearLayout
    private lateinit var emojiStatus: TextView
    private val emojiAdapter = EmojiAdapter()

    private val letterKeys = mutableListOf<Pair<TextView, KeySpec.Text>>()
    private var shiftKey: TextView? = null
    private var popup: PopupWindow? = null

    private val handler = Handler(Looper.getMainLooper())
    private val repeatDelete = object : Runnable {
        override fun run() {
            deleteBackward()
            handler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    // ---------------------------------------------------------------------
    // Look
    // ---------------------------------------------------------------------

    private val dp get() = resources.displayMetrics.density
    private val night
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val bgColor get() = if (night) Color.rgb(0x1B, 0x1B, 0x1B) else Color.rgb(0xE4, 0xE4, 0xE4)
    private val keyColor get() = if (night) Color.rgb(0x3B, 0x3B, 0x3B) else Color.WHITE
    private val specialKeyColor get() = if (night) Color.rgb(0x2B, 0x2B, 0x2B) else Color.rgb(0xCC, 0xCC, 0xCC)
    private val pressedColor get() = if (night) Color.rgb(0x5A, 0x5A, 0x5A) else Color.rgb(0xA8, 0xA8, 0xA8)
    private val accentColor = Color.rgb(0x4A, 0x90, 0xE2)
    private val fgColor get() = if (night) Color.WHITE else Color.BLACK

    private fun px(value: Int) = (value * dp).toInt()

    private fun rounded(color: Int) = GradientDrawable().apply {
        cornerRadius = 8 * dp
        setColor(color)
    }

    private fun keyBackground(color: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(pressedColor))
        addState(intArrayOf(), rounded(color))
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        language = KeyboardPrefs.language(this)
        EmojiRenderer.deleteOldFiles(EmojiRenderer.cacheDir(this))
        clipboard = getSystemService(ClipboardManager::class.java)?.apply {
            addPrimaryClipChangedListener(clipListener)
        }
    }

    override fun onDestroy() {
        // Leaves no trace of sent emoji behind once the keyboard stops.
        EmojiRenderer.deleteOldFiles(EmojiRenderer.cacheDir(this), maxAgeMs = 0)
        clipboard?.removePrimaryClipChangedListener(clipListener)
        clipHistory.clear()
        popup?.dismiss()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(px(2), px(2), px(2), px(4))
        }

        // Toolbar: panel tabs and the latest clipboard entries.
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabs = listOf(Panel.LETTERS to "⌨", Panel.EMOJI to "😀", Panel.CLIPBOARD to "📋").map { (target, icon) ->
            target to plainKey(icon, "Show ${target.name.lowercase()}") { showPanel(target) }.also {
                toolbar.addView(it, LinearLayout.LayoutParams(px(48), px(40)))
            }
        }
        clipChips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(clipChips)
            },
            LinearLayout.LayoutParams(0, px(40), 1f),
        )
        root.addView(toolbar)

        // Panels share one fixed height so switching never makes the keyboard jump.
        val content = FrameLayout(this)
        letterPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emojiPanel = buildEmojiPanel()
        clipPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(4), 0, px(4), 0)
        }
        clipPanelScroll = ScrollView(this).apply { addView(clipPanel) }
        listOf(letterPanel, emojiPanel, clipPanelScroll).forEach {
            content.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ROW_HEIGHT_DP * 4)))

        inputView = root
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val pack = KeyboardPrefs.emojiPack(this)
        val current = renderer
        if (current == null || current.packFile != pack || current.packModified != (pack?.lastModified() ?: 0L)) {
            renderer = EmojiRenderer(this, pack)
        }
        if (!restarting) {
            panel = Panel.LETTERS
            page = when ((info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME ->
                    KeyboardPage.SYMBOLS
                else -> KeyboardPage.LETTERS
            }
        }
        emojiStatus.text = when {
            pack == null -> "System emoji · pick a pack in the app"
            acceptsImages(info) -> "${pack.nameWithoutExtension} · sends images"
            else -> "${pack.nameWithoutExtension} · app accepts text only"
        }
        readClipboard()
        refreshClips()
        buildLetters()
        updateAutoShift()
        buildEmojiCategories()
        showPanel(panel)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        popup?.dismiss()
        handler.removeCallbacks(repeatDelete)
        super.onFinishInputView(finishingInput)
    }

    private fun showPanel(target: Panel) {
        panel = target
        popup?.dismiss()
        letterPanel.visibility = if (target == Panel.LETTERS) View.VISIBLE else View.GONE
        emojiPanel.visibility = if (target == Panel.EMOJI) View.VISIBLE else View.GONE
        clipPanelScroll.visibility = if (target == Panel.CLIPBOARD) View.VISIBLE else View.GONE
        tabs.forEach { (p, view) -> view.alpha = if (p == target) 1f else 0.45f }
        when (target) {
            Panel.EMOJI -> showEmojiCategory(emojiCategory)
            Panel.CLIPBOARD -> refreshClipPanel()
            Panel.LETTERS -> Unit
        }
    }

    // ---------------------------------------------------------------------
    // Letters, numbers & symbols
    // ---------------------------------------------------------------------

    private fun buildLetters() {
        letterPanel.removeAllViews()
        letterKeys.clear()
        shiftKey = null
        val rows = KeyboardLayouts.rows(language, page)
        val maxWeight = rows.maxOf { row -> row.sumOf { it.width.toDouble() } }.toFloat()
        rows.forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            // Shorter rows (e.g. QWERTY "asdfghjkl") are centred with spacers.
            val padding = (maxWeight - row.sumOf { it.width.toDouble() }.toFloat()) / 2f
            if (padding > 0f) line.addView(View(this), LinearLayout.LayoutParams(0, 1, padding))
            row.forEach { spec -> line.addView(keyFor(spec), keyParams(spec.width)) }
            if (padding > 0f) line.addView(View(this), LinearLayout.LayoutParams(0, 1, padding))
            letterPanel.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        refreshShift()
    }

    private fun keyParams(weight: Float) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(px(3), px(4), px(3), px(4))
        }

    private fun keyFor(spec: KeySpec): View = when (spec) {
        is KeySpec.Text -> key(spec.value, keyColor, textSp = 21f) { typeText(spec.value) }.also { view ->
            letterKeys += view to spec
            if (spec.popup.isNotEmpty()) {
                view.setOnLongClickListener {
                    showPopup(it, spec.popup)
                    true
                }
            }
        }
        KeySpec.Shift -> key("⇧", specialKeyColor, description = "Shift") {
            shift.onShiftTap(SystemClock.uptimeMillis())
            refreshShift()
        }.also { shiftKey = it }
        KeySpec.Backspace -> key("⌫", specialKeyColor, description = "Delete") {}.also { repeatingDelete(it) }
        KeySpec.Enter -> key(enterLabel(), accentColor, description = "Enter") { enter() }.apply {
            setTextColor(Color.WHITE)
        }
        KeySpec.Space -> key(language.label, keyColor, textSp = 13f, description = "Space") { typeText(" ") }
        KeySpec.Language -> key("🌐", specialKeyColor, description = "Language: ${language.label}") {
            language = language.next()
            KeyboardPrefs.setLanguage(this, language)
            buildLetters()
        }.apply {
            // Long-press: switch to another keyboard installed on the device.
            setOnLongClickListener {
                getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
                true
            }
        }
        KeySpec.Emoji -> key("😀", specialKeyColor, description = "Emoji") { showPanel(Panel.EMOJI) }
        is KeySpec.Page -> key(spec.label, specialKeyColor, textSp = 14f) {
            page = spec.target
            buildLetters()
        }
    }

    private fun key(
        label: String,
        color: Int,
        textSp: Float = 18f,
        description: String = label,
        onClick: () -> Unit,
    ): TextView = TextView(this).apply {
        text = label
        contentDescription = description
        gravity = Gravity.CENTER
        maxLines = 1
        setTextColor(fgColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        background = keyBackground(color)
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    }

    /** Flat key without background, used in the toolbar and emoji panel. */
    private fun plainKey(label: String, description: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            minWidth = px(44)
            minHeight = px(40)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }

    private fun repeatingDelete(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    deleteBackward()
                    handler.postDelayed(repeatDelete, REPEAT_START_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeatDelete)
                    updateAutoShift()
                }
            }
            true
        }
    }

    private fun typeText(value: String) {
        val connection = currentInputConnection ?: return
        connection.commitText(KeyboardLayouts.applyShift(value, shift.isUpper), 1)
        shift.onCharacterTyped()
        updateAutoShift()
    }

    /** Deletes like a hardware backspace key, so selections and emoji sequences work. */
    private fun deleteBackward() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun editorAction(): Int {
        val options = currentInputEditorInfo?.imeOptions ?: return EditorInfo.IME_ACTION_NONE
        if ((options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return EditorInfo.IME_ACTION_NONE
        return options and EditorInfo.IME_MASK_ACTION
    }

    private fun enterLabel(): String = when (editorAction()) {
        EditorInfo.IME_ACTION_GO -> "→"
        EditorInfo.IME_ACTION_SEARCH -> "🔍"
        EditorInfo.IME_ACTION_SEND -> "➤"
        EditorInfo.IME_ACTION_NEXT -> "⇥"
        EditorInfo.IME_ACTION_PREVIOUS -> "⇤"
        EditorInfo.IME_ACTION_DONE -> "✓"
        else -> "⏎"
    }

    private fun enter() {
        val connection = currentInputConnection ?: return
        val action = editorAction()
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            connection.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        updateAutoShift()
    }

    /** Capitalise at the start of a sentence if the text field asks for it. */
    private fun updateAutoShift() {
        val info = currentInputEditorInfo
        val isText = info != null && (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        val caps = isText && (currentInputConnection?.getCursorCapsMode(info!!.inputType) ?: 0) != 0
        shift.setAuto(caps)
        refreshShift()
    }

    private fun refreshShift() {
        letterKeys.forEach { (view, spec) -> view.text = KeyboardLayouts.applyShift(spec.value, shift.isUpper) }
        shiftKey?.apply {
            text = when (shift.mode) {
                ShiftMode.OFF -> "⇧"
                ShiftMode.ONCE -> "⬆"
                ShiftMode.LOCKED -> "⇪"
            }
            background = keyBackground(if (shift.mode == ShiftMode.OFF) specialKeyColor else accentColor)
        }
    }

    private fun showPopup(anchor: View, options: List<String>) {
        popup?.dismiss()
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(specialKeyColor)
            setPadding(px(4), px(4), px(4), px(4))
        }
        val popupWindow = PopupWindow(row, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        options.forEach { option ->
            row.addView(
                key(KeyboardLayouts.applyShift(option, shift.isUpper), keyColor, textSp = 21f) {
                    typeText(option)
                    popupWindow.dismiss()
                },
                LinearLayout.LayoutParams(px(40), px(48)).apply { setMargins(px(2), 0, px(2), 0) },
            )
        }
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popupWindow.showAsDropDown(anchor, 0, -anchor.height - row.measuredHeight)
        popup = popupWindow
    }

    // ---------------------------------------------------------------------
    // Emoji
    // ---------------------------------------------------------------------

    private fun buildEmojiPanel(): LinearLayout {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emojiCategoryBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        panel.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(emojiCategoryBar)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        val grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = px(46)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            adapter = emojiAdapter
            setOnItemClickListener { _, cell, position, _ ->
                cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                sendEmoji(emojiAdapter.getItem(position))
            }
        }
        panel.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bottom.addView(key("ABC", specialKeyColor, textSp = 14f) { showPanel(Panel.LETTERS) }, keyParams(1.5f))
        bottom.addView(key("␣", keyColor, description = "Space") { typeText(" ") }, keyParams(1.5f))
        emojiStatus = TextView(this).apply {
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        bottom.addView(emojiStatus, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 4f))
        bottom.addView(key("⌫", specialKeyColor, description = "Delete") {}.also { repeatingDelete(it) }, keyParams(1.5f))
        panel.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ROW_HEIGHT_DP)))
        return panel
    }

    private fun buildEmojiCategories() {
        emojiCategoryBar.removeAllViews()
        val icons = listOf("🕘") + EmojiCatalog.categories.map { it.icon }
        icons.forEachIndexed { index, icon ->
            val title = if (index == 0) "Recent" else EmojiCatalog.categories[index - 1].title
            emojiCategoryBar.addView(plainKey(icon, title) { showEmojiCategory(index) }.apply {
                typeface = renderer?.typeface ?: Typeface.DEFAULT
            })
        }
    }

    /** Index 0 is "recent", then [EmojiCatalog.categories]. */
    private fun showEmojiCategory(index: Int) {
        emojiCategory = index
        val r = renderer ?: return
        emojiAdapter.update(
            if (index == 0) KeyboardPrefs.recent() else r.emojiFor(EmojiCatalog.categories[index - 1]),
            r.typeface,
        )
        for (i in 0 until emojiCategoryBar.childCount) {
            emojiCategoryBar.getChildAt(i).alpha = if (i == index) 1f else 0.45f
        }
    }

    private fun acceptsImages(info: EditorInfo?): Boolean =
        info != null && EditorInfoCompat.getContentMimeTypes(info).any {
            ClipDescription.compareMimeTypes("image/png", it)
        }

    private fun sendEmoji(emoji: String) {
        val connection = currentInputConnection ?: return
        val info = currentInputEditorInfo
        KeyboardPrefs.addRecent(emoji)
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
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(46))
            }
            view.typeface = typeface ?: Typeface.DEFAULT
            view.text = items[position]
            return view
        }
    }

    // ---------------------------------------------------------------------
    // Clipboard
    // ---------------------------------------------------------------------

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

    private fun refreshClips() {
        if (inputView == null) return
        clipChips.removeAllViews()
        clipHistory.items(SystemClock.elapsedRealtime()).take(3).forEach { entry ->
            clipChips.addView(chip(entry.label(20)) { paste(entry) })
        }
        if (panel == Panel.CLIPBOARD) refreshClipPanel()
    }

    private fun refreshClipPanel() {
        clipPanel.removeAllViews()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = "Clipboard · memory only"
                setTextColor(fgColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(plainKey("🗑", "Clear clipboard") {
            clipHistory.clear()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { clipboard?.clearPrimaryClip() }
            }
            refreshClips()
            refreshClipPanel()
        })
        clipPanel.addView(header)

        val items = clipHistory.items(SystemClock.elapsedRealtime())
        if (items.isEmpty()) {
            clipPanel.addView(TextView(this).apply {
                text = "Copy something and it shows up here. Nothing is saved; sensitive clips are masked and vanish after 60 s."
                setTextColor(fgColor)
                alpha = 0.7f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, px(8), 0, px(8))
            })
        }
        items.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                chip(entry.label(80)) { paste(entry) }.apply { maxLines = 2 },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, px(3), 0, px(3))
                },
            )
            row.addView(plainKey("✕", "Remove") {
                clipHistory.remove(entry)
                refreshClips()
                refreshClipPanel()
            })
            clipPanel.addView(row)
        }
    }

    private fun paste(entry: ClipEntry) {
        currentInputConnection?.commitText(entry.text, 1)
        updateAutoShift()
    }

    private fun chip(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            contentDescription = "Paste: $label"
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(px(12), px(8), px(12), px(8))
            background = keyBackground(keyColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(px(3), px(3), px(3), px(3)) }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }

    private companion object {
        const val ROW_HEIGHT_DP = 54
        const val REPEAT_START_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
    }
}
