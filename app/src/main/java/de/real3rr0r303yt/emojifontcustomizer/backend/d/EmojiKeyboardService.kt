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
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import de.real3rr0r303yt.emojifontcustomizer.R

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
    private lateinit var tabs: List<Pair<Panel, ImageView>>
    private lateinit var clipPill: LinearLayout
    private lateinit var clipPillText: TextView
    private lateinit var letterPanel: LinearLayout
    private lateinit var emojiPanel: LinearLayout
    private lateinit var clipPanelScroll: ScrollView
    private lateinit var clipPanel: LinearLayout
    private lateinit var emojiCategoryBar: LinearLayout
    private lateinit var emojiStatus: TextView
    private val emojiAdapter = EmojiAdapter()

    private val letterKeys = mutableListOf<Pair<TextView, KeySpec.Text>>()
    private var shiftKey: ImageView? = null
    private var popup: PopupWindow? = null
    private var preview: PopupWindow? = null
    private var previewText: TextView? = null
    private val hideClipPill = Runnable { refreshClips() }

    private val handler = Handler(Looper.getMainLooper())
    private val repeatDelete = object : Runnable {
        override fun run() {
            deleteBackward()
            handler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    // ---------------------------------------------------------------------
    // Look: Nothing-style deep black, flat grey keys, red accent
    // ---------------------------------------------------------------------

    private val dp get() = resources.displayMetrics.density
    private val night
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val bgColor get() = if (night) Color.BLACK else Color.rgb(0xF2, 0xF2, 0xF2)
    private val keyColor get() = if (night) Color.rgb(0x1F, 0x1F, 0x1F) else Color.WHITE
    private val specialKeyColor get() = if (night) Color.rgb(0x33, 0x33, 0x33) else Color.rgb(0xDA, 0xDA, 0xDA)
    private val rippleColor get() = if (night) Color.argb(0x55, 0xFF, 0xFF, 0xFF) else Color.argb(0x33, 0, 0, 0)
    private val fgColor get() = if (night) Color.WHITE else Color.rgb(0x11, 0x11, 0x11)
    private val mutedColor get() = if (night) Color.rgb(0x8E, 0x8E, 0x8E) else Color.rgb(0x6E, 0x6E, 0x6E)

    /** Nothing red. */
    private val accentColor = Color.rgb(0xD7, 0x19, 0x21)

    private val keyTypeface: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val labelTypeface: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun px(value: Int) = (value * dp).toInt()

    /** Height setting from the app (Keyboard card), applied when the view is built. */
    private var heightScale = 1f

    /**
     * Row height adapts to the screen: about 8.4 % of the screen height in portrait
     * (64–78 dp, as tall as Gboard at its default height), flatter in landscape, then
     * scaled by the user's height setting.
     */
    private fun rowHeightPx(): Int {
        val metrics = resources.displayMetrics
        val screenDp = metrics.heightPixels / metrics.density
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val rowDp = if (landscape) (screenDp * 0.12f).coerceIn(44f, 58f) else (screenDp * 0.084f).coerceIn(64f, 78f)
        return (rowDp * heightScale * metrics.density).toInt()
    }

    private fun rounded(color: Int, radiusDp: Float = 12f) = GradientDrawable().apply {
        cornerRadius = radiusDp * dp
        setColor(color)
    }

    /** Key surface with a ripple on touch. */
    private fun keyBackground(color: Int, radiusDp: Float = 12f): Drawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), rounded(color, radiusDp), rounded(Color.WHITE, radiusDp))

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
        heightScale = KeyboardPrefs.heightScale(this)
        popup?.dismiss()
        preview?.dismiss()
        preview = null
        previewText = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(px(4), px(4), px(4), px(10))
        }

        // Toolbar: three icon tabs, and a paste pill only right after copying something.
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabs = listOf(
            Panel.LETTERS to (R.drawable.ic_kb_keyboard to "Letters"),
            Panel.EMOJI to (R.drawable.ic_kb_emoji to "Emoji"),
            Panel.CLIPBOARD to (R.drawable.ic_kb_clipboard to "Clipboard"),
        ).map { (target, iconAndLabel) ->
            val (icon, label) = iconAndLabel
            target to iconButton(icon, label) { showPanel(target) }.also {
                toolbar.addView(it, LinearLayout.LayoutParams(px(60), px(44)).apply { setMargins(px(2), 0, px(2), 0) })
            }
        }
        toolbar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        clipPillText = TextView(this).apply {
            setTextColor(fgColor)
            typeface = keyTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = px(150)
        }
        clipPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), 0, px(14), 0)
            background = keyBackground(keyColor, radiusDp = 18f)
            visibility = View.GONE
            addView(ImageView(this@EmojiKeyboardService).apply {
                setImageResource(R.drawable.ic_kb_clipboard)
                imageTintList = ColorStateList.valueOf(mutedColor)
            }, LinearLayout.LayoutParams(px(18), px(18)).apply { marginEnd = px(8) })
            addView(clipPillText)
        }
        toolbar.addView(clipPill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(38)).apply {
            marginEnd = px(4)
        })
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52)))

        // Panels share one fixed height so switching never makes the keyboard jump.
        val content = FrameLayout(this)
        letterPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        emojiPanel = buildEmojiPanel()
        clipPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(6), 0, px(6), px(6))
        }
        clipPanelScroll = ScrollView(this).apply { addView(clipPanel) }
        listOf(letterPanel, emojiPanel, clipPanelScroll).forEach {
            content.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeightPx() * 4))

        inputView = root
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // The height was changed in the app since the view was built: rebuild it.
        if (KeyboardPrefs.heightScale(this) != heightScale) setInputView(onCreateInputView())
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
            pack == null -> ""
            acceptsImages(info) -> "${pack.nameWithoutExtension} · image"
            else -> "${pack.nameWithoutExtension} · text"
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
        hidePreview()
        handler.removeCallbacks(repeatDelete)
        super.onFinishInputView(finishingInput)
    }

    private fun showPanel(target: Panel) {
        val changed = target != panel
        panel = target
        popup?.dismiss()
        hidePreview()
        val views = mapOf(Panel.LETTERS to letterPanel, Panel.EMOJI to emojiPanel, Panel.CLIPBOARD to clipPanelScroll)
        views.forEach { (p, view) ->
            if (p == target) {
                if (changed || view.visibility != View.VISIBLE) {
                    view.alpha = 0f
                    view.visibility = View.VISIBLE
                    view.animate().alpha(1f).setDuration(PANEL_FADE_MS).start()
                }
            } else {
                view.animate().cancel()
                view.visibility = View.GONE
            }
        }
        tabs.forEach { (p, view) ->
            val selected = p == target
            view.imageTintList = ColorStateList.valueOf(if (selected) fgColor else mutedColor)
            view.background = if (selected) keyBackground(specialKeyColor, radiusDp = 20f) else keyBackground(Color.TRANSPARENT, radiusDp = 20f)
        }
        when (target) {
            Panel.EMOJI -> showEmojiCategory(emojiCategory)
            Panel.CLIPBOARD -> refreshClipPanel()
            Panel.LETTERS -> Unit
        }
        refreshClips()
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
            setMargins(px(3), px(6), px(3), px(6))
        }

    private fun keyFor(spec: KeySpec): View = when (spec) {
        is KeySpec.Text -> key(spec.value, keyColor, textSp = 26f) { typeText(spec.value) }.also { view ->
            letterKeys += view to spec
            attachPreview(view)
            if (spec.popup.isNotEmpty()) {
                view.setOnLongClickListener {
                    showPopup(it, spec.popup)
                    true
                }
            }
        }
        is KeySpec.Shift -> iconKey(R.drawable.ic_kb_shift, specialKeyColor, "Shift") {
            shift.onShiftTap(SystemClock.uptimeMillis())
            refreshShift()
        }.also { shiftKey = it }
        is KeySpec.Backspace -> iconKey(R.drawable.ic_kb_backspace, specialKeyColor, "Delete") {}.also {
            repeatingDelete(it)
        }
        KeySpec.Enter -> iconKey(enterIcon(), accentColor, "Enter", tint = Color.WHITE) { enter() }
        is KeySpec.Space -> key(language.label, keyColor, textSp = 14f, description = "Space") { typeText(" ") }.apply {
            setTextColor(mutedColor)
        }
        KeySpec.Language -> iconKey(R.drawable.ic_kb_globe, specialKeyColor, "Language: ${language.label}") {
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
        is KeySpec.Page -> key(spec.label, specialKeyColor, textSp = 16f) {
            page = spec.target
            buildLetters()
        }.apply { typeface = labelTypeface }
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
        includeFontPadding = false
        typeface = keyTypeface
        setTextColor(fgColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
        background = keyBackground(color)
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    }

    private fun iconKey(
        icon: Int,
        color: Int,
        description: String,
        tint: Int = fgColor,
        onClick: () -> Unit,
    ): ImageView = ImageView(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        background = keyBackground(color)
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    }

    /** Flat icon button (toolbar, emoji categories, clipboard panel). */
    private fun iconButton(icon: Int, description: String, onClick: () -> Unit): ImageView =
        iconKey(icon, Color.TRANSPARENT, description, tint = mutedColor, onClick = onClick).apply {
            background = keyBackground(Color.TRANSPARENT, radiusDp = 20f)
        }

    /** Gboard-style balloon above the pressed letter; touches still reach the key. */
    private fun attachPreview(view: TextView) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> showPreview(v, (v as TextView).text)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> hidePreview()
            }
            false
        }
    }

    private fun showPreview(anchor: View, label: CharSequence) {
        if (anchor.width == 0) return
        val text = previewText ?: TextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = keyTypeface
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            background = rounded(specialKeyColor, radiusDp = 12f)
            elevation = 6 * dp
        }.also { previewText = it }
        text.text = label
        val width = (anchor.width * 1.2f).toInt()
        val height = (anchor.height * 1.15f).toInt()
        val balloon = preview ?: PopupWindow(text, width, height, false).apply {
            isTouchable = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }.also { preview = it }
        // Overlap the key's top quarter so the balloon also fits above the top row.
        val xOff = (anchor.width - width) / 2
        val yOff = -height - anchor.height + anchor.height / 4
        if (balloon.isShowing) {
            balloon.update(anchor, xOff, yOff, width, height)
        } else {
            balloon.width = width
            balloon.height = height
            balloon.showAsDropDown(anchor, xOff, yOff)
        }
    }

    private fun hidePreview() {
        preview?.dismiss()
    }

    private fun repeatingDelete(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.drawableHotspotChanged(event.x, event.y)
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

    private fun enterIcon(): Int = when (editorAction()) {
        EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_kb_search
        EditorInfo.IME_ACTION_SEND -> R.drawable.ic_kb_send
        EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_kb_next
        EditorInfo.IME_ACTION_DONE -> R.drawable.ic_kb_done
        else -> R.drawable.ic_kb_enter
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
            setImageResource(
                when (shift.mode) {
                    ShiftMode.OFF -> R.drawable.ic_kb_shift
                    ShiftMode.ONCE -> R.drawable.ic_kb_shift_on
                    ShiftMode.LOCKED -> R.drawable.ic_kb_caps
                },
            )
            val locked = shift.mode == ShiftMode.LOCKED
            imageTintList = ColorStateList.valueOf(if (locked) Color.WHITE else fgColor)
            background = keyBackground(if (locked) accentColor else specialKeyColor)
        }
    }

    private fun showPopup(anchor: View, options: List<String>) {
        popup?.dismiss()
        hidePreview()
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(specialKeyColor, radiusDp = 12f)
            elevation = 6 * dp
            setPadding(px(4), px(4), px(4), px(4))
        }
        val popupWindow = PopupWindow(row, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        options.forEach { option ->
            row.addView(
                key(KeyboardLayouts.applyShift(option, shift.isUpper), keyColor, textSp = 26f) {
                    typeText(option)
                    popupWindow.dismiss()
                },
                LinearLayout.LayoutParams(px(48), px(60)).apply { setMargins(px(2), 0, px(2), 0) },
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
        emojiCategoryBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        panel.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(emojiCategoryBar)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(48)),
        )
        val grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = px(54)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = px(2)
            selector = keyBackground(Color.TRANSPARENT)
            adapter = emojiAdapter
            setOnItemClickListener { _, cell, position, _ ->
                cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                sendEmoji(emojiAdapter.getItem(position))
            }
        }
        panel.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(
            key("ABC", specialKeyColor, textSp = 15f) { showPanel(Panel.LETTERS) }.apply { typeface = labelTypeface },
            keyParams(1.5f),
        )
        // The space bar shows which emoji pack is active and whether it is sent as image or text.
        emojiStatus = key("", keyColor, textSp = 12f, description = "Space") { typeText(" ") }.apply {
            setTextColor(mutedColor)
            ellipsize = TextUtils.TruncateAt.END
        }
        bottom.addView(emojiStatus, keyParams(5f))
        bottom.addView(
            iconKey(R.drawable.ic_kb_backspace, specialKeyColor, "Delete") {}.also { repeatingDelete(it) },
            keyParams(1.5f),
        )
        panel.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (rowHeightPx() * 0.9f).toInt()))
        return panel
    }

    private fun buildEmojiCategories() {
        emojiCategoryBar.removeAllViews()
        emojiCategoryBar.addView(iconButton(R.drawable.ic_kb_recent, "Recent") { showEmojiCategory(0) }, categoryParams())
        EmojiCatalog.categories.forEachIndexed { index, category ->
            emojiCategoryBar.addView(
                TextView(this).apply {
                    text = category.icon
                    contentDescription = category.title
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    typeface = renderer?.typeface ?: Typeface.DEFAULT
                    background = keyBackground(Color.TRANSPARENT, radiusDp = 20f)
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        showEmojiCategory(index + 1)
                    }
                },
                categoryParams(),
            )
        }
    }

    private fun categoryParams() = LinearLayout.LayoutParams(px(52), px(40)).apply { setMargins(px(2), 0, px(2), 0) }

    /** Index 0 is "recent", then [EmojiCatalog.categories]. */
    private fun showEmojiCategory(index: Int) {
        emojiCategory = index
        val r = renderer ?: return
        emojiAdapter.update(
            if (index == 0) KeyboardPrefs.recent() else r.emojiFor(EmojiCatalog.categories[index - 1]),
            r.typeface,
        )
        for (i in 0 until emojiCategoryBar.childCount) {
            val child = emojiCategoryBar.getChildAt(i)
            val selected = i == index
            child.alpha = if (selected) 1f else 0.55f
            child.background = keyBackground(if (selected) specialKeyColor else Color.TRANSPARENT, radiusDp = 20f)
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
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56))
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

    /**
     * Toolbar pill: offers the newest clip for [CLIP_PILL_MS] after it was copied, then
     * the toolbar is clean again. The full history is in the clipboard panel.
     */
    private fun refreshClips() {
        if (inputView == null) return
        handler.removeCallbacks(hideClipPill)
        val now = SystemClock.elapsedRealtime()
        val newest = clipHistory.items(now).firstOrNull()
        val age = newest?.let { now - it.addedAt } ?: Long.MAX_VALUE
        if (newest != null && age < CLIP_PILL_MS && panel != Panel.CLIPBOARD) {
            clipPillText.text = newest.label(18)
            clipPill.contentDescription = "Paste: ${newest.label(18)}"
            clipPill.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                paste(newest)
            }
            if (clipPill.visibility != View.VISIBLE) {
                clipPill.alpha = 0f
                clipPill.visibility = View.VISIBLE
                clipPill.animate().alpha(1f).setDuration(PANEL_FADE_MS).start()
            }
            handler.postDelayed(hideClipPill, CLIP_PILL_MS - age)
        } else {
            clipPill.visibility = View.GONE
        }
        if (panel == Panel.CLIPBOARD) refreshClipPanel()
    }

    private fun refreshClipPanel() {
        clipPanel.removeAllViews()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(6), px(4), 0, px(4))
        }
        header.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@EmojiKeyboardService).apply {
                    text = "Clipboard"
                    typeface = labelTypeface
                    setTextColor(fgColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                })
                addView(TextView(this@EmojiKeyboardService).apply {
                    text = "Memory only · nothing is saved"
                    setTextColor(mutedColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                })
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            iconButton(R.drawable.ic_kb_trash, "Clear clipboard") {
                clipHistory.clear()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { clipboard?.clearPrimaryClip() }
                }
                refreshClips()
                refreshClipPanel()
            },
            LinearLayout.LayoutParams(px(48), px(44)),
        )
        clipPanel.addView(header)

        val items = clipHistory.items(SystemClock.elapsedRealtime())
        if (items.isEmpty()) {
            clipPanel.addView(TextView(this).apply {
                text = "Copied text shows up here. Sensitive clips (passwords) are masked and disappear after 60 seconds."
                setTextColor(mutedColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(px(6), px(12), px(6), px(12))
            })
        }
        items.forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = keyBackground(keyColor, radiusDp = 12f)
                setPadding(px(14), px(4), px(4), px(4))
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    paste(entry)
                }
            }
            row.addView(
                TextView(this).apply {
                    text = entry.label(120)
                    contentDescription = "Paste: ${entry.label(40)}"
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(fgColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            row.addView(
                iconButton(R.drawable.ic_kb_close, "Remove") {
                    clipHistory.remove(entry)
                    refreshClips()
                    refreshClipPanel()
                },
                LinearLayout.LayoutParams(px(44), px(44)),
            )
            clipPanel.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, px(4), 0, px(4))
            })
        }
    }

    private fun paste(entry: ClipEntry) {
        currentInputConnection?.commitText(entry.text, 1)
        updateAutoShift()
    }

    private companion object {
        const val PANEL_FADE_MS = 150L
        const val CLIP_PILL_MS = 60_000L
        const val REPEAT_START_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
    }
}
