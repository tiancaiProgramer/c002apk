package com.example.c001apk.compose.ui.feed.article

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.core.content.edit
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** One private draft per account. Copy selected media so document grants cannot expire. */
internal class ArticleDraftStore(context: Context, uid: String) {
    private val context = context.applicationContext
    private val accountKey = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val prefs = context.getSharedPreferences("article_draft_$accountKey", Context.MODE_PRIVATE)
    private val media = File(context.filesDir, "article-drafts/$accountKey").apply { mkdirs() }
    private val gson = Gson()

    fun read(): ArticleDraft = prefs.getString("draft", null)?.let {
        // A corrupt draft is reported instead of silently overwriting it with an empty one.
        requireNotNull(gson.fromJson(it, ArticleDraft::class.java)).also { draft ->
            require(draft.blocks.all { block -> block.type in setOf("text", "image") })
        }
    } ?: ArticleDraft()

    fun save(draft: ArticleDraft) = prefs.edit { putString("draft", gson.toJson(draft)) }

    fun clear() {
        prefs.edit { remove("draft") }
        media.listFiles()?.forEach { it.delete() }
    }

    fun file(uri: String): File {
        val parsed = Uri.parse(uri)
        require(parsed.scheme == "file") { "草稿图片无效，请重新选择" }
        val file = File(requireNotNull(parsed.path)).canonicalFile
        require(file.parentFile == media.canonicalFile && file.isFile) { "草稿图片不存在，请重新选择" }
        return file
    }

    fun importImage(uri: Uri): String {
        val dest = File(media, UUID.randomUUID().toString())
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use(input::copyTo)
            } ?: error("无法读取所选图片")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(dest.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType?.startsWith("image/") == true) {
                "无法识别所选图片，请选择有效的图片文件"
            }
            return Uri.fromFile(dest).toString()
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
    }

    fun prune(draft: ArticleDraft) {
        val kept = (draft.blocks.map { it.image } + draft.cover).toSet()
        media.listFiles()?.filter { Uri.fromFile(it).toString() !in kept }?.forEach { it.delete() }
    }
}
