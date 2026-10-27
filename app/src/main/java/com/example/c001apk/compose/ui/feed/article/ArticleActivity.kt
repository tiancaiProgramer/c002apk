package com.example.c001apk.compose.ui.feed.article

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.c001apk.compose.ui.component.BackButton
import com.example.c001apk.compose.ui.component.rememberHapticClick
import com.example.c001apk.compose.ui.theme.C001apkComposeTheme
import com.example.c001apk.compose.util.CookieUtil
import com.example.c001apk.compose.util.makeToast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ArticleActivity : ComponentActivity() {
    private val viewModel: ArticleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!CookieUtil.isLogin) { makeToast("请先登录"); finish(); return }
        enableEdgeToEdge()
        setContent {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleEditor(viewModel: ArticleViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft
    var preview by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var confirmPublish by rememberSaveable { mutableStateOf(false) }
    val enabled = state.ready && !state.busy && state.createdId == null
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        viewModel.importImages(it)
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> viewModel.importImages(listOf(uri), cover = true) }
    }
    fun pickAfter(id: String?) {
        viewModel.beginPick(id)
        picker.launch(arrayOf("image/*"))
    }

    BackHandler {
        if (preview && !state.busy) preview = false else onBack()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (preview) "图文预览" else "写图文") },
                navigationIcon = { BackButton { if (preview) preview = false else onBack() } },
                actions = {
                    TextButton(enabled = enabled, onClick = rememberHapticClick { preview = !preview }) {
                        Text(if (preview) "编辑" else "预览")
                    }
                    TextButton(enabled = enabled, onClick = rememberHapticClick { confirmPublish = true }) { Text("发布") }
                    Box {
                        IconButton(enabled = !state.busy, onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, "更多选项")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("清空草稿") }, onClick = { showMenu = false; discard = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!preview && state.ready) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (state.busy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(state.progress, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("草稿自动保存在本机 · 每段下方可插入文字或图片", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(enabled = enabled, onClick = { viewModel.addText(draft.blocks.lastOrNull()?.id) }) {
                                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("添加文字")
                                }
                                TextButton(enabled = enabled, onClick = { pickAfter(draft.blocks.lastOrNull()?.id) }) {
                                    Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(4.dp)); Text("添加图片")
                                }
                            }
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
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "title") {
                        OutlinedTextField(value = draft.title, onValueChange = { value -> viewModel.edit { it.copy(title = value) } },
                            modifier = Modifier.fillMaxWidth(), label = { Text("标题") }, enabled = enabled,
                            textStyle = MaterialTheme.typography.titleLarge, minLines = 1, maxLines = 3)
                    }
                    item(key = "cover") {
                        if (draft.cover.isNotBlank()) {
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                AsyncImage(draft.cover, "题图", modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit)
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                                    TextButton(enabled = enabled, onClick = { coverPicker.launch(arrayOf("image/*")) }) { Text("更换题图") }
                                    TextButton(enabled = enabled, onClick = { viewModel.edit { it.copy(cover = "") } }) { Text("移除") }
                                }
                            }
                        } else {
                            OutlinedButton(enabled = enabled, onClick = { coverPicker.launch(arrayOf("image/*")) }) {
                                Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text("添加题图（可选）")
                            }
                        }
                    }
                    itemsIndexed(draft.blocks, key = { _, block -> block.id }) { index, block ->
                        ArticleBlockEditor(block, index, draft.blocks.size, enabled,
                            onChange = { next -> viewModel.updateBlock(block.id) { next } },
                            onMove = { viewModel.move(block.id, it) },
                            onDelete = { viewModel.remove(block.id) },
                            onAddText = { viewModel.addText(block.id) },
                            onAddImages = { pickAfter(block.id) },
                        )
                    }
                    item(key = "visibility") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = draft.onlySelf, enabled = enabled,
                                onCheckedChange = { value -> viewModel.edit { it.copy(onlySelf = value) } })
                            Text("仅自己可见", style = MaterialTheme.typography.bodyMedium)
                        }
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
private fun ArticleBlockEditor(
    block: ArticleBlock, index: Int, count: Int, enabled: Boolean,
    onChange: (ArticleBlock) -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit,
    onAddText: () -> Unit, onAddImages: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (block.type == "text") "文字段落" else "图片", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(enabled = enabled && index > 0, onClick = { onMove(-1) }) { Icon(Icons.Outlined.ArrowUpward, "上移此段") }
                IconButton(enabled = enabled && index < count - 1, onClick = { onMove(1) }) { Icon(Icons.Outlined.ArrowDownward, "下移此段") }
                IconButton(enabled = enabled, onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "删除此段") }
            }
            if (block.type == "text") {
                OutlinedTextField(value = block.text, onValueChange = { onChange(block.copy(text = it)) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("输入正文…") }, minLines = 3)
            } else {
                AsyncImage(block.image, "正文图片", modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 320.dp), contentScale = ContentScale.Fit)
                OutlinedTextField(value = block.description, onValueChange = { onChange(block.copy(description = it)) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth(), label = { Text("图片说明（可选）") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = enabled, onClick = onAddText) { Text("在此后插入文字") }
                TextButton(enabled = enabled, onClick = onAddImages) { Text("插入图片") }
            }
        }
    }
}

@Composable
private fun ArticlePreview(draft: ArticleDraft) {
    LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (draft.cover.isNotBlank()) item {
            AsyncImage(draft.cover, "题图", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        }
        item { Text(draft.title.ifBlank { "未填写标题" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        itemsIndexed(draft.blocks, key = { _, block -> block.id }) { _, block ->
            if (block.type == "text") {
                if (block.text.isNotBlank()) Text(block.text, style = MaterialTheme.typography.bodyLarge)
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
