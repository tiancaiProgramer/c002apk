package com.example.c001apk.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.c001apk.compose.ui.component.PublishMenuButton
import com.example.c001apk.compose.ui.feed.article.ArticleActivity
import com.example.c001apk.compose.ui.feed.article.ArticleBlock
import com.example.c001apk.compose.ui.feed.article.ArticleDraft
import com.example.c001apk.compose.ui.feed.article.ArticleDraftStore
import com.example.c001apk.compose.ui.theme.C001apkComposeTheme
import com.example.c001apk.compose.util.CookieUtil
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Offline editor tests. No real credentials, upload, or createFeed request is used. */
@RunWith(AndroidJUnit4::class)
class ArticleEditorDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var store: ArticleDraftStore

    @Before fun setup() {
        check(context.packageName.endsWith(".articletest")) { "Run only in the disposable article test package" }
        CookieUtil.isLogin = true
        CookieUtil.uid = "offline-article-editor-test"
        CookieUtil.token = "offline-not-a-real-token"
        CookieUtil.isDarkMode = false
        store = ArticleDraftStore(context, CookieUtil.uid)
        store.clear()
    }

    @After fun cleanup() {
        store.clear()
        CookieUtil.isLogin = false
        CookieUtil.uid = ""
        CookieUtil.token = ""
        CookieUtil.isDarkMode = false
    }

    @Test fun plusMenuOffersExactlyTheTwoModesAndDispatchesTheSelectedOne() {
        var feeds = 0
        var articles = 0
        ActivityScenario.launch(ArticleActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                C001apkComposeTheme {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
                        PublishMenuButton(onFeed = { feeds++ }, onArticle = { articles++ })
                    }
                }
            } }
            compose.onNodeWithContentDescription("发布").performClick()
            compose.onNodeWithText("发动态").assertIsDisplayed()
            compose.onNodeWithText("写图文").assertIsDisplayed()
            screenshot("article-publish-menu.png")
            compose.onNodeWithText("写图文").performClick()
            compose.runOnIdle { assertEquals(1, articles); assertEquals(0, feeds) }
            compose.onNodeWithText("发动态").assertDoesNotExist()
            compose.onNodeWithContentDescription("发布").performClick()
            compose.onNodeWithText("发动态").performClick()
            compose.runOnIdle { assertEquals(1, articles); assertEquals(1, feeds) }
        }
    }

    @Test fun draftMediaIsLocalPreviewKeepsInterleavingAndDraftSurvivesRecreation() {
        val external = File(context.cacheDir, "article-external.png")
        Bitmap.createBitmap(640, 240, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.rgb(61, 133, 110))
            external.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val local = store.importImage(Uri.fromFile(external))
        external.delete()
        assertTrue(store.file(local).isFile)
        store.save(ArticleDraft(title = "图文混排预览", blocks = listOf(
            ArticleBlock(id = "first", text = "第一段文字：图片会出现在这段文字之后。"),
            ArticleBlock(id = "photo", type = "image", image = local, description = "图片说明"),
            ArticleBlock(id = "last", text = "第二段文字：继续在图片之后阅读。"),
        )))
        ActivityScenario.launch(ArticleActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { compose.onAllNodesWithText("图文混排预览").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("图文混排预览").assertIsDisplayed()
            screenshot("article-editor.png")
            compose.onNodeWithText("预览").performClick()
            compose.onNodeWithText("第一段文字：图片会出现在这段文字之后。").assertIsDisplayed()
            compose.onNodeWithText("第二段文字：继续在图片之后阅读。").performScrollTo().assertIsDisplayed()
            screenshot("article-preview.png")
            compose.onNodeWithText("编辑").performClick()
            compose.onNodeWithText("图文混排预览").performTextReplacement("保存后的图文标题")
            scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("保存后的图文标题").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle {
                assertEquals("保存后的图文标题", store.read().title)
                assertEquals(listOf("text", "image", "text"), store.read().blocks.map { it.type })
                assertTrue(store.file(local).isFile)
            }
            CookieUtil.isDarkMode = true
            scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("保存后的图文标题").fetchSemanticsNodes().isNotEmpty() }
            screenshot("article-editor-dark.png")
            compose.onNodeWithText("发布").performClick()
            compose.onNodeWithText("图文将公开发布，是否继续？").assertIsDisplayed()
            // Cancel here: the offline tests must never publish content.
            compose.onNodeWithText("取消").performClick()
        }
        val another = ArticleDraftStore(context, "another-offline-test-account")
        assertFalse(another.read().hasContent)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
