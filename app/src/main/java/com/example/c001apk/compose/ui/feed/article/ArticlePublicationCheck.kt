package com.example.c001apk.compose.ui.feed.article

import com.example.c001apk.compose.util.ArticleContentParser
import org.jsoup.Jsoup
import java.net.URI

internal object ArticlePublicationCheck {
    const val CONVERTED = "服务器已创建内容，但将图文转成了普通动态，混排和图片说明未被保留。原草稿及图片已保留。请先到个人主页检查并处理这条内容，再补充正文后重新发布，避免重复发布。"
    const val UNVERIFIED = "服务器已创建内容，但尚未确认混排顺序和图片说明完整。原草稿及图片已保留。请先到个人主页检查这条内容，避免重复发布。"

    fun preserves(draft: ArticleDraft, raw: String?, uploaded: Map<String, String>): Boolean =
        problem(draft, raw, uploaded) == null

    fun problem(draft: ArticleDraft, raw: String?, uploaded: Map<String, String>): String? = try {
        val expected = draft.blocks.filter { it.type != "text" || it.text.isNotBlank() }
        val actual = ArticleContentParser.parse(raw)
        if (expected.size != actual.size) "读回的段落和图片数量与原稿不一致。"
        else {
            var imageIndex = 0
            expected.zip(actual).firstNotNullOfOrNull { (before, after) ->
                when {
                    before.type != after.type -> "读回的段落和图片顺序与原稿不一致。"
                    before.type == "text" -> if (Jsoup.parse(before.text).text() != Jsoup.parse(after.message.orEmpty()).text())
                        "读回的正文与原稿不一致。" else null
                    before.type == "image" -> {
                        imageIndex++
                        when {
                            before.description != after.description.orEmpty() -> "第 ${imageIndex} 张图片的说明与原稿不一致。"
                            after.url.isNullOrBlank() || uploaded[before.id]?.let { URI(it).path.substringBefore('!') } !=
                                URI(after.url).path.substringBefore('!') -> "第 ${imageIndex} 张图片与原稿不一致。"
                            else -> null
                        }
                    }
                    else -> "无法核验图文内容。"
                }
            }
        }
    } catch (_: Exception) { "无法读取返回的图文排版。" }
}
