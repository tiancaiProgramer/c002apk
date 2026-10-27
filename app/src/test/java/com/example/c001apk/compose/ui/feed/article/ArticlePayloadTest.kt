package com.example.c001apk.compose.ui.feed.article

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ArticlePayloadTest {
    private val draft = ArticleDraft(title = "图文测试", cover = "file:///cover", blocks = listOf(
        ArticleBlock(id = "p1", text = "第一段\n换行、引号\"与表情 😀"),
        ArticleBlock(id = "image1", type = "image", image = "file:///one", description = "图片一"),
        ArticleBlock(id = "p2", text = "第二段"),
        ArticleBlock(id = "image2", type = "image", image = "file:///two"),
        ArticleBlock(id = "p3", text = "第三段"),
    ))
    private val urls = mapOf("image2" to "https://cdn.example/two.jpg", ArticlePayload.COVER to "https://cdn.example/cover.jpg",
        "image1" to "https://cdn.example/one.jpg")

    @Test fun articleContractPreservesInterleavingAndKeepsCoverAndTitleOutsideBody() {
        val form = ArticlePayload.build(draft, urls)
        assertEquals("feed", form["type"])
        assertEquals("1", form["is_html_article"])
        assertEquals("图文测试", form["message_title"])
        assertEquals("https://cdn.example/cover.jpg", form["message_cover"])
        assertEquals("", form["pic"])
        val blocks = JsonParser.parseString(form.getValue("message")).asJsonArray
        assertEquals(listOf("text", "image", "text", "image", "text"), blocks.map { it.asJsonObject["type"].asString })
        assertEquals(draft.blocks[0].text, blocks[0].asJsonObject["message"].asString)
        assertEquals("https://cdn.example/one.jpg", blocks[1].asJsonObject["url"].asString)
        assertEquals("https://cdn.example/two.jpg", blocks[3].asJsonObject["url"].asString)
        assertEquals("图片一", blocks[1].asJsonObject["description"].asString)
        assertFalse(blocks.any { it.asJsonObject["type"].asString == "top" })
    }

    @Test fun unfinishedOrMissingImagesCannotBeSubmittedEvenWhenOtherImagesSucceeded() {
        assertThrows(IllegalArgumentException::class.java) { ArticlePayload.build(draft, urls - "image1") }
        assertThrows(IllegalArgumentException::class.java) { ArticlePayload.build(draft, urls - ArticlePayload.COVER) }
        assertThrows(IllegalArgumentException::class.java) { ArticlePayload.build(draft, urls + ("image1" to "file:///local")) }
    }

    @Test fun editingOrderSurvivesDraftSerializationAndUrlsRemainBoundToImageIdentity() {
        val moved = draft.move("image2", -1).insertAfter("p1", listOf(ArticleBlock(id = "new", text = "插入段落")))
        val restored = Gson().fromJson(Gson().toJson(moved), ArticleDraft::class.java)
        assertEquals(moved, restored)
        val body = JsonParser.parseString(ArticlePayload.build(restored, urls).getValue("message")).asJsonArray
        assertEquals(listOf("text", "text", "image", "image", "text", "text"), body.map { it.asJsonObject["type"].asString })
        assertEquals("https://cdn.example/one.jpg", body[2].asJsonObject["url"].asString)
        assertEquals("https://cdn.example/two.jpg", body[3].asJsonObject["url"].asString)
        assertEquals(draft, draft.move("p1", -1))
        assertEquals(draft, draft.move("p3", 1))
    }

    @Test fun privacyUsesPublishStatusAndEmptyDraftCannotBecomeAnArticle() {
        assertEquals("0", ArticlePayload.build(draft, urls)["publish_status"])
        val privateForm = ArticlePayload.build(draft.copy(onlySelf = true), urls)
        assertEquals("1", privateForm["publish_status"])
        assertEquals("1", privateForm["status"])
        assertThrows(IllegalArgumentException::class.java) { ArticlePayload.build(draft.copy(title = "  "), urls) }
        assertThrows(IllegalArgumentException::class.java) { ArticlePayload.build(draft.copy(blocks = draft.blocks.filter { it.type == "image" }), urls) }
        assertThrows(IllegalArgumentException::class.java) { ArticlePayload.build(draft.copy(blocks = draft.blocks + draft.blocks[1]), urls) }
        val textOnly = ArticlePayload.build(ArticleDraft(title = "纯文字图文", blocks = listOf(ArticleBlock(text = "正文"))), emptyMap())
        assertEquals("", textOnly["message_cover"])
    }

    @Test fun insertingAtCursorSplitsParagraphAndPreservesTheRestOfTheArticle() {
        val original = ArticleDraft(title = "光标插图", blocks = listOf(
            ArticleBlock(id = "paragraph", text = "图片前图片后"), ArticleBlock(id = "next", text = "下一段")))
        val image = ArticleBlock(id = "inserted-image", type = "image", image = "file:///photo")
        val inserted = original.insertAtCursor("paragraph", 3, 3, listOf(image))
        assertEquals(listOf("text", "image", "text", "text"), inserted.blocks.map { it.type })
        assertEquals("图片前", inserted.blocks[0].text)
        assertEquals("图片后", inserted.blocks[2].text)
        assertEquals("下一段", inserted.blocks[3].text)
        val body = JsonParser.parseString(ArticlePayload.build(inserted, mapOf(image.id to "https://cdn.example/photo.jpg")).getValue("message")).asJsonArray
        assertEquals("图片前", body[0].asJsonObject["message"].asString)
        assertEquals("https://cdn.example/photo.jpg", body[1].asJsonObject["url"].asString)
        assertEquals("图片后", body[2].asJsonObject["message"].asString)
    }
}
