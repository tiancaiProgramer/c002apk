package com.example.c001apk.compose.util

import org.junit.Assert.*
import org.junit.Test

class ArticleContentParserTest {
    @Test fun gsonBlocksKeepWireOrderCaptionsAndUniqueStableKeys() {
        val raw = """[{"type":"text","message":"第一段"},{"type":"image","url":"https://example/a","description":"说明 A"},{"type":"text","message":"中间段"},{"type":"image","url":"https://example/b","description":"说明 B"},{"type":"text","message":"结尾"}]"""
        val blocks = ArticleContentParser.parse(raw)
        assertEquals(listOf("text", "image", "text", "image", "text"), blocks.map { it.type })
        assertEquals(listOf("说明 A", "说明 B"), blocks.filter { it.type == "image" }.map { it.description })
        assertEquals(5, blocks.map { it.key }.distinct().size)
        assertTrue(blocks.all { it.key.isNotBlank() })
        assertEquals(blocks, ArticleContentParser.parse(raw))
    }

    @Test fun ordinaryFeedsNullEntriesAndUnknownCardsAreHandled() {
        listOf(null, "", "  ", "null", "[]").forEach { assertTrue(ArticleContentParser.parse(it).isEmpty()) }
        val blocks = ArticleContentParser.parse("""[null,{"type":"top"},{"type":"image","description":"保留"},{"type":"shareUrl","title":"链接"}]""")
        assertEquals(listOf("image", "shareUrl"), blocks.map { it.type })
        assertEquals("保留", blocks.first().description)
    }
}
