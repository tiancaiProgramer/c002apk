package com.example.c001apk.compose.ui.feed.article

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.example.c001apk.compose.ui.component.PublishMenuPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishMenuPositionTest {
    @Test fun bottomRightCornerMeetsFabCentreAtDifferentWindowSizesAndDirections() {
        for (window in listOf(IntSize(1080, 2400), IntSize(2400, 1080))) {
            val fab = IntRect(window.width - 160, window.height - 260, window.width - 48, window.height - 148)
            val menu = IntSize(360, 240)
            for (direction in LayoutDirection.entries) {
                val position = PublishMenuPosition.calculatePosition(fab, window, direction, menu)
                assertEquals(fab.center.x, position.x + menu.width)
                assertEquals(fab.center.y, position.y + menu.height)
            }
        }
    }

    @Test fun smallWindowsKeepTheMenuOnScreenInsteadOfMovingTheAnchorOffScreen() {
        val window = IntSize(320, 320)
        val menu = IntSize(200, 160)
        val position = PublishMenuPosition.calculatePosition(IntRect(0, 0, 56, 56), window, LayoutDirection.Ltr, menu)
        assertEquals(IntOffset.Zero, position)
        assertTrue(position.x + menu.width <= window.width)
        assertTrue(position.y + menu.height <= window.height)
    }
}
