package com.example.c001apk.compose.ui.feed.article

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.c001apk.compose.util.EmojiUtils

/** Same emoji assets and Recent / Default / CoolB groups as the dynamic editor. */
@Composable
internal fun ArticleEmojiPanel(recent: List<String>, onSelect: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(1) }
    val groups = remember(recent) {
        listOf(recent.take(27).mapNotNull { key -> EmojiUtils.emojiMap[key]?.let { key to it } },
            EmojiUtils.emojiList.flatten(), EmojiUtils.coolBList.flatten())
    }
    Column {
        HorizontalDivider()
        if (groups[tab].isEmpty()) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Text("暂无最近使用的表情", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyVerticalGrid(columns = GridCells.Fixed(9), modifier = Modifier.fillMaxWidth().height(180.dp),
            contentPadding = PaddingValues(8.dp)) {
            items(groups[tab], key = { it.first }) { (key, drawable) ->
                Box(Modifier.height(48.dp).clickable { onSelect(key) }, contentAlignment = Alignment.Center) {
                    Image(painterResource(drawable), key, modifier = Modifier.size(30.dp))
                }
            }
        }
        TabRow(selectedTabIndex = tab) {
            listOf("最近", "默认", "酷币").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
    }
}
