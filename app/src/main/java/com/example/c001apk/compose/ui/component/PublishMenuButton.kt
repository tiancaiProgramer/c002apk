package com.example.c001apk.compose.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*

/** The two publishing modes share the existing homepage FAB and Material theme. */
@Composable
internal fun PublishMenuButton(onFeed: () -> Unit, onArticle: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = rememberHapticClick { expanded = true }) {
            Icon(Icons.Default.Add, contentDescription = "发布")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("发动态") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                onClick = rememberHapticClick { expanded = false; onFeed() })
            DropdownMenuItem(text = { Text("写图文") }, leadingIcon = { Icon(Icons.Outlined.Article, null) },
                onClick = rememberHapticClick { expanded = false; onArticle() })
        }
    }
}
