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
        org.junit.Assume.assumeTrue("This isolated component fixture composes test-only call sites; use the real app menu for R8 smoke checks",
            context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
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

    @Test fun keyboardEmojiSwitchKeepsTextAndReturnsToKeyboard() {
        store.save(ArticleDraft(title = "键盘切换测试", blocks = listOf(ArticleBlock(id = "keyboard", text = "正文"))))
        launchEditor().use { scenario ->
            waitText("正文")
            lateinit var editor: android.widget.EditText
            scenario.onActivity { activity ->
                fun find(view: View): android.widget.EditText? {
                    if (view is android.widget.EditText && view.text.toString() == "正文") return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                editor = requireNotNull(find(activity.window.decorView))
                editor.requestFocus(); editor.setSelection(editor.length())
                editor.post { activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
            }
            fun imeVisible() = if (android.os.Build.VERSION.SDK_INT >= 30)
                editor.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
            else androidx.core.view.ViewCompat.getRootWindowInsets(editor)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            waitUi("keyboard opens") { imeVisible() }
            screenshot("article-keyboard.png")
            click { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("表情") == true }
            waitUi("emoji replaces keyboard") { !imeVisible() }
            waitText("默认")
            fun grid(view: View): android.widget.GridView? {
                if (view is android.widget.GridView && view.isShown && view.childCount > 0 &&
                    view.getGlobalVisibleRect(android.graphics.Rect()) && view.getChildAt(0)?.hasOnClickListeners() == true) return view
                if (view is ViewGroup) for (i in 0 until view.childCount) grid(view.getChildAt(i))?.let { return it }
                return null
            }
            waitUi("visible emoji page laid out") { roots.any { grid(it.view) != null } }
            instrumentation.runOnMainSync {
                val visibleGrid = requireNotNull(roots.firstNotNullOfOrNull { grid(it.view) })
                assertTrue(visibleGrid.getChildAt(0).performClick())
            }
            waitUi("emoji inserted without opening keyboard") { editor.text.toString().startsWith("正文[") && !imeVisible() }
            screenshot("article-emoji-inserted.png")
            click { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("表情") == true }
            waitUi("keyboard returns") { imeVisible() }
            screenshot("article-keyboard-return.png")
            assertTrue(store.read().blocks.first().text.startsWith("正文["))
        }
    }

    @Test fun captureScrollPerformanceWithGeneratedDrafts() {
        val fixture = File(context.cacheDir, "scroll-image.png")
        Bitmap.createBitmap(960, 420, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(Color.rgb(61, 133, 110))
            fixture.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        val image = store.importImage(Uri.fromFile(fixture)); fixture.delete()
        val line = "这是用于测量图文编辑区滑动的生成文字，包含换行、@测试用户 和 #测试话题#。[哈哈哈]"
        val keyboardCase = InstrumentationRegistry.getArguments().getString("article_scroll_keyboard") == "true"
        val allCases = listOf(
            "paragraphs" to (0 until 32).map { ArticleBlock(id = "paragraph-$it", text = "第 $it 段\n" + line.repeat(3)) },
            "mixed" to (0 until 24).flatMap { index -> listOf(ArticleBlock(id = "text-$index", text = "第 $index 段\n" + line.repeat(3)),
                ArticleBlock(id = "image-$index", type = "image", image = image)) },
            "long_paragraph" to listOf(ArticleBlock(id = "long", text = (0 until 180).joinToString("\n") { "$it $line" })),
        )
        val cases = if (keyboardCase) allCases.filter { it.first == "long_paragraph" } else allCases
        val results = linkedMapOf<String, Any>()
        for ((name, blocks) in cases) {
            store.save(ArticleDraft(title = "滚动性能测试 $name", blocks = blocks))
            launchEditor().use { scenario ->
                waitText("滚动性能测试 $name"); SystemClock.sleep(500)
                if (keyboardCase) {
                    var keyboardField: android.widget.EditText? = null
                    scenario.onActivity { activity ->
                        fun find(view: View): android.widget.EditText? {
                            if (view is android.widget.EditText && view.isShown) return view
                            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                            return null
                        }
                        val field = requireNotNull(find(activity.window.decorView)); keyboardField = field
                        field.requestFocus(); field.setSelection(field.length() / 2)
                        activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                            .showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                    waitUi("keyboard visible for focused scroll") {
                        keyboardField?.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
                    }
                    SystemClock.sleep(300)
                }
                val samples = java.util.Collections.synchronizedList(mutableListOf<Map<String, Long>>())
                val thread = android.os.HandlerThread("article-frame-metrics").apply { start() }
                var droppedCallbacks = 0
                val listener = android.view.Window.OnFrameMetricsAvailableListener { _, frame, dropped ->
                    droppedCallbacks += dropped
                    if (frame.getMetric(android.view.FrameMetrics.FIRST_DRAW_FRAME) == 0L) samples.add(mapOf(
                        "total_ns" to frame.getMetric(android.view.FrameMetrics.TOTAL_DURATION),
                        "layout_ns" to frame.getMetric(android.view.FrameMetrics.LAYOUT_MEASURE_DURATION),
                        "draw_ns" to frame.getMetric(android.view.FrameMetrics.DRAW_DURATION),
                        "gpu_ns" to frame.getMetric(android.view.FrameMetrics.GPU_DURATION),
                        "unknown_ns" to frame.getMetric(android.view.FrameMetrics.UNKNOWN_DELAY_DURATION),
                        "animation_ns" to frame.getMetric(android.view.FrameMetrics.ANIMATION_DURATION),
                        "sync_ns" to frame.getMetric(android.view.FrameMetrics.SYNC_DURATION),
                        "deadline_ns" to frame.getMetric(android.view.FrameMetrics.DEADLINE),
                    ))
                }
                var window: android.view.Window? = null
                var width = 0; var height = 0; var refreshRate = 0f; var displayId = 0
                var focused: (() -> Boolean) = { false }
                scenario.onActivity { activity ->
                    window = activity.window; width = activity.window.decorView.width; height = activity.window.decorView.height
                    if (keyboardCase) height -= activity.window.decorView.rootWindowInsets
                        ?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: 0
                    refreshRate = activity.display?.refreshRate ?: 0f
                    displayId = activity.display?.displayId ?: 0
                    focused = { activity.hasWindowFocus() }
                    activity.window.addOnFrameMetricsAvailableListener(listener, android.os.Handler(thread.looper))
                }
                assertEquals("Use the physical display for both windows and touches", 0, displayId)
                waitUi("physical editor window focused", focused)
                fun scrollValue(): Float {
                    var value = 0f
                    instrumentation.runOnMainSync {
                        value = nodes().firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) }?.value?.invoke() ?: 0f
                    }
                    return value
                }
                val positions = mutableListOf(scrollValue())
                try {
                    repeat(8) { swipe ->
                        val down = SystemClock.uptimeMillis()
                        val from = height * if (swipe < 4) .74f else .32f
                        val to = height * if (swipe < 4) .32f else .74f
                        fun send(action: Int, y: Float) {
                            val event = requireNotNull(if (android.os.Build.VERSION.SDK_INT >= 34) android.view.MotionEvent.obtain(
                                down, SystemClock.uptimeMillis(), action, 1,
                                arrayOf(android.view.MotionEvent.PointerProperties().apply { id = 0; toolType = android.view.MotionEvent.TOOL_TYPE_FINGER }),
                                arrayOf(android.view.MotionEvent.PointerCoords().apply { x = width * .52f; this.y = y; pressure = 1f; size = 1f }),
                                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, displayId, 0, android.view.MotionEvent.CLASSIFICATION_NONE)
                            else android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, width * .52f, y, 0))
                            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                            // Replay through this window only. Global injection can be
                            // interrupted by another virtual display's input stream.
                            try {
                                var handled = false
                                instrumentation.runOnMainSync { handled = requireNotNull(window).decorView.dispatchTouchEvent(event) }
                                if (action == android.view.MotionEvent.ACTION_DOWN) assertTrue("Editor did not handle touch down", handled)
                            } finally { event.recycle() }
                        }
                        send(android.view.MotionEvent.ACTION_DOWN, from)
                        for (step in 1..20) { SystemClock.sleep(16); send(android.view.MotionEvent.ACTION_MOVE, from + (to - from) * step / 20) }
                        send(android.view.MotionEvent.ACTION_UP, to)
                        SystemClock.sleep(250)
                        positions.add(scrollValue())
                    }
                } finally {
                    instrumentation.runOnMainSync { window?.removeOnFrameMetricsAvailableListener(listener) }
                    thread.quitSafely(); thread.join(1000)
                }
                val snapshot = synchronized(samples) { samples.toList() }
                fun percentile(field: String, fraction: Double): Double {
                    val values = snapshot.map { it.getValue(field) }.sorted()
                    return values.getOrElse(((values.size - 1) * fraction).toInt().coerceAtLeast(0)) { 0L } / 1_000_000.0
                }
                results[name] = mapOf("frames" to snapshot.size, "keyboard_visible" to keyboardCase, "display_id" to displayId, "input_replay" to "window_dispatch", "refresh_hz" to refreshRate, "dropped_metric_callbacks" to droppedCallbacks,
                    "over_deadline" to snapshot.count { it.getValue("deadline_ns") > 0 && it.getValue("total_ns") > it.getValue("deadline_ns") },
                    "total_p50_ms" to percentile("total_ns", .5), "total_p95_ms" to percentile("total_ns", .95),
                    "layout_p95_ms" to percentile("layout_ns", .95), "draw_p95_ms" to percentile("draw_ns", .95),
                    "gpu_p95_ms" to percentile("gpu_ns", .95), "unknown_p95_ms" to percentile("unknown_ns", .95),
                    "scroll_positions" to positions, "raw_frames" to snapshot)
                File(context.filesDir, "article-scroll-performance.json").writeText(Gson().toJson(results))
                assertTrue("No frame metrics collected for $name", snapshot.size > 20)
                assertEquals("Scrolling must not change draft text or image order", blocks, store.read().blocks)
            }
        }
    }

    @Test fun editingAfterScrollingOnlyChangesTheVisibleParagraph() {
        val blocks = (0 until 40).map { ArticleBlock(id = "edit-$it",
            text = "段落 $it：" + "滚动后仍应编辑当前段落。[哈哈哈]".repeat(8)) }
        store.save(ArticleDraft(title = "滚动后编辑回归", blocks = blocks))
        launchEditor().use {
            waitText("滚动后编辑回归")
            repeat(5) { step ->
                instrumentation.runOnMainSync {
                    val action = nodes().firstNotNullOf { it.config.getOrNull(SemanticsActions.ScrollBy)?.action }
                    assertTrue(action.invoke(0f, if (step < 4) 1500f else -800f))
                }
                SystemClock.sleep(250)
            }
            var editedId = ""
            instrumentation.runOnMainSync {
                fun find(view: View): android.widget.EditText? {
                    if (view is android.widget.EditText && view.isShown && view.getGlobalVisibleRect(android.graphics.Rect()) &&
                        blocks.any { it.text == view.text.toString() }) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                val field = requireNotNull(roots.firstNotNullOfOrNull { find(it.view) })
                editedId = blocks.first { it.text == field.text.toString() }.id
                field.setSelection(field.length())
                field.append(" 滑动后修改")
            }
            waitUi("edited paragraph saved") { store.read().blocks.first { it.id == editedId }.text.endsWith(" 滑动后修改") }
            assertEquals(blocks.map { if (it.id == editedId) it.copy(text = it.text + " 滑动后修改") else it }, store.read().blocks)
        }
    }

    @Test fun keyboardStaysOpenWhenFocusedParagraphMovesForIme() {
        val blocks = (0 until 32).map { ArticleBlock(id = "ime-$it", text = "输入法段落 $it：" + "保持当前编辑焦点，不因键盘缩放自动关闭。".repeat(8)) }
        store.save(ArticleDraft(title = "输入法焦点稳定测试", blocks = blocks))
        launchEditor().use { scenario ->
            waitText("输入法焦点稳定测试"); SystemClock.sleep(400)
            repeat(3) {
                instrumentation.runOnMainSync { nodes().firstNotNullOf { it.config.getOrNull(SemanticsActions.ScrollBy)?.action }.invoke(0f, 1300f) }
                SystemClock.sleep(200)
            }
            lateinit var activity: ArticleActivity
            lateinit var field: android.widget.EditText
            scenario.onActivity { host ->
                activity = host
                val fields = mutableListOf<android.widget.EditText>()
                fun visit(view: View) {
                    val rect = android.graphics.Rect()
                    if (view is android.widget.EditText && view.isShown && view.getGlobalVisibleRect(rect) && rect.height() > 60) fields.add(view)
                    if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                }
                visit(host.window.decorView)
                field = fields.maxBy { val rect = android.graphics.Rect(); it.getGlobalVisibleRect(rect); rect.centerY() }
                field.requestFocus(); field.setSelection(field.length() / 2)
                field.post { host.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
            }
            val samples = mutableListOf<Map<String, Any>>()
            fun pinState(): Boolean = field.javaClass.getDeclaredField("focusPin").apply { isAccessible = true }.get(field) != null
            repeat(70) { tick ->
                instrumentation.runOnMainSync {
                    samples.add(mapOf("tick" to tick, "ime" to (activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true),
                        "focused" to field.isFocused, "attached" to field.isAttachedToWindow, "pinned" to pinState(),
                        "current_focus" to (activity.currentFocus?.javaClass?.simpleName ?: "none")))
                }
                SystemClock.sleep(35)
            }
            File(context.filesDir, "article-ime-focus-retention.json").writeText(Gson().toJson(samples))
            assertTrue("Keyboard never opened", samples.any { it["ime"] == true })
            assertTrue("Keyboard or editor focus disappeared during IME resize", samples.takeLast(15).all { it["ime"] == true && it["focused"] == true && it["attached"] == true })
            val dragSamples = mutableListOf<Map<String, Any>>()
            repeat(3) { swipe ->
                var height = 0; var width = 0
                instrumentation.runOnMainSync {
                    val decor = activity.window.decorView
                    height = decor.height - (decor.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: 0)
                    width = decor.width
                }
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, fraction: Float) {
                    val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, width * .52f, height * fraction, 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    try { instrumentation.runOnMainSync { activity.window.decorView.dispatchTouchEvent(event) } } finally { event.recycle() }
                }
                send(android.view.MotionEvent.ACTION_DOWN, .30f)
                for (step in 1..20) { SystemClock.sleep(16); send(android.view.MotionEvent.ACTION_MOVE, .30f + .45f * step / 20) }
                send(android.view.MotionEvent.ACTION_UP, .75f); SystemClock.sleep(250)
                instrumentation.runOnMainSync { dragSamples.add(mapOf("swipe" to swipe,
                    "ime" to (activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true),
                    "focused" to field.isFocused, "attached" to field.isAttachedToWindow, "pinned" to pinState())) }
            }
            File(context.filesDir, "article-ime-drag-retention.json").writeText(Gson().toJson(dragSamples))
            assertTrue("Dragging content dismissed keyboard or recycled the active editor", dragSamples.all { it["ime"] == true && it["focused"] == true && it["attached"] == true })
        }
    }

    @Test fun typingKeepsCaretAboveKeyboard() {
        store.save(ArticleDraft(title = "输入光标跟随测试", blocks = listOf(ArticleBlock(id = "typing",
            text = (0 until 120).joinToString("\n") { "原有第 $it 行：用于检查长文输入位置跟随。" }))))
        launchEditor().use { scenario ->
            waitText("输入光标跟随测试")
            lateinit var activity: ArticleActivity
            lateinit var field: android.widget.EditText
            scenario.onActivity { host ->
                activity = host
                fun find(view: View): android.widget.EditText? {
                    if (view is android.widget.EditText && view.isShown) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                field = requireNotNull(find(host.window.decorView))
                field.requestFocus(); field.setSelection(field.length())
                field.post { host.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .showSoftInput(field, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
            }
            waitUi("keyboard opens for typing") { activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true }
            val added = (0 until 15).joinToString("\n", prefix = "\n") { "新增第 $it 行：光标应始终位于键盘上方。" }
            instrumentation.runOnMainSync {
                val connection = requireNotNull(field.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
                assertTrue(connection.commitText(added, 1))
            }
            SystemClock.sleep(900)
            var details = emptyMap<String, Any>()
            instrumentation.runOnMainSync {
                val xy = IntArray(2); field.getLocationOnScreen(xy)
                val line = field.layout.getLineForOffset(field.selectionEnd)
                val caretBottom = xy[1] + field.totalPaddingTop + field.layout.getLineBottom(line) - field.scrollY
                val ime = activity.window.decorView.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: 0
                val toolbar = 88f * activity.resources.displayMetrics.density * CookieUtil.contentScale
                val viewportBottom = activity.window.decorView.height - ime - toolbar
                details = mapOf("caret_bottom_px" to caretBottom, "viewport_bottom_px" to viewportBottom,
                    "focused" to field.isFocused, "attached" to field.isAttachedToWindow, "ime_bottom_px" to ime)
            }
            File(context.filesDir, "article-caret-visibility.json").writeText(Gson().toJson(details))
            assertTrue("Draft text was not preserved", store.read().blocks.single().text.endsWith(added))
            assertTrue("Latest typed line is behind keyboard or editor toolbar", (details.getValue("caret_bottom_px") as Int) <= (details.getValue("viewport_bottom_px") as Float) + 2)
        }
    }

    private fun waitUi(description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 8000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = predicate() }
            if (ready) { SystemClock.sleep(250); return }
            SystemClock.sleep(50)
        }
        screenshot("article-ui-timeout.png")
        instrumentation.runOnMainSync {
            val details = mutableListOf<Map<String, Any?>>()
            fun record(view: View) {
                val rect = android.graphics.Rect()
                details.add(mapOf("class" to view.javaClass.name, "width" to view.width, "height" to view.height,
                    "shown" to view.isShown, "global_visible" to view.getGlobalVisibleRect(rect), "bounds" to rect.toShortString(),
                    "click" to view.hasOnClickListeners(), "children" to (view as? ViewGroup)?.childCount,
                    "ime_visible" to (if (android.os.Build.VERSION.SDK_INT >= 30) view.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())
                        else androidx.core.view.ViewCompat.getRootWindowInsets(view)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))))
                if (view is ViewGroup) for (i in 0 until view.childCount) record(view.getChildAt(i))
            }
            roots.forEach { record(it.view) }
            File(context.filesDir, "article-ui-timeout.json").writeText(Gson().toJson(details))
        }
        error("Timed out: $description")
    }

    private fun launchEditor(): EditorHost {
        val monitor = instrumentation.addMonitor(ArticleActivity::class.java.name, null, false)
        // Some vendor ROMs silently block instrumentation's app-origin activity launch.
        // Debug alone grants shell access with DUMP permission; release remains unexported.
        val command = "am start --display 0 -n ${context.packageName}/${ArticleActivity::class.java.name}"
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
