package com.bobot.ailauncher.ui.apps

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.CustomCategories
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.listLaunchableApps
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * 全部应用列表内容（含 A-Z 快速索引），可嵌入可拖动 BottomSheet。
 * - 按首字母分组（A-Z；中文按拼音首字母，非 A-Z 开头归 "#"），分组头
 * - 右侧 A-Z 纵条：点按/拖动按 y 坐标定位字母，scrollToItem 跳转；
 *   拖动时中央悬浮大字母指示器，松手 600ms 后渐隐（[showIndexBar]=false 时隐藏）
 * - 搜索态隐藏索引条；搜索大小写不敏感，imeAction=Search
 * - 长按应用可整理分类（加入分组 / 新建分类 / 恢复自动）
 * - 启动应用时记录频次（Dock 常用排序用）
 * - [showSearch]=false 时隐藏搜索框（上拉 Dock 全屏态）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsContent(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    nestedScrollConnection: NestedScrollConnection? = null,
    onSearchFocus: () -> Unit = {},
    showIndexBar: Boolean = true,
    showSearch: Boolean = true,
    topPadding: Dp = 12.dp,
    indexBarHeightFraction: Float = 1f
) {
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
    // 字母 → LazyColumn 首项 index（计入分组头）
    val letterIndex = remember(groups) {
        val m = mutableMapOf<Char, Int>()
        var idx = 0
        groups.forEach { (letter, list) ->
            m[letter] = idx
            idx += 1 + list.size
        }
        m
    }
    val letters = remember { ('A'..'Z').toList() + '#' }
    // 惯用手：决定 A-Z 导航 rail 在哪一侧、按住时往哪边偏移（设置页可改）
    val handed = remember { UiPrefs.getHanded(context) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var barActiveIndex by remember { mutableStateOf<Int?>(null) }
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
        AppUsageTracker.recordLaunch(context, packageName)
        val intent: Intent? = context.packageManager
            .getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) context.startActivity(intent)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent) // 玻璃拟态：底色由 Dock 卡片提供，不再铺不透明暖灰
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(topPadding))
        if (showSearch) {
            AllAppsSearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearchFocus = onSearchFocus
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            var listModifier = Modifier.fillMaxSize()
            if (nestedScrollConnection != null) {
                listModifier = listModifier.nestedScroll(nestedScrollConnection)
            }
            LazyColumn(
                state = listState,
                modifier = listModifier,
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
                                    .background(Color.Transparent)
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
            // 右侧弧形 A-Z 导航：平时收拢窄条，按住后字母沿高斯弧线向屏内展开，
            // 当前字母放大 + 气泡（往拇指反方向偏移 64dp，不被拇指盖住），
            // 列表跟手滚动，松手回弹；惯用手决定 rail 在左还是右
            if (showIndexBar && !searching && groups.isNotEmpty()) {
                ArcIndexBar(
                    letters = letters,
                    activeIndex = barActiveIndex,
                    onIndex = { i ->
                        if (i < 0) {
                            barActiveIndex = null
                            activeLetter = null
                            hideJob?.cancel()
                        } else {
                            barActiveIndex = i
                            jumpTo(letters[i])
                        }
                    },
                    modifier = Modifier.align(
                        if (handed == UiPrefs.Handed.RIGHT) Alignment.CenterEnd
                        else Alignment.CenterStart
                    ),
                    heightFraction = indexBarHeightFraction,
                    handed = handed
                )
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

/**
 * 弧形 A-Z 快速导航（v0.12）：
 * - 平时是 20dp 收拢窄条，不占地方
 * - 按住后 26 个字母沿高斯弧线向屏幕内侧展开（越靠近手指越大），
 *   当前字母在拇指反方向弹出气泡；拖动时列表跟手 scrollToItem，松手回弹
 * - 拇指遮挡：按住瞬间整组视觉往拇指反方向偏移约 64dp（弹簧），
 *   右手→往左偏，左手→往右偏；触摸层不偏移，映射保持准确
 * - 惯用手：右手 rail 在右、弧线向左展开；左手 rail 在左、弧线向右展开
 */
@Composable
private fun ArcIndexBar(
    letters: List<Char>,
    activeIndex: Int?,
    onIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    heightFraction: Float = 1f,
    handed: UiPrefs.Handed = UiPrefs.Handed.RIGHT
) {
    val density = LocalDensity.current
    // 右手：-1（往左偏/向左展开）；左手：+1（往右偏/向右展开）
    val dirSign = if (handed == UiPrefs.Handed.RIGHT) -1f else 1f
    val barAlign = if (handed == UiPrefs.Handed.RIGHT) Alignment.CenterEnd
    else Alignment.CenterStart
    BoxWithConstraints(modifier = modifier.fillMaxHeight(heightFraction).width(48.dp)) {
        val hPx = constraints.maxHeight.toFloat()
        if (hPx <= 0f || letters.isEmpty()) return@BoxWithConstraints
        val rowHpx = hPx / letters.size
        val bulgePx = with(density) { 40.dp.toPx() }
        val shiftPx = with(density) { 64.dp.toPx() }
        // 按住时整组视觉往拇指反方向偏移（弹簧），字母/气泡不再被拇指盖住
        val shiftX by animateFloatAsState(
            targetValue = if (activeIndex != null) dirSign * shiftPx else 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = 0.85f
            ),
            label = "railShift"
        )
        // 触摸层：整块可触摸（含点按与纵向拖动），不偏移
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(letters, hPx) {
                    detectTapGestures(onTap = { offset ->
                        val i = ((offset.y / hPx) * letters.size)
                            .toInt().coerceIn(letters.indices)
                        onIndex(i)
                    })
                }
                .pointerInput(letters, hPx) {
                    detectVerticalDragGestures(
                        onDragEnd = { onIndex(-1) },
                        onDragCancel = { onIndex(-1) },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val i = ((change.position.y / hPx) * letters.size)
                                .toInt().coerceIn(letters.indices)
                            onIndex(i)
                        }
                    )
                }
        )
        // 视觉层：字母列 + 气泡，随按住偏移
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { translationX = shiftX }
        ) {
            Column(
                modifier = Modifier
                    .align(barAlign)
                    .width(24.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                letters.forEachIndexed { i, ch ->
                    val d = if (activeIndex != null) (i - activeIndex).toFloat() else 999f
                    val gTarget = if (activeIndex != null) exp(-(d * d) / 15.68f) else 0f
                    val g by animateFloatAsState(
                        targetValue = gTarget,
                        animationSpec = tween(120),
                        label = "arcG"
                    )
                    // 每行固定 1/27 高度：无论系统字号/屏幕尺寸，26 个字母 + # 必定完整显示，
                    // 不再依赖文字自然高度（之前大字号下会被裁剪）；触摸映射本就按等分计算
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ch.toString(),
                            fontSize = 11.sp,
                            // 全屏卡片是白 88% 玻璃：静息态用深炭灰字母保证对比度
                            //（纯白在白玻璃上不可见），当前字母保持金色强调
                            color = if (g > 0.5f) AILauncherColors.Accent
                            else AILauncherColors.Title.copy(alpha = 0.85f),
                            fontWeight = if (g > 0.5f) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.White.copy(alpha = 0.6f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 2f
                                )
                            ),
                            modifier = Modifier.graphicsLayer {
                                translationX = dirSign * bulgePx * g
                                val sc = 1f + g
                                scaleX = sc
                                scaleY = sc
                            }
                        )
                    }
                }
            }
            // 当前字母气泡：右手在字母左侧，左手在字母右侧（拇指反方向）
            activeIndex?.let { idx ->
                val rPx = with(density) { 22.dp.toPx() }
                Box(
                    modifier = Modifier
                        .align(
                            if (handed == UiPrefs.Handed.RIGHT) Alignment.TopStart
                            else Alignment.TopEnd
                        )
                        .offset {
                            IntOffset(
                                0,
                                (idx * rowHpx + rowHpx / 2f - rPx).roundToInt()
                            )
                        }
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AILauncherColors.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letters[idx].toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
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

/** 搜索框（showSearch=false 时不渲染） */
@Composable
private fun AllAppsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchFocus: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AILauncherColors.GlassCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, AILauncherColors.GlassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = AILauncherColors.Hint)
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索应用", color = AILauncherColors.Hint) },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        if (it.isFocused) onSearchFocus()
                        else keyboardController?.hide()
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
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
}
