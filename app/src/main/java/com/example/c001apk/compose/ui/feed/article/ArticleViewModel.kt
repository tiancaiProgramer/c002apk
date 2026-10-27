package com.example.c001apk.compose.ui.feed.article

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.c001apk.compose.logic.repository.NetworkRepo
import com.example.c001apk.compose.util.CookieUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class ArticleEditorState(
    val draft: ArticleDraft = ArticleDraft(),
    val ready: Boolean = false,
    val loadError: Boolean = false,
    val busy: Boolean = false,
    val progress: String = "",
    val error: String? = null,
    val captcha: ByteArray? = null,
    val createdId: String? = null,
)

@HiltViewModel
class ArticleViewModel @Inject constructor(
    application: Application,
    private val network: NetworkRepo,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val owner = CookieUtil.uid
    private val ownerToken = CookieUtil.token
    private val store = ArticleDraftStore(application, owner)
    private val mutableState = MutableStateFlow(ArticleEditorState())
    internal val state = mutableState.asStateFlow()
    private var uploaded = emptyMap<String, String>()
    private var pendingForm: HashMap<String, String>? = null
    private var loadFailed = false

    init {
        viewModelScope.launch {
            try {
                val draft = withContext(Dispatchers.IO) { store.read() }
                mutableState.value = ArticleEditorState(draft, ready = true)
            } catch (e: Exception) {
                loadFailed = true
                mutableState.value = ArticleEditorState(loadError = true, error = "无法读取草稿，请先清空损坏的草稿后重试")
            }
        }
    }

    private fun sameAccount() = owner.isNotBlank() && ownerToken.isNotBlank() &&
        CookieUtil.isLogin && CookieUtil.uid == owner && CookieUtil.token == ownerToken

    internal fun edit(transform: (ArticleDraft) -> ArticleDraft) {
        val old = mutableState.value
        if (!old.ready || old.busy || old.createdId != null) return
        if (!sameAccount()) { fail("登录状态已变化，请退出编辑页后重新打开"); return }
        val draft = transform(old.draft)
        if (draft.cover != old.draft.cover) uploaded = uploaded - ArticlePayload.COVER
        store.save(draft)
        pendingForm = null
        mutableState.value = old.copy(draft = draft, error = null, captcha = null)
    }

    internal fun updateBlock(id: String, transform: (ArticleBlock) -> ArticleBlock) = edit { draft ->
        draft.copy(blocks = draft.blocks.map { if (it.id == id) transform(it) else it })
    }

    internal fun addText(after: String?) = edit { it.insertAfter(after, listOf(ArticleBlock())) }
    internal fun move(id: String, by: Int) = edit { it.move(id, by) }
    internal fun remove(id: String) {
        edit { it.copy(blocks = it.blocks.filterNot { block -> block.id == id }.ifEmpty { listOf(ArticleBlock()) }) }
    }

    internal fun beginPick(after: String?) { savedState["insertAfter"] = after }

    internal fun importImages(uris: List<Uri>, cover: Boolean = false) {
        if (uris.isEmpty() || mutableState.value.busy || !mutableState.value.ready) return
        val before = mutableState.value.draft
        val after: String? = savedState["insertAfter"]
        mutableState.value = mutableState.value.copy(busy = true, progress = "正在读取图片…", error = null)
        viewModelScope.launch {
            try {
                check(sameAccount()) { "登录状态已变化，请重新打开编辑页" }
                val local = withContext(Dispatchers.IO) { uris.map(store::importImage) }
                check(sameAccount()) { "登录状态已变化，请重新打开编辑页" }
                val next = if (cover) before.copy(cover = local.first())
                else before.insertAfter(after, local.map { ArticleBlock(type = "image", image = it) } + ArticleBlock())
                if (cover) uploaded = uploaded - ArticlePayload.COVER
                store.save(next)
                withContext(Dispatchers.IO) { store.prune(next) }
                pendingForm = null
                mutableState.value = mutableState.value.copy(draft = next)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e.message ?: "读取图片失败") }
            finally { mutableState.value = mutableState.value.copy(busy = false, progress = "") }
        }
    }

    internal fun discard() {
        if (mutableState.value.busy) return
        store.clear()
        loadFailed = false
        uploaded = emptyMap()
        pendingForm = null
        mutableState.value = ArticleEditorState(ready = true)
    }

    internal fun publish() {
        if (mutableState.value.busy || !mutableState.value.ready || loadFailed || mutableState.value.createdId != null) return
        val draft = mutableState.value.draft
        try { draft.validate(); check(sameAccount()) { "请先登录后再发布" } }
        catch (e: Exception) { fail(e.message ?: "请检查图文内容"); return }
        mutableState.value = mutableState.value.copy(busy = true, error = null, captcha = null)
        viewModelScope.launch {
            try {
                val needed = draft.blocks.filter { it.type == "image" }.map { it.id }.toSet() +
                    if (draft.cover.isNotBlank()) setOf(ArticlePayload.COVER) else emptySet()
                if (!uploaded.keys.containsAll(needed)) {
                    uploaded = ArticleImageUploader(getApplication(), network).upload(draft, store) { progress ->
                        mutableState.value = mutableState.value.copy(progress = progress)
                    }
                }
                check(sameAccount()) { "登录状态已变化，已停止发布" }
                pendingForm = ArticlePayload.build(draft, uploaded)
                submit()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail((e.message ?: "网络异常") + "。发布未确认时请先查看个人主页，避免重复发布。草稿已保留。") }
            finally { mutableState.value = mutableState.value.copy(busy = false, progress = "") }
        }
    }

    private suspend fun submit() {
        check(sameAccount()) { "登录状态已变化，已停止发布" }
        mutableState.value = mutableState.value.copy(progress = "正在发布图文…", captcha = null)
        val response = network.postCreateFeed(requireNotNull(pendingForm)).first().getOrThrow()
        val id = response.data?.id?.takeIf { it.isNotBlank() && it != "0" }
        if (id != null) {
            store.clear()
            uploaded = emptyMap()
            pendingForm = null
            mutableState.value = mutableState.value.copy(createdId = id, captcha = null)
        } else if (response.messageStatus == "err_request_captcha") {
            loadCaptcha()
        } else {
            fail(response.message ?: response.messageStatus ?: "服务端未返回新图文 ID，未确认发布成功；草稿已保留")
        }
    }

    private suspend fun loadCaptcha() {
        val result = network.getValidateCaptcha("/v6/account/captchaImage?${System.currentTimeMillis() / 1000L}&w=270&h=113").first().getOrThrow()
        check(result.isSuccessful) { "获取验证码失败，请稍后重试" }
        val bytes = withContext(Dispatchers.IO) { result.body()?.use { it.bytes() } }
        check(bytes != null && bytes.isNotEmpty()) { "验证码图片为空" }
        mutableState.value = mutableState.value.copy(captcha = bytes, error = null)
    }

    internal fun cancelCaptcha() { mutableState.value = mutableState.value.copy(captcha = null) }

    internal fun verifyCaptcha(code: String) {
        if (mutableState.value.busy || code.isBlank() || pendingForm == null) return
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                check(sameAccount()) { "登录状态已变化，已停止发布" }
                val response = network.postRequestValidate(hashMapOf(
                    "type" to "err_request_captcha", "code" to code.trim(),
                    "mobile" to "", "idcard" to "", "name" to "",
                )).first().getOrThrow()
                if (response.data?.count == "验证通过") submit()
                else { fail(response.message ?: "验证码未通过，请重试"); loadCaptcha() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e.message ?: "验证失败，草稿已保留") }
            finally { mutableState.value = mutableState.value.copy(busy = false, progress = "") }
        }
    }

    internal fun dismissError() { mutableState.value = mutableState.value.copy(error = null) }
    private fun fail(message: String) { mutableState.value = mutableState.value.copy(error = message) }
}
