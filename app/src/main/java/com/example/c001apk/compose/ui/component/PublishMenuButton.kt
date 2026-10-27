package com.example.c001apk.compose.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Physical bottom-right corner points at the FAB centre, also in RTL layouts. */
internal object PublishMenuPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        (anchorBounds.center.x - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
        (anchorBounds.center.y - popupContentSize.height).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )
}

/** The two publishing modes share the existing homepage FAB and Material theme. */
@Composable
internal fun PublishMenuButton(onFeed: () -> Unit, onArticle: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(onClick = rememberHapticClick { expanded = true }) {
            Icon(Icons.Default.Add, contentDescription = "发布")
        }
        if (expanded) {
            Popup(popupPositionProvider = PublishMenuPosition, onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)) {
                Surface(modifier = Modifier.testTag("publish-menu"),
                    shape = AbsoluteRoundedCornerShape(
                        topLeft = MaterialTheme.shapes.extraSmall.topStart,
                        topRight = MaterialTheme.shapes.extraSmall.topEnd,
                        bottomLeft = MaterialTheme.shapes.extraSmall.bottomStart,
                        bottomRight = CornerSize(0.dp),
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 8.dp) {
                    Column(Modifier.width(IntrinsicSize.Max).padding(vertical = 8.dp)) {
                        DropdownMenuItem(text = { Text("发动态") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = rememberHapticClick { expanded = false; onFeed() })
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp).testTag("publish-menu-divider"))
                        DropdownMenuItem(text = { Text("写图文") }, leadingIcon = { Icon(Icons.Outlined.Article, null) },
                            onClick = rememberHapticClick { expanded = false; onArticle() })
                    }
                }
            }
        }
    }
}
