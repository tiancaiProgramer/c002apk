package com.example.c001apk.compose.ui.feed.article

import org.junit.Assert.*
import org.junit.Test

class ArticlePublicationCheckTest {
    private val draft = ArticleDraft(title = "核验", blocks = listOf(
        ArticleBlock(text = "图片前"), ArticleBlock(type = "image", image = "local-a", description = "说明 A"),
        ArticleBlock(text = "图片之间"), ArticleBlock(type = "image", image = "local-b", description = "说明 B"),
        ArticleBlock(text = "图片后"), ArticleBlock(text = " ")
    ))
    private val valid = """[{"type":"text","message":"图片前"},{"type":"image","url":"https://example/a","description":"说明 A"},{"type":"text","message":"图片之间"},{"type":"image","url":"https://example/b","description":"说明 B"},{"type":"text","message":"图片后"}]"""
    private val uploaded = draft.blocks.filter { it.type == "image" }.mapIndexed { index, block ->
        block.id to "https://example/${if (index == 0) "a" else "b"}"
    }.toMap()

    @Test fun verifiesAlternationAndCaptionsWithServerHtmlFormatting() {
        assertTrue(ArticlePublicationCheck.preserves(draft, valid, uploaded))
        assertTrue(ArticlePublicationCheck.preserves(draft, valid.replace("图片前", "<p>图片前</p>"), uploaded))
        assertTrue(ArticlePublicationCheck.preserves(draft, valid.replace("https://example/a", "https://cdn.example/a!small"), uploaded))
    }
    @Test fun convertedOrIncompleteOrReorderedContentMustKeepTheDraft() {
        listOf(null, "null", "[]", "{}", "broken", valid.replace("说明 A", ""),
            valid.replace("图片前", "图片之间"), valid.replace("https://example/a", ""),
            valid.replace("https://example/a", "https://example/b")
        ).forEach { assertFalse(ArticlePublicationCheck.preserves(draft, it, uploaded)) }
    }
}
