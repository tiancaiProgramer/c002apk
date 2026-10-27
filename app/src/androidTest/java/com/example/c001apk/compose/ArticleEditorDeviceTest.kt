package com.example.c001apk.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelProvider
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.c001apk.compose.ui.component.PublishMenuButton
import com.example.c001apk.compose.ui.feed.article.ArticleActivity
import com.example.c001apk.compose.ui.feed.article.ArticleBlock
import com.example.c001apk.compose.ui.feed.article.ArticleDraft
import com.example.c001apk.compose.ui.feed.article.ArticleDraftStore
import com.example.c001apk.compose.ui.feed.article.ArticleViewModel
import com.example.c001apk.compose.ui.feed.reply.ReplyActivity
import com.google.gson.Gson
import com.example.c001apk.compose.ui.theme.C001apkComposeTheme
import com.example.c001apk.compose.util.CookieUtil
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Offline editor tests. No real credentials, upload, or createFeed request is used. */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class ArticleEditorDeviceTest {
    private val roots = mutableListOf<ViewRootForTest>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var store: ArticleDraftStore

    @Before fun setup() {
        check(context.packageName.endsWith(".articletest")) { "Run only in the disposable article test package" }
        instrumentation.runOnMainSync {
            ViewRootForTest.onViewCreatedCallback = { roots.add(it) }
        }
        CookieUtil.isLogin = true
        CookieUtil.uid = "offline-article-editor-test"
        CookieUtil.token = "offline-not-a-real-token"
        CookieUtil.isDarkMode = false
        store = ArticleDraftStore(context, CookieUtil.uid)
        store.clear()
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync { ViewRootForTest.onViewCreatedCallback = null; roots.clear() }
        store.clear()
        CookieUtil.isLogin = false
        CookieUtil.uid = ""
        CookieUtil.token = ""
        CookieUtil.isDarkMode = false
    }

    @Test fun plusMenuOffersExactlyTheTwoModesAndDispatchesTheSelectedOne() {
        var feeds = 0
        var articles = 0
        launchEditor().use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                C001apkComposeTheme {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
                        PublishMenuButton(onFeed = { feeds++ }, onArticle = { articles++ })
                    }
                }
            } }
            waitNode { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("发布") == true }
            val fab = screenBounds { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("发布") == true }
            click { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("发布") == true }
            waitText("发动态"); waitText("写图文")
            val menu = screenBounds { it.config.getOrNull(SemanticsProperties.TestTag) == "publish-menu" }
            assertEquals((fab[0] + fab[2]) / 2, menu[2], 1f)
            assertEquals((fab[1] + fab[3]) / 2, menu[3], 1f)
            waitNode { it.config.getOrNull(SemanticsProperties.TestTag) == "publish-menu-divider" }
            screenshot("article-publish-menu.png")
            clickText("写图文")
            instrumentation.runOnMainSync { assertEquals(1, articles); assertEquals(0, feeds) }
            click { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("发布") == true }
            clickText("发动态")
            instrumentation.runOnMainSync { assertEquals(1, articles); assertEquals(1, feeds) }
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
        launchEditor().use { scenario ->
            waitText("图文混排预览")
            screenshot("article-editor.png")
            clickText("预览")
            waitText("第一段文字：图片会出现在这段文字之后。")
            waitText("第二段文字：继续在图片之后阅读。")
            screenshot("article-preview.png")
            clickText("编辑")
            setText("图文混排预览", "保存后的图文标题")
            scenario.recreate()
            waitText("保存后的图文标题")
            assertEquals("保存后的图文标题", store.read().title)
            assertEquals(listOf("text", "image", "text"), store.read().blocks.map { it.type })
            assertTrue(store.file(local).isFile)
            CookieUtil.isDarkMode = true
            scenario.recreate()
            waitText("保存后的图文标题")
            screenshot("article-editor-dark.png")
            click { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("表情") == true }
            waitText("默认"); waitText("最近"); waitText("酷币")
            screenshot("article-emoji-panel.png")
            click { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("表情") == true }
            clickText("发布")
            waitText("图文将公开发布，是否继续？")
            // Cancel here: the offline tests must never publish content.
            clickText("取消")
        }
        val another = ArticleDraftStore(context, "another-offline-test-account")
        assertFalse(another.read().hasContent)
    }

    @Test fun cursorSelectionAndImportedPictureSplitTheActualDraftWithoutLosingText() {
        store.save(ArticleDraft(title = "光标插图测试", blocks = listOf(ArticleBlock(id = "cursor", text = "图片前图片后"))))
        val source = File(context.cacheDir, "cursor-test.png")
        Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888).apply {
            source.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        launchEditor().use { scenario ->
            waitText("图片前图片后")
            instrumentation.runOnMainSync {
                fun findEditor(view: View): android.widget.EditText? {
                    if (view is android.widget.EditText && view.text.toString() == "图片前图片后") return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) findEditor(view.getChildAt(i))?.let { return it }
                    return null
                }
                val field = requireNotNull(roots.firstNotNullOfOrNull { findEditor(it.view) })
                field.requestFocus()
                field.setSelection(3)
            }
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[ArticleViewModel::class.java]
                model.captureImageInsertion()
                // Deliver the same Uri callback as a picker, using a generated fixture only.
                model.importImages(listOf(Uri.fromFile(source)))
            }
            waitText("图片后")
            val actual = store.read()
            assertEquals(listOf("text", "image", "text"), actual.blocks.map { it.type })
            assertEquals("图片前", actual.blocks[0].text)
            assertEquals("图片后", actual.blocks[2].text)
            assertTrue(store.file(actual.blocks[1].image).isFile)
        }
        source.delete()
    }

    @Test fun captureExistingDynamicEditorStyleWithoutPublishing() {
        launchEditor().use { scenario ->
            val monitor = instrumentation.addMonitor(ReplyActivity::class.java.name, null, false)
            scenario.onActivity { activity ->
                activity.startActivity(Intent(activity, ReplyActivity::class.java).putExtra("type", "createFeed"))
            }
            val reply = monitor.waitForActivityWithTimeout(8000) as? ReplyActivity
                ?: error("Existing dynamic editor did not start")
            try {
                SystemClock.sleep(1300)
                instrumentation.runOnMainSync {
                    val editor = reply.findViewById<android.widget.EditText>(R.id.editText)
                    editor.setText("这是动态编辑器的样式对照。\n这是一段不会发布的测试文字。")
                    reply.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .hideSoftInputFromWindow(editor.windowToken, 0)
                    val panel = reply.findViewById<View>(R.id.inputLayout)
                    val metrics = reply.resources.displayMetrics
                    val details = mapOf(
                        "title_sp" to reply.findViewById<TextView>(R.id.title).textSize / metrics.scaledDensity,
                        "publish_sp" to reply.findViewById<TextView>(R.id.publish).textSize / metrics.scaledDensity,
                        "body_sp" to editor.textSize / metrics.scaledDensity,
                        "panel_color_argb" to (panel.background as? GradientDrawable)?.color?.defaultColor,
                        "panel_corner_radii_px" to (panel.background as? GradientDrawable)?.cornerRadii?.toList(),
                        "density" to metrics.density,
                        "toolbar_height_dp" to reply.findViewById<View>(R.id.imageBtn).height / metrics.density,
                    )
                    File(context.filesDir, "dynamic-ui-reference.json").writeText(Gson().toJson(details))
                }
                SystemClock.sleep(350)
                screenshot("dynamic-editor-reference.png")
            } finally {
                instrumentation.runOnMainSync { reply.finish() }
                instrumentation.removeMonitor(monitor)
            }
        }
    }

    private fun launchEditor(): EditorHost {
        val monitor = instrumentation.addMonitor(ArticleActivity::class.java.name, null, false)
        // Some vendor ROMs silently block instrumentation's app-origin activity launch.
        // Debug alone grants shell access with DUMP permission; release remains unexported.
        val command = "am start -n ${context.packageName}/${ArticleActivity::class.java.name}"
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        val activity = monitor.waitForActivityWithTimeout(8000) as? ArticleActivity
            ?: error("Shell-launched editor did not start within 8 seconds")
        return EditorHost(activity, monitor)
    }

    private inner class EditorHost(
        private var activity: ArticleActivity,
        private val monitor: android.app.Instrumentation.ActivityMonitor,
    ) : AutoCloseable {
        fun onActivity(action: (ArticleActivity) -> Unit) = instrumentation.runOnMainSync { action(activity) }
        fun recreate() {
            instrumentation.runOnMainSync { activity.recreate() }
            activity = monitor.waitForActivityWithTimeout(8000) as? ArticleActivity
                ?: error("Editor did not recreate within 8 seconds")
        }
        override fun close() {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.removeMonitor(monitor)
            SystemClock.sleep(200)
        }
    }

    // Query public Compose semantics directly rather than injecting system-wide touches.
    // All waits are bounded and target this process only.
    private fun nodes(): List<SemanticsNode> {
        val result = mutableListOf<SemanticsNode>()
        fun visit(node: SemanticsNode) { result.add(node); node.children.forEach(::visit) }
        roots.filter { it.view.isAttachedToWindow }.forEach { visit(it.semanticsOwner.rootSemanticsNode) }
        return result
    }

    private fun matchesText(node: SemanticsNode, text: String): Boolean =
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true ||
            node.config.getOrNull(SemanticsProperties.EditableText)?.text == text

    private fun waitNode(predicate: (SemanticsNode) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 8000
        while (SystemClock.elapsedRealtime() < deadline) {
            var found = false
            instrumentation.runOnMainSync { found = nodes().any(predicate) }
            if (found) { SystemClock.sleep(100); return }
            SystemClock.sleep(50)
        }
        error("Expected editor UI node was not rendered within 8 seconds")
    }

    private fun waitText(text: String) {
        fun nativeText(view: View): Boolean = view.isShown && ((view is TextView && view.text.toString() == text) ||
            (view is ViewGroup && (0 until view.childCount).any { nativeText(view.getChildAt(it)) }))
        val deadline = SystemClock.elapsedRealtime() + 8000
        while (SystemClock.elapsedRealtime() < deadline) {
            var found = false
            instrumentation.runOnMainSync {
                found = nodes().any { matchesText(it, text) } || roots.any { nativeText(it.view) }
            }
            if (found) { SystemClock.sleep(100); return }
            SystemClock.sleep(50)
        }
        error("Expected text was not rendered within 8 seconds: $text")
    }
    private fun clickText(text: String) = click { matchesText(it, text) }
    private fun click(predicate: (SemanticsNode) -> Boolean) {
        waitNode(predicate)
        instrumentation.runOnMainSync {
            var node: SemanticsNode? = nodes().first(predicate)
            while (node != null && node.config.getOrNull(SemanticsActions.OnClick)?.action == null) node = node.parent
            assertTrue(requireNotNull(node?.config?.getOrNull(SemanticsActions.OnClick)?.action).invoke())
        }
        SystemClock.sleep(180)
    }

    private fun setText(old: String, value: String) {
        waitText(old)
        instrumentation.runOnMainSync {
            val node = nodes().first { matchesText(it, old) && it.config.getOrNull(SemanticsActions.SetText)?.action != null }
            assertTrue(requireNotNull(node.config.getOrNull(SemanticsActions.SetText)?.action).invoke(AnnotatedString(value)))
        }
        SystemClock.sleep(180)
    }

    private fun screenBounds(predicate: (SemanticsNode) -> Boolean): FloatArray {
        waitNode(predicate)
        var bounds = floatArrayOf()
        instrumentation.runOnMainSync {
            val node = nodes().first(predicate)
            val position = node.positionOnScreen
            bounds = floatArrayOf(position.x, position.y, position.x + node.size.width, position.y + node.size.height)
        }
        return bounds
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(350)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
