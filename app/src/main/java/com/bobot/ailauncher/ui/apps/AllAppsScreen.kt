package com.bobot.ailauncher.ui.apps

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.CustomCategories
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全部应用列表（含 A-Z 快速索引）。
 * - 按首字母分组（A-Z；中文按拼音首字母，非 A-Z 开头归 "#"），stickyHeader 分组头
 * - 右侧 A-Z 纵条：点按/拖动按 y 坐标定位字母，scrollToItem 跳转；
 *   拖动时中央悬浮大字母指示器，松手 600ms 后渐隐
 * - 搜索态隐藏索引条；搜索大小写不敏感，imeAction=Search
 * - 长按应用可整理分类（加入分组 / 新建分类 / 恢复自动）
 *
 * @param onClose 抽屉模式：非空时顶部显示把手（下滑关闭）+ 关闭按钮；为空时显示普通大标题。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsScreen(onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var organizeApp by remember { mutableStateOf<AppInfo?>(null) }
    val apps = remember {
        val collator = Collator.getInstance(Locale.CHINA)
        listLaunchableApps(context).sortedWith { a, b ->
            collator.compare(a.label.toString(), b.label.toString())
        }
    }
    val searching = query.trim().isNotBlank()
    val filtered = remember(query, apps) {
        val q = query.trim()
        if (q.isBlank()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    // 首字母分组：A-Z 在前，"#"（非字母开头）最后；中文按拼音首字母
    val groups: List<Pair<Char, List<AppInfo>>> = remember(filtered, searching) {
        if (searching) return@remember emptyList()
        val map = linkedMapOf<Char, MutableList<AppInfo>>()
        filtered.forEach { app ->
            val key = groupKey(app.label)
            map.getOrPut(key) { mutableListOf() }.add(app)
        }
        map.toList().sortedWith(compareBy({ if (it.first == '#') 1 else 0 }, { it.first }))
    }
    // 字母 → LazyColumn 首项 index（计入 stickyHeader）
    val letterIndex = remember(groups) {
        val m = mutableMapOf<Char, Int>()
        var idx = 0
        groups.forEach { (letter, list) ->
            m[letter] = idx
            idx += 1 + list.size
        }
        m
    }
    val listState = rememberLazyListState()
    val letters = remember { ('A'..'Z').toList() + '#' }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    fun jumpTo(letter: Char) {
        activeLetter = letter
        val sortedKeys = letterIndex.keys.sortedWith(compareBy({ it == '#' }, { it }))
        val targetKey = if (letterIndex.containsKey(letter)) letter
        else sortedKeys.firstOrNull { it != '#' && it >= letter } ?: sortedKeys.lastOrNull()
        targetKey?.let { key ->
            letterIndex[key]?.let { idx ->
                scope.launch { listState.scrollToItem(idx) }
            }
        }
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(600)
            activeLetter = null
        }
    }

    fun launchApp(packageName: String) {
        val intent: Intent? = context.packageManager
            .getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) context.startActivity(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AILauncherColors.Background)
            .padding(horizontal = 20.dp)
    ) {
        if (onClose != null) {
            var dragAccum by remember { mutableFloatStateOf(0f) }
            val closeThresholdPx = with(density) { 90.dp.toPx() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { dragAccum = 0f },
                            onDragCancel = { dragAccum = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 0f) {
                                    dragAccum += dragAmount
                                    change.consume()
                                }
                                if (dragAccum > closeThresholdPx) {
                                    dragAccum = 0f
                                    onClose()
                                }
                            }
                        )
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AILauncherColors.Divider)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = AILauncherColors.Hint
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = "全部应用",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AILauncherColors.Title
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = AILauncherColors.Hint)
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索应用", color = AILauncherColors.Hint) },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (!it.isFocused) keyboardController?.hide() },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "清空",
                                    tint = AILauncherColors.Hint
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (searching) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            onLaunch = {
                                launchApp(app.packageName)
                                query = ""
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            },
                            onLongClick = { organizeApp = app }
                        )
                    }
                } else {
                    groups.forEach { (letter, list) ->
                        item(key = "header-$letter") {
                            Text(
                                text = letter.toString(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AILauncherColors.Hint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AILauncherColors.Background)
                                    .padding(vertical = 4.dp)
                            )
                        }
                        items(list, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                onLaunch = { launchApp(app.packageName) },
                                onLongClick = { organizeApp = app }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
            // 右侧 A-Z 快速索引条（搜索态隐藏）
            if (!searching && groups.isNotEmpty()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(28.dp)
                ) {
                    // maxHeight 只在 BoxWithConstraints 内容作用域可见，
                    // 必须在 pointerInput 外先算好像素高度再传入手势闭包
                    val hPx = with(density) { maxHeight.toPx() }
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .pointerInput(letters) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val i = ((offset.y / hPx) * letters.size)
                                            .toInt().coerceIn(0, letters.size - 1)
                                        jumpTo(letters[i])
                                    }
                                )
                            }
                            .pointerInput(letters) {
                                detectVerticalDragGestures(
                                    onDragEnd = { /* hideJob 的 600ms 计时负责渐隐 */ },
                                    onVerticalDrag = { change, _ ->
                                        change.consume()
                                        val i = ((change.position.y / hPx) * letters.size)
                                            .toInt().coerceIn(0, letters.size - 1)
                                        jumpTo(letters[i])
                                    }
                                )
                            },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        letters.forEach { letter ->
                            Text(
                                text = letter.toString(),
                                fontSize = 10.sp,
                                color = if (letter == activeLetter) AILauncherColors.Accent
                                else AILauncherColors.Hint,
                                fontWeight = if (letter == activeLetter) FontWeight.Bold
                                else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            // 中央悬浮大字母指示器（alpha 动画实现渐显渐隐）
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (activeLetter != null) 1f else 0f,
                label = "letterIndicatorAlpha"
            )
            if (indicatorAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AILauncherColors.Accent)
                        .alpha(indicatorAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeLetter?.toString().orEmpty(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // 长按整理分类 Dialog
    organizeApp?.let { app ->
        OrganizeDialog(app = app, onDismiss = { organizeApp = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppInfo, onLaunch: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onLaunch, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(
            drawable = app.icon,
            contentDescription = app.label.toString(),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = app.label.toString(),
            fontSize = 16.sp,
            color = AILauncherColors.Title
        )
    }
}

/** 分组 key：a-z/A-Z→大写；中文→拼音首字母大写（GB2312 区位边界法，无需第三方库）；数字及其他→'#' */
private fun groupKey(label: CharSequence): Char {
    val c = label.firstOrNull() ?: return '#'
    if (c in 'a'..'z') return c.uppercaseChar()
    if (c in 'A'..'Z') return c
    if (c in '0'..'9') return '#'
    return pinyinInitial(c)
}

/** 汉字拼音首字母：GB2312 编码区位与拼音首字母边界对照（i/u/v 不做声母，23 个字母） */
private fun pinyinInitial(c: Char): Char {
    return try {
        val bytes = c.toString().toByteArray(charset("GB2312"))
        if (bytes.size < 2) return '#'
        val secPos = (bytes[0].toInt() and 0xFF) * 100 + (bytes[1].toInt() and 0xFF) - 16160
        // 上式 = ((b0-160)*100 + (b1-160))，即区位码
        val bounds = intArrayOf(
            1601, 1637, 1833, 2078, 2274, 2302, 2433, 2594, 2787,
            3106, 3212, 3472, 3635, 3722, 3730, 3858, 4027, 4086,
            4390, 4558, 4684, 4925, 5249, 5590
        )
        val letters = "abcdefghjklmnopqrstwxyz"
        for (i in bounds.indices.reversed()) {
            if (secPos >= bounds[i]) return letters[i].uppercaseChar()
        }
        '#'
    } catch (_: Exception) {
        '#'
    }
}

/** 长按应用 → 整理分类：加入分组 / 新建分类 / 恢复自动 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OrganizeDialog(app: AppInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val builtinGroups = remember { CapabilityRegistry.groups() }
    val customGroups = remember { CustomCategories.getCustomGroups(context) }
    val mapping = remember { CustomCategories.getMapping(context) }
    val currentGroupId = mapping[app.packageName]
    val labelOf: (String) -> String = { gid ->
        builtinGroups.firstOrNull { it.id == gid }?.label
            ?: customGroups.firstOrNull { it.id == gid }?.label
            ?: gid
    }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("整理分类") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${app.label} · 当前归属：${currentGroupId?.let(labelOf) ?: "自动"}",
                    fontSize = 13.sp,
                    color = AILauncherColors.Hint
                )
                Spacer(modifier = Modifier.height(8.dp))
                val allGroups = builtinGroups.map { it.id to it.label } +
                    customGroups.map { it.id to it.label }
                allGroups.forEach { (gid, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = {
                                CustomCategories.setMapping(context, app.packageName, gid)
                                Toast.makeText(context, "已加入「$label」", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            })
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = AILauncherColors.Title,
                            modifier = Modifier.weight(1f)
                        )
                        if (currentGroupId == gid) {
                            Text("当前", fontSize = 12.sp, color = AILauncherColors.Accent)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (creating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("新分类名称", color = AILauncherColors.Hint) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            val name = newName.trim()
                            if (name.isBlank()) {
                                Toast.makeText(context, "请输入分类名称", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            val gid = CustomCategories.addCustomGroup(context, name)
                            CustomCategories.setMapping(context, app.packageName, gid)
                            Toast.makeText(context, "已加入「$name」", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }) {
                            Text("确定")
                        }
                    }
                } else {
                    TextButton(onClick = { creating = true }) {
                        Text("＋ 新建分类")
                    }
                }
            }
        },
        confirmButton = {
            if (currentGroupId != null) {
                TextButton(onClick = {
                    CustomCategories.removeMapping(context, app.packageName)
                    Toast.makeText(context, "已恢复自动", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) {
                    Text("恢复自动")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
