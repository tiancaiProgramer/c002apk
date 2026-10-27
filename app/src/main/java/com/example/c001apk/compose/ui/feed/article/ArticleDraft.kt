package com.example.c001apk.compose.ui.feed.article

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.util.UUID

/** Local editor state. Identifiers link uploaded images to blocks, never list positions. */
data class ArticleBlock(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("type") val type: String = "text",
    @SerializedName("text") val text: String = "",
    @SerializedName("image") val image: String = "",
    @SerializedName("description") val description: String = "",
)

data class ArticleDraft(
    @SerializedName("title") val title: String = "",
    @SerializedName("cover") val cover: String = "",
    @SerializedName("blocks") val blocks: List<ArticleBlock> = listOf(ArticleBlock()),
    @SerializedName("onlySelf") val onlySelf: Boolean = false,
) {
    val hasContent: Boolean get() = title.isNotBlank() || cover.isNotBlank() ||
        blocks.any { it.text.isNotBlank() || it.image.isNotBlank() }

    fun insertAfter(id: String?, added: List<ArticleBlock>): ArticleDraft {
        val index = blocks.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) ?: blocks.size
        return copy(blocks = blocks.toMutableList().apply { addAll(index, added) })
    }

    fun insertAtCursor(id: String?, start: Int, end: Int, added: List<ArticleBlock>): ArticleDraft {
        val index = blocks.indexOfFirst { it.id == id && it.type == "text" }
        if (index < 0) return insertAfter(id, added + ArticleBlock())
        val block = blocks[index]
        val before = minOf(start, end).coerceIn(0, block.text.length)
        val after = maxOf(start, end).coerceIn(before, block.text.length)
        val replacement = listOf(block.copy(text = block.text.substring(0, before))) +
            added + ArticleBlock(text = block.text.substring(after))
        return copy(blocks = blocks.take(index) + replacement + blocks.drop(index + 1))
    }

    fun move(id: String, offset: Int): ArticleDraft {
        val from = blocks.indexOfFirst { it.id == id }
        val to = from + offset
        if (from < 0 || to !in blocks.indices) return this
        return copy(blocks = blocks.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun validate() {
        require(title.isNotBlank()) { "请填写图文标题" }
        require(blocks.any { it.type == "text" && it.text.isNotBlank() }) { "请至少填写一段正文" }
        require(blocks.all { it.type in setOf("text", "image") }) { "草稿包含无法识别的内容" }
        require(blocks.map { it.id }.distinct().size == blocks.size) { "草稿内容标识重复" }
        require(blocks.filter { it.type == "image" }.all { it.image.isNotBlank() }) { "正文图片丢失，请重新选择" }
    }
}

/** ArticleModel message array, not a plain message with an attached pic list. */
internal object ArticlePayload {
    const val COVER = "article-cover"

    fun build(draft: ArticleDraft, uploaded: Map<String, String>): HashMap<String, String> {
        draft.validate()
        fun url(id: String): String = uploaded[id]?.takeIf {
            it.startsWith("https://") || it.startsWith("http://")
        } ?: throw IllegalArgumentException("图片尚未上传完成，请重试")

        val body = JsonArray()
        draft.blocks.forEach { block ->
            if (block.type == "text" && block.text.isBlank()) return@forEach
            body.add(JsonObject().apply {
                addProperty("type", block.type)
                if (block.type == "text") addProperty("message", block.text)
                else {
                    addProperty("url", url(block.id))
                    addProperty("description", block.description)
                }
            })
        }
        return hashMapOf(
            "id" to "", "type" to "feed", "status" to "1",
            "publish_status" to if (draft.onlySelf) "1" else "0",
            "is_html_article" to "1", "message" to body.toString(),
            "message_title" to draft.title.trim(),
            "message_cover" to if (draft.cover.isNotBlank()) url(COVER) else "",
            "pic" to "",
        )
    }
}
