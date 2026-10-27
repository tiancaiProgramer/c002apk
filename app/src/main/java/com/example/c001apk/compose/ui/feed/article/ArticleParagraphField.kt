package com.example.c001apk.compose.ui.feed.article

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import com.example.c001apk.compose.util.EmojiTextWatcher
import com.example.c001apk.compose.util.FastDeleteAtUserKeyListener
import com.example.c001apk.compose.ui.component.EditorAppearance
import com.example.c001apk.compose.ui.component.rememberEditorViewContext

/** Same native text sizing, line spacing, emoji and @/# rendering as the dynamic editor. */
internal class ArticleParagraphEditText(context: Context) : AppCompatEditText(context) {
    var bindingText = false
    var caretChanged: ((Int, Int) -> Unit)? = null
    override fun onSelectionChanged(start: Int, end: Int) {
        super.onSelectionChanged(start, end)
        if (!bindingText && start >= 0 && end >= 0) caretChanged?.invoke(start, end)
    }
}

@Composable
internal fun ArticleParagraphField(
    block: ArticleBlock, enabled: Boolean, requestFocus: Boolean, focusOffset: Int, keyboardOnFocus: Boolean,
    onChange: (ArticleBlock) -> Unit, onCaret: (Int, Int) -> Unit,
    onFocusConsumed: () -> Unit, minLines: Int,
) {
    val currentBlock by rememberUpdatedState(block)
    val changed by rememberUpdatedState(onChange)
    val caret by rememberUpdatedState(onCaret)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val editorContext = rememberEditorViewContext()
    val selectionColor = EditorAppearance.primaryDark(editorContext, primary)
    var native by remember(block.id) { mutableStateOf<ArticleParagraphEditText?>(null) }
    LaunchedEffect(requestFocus, native, focusOffset) {
        if (requestFocus) native?.let { field ->
            withFrameNanos { }
            val focused = field.isFocused
            field.requestFocus()
            field.setSelection(focusOffset.coerceIn(0, field.length()))
            if (!focused && keyboardOnFocus) field.context.getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            onFocusConsumed()
        }
    }
    AndroidView(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), factory = { context ->
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
            caretChanged = { a, b -> caret(a, b) }
            setOnFocusChangeListener { _, focused -> if (focused) caret(selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0)) }
            addTextChangedListener(EmojiTextWatcher(context, textSize, primary) {
                if (!bindingText && text.toString() != currentBlock.text) changed(currentBlock.copy(text = text.toString()))
            })
        }
    }, update = { field ->
        field.bindingText = true
        field.isEnabled = enabled
        field.minLines = minLines
        field.setTextColor(textColor)
        field.setHintTextColor(hintColor)
        field.highlightColor = ColorUtils.setAlphaComponent(selectionColor, 128)
        if (Build.VERSION.SDK_INT >= 29) {
            field.textCursorDrawable = GradientDrawable().apply {
                setColor(primary)
                setSize((2 * field.resources.displayMetrics.density).toInt().coerceAtLeast(2), field.textSize.toInt().coerceAtLeast(1))
            }
        }
        if (field.text.toString() != block.text) {
            val selection = field.selectionStart.coerceAtLeast(0).coerceAtMost(block.text.length)
            field.setText(block.text)
            field.setSelection(selection)
        }
        field.bindingText = false
    })
}
