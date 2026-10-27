package com.example.c001apk.compose.ui.feed.article

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.viewpager2.widget.ViewPager2
import com.example.c001apk.compose.R
import com.example.c001apk.compose.ui.feed.reply.emoji.EmojiPagerAdapter
import com.example.c001apk.compose.ui.component.rememberEditorViewContext
import com.example.c001apk.compose.util.EmojiUtils
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Reuses the dynamic editor's exact 7-column pages, dots, tabs and backspace control. */
@Composable
internal fun ArticleEmojiPanel(visible: Boolean, heightPx: Int, coveredImePx: Int,
    recent: List<String>, onSelect: (String) -> Unit, onBackspace: () -> Unit) {
    val themed = rememberEditorViewContext()
    val density = LocalDensity.current
    val select by rememberUpdatedState(onSelect)
    val backspace by rememberUpdatedState(onBackspace)
    val scope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    val height = with(density) { remainingArticleEmojiHeight(heightPx, coveredImePx).toDp() }
    DisposableEffect(visible) {
        if (!visible) repeatJob?.cancel()
        onDispose { repeatJob?.cancel() }
    }
    AndroidView(modifier = Modifier.fillMaxWidth().height(if (visible) height else with(density) { 0.toDp() }),
        factory = {
            NativeArticleEmojiPanel(themed, onSelect = { key -> if (key == "[c001apk]") backspace() else select(key) },
                startDelete = { repeatJob?.cancel(); repeatJob = scope.launch { while (isActive) { backspace(); delay(50) } } },
                stopDelete = { repeatJob?.cancel() })
        }, update = { panel ->
            panel.visibility = if (visible) View.VISIBLE else View.GONE
            panel.updateRecent(recent)
        })
}

internal fun remainingArticleEmojiHeight(panelPx: Int, coveredImePx: Int): Int =
    (panelPx.coerceAtLeast(0) - coveredImePx.coerceAtLeast(0)).coerceAtLeast(0)

private class NativeArticleEmojiPanel(
    context: Context, onSelect: (String) -> Unit, startDelete: () -> Unit, stopDelete: () -> Unit,
) : LinearLayout(context) {
    private val recentPages = arrayListOf<List<Pair<String, Int>>>(emptyList())
    private val pager = ViewPager2(context)
    private val labels = ArrayList<TextView>()
    private var previousRecent: List<String>? = null
    private val adapter = EmojiPagerAdapter(listOf(recentPages, EmojiUtils.emojiList, EmojiUtils.coolBList), onSelect, startDelete, stopDelete)
    private fun px(value: Int) = (value * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        pager.id = R.id.emojiPanel
        pager.adapter = adapter
        addView(pager, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(View(context).apply { setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, 0)) },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1)))
        val tabs = LinearLayout(context).apply { orientation = HORIZONTAL; id = R.id.indicator }
        listOf("最近", "默认", "酷币").forEachIndexed { index, label ->
            val text = TextView(context).apply {
                this.text = label
                gravity = Gravity.CENTER
                setOnClickListener { pager.setCurrentItem(index, false) }
            }
            labels.add(text)
            tabs.addView(text, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            if (index < 2) tabs.addView(View(context).apply {
                setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, 0))
            }, LayoutParams(px(1), ViewGroup.LayoutParams.MATCH_PARENT))
        }
        addView(tabs, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(41)))
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateTabs(position) }
        })
        pager.setCurrentItem(1, false)
        updateTabs(1)
    }

    private fun updateTabs(selected: Int) {
        labels.forEachIndexed { index, text ->
            text.background = AppCompatResources.getDrawable(context,
                if (index == selected) R.drawable.selector_emoji_indicator_selected else R.drawable.selector_emoji_indicator)
            text.setTextColor(MaterialColors.getColor(text, if (index == selected)
                com.google.android.material.R.attr.colorOnPrimary else com.google.android.material.R.attr.colorControlNormal, 0))
        }
    }

    fun updateRecent(values: List<String>) {
        val keys = values.take(27)
        if (keys == previousRecent) return
        previousRecent = keys
        recentPages[0] = keys.mapNotNull { key -> EmojiUtils.emojiMap[key]?.let { key to it } }
        adapter.notifyItemChanged(0)
    }
}
