package com.example.c001apk.compose.ui.feed.article

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.alibaba.sdk.android.oss.ClientConfiguration
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSStsTokenCredentialProvider
import com.alibaba.sdk.android.oss.model.ObjectMetadata
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import com.example.c001apk.compose.logic.model.OSSUploadPrepareModel
import com.example.c001apk.compose.logic.repository.NetworkRepo
import com.example.c001apk.compose.util.OSSUtil.getImageDimensionsAndMD5
import com.example.c001apk.compose.util.OSSUtil.toHex
import com.example.c001apk.compose.util.OssUploadUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** All objects must finish uploading before the caller can submit an article. */
internal class ArticleImageUploader(private val context: Context, private val network: NetworkRepo) {
    suspend fun upload(
        draft: ArticleDraft, store: ArticleDraftStore, progress: (String) -> Unit,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val images = buildList {
            if (draft.cover.isNotBlank()) add(ArticlePayload.COVER to draft.cover)
            draft.blocks.filter { it.type == "image" }.forEach { add(it.id to it.image) }
        }
        if (images.isEmpty()) return@withContext emptyMap()
        val files = images.map { (_, source) ->
            val file = store.file(source)
            val (dimensions, digest) = getImageDimensionsAndMD5(context.contentResolver, Uri.fromFile(file))
            require(dimensions != null && dimensions.first > 0 && dimensions.second > 0 && digest != null) { "无法读取草稿图片" }
            val mime = dimensions.third ?: error("无法识别图片格式")
            Triple(OSSUploadPrepareModel(file.name + "." + mime.substringAfter('/').substringBefore('+'),
                "${dimensions.first}x${dimensions.second}", digest.toHex()), mime, digest)
        }
        progress("正在准备上传 ${images.size} 张图片…")
        val prepared = network.postOSSUploadPrepare(hashMapOf(
            "uploadBucket" to "image", "uploadDir" to "feed", "is_anonymous" to "0",
            "uploadFileList" to Gson().toJson(files.map { it.first }), "toUid" to "",
        )).first().getOrThrow()
        val data = prepared.data ?: error(prepared.message ?: "未取得图片上传凭据")
        val info = data.uploadPrepareInfo
        val returned = data.fileInfo.associateBy { it.name }
        require(returned.size == images.size && files.all { it.first.name in returned }) { "图片上传清单不完整" }
        val client by lazy {
            require(info.endPoint.isNotBlank() && info.accessKeyId.isNotBlank() && info.accessKeySecret.isNotBlank()) { "图片上传凭据不完整" }
            OSSClient(context, info.endPoint, OSSStsTokenCredentialProvider(
                info.accessKeyId, info.accessKeySecret, info.securityToken,
            ), ClientConfiguration().apply {
                connectionTimeout = 30_000; socketTimeout = 60_000; maxErrorRetry = 1
            })
        }
        val result = mutableMapOf<String, String>()
        // Sequential completion avoids mistaking the last-index callback for completion of all uploads.
        images.forEachIndexed { index, (id, source) ->
            currentCoroutineContext().ensureActive()
            progress("正在上传图片 ${index + 1}/${images.size}…")
            val (requestFile, mime, digest) = files[index]
            val remote = returned.getValue(requestFile.name)
            // Matching MD5s may already be registered; no PUT or credentials are needed then.
            if (!remote.url.isNullOrBlank()) {
                result[id] = remote.url
                return@forEachIndexed
            }
            require(remote.uploadFileName.isNotBlank()) { "图片上传路径为空" }
            val put = PutObjectRequest(info.bucket, remote.uploadFileName, store.file(source).path)
            put.metadata = ObjectMetadata().apply {
                contentType = mime
                contentMD5 = Base64.encodeToString(digest, Base64.NO_WRAP)
                setHeader("x-oss-callback", OssUploadUtil.IMAGE_CALLBACK)
                setHeader("x-oss-callback-var", OssUploadUtil.IMAGE_CALLBACK_VAR)
            }
            client.putObject(put)
            result[id] = info.uploadImagePrefix.trimEnd('/') + "/" + remote.uploadFileName.trimStart('/')
        }
        result
    }
}
