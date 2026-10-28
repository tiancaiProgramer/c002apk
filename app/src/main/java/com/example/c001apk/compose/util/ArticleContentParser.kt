package com.example.c001apk.compose.util

import com.example.c001apk.compose.logic.model.FeedArticleContentBean
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal object ArticleContentParser {
    fun parse(raw: String?): List<FeedArticleContentBean> {
        if (raw.isNullOrBlank() || raw.trim() == "null") return emptyList()
        val value = JsonParser.parseString(raw)
        if (!value.isJsonArray) throw JsonParseException("图文内容格式异常")
        val gson = Gson()
        return value.asJsonArray.mapIndexedNotNull { index, element ->
            if (element.isJsonNull) return@mapIndexedNotNull null
            val block = gson.fromJson(element, FeedArticleContentBean::class.java)
            if (block.type !in setOf("text", "image", "shareUrl")) return@mapIndexedNotNull null
            // Gson can bypass the Kotlin constructor: the default UUID is then null.
            block.copy(key = "article-block-$index")
        }
    }
}
