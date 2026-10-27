package com.example.c001apk.compose.ui.feed.article

import android.content.Intent
import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.c001apk.compose.ui.component.BackButton
import com.example.c001apk.compose.ui.component.LinkText
import com.example.c001apk.compose.ui.component.rememberHapticClick
import com.example.c001apk.compose.ui.feed.reply.AtTopicActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.example.c001apk.compose.R
import com.example.c001apk.compose.logic.providable.LocalUserPreferences
import com.example.c001apk.compose.logic.datastore.UserPreferencesCompat
import com.example.c001apk.compose.ui.theme.C001apkComposeTheme
import com.example.c001apk.compose.util.CookieUtil
import com.example.c001apk.compose.util.makeToast
import com.example.c001apk.compose.util.openInBrowser
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ArticleActivity : ComponentActivity() {
    private val viewModel: ArticleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!CookieUtil.isLogin) { makeToast("请先登录"); finish(); return }
        enableEdgeToEdge()
        setContent {
            val uiPreferences = UserPreferencesCompat.default().copy(
                fontScale = CookieUtil.fontScale, contentScale = CookieUtil.contentScale,
                showEmoji = CookieUtil.showEmoji, imageFilter = CookieUtil.imageFilter,
                hapticFeedback = CookieUtil.hapticFeedback, hapticStrength = CookieUtil.hapticStrength,
                customHaptics = CookieUtil.customHaptics, reverseHapticEffects = CookieUtil.reverseHapticEffects,
            )
            CompositionLocalProvider(LocalUserPreferences provides uiPreferences) {
            C001apkComposeTheme(
                darkTheme = CookieUtil.isDarkMode,
                themeType = CookieUtil.themeType,
                seedColor = CookieUtil.seedColor,
                materialYou = CookieUtil.materialYou,
                pureBlack = CookieUtil.pureBlack,
                paletteStyle = CookieUtil.paletteStyle,
                fontScale = CookieUtil.fontScale,
                contentScale = CookieUtil.contentScale,
            ) {
                ArticleEditor(viewModel) {
                    val state = viewModel.state.value
                    if (state.busy) makeToast("正在处理，请稍候")
                    else {
                        state.createdId?.let { setResult(RESULT_OK, Intent().putExtra("feed_id", it)) }
                        finish()
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleEditor(viewModel: ArticleViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft
    var preview by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var confirmPublish by rememberSaveable { mutableStateOf(false) }
    var showEmoji by rememberSaveable { mutableStateOf(false) }
    val editorListState = rememberLazyListState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) { if (imeVisible) showEmoji = false }
    val recent by viewModel.recentEmojis.collectAsStateWithLifecycle(initialValue = emptyList())
    val enabled = state.ready && !state.busy && state.createdId == null
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        viewModel.importImages(it)
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> viewModel.importImages(listOf(uri), cover = true) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        it?.let { uri -> viewModel.importImages(listOf(uri)) }
    }
    val mentionPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringExtra("data")?.let(viewModel::insertText)
    }
    fun pickAtCursor(files: Boolean) {
        showEmoji = false
        keyboard?.hide()
        viewModel.captureImageInsertion()
        if (files) picker.launch(arrayOf("image/*")) else imagePicker.launch("image/*")
    }
    LaunchedEffect(state.focusBlockId) {
        state.focusBlockId?.let { id ->
            val index = draft.blocks.indexOfFirst { it.id == id }
            if (index >= 0) editorListState.animateScrollToItem(index + 2)
        }
    }

    BackHandler {
        if (showEmoji) showEmoji = false
        else if (preview && !state.busy) preview = false else onBack()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (preview) "图文预览" else "发布图文", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (preview) BackButton { preview = false }
                    else IconButton(onClick = onBack) { Icon(Icons.Default.Close, "关闭编辑器") }
                },
                actions = {
                    TextButton(enabled = enabled, onClick = rememberHapticClick { preview = !preview }) {
                        Text(if (preview) "编辑" else "预览")
                    }
                    Button(enabled = enabled, shape = MaterialTheme.shapes.extraSmall,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        onClick = rememberHapticClick { confirmPublish = true }) { Text("发布") }
                    Box {
                        IconButton(enabled = !state.busy, onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, "更多选项")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("上移当前段") }, enabled = enabled,
                                onClick = { showMenu = false; viewModel.currentBlock()?.let { viewModel.move(it.id, -1) } })
                            DropdownMenuItem(text = { Text("下移当前段") }, enabled = enabled,
                                onClick = { showMenu = false; viewModel.currentBlock()?.let { viewModel.move(it.id, 1) } })
                            DropdownMenuItem(text = { Text("删除当前段") }, enabled = enabled,
                                onClick = { showMenu = false; viewModel.currentBlock()?.let { viewModel.remove(it.id) } })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("清空草稿") }, onClick = { showMenu = false; discard = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!preview && state.ready) {
                Surface {
                    Column(Modifier.navigationBarsPadding().fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = draft.onlySelf, enabled = enabled,
                                onCheckedChange = { value -> viewModel.edit { it.copy(onlySelf = value) } })
                            Text("仅自己可见", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                        if (state.busy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(state.progress, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                ArticleToolButton(R.drawable.outline_emoji_emotions_24, "表情", enabled) {
                                    showEmoji = !showEmoji
                                    if (showEmoji) keyboard?.hide() else keyboard?.show()
                                }
                                ArticleToolButton(R.drawable.outline_image_24, "插入图片", enabled) { pickAtCursor(false) }
                                ArticleToolButton(R.drawable.outline_note_alt_24, "从文件插入图片", enabled) { pickAtCursor(true) }
                                ArticleToolButton(R.drawable.outline_alternate_email_24, "提及用户", enabled) {
                                    showEmoji = false; keyboard?.hide()
                                    mentionPicker.launch(Intent(context, AtTopicActivity::class.java).putExtra("type", "user"))
                                }
                                ArticleToolButton(R.drawable.outline_tag_24, "插入话题", enabled) {
                                    showEmoji = false; keyboard?.hide()
                                    mentionPicker.launch(Intent(context, AtTopicActivity::class.java).putExtra("type", "topic"))
                                }
                            }
                            if (showEmoji) ArticleEmojiPanel(recent.map { it.data }, viewModel::insertEmoji)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (!state.ready) {
                if (state.loadError) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("草稿暂时无法读取，请清空损坏的草稿后重新编辑。")
                        TextButton(onClick = { discard = true }) { Text("清空草稿") }
                    }
                } else CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (preview) {
                ArticlePreview(draft)
            } else {
                LazyColumn(
                    state = editorListState,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "cover") {
                        if (draft.cover.isNotBlank()) {
                            Column(Modifier.fillMaxWidth()) {
                                AsyncImage(draft.cover, "题图", modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit)
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                                    TextButton(enabled = enabled, onClick = { coverPicker.launch(arrayOf("image/*")) }) { Text("更换题图") }
                                    TextButton(enabled = enabled, onClick = { viewModel.edit { it.copy(cover = "") } }) { Text("移除") }
                                }
                            }
                        } else {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth().height(132.dp).clickable(enabled = enabled) {
                                    coverPicker.launch(arrayOf("image/*"))
                                }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("＋ 添加题图", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("无题图会导致无法获得推荐", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    item(key = "title") {
                        BasicTextField(value = draft.title, onValueChange = { value -> viewModel.edit { it.copy(title = value) } },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), enabled = enabled,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner -> Box {
                                if (draft.title.isEmpty()) Text("添加标题", style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            } })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    itemsIndexed(draft.blocks, key = { _, block -> block.id }) { _, block ->
                        ArticleBlockEditor(block, enabled, state.focusBlockId == block.id, state.focusOffset,
                            onChange = { next -> viewModel.updateBlock(block.id) { next } },
                            onCaret = { start, end -> viewModel.setCaret(block.id, start, end) },
                            onFocusConsumed = viewModel::consumeFocus,
                            onDelete = { viewModel.remove(block.id) },
                            minLines = if (draft.blocks.size == 1) 8 else 1,
                        )
                    }
                }
            }
        }
    }

    if (confirmPublish) {
        AlertDialog(onDismissRequest = { confirmPublish = false }, title = { Text("发布图文") },
            text = { Text(if (draft.onlySelf) "发布后仅自己可见，是否继续？" else "图文将公开发布，是否继续？") },
            confirmButton = { TextButton(onClick = { confirmPublish = false; preview = false; viewModel.publish() }) { Text("发布") } },
            dismissButton = { TextButton(onClick = { confirmPublish = false }) { Text("取消") } })
    }
    if (discard) {
        AlertDialog(onDismissRequest = { discard = false }, title = { Text("清空草稿？") },
            text = { Text("将删除本机保存的这篇图文及所选图片。") },
            confirmButton = { TextButton(onClick = { discard = false; preview = false; viewModel.discard() }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("取消") } })
    }
    state.error?.let { message ->
        AlertDialog(onDismissRequest = viewModel::dismissError, title = { Text("提示") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("知道了") } },
            dismissButton = if (!state.ready) ({ TextButton(onClick = { discard = true; viewModel.dismissError() }) { Text("清空草稿") } }) else null)
    }
    state.captcha?.let { bytes ->
        val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        var code by rememberSaveable(bytes.contentHashCode()) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!state.busy) viewModel.cancelCaptcha() }, title = { Text("请输入验证码") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (bitmap != null) Image(bitmap, "图形验证码", modifier = Modifier.fillMaxWidth().height(100.dp), contentScale = ContentScale.Fit)
                else Text("验证码图片无法显示，请取消后重试")
                OutlinedTextField(value = code, onValueChange = { code = it }, singleLine = true, enabled = !state.busy,
                    label = { Text("验证码") }, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(enabled = !state.busy && bitmap != null && code.isNotBlank(), onClick = { viewModel.verifyCaptcha(code) }) { Text("验证并继续") } },
            dismissButton = { TextButton(enabled = !state.busy, onClick = viewModel::cancelCaptcha) { Text("取消") } })
    }
    state.createdId?.let {
        AlertDialog(onDismissRequest = onBack, title = { Text("发布成功") }, text = { Text("图文已发布，本机草稿已清除。") },
            confirmButton = { TextButton(onClick = onBack) { Text("完成") } })
    }
}

@Composable
private fun RowScope.ArticleToolButton(drawable: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(enabled = enabled, onClick = rememberHapticClick(onClick = onClick), modifier = Modifier.weight(1f)) {
        Icon(painterResource(drawable), label, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ArticleBlockEditor(
    block: ArticleBlock, enabled: Boolean, requestFocus: Boolean, focusOffset: Int,
    onChange: (ArticleBlock) -> Unit, onCaret: (Int, Int) -> Unit,
    onFocusConsumed: () -> Unit, onDelete: () -> Unit, minLines: Int,
) {
    if (block.type == "text") {
        var input by remember(block.id) { mutableStateOf(TextFieldValue(block.text)) }
        val requester = remember { FocusRequester() }
        LaunchedEffect(block.text) {
            if (input.text != block.text) input = TextFieldValue(block.text,
                TextRange(input.selection.start.coerceAtMost(block.text.length)))
        }
        LaunchedEffect(requestFocus, focusOffset) {
            if (requestFocus) {
                input = TextFieldValue(block.text, TextRange(focusOffset.coerceIn(0, block.text.length)))
                withFrameNanos { }
                requester.requestFocus()
                onFocusConsumed()
            }
        }
        BasicTextField(value = input, onValueChange = { next ->
            val changed = next.text != input.text
            input = next
            onCaret(next.selection.start, next.selection.end)
            if (changed) onChange(block.copy(text = next.text))
        }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged {
                if (it.isFocused) onCaret(input.selection.start, input.selection.end)
            }.padding(vertical = 8.dp),
            minLines = minLines,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            decorationBox = { inner -> Box {
                if (input.text.isEmpty()) Text("开始写正文…", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            } })
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(block.image, "正文图片", modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 100.dp, max = 480.dp).clickable(enabled = enabled) { onCaret(0, 0) },
                    contentScale = ContentScale.Fit)
                Surface(shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                    IconButton(enabled = enabled, onClick = onDelete) {
                        Icon(Icons.Default.Close, "移除正文图片", tint = MaterialTheme.colorScheme.onError)
                    }
                }
            }
            BasicTextField(value = block.description, onValueChange = { onChange(block.copy(description = it)) },
                enabled = enabled, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    .onFocusChanged { if (it.isFocused) onCaret(0, 0) },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner -> Box(contentAlignment = Alignment.Center) {
                    if (block.description.isEmpty()) Text("添加图片说明", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                    inner()
                } })
        }
    }
}

@Composable
private fun ArticlePreview(draft: ArticleDraft) {
    val context = LocalContext.current
    LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (draft.cover.isNotBlank()) item {
            AsyncImage(draft.cover, "题图", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        }
        item { Text(draft.title.ifBlank { "未填写标题" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        itemsIndexed(draft.blocks, key = { _, block -> block.id }) { _, block ->
            if (block.type == "text") {
                if (block.text.isNotBlank()) LinkText(text = block.text, textSize = 16f,
                    lineSpacingMultiplier = 1.3f, modifier = Modifier.fillMaxWidth(),
                    onOpenLink = { url, _ -> context.openInBrowser(url) })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AsyncImage(block.image, "正文图片", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    if (block.description.isNotBlank()) Text(block.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}
