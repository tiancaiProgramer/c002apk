package com.example.c001apk.compose.ui.feed.article

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleInputInsetsTest {
    @Test fun keyboardAndEmojiPanelShareSpaceDuringTheTransition() {
        assertEquals(600, remainingArticleEmojiHeight(600, 0))
        assertEquals(0, remainingArticleEmojiHeight(600, 600))
        assertEquals(0, remainingArticleEmojiHeight(600, 700))
        for (keyboard in 0..600) {
            assertEquals(600, keyboard + remainingArticleEmojiHeight(600, keyboard))
        }
        assertEquals(0, remainingArticleEmojiHeight(-1, 0))
    }
}
