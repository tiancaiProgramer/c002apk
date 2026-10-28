package com.example.c001apk.compose.ui.feed.article

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import com.example.c001apk.compose.util.EmojiTextWatcher
import com.example.c001apk.compose.util.FastDeleteAtUserKeyListener
import com.example.c001apk.compose.ui.component.EditorAppearance
import com.example.c001apk.compose.ui.component.rememberEditorViewContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Same native text sizing, line spacing, emoji and @/# rendering as the dynamic editor. */
internal class ArticleParagraphEditText(context: Context) : AppCompatEditText(context) {
    var bindingText = false
    var caretChanged: ((Int, Int) -> Unit)? = null
    var blockChanged: ((ArticleBlock) -> Unit)? = null
    var boundBlock: ArticleBlock? = null
    var renderedText = ""
    var cursorColor: Int? = null
    var caretReveal: ((Rect) -> Unit)? = null
    private var revealPending = false
    private val revealRunnable = Runnable { dispatchCaretReveal() }
    fun requestCaretReveal() {
        if (!isFocused || bindingText) return
        revealPending = true
        removeCallbacks(revealRunnable)
        post(revealRunnable)
    }
    private fun dispatchCaretReveal() {
        if (!revealPending || !isFocused || bindingText || !isAttachedToWindow || isLayoutRequested) return
        val textLayout = layout ?: return
        val offset = selectionEnd.coerceIn(0, textLayout.text.length)
        val line = textLayout.getLineForOffset(offset)
        revealPending = false
        caretReveal?.invoke(Rect(0, textLayout.getLineTop(line) + totalPaddingTop - scrollY,
            width, textLayout.getLineBottom(line) + totalPaddingTop - scrollY + lineHeight))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (revealPending) { removeCallbacks(revealRunnable); post(revealRunnable) }
    }
    private var pinContainer: PinnableContainer? = null
    private var focusPin: PinnableContainer.PinnedHandle? = null
    fun bindPinContainer(next: PinnableContainer?) {
        if (pinContainer !== next) { releaseFocusPin(); pinContainer = next }
        updateFocusPin(isFocused)
    }
    fun updateFocusPin(focused: Boolean) {
        if (focused) {
            if (focusPin == null) focusPin = pinContainer?.pin()
        } else releaseFocusPin()
    }
    private fun releaseFocusPin() {
        val handle = focusPin
        focusPin = null
        handle?.release()
    }
    fun deactivateBinding() {
        bindingText = true
        caretChanged = null
        blockChanged = null
        caretReveal = null
        revealPending = false
        removeCallbacks(revealRunnable)
        cancelPendingInputEvents()
        isPressed = false
        // onReset can mean temporary deactivation, not a different paragraph.
        // Keep focus/composition until update confirms a real identity change.
    }
    fun releaseBinding() {
        deactivateBinding()
        releaseFocusPin()
        pinContainer = null
        clearFocus()
        clearComposingText()
        boundBlock = null
    }
    override fun onSelectionChanged(start: Int, end: Int) {
        super.onSelectionChanged(start, end)
        if (!bindingText && start >= 0 && end >= 0) caretChanged?.invoke(start, end)
    }
}

private class CaretRevealTask { var job: Job? = null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArticleParagraphField(
    block: ArticleBlock, enabled: Boolean, requestFocus: Boolean, focusOffset: Int, keyboardOnFocus: Boolean,
    onChange: (ArticleBlock) -> Unit, onCaret: (Int, Int) -> Unit,
    onFocusConsumed: () -> Unit, minLines: Int,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val editorContext = rememberEditorViewContext()
    val selectionColor = EditorAppearance.primaryDark(editorContext, primary)
    val pinContainer = LocalPinnableContainer.current
    val bringIntoView = remember(block.id) { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val revealTask = remember(block.id) { CaretRevealTask() }
    DisposableEffect(revealTask) { onDispose { revealTask.job?.cancel() } }
    var native by remember(block.id) { mutableStateOf<ArticleParagraphEditText?>(null) }
    LaunchedEffect(requestFocus, native, focusOffset) {
        if (requestFocus) native?.let { field ->
            withFrameNanos { }
            val focused = field.isFocused
            field.requestFocus()
            field.setSelection(focusOffset.coerceIn(0, field.length()))
            if (!focused && keyboardOnFocus) field.context.getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            field.requestCaretReveal()
            onFocusConsumed()
        }
    }
    AndroidView(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).bringIntoViewRequester(bringIntoView), factory = { context ->
        ArticleParagraphEditText(context).apply {
            native = this
            background = null
            setPadding(0, 0, 0, 0)
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setHorizontallyScrolling(false)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(0f, 1.2f)
            setOnKeyListener(FastDeleteAtUserKeyListener)
            hint = "开始写正文…"
            setOnFocusChangeListener { _, focused ->
                updateFocusPin(focused)
                if (focused && !bindingText) {
                    caretChanged?.invoke(selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0))
                    requestCaretReveal()
                }
            }
            addTextChangedListener(EmojiTextWatcher(context, textSize, primary) {
                renderedText = text.toString()
                val current = boundBlock
                if (!bindingText && current != null && renderedText != current.text) {
                    blockChanged?.invoke(current.copy(text = renderedText))
                    requestCaretReveal()
                }
            })
        }
    // Focused native editors must not enter AndroidView's cross-item reuse pool.
    // Pin the paragraph, keep its input connection, and reuse only Compose items.
    }, onReset = null, onRelease = { it.releaseBinding() }, update = { field ->
        if (native !== field) native = field
        field.bindingText = true
        val changedIdentity = field.boundBlock?.id != block.id
        if (changedIdentity && field.boundBlock != null) {
            field.clearFocus()
            field.clearComposingText()
        }
        field.bindPinContainer(pinContainer)
        field.boundBlock = block
        field.blockChanged = onChange
        field.caretChanged = onCaret
        field.caretReveal = { rect ->
            revealTask.job?.cancel()
            revealTask.job = scope.launch {
                bringIntoView.bringIntoView(androidx.compose.ui.geometry.Rect(rect.left.toFloat(), rect.top.toFloat(),
                    rect.right.toFloat(), rect.bottom.toFloat()))
            }
        }
        if (field.isEnabled != enabled) field.isEnabled = enabled
        if (field.minLines != minLines) field.minLines = minLines
        if (field.currentTextColor != textColor) field.setTextColor(textColor)
        if (field.currentHintTextColor != hintColor) field.setHintTextColor(hintColor)
        val highlight = ColorUtils.setAlphaComponent(selectionColor, 128)
        if (field.highlightColor != highlight) field.highlightColor = highlight
        if (Build.VERSION.SDK_INT >= 29 && field.cursorColor != primary) {
            field.cursorColor = primary
            field.textCursorDrawable = GradientDrawable().apply {
                setColor(primary)
                setSize((2 * field.resources.displayMetrics.density).toInt().coerceAtLeast(2), field.textSize.toInt().coerceAtLeast(1))
            }
        }
        if (field.renderedText != block.text) {
            val selection = if (changedIdentity) 0 else field.selectionStart.coerceAtLeast(0).coerceAtMost(block.text.length)
            field.setText(block.text)
            field.setSelection(selection)
        } else if (changedIdentity) field.setSelection(0)
        field.bindingText = false
    })
}
