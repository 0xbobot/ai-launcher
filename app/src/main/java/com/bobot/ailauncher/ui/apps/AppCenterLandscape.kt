@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.home.IntentCapsuleField
import com.bobot.ailauncher.ui.theme.AILauncherColors
import kotlinx.coroutines.launch

/**
 * v0.68.66（Bob）：横屏应用中心 v4。
 * v0.68.73（Bob）：字母导航移到右侧（同竖屏）+ 显示完整 A-Z；
 *   字母头占一个完整应用行位（68.dp），融入应用流。
 * - 右 A-Z 全高数列：点按 + 按住上下滑动快速定位（drag → 字母 → 横滚）
 * - 应用区：竖排流——字母占首行位，一列排满往右续列（续列无头），不嵌套滚动
 */
@Composable
fun AppCenterLandscape(
    azGroups: List<Pair<Char, List<AppInfo>>>,
    letters: List<Char>,
    totalCount: Int,
    favApps: List<AppInfo> = emptyList(),
    hiddenApps: List<AppInfo> = emptyList(),
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    // v0.68.82（横屏专项第 1 项）：三套手势对齐竖屏
    onPullDownToD2: () -> Unit,
    onHeaderSwipeRight: () -> Unit,
    onBottomSwipeUp: () -> Unit,
    // F2 销账（线 C）：搜索契约与竖屏统一——同一 IntentCapsuleField 组件
    // （showClearButton 变体）、同一 TextFieldValue 状态层（composing 守卫 /
    // 120ms 防抖 / AppSearchIndex 过滤语义对横屏同样生效），本文件只保留
    // 横屏布局（列流 + 字母轨），不再自绘搜索框、不再走裸 String 契约
    searchFieldValue: TextFieldValue,
    onSearchFieldChange: (TextFieldValue) -> Unit,
    searchResults: List<AppInfo>?,
    onSearchSubmit: () -> Unit,
    onAskQizai: (String) -> Unit,
    // 与竖屏同一套焦点礼仪（v0.68.93）：焦点外报 + 范围捕获，
    // 供 AppCenter 根 Box 的点外部清焦观察者使用
    onSearchFocusChanged: (Boolean) -> Unit = {},
    onSearchPositioned: (Rect) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val onPullDownToD2State = rememberUpdatedState(onPullDownToD2)
    val onHeaderSwipeRightState = rememberUpdatedState(onHeaderSwipeRight)
    val onBottomSwipeUpState = rememberUpdatedState(onBottomSwipeUp)
    val rowState = rememberLazyListState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // D-6：底部上滑回桌面观察者挂根容器（原 88dp 覆盖条已删，见文末注）；
            // 起点让出右侧 57dp Rail 区，搜索态 Rail 隐藏时不让
            .observeBottomSwipeUp(
                rightExclusionPx = {
                    if (searchResults == null) with(density) { 57.dp.toPx() } else 0f
                }
            ) { onBottomSwipeUpState.value() }
    ) {
        val rowH = LandscapeRowH
        // v0.68.73：字母头占一个完整行位（rowH），与应用同高
        // v0.68.75（Bob）：减去顶栏 56.dp + 底部分隔 8.dp，否则多算一行导致末行被压缩
        val rowsPerCol = ((maxHeight - 56.dp - 8.dp) / rowH).toInt().coerceAtLeast(3)

        // 按字母切列：每个字母从新列顶部开始，字母头占首行位（Bob）
        // 首列：[字母头 rowH][应用...]；续列：无头，应用直接从顶部开始
        // v0.68.75（Bob）：收藏放最前、隐藏放最后，核心功能不能丢
        val columns = remember(azGroups, favApps, hiddenApps, rowsPerCol) {
            val cols = mutableListOf<MutableList<FlowItem>>()
            var cur = mutableListOf<FlowItem>()
            fun colCap(): Int = rowsPerCol
            fun flush() {
                if (cur.isNotEmpty()) { cols.add(cur); cur = mutableListOf() }
            }
            // 收藏区（★头）
            if (favApps.isNotEmpty()) {
                flush()
                cur.add(FlowItem.SectionHeader("★ 收藏"))
                var cap = colCap() - 1
                var count = 0
                favApps.forEach { app ->
                    if (count >= cap) {
                        flush(); cap = colCap(); count = 0
                    }
                    cur.add(FlowItem.App(app)); count++
                }
            }
            azGroups.forEach { (letter, apps) ->
                flush()  // 新字母新起一列
                cur.add(FlowItem.Header(letter))
                var cap = colCap() - 1  // 字母头已占 1 行
                var count = 0
                apps.forEach { app ->
                    if (count >= cap) {
                        flush()
                        // 续列：无头，应用直接顶头排
                        cap = colCap()
                        count = 0
                    }
                    cur.add(FlowItem.App(app)); count++
                }
            }
            // 隐藏区（👁头）
            if (hiddenApps.isNotEmpty()) {
                flush()
                cur.add(FlowItem.SectionHeader("👁 隐藏"))
                var cap = colCap() - 1
                var count = 0
                hiddenApps.forEach { app ->
                    if (count >= cap) {
                        flush(); cap = colCap(); count = 0
                    }
                    cur.add(FlowItem.App(app)); count++
                }
            }
            flush()
            cols.map { it.toList() }
        }
        // 字母 → 列索引（取该字母首次出现的列）；★→0，👁→末列
        val letterToCol = remember(columns) {
            val map = mutableMapOf<Char, Int>()
            columns.forEachIndexed { colIdx, col ->
                col.forEach { item ->
                    if (item is FlowItem.Header && !map.containsKey(item.letter)) {
                        map[item.letter] = colIdx
                    }
                }
            }
            map
        }
        val favColIdx = 0
        val hiddenColIdx = remember(columns) {
            columns.indexOfFirst { col -> col.any { it is FlowItem.SectionHeader && it.title.contains("隐藏") } }
                .takeIf { it >= 0 }
        }

        fun scrollToLetter(letter: Char) {
            letterToCol[letter]?.let { idx ->
                scope.launch { rowState.animateScrollToItem(idx) }
            }
        }

        Row(Modifier.fillMaxSize()) {
            // 左：内容区
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // 紧凑顶栏
                // v0.68.82（横屏专项第 1 项）：顶栏下滑 → D2（阈值沿用竖屏 80dp）
                val headThreshPx = with(density) { 80.dp.toPx() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 24.dp)
                        .pointerInput(Unit) {
                            var accumY = 0f
                            var fired = false
                            detectVerticalDragGestures(
                                onDragStart = { accumY = 0f; fired = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (fired) return@detectVerticalDragGestures
                                    accumY += dragAmount
                                    if (accumY > headThreshPx) {
                                        fired = true
                                        onPullDownToD2State.value()
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // v0.68.82（横屏专项第 1 项）：顶栏左半右滑 → D2（记住档，阈值沿用竖屏 48dp）
                    // 只包 INDEX + 款数：避开中间搜索框与右侧齿轮
                    Row(
                        modifier = Modifier.pointerInput(Unit) {
                            var accumX = 0f
                            var fired = false
                            val hPx = with(density) { 48.dp.toPx() }
                            detectHorizontalDragGestures(
                                onDragStart = { accumX = 0f; fired = false },
                                onDragCancel = { fired = true },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (fired) return@detectHorizontalDragGestures
                                    accumX += dragAmount
                                    if (accumX > hPx) {
                                        fired = true
                                        onHeaderSwipeRightState.value()
                                    }
                                }
                            )
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INDEX",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            color = AILauncherColors.current.Accent
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$totalCount 款",
                            fontSize = 12.sp,
                            color = AILauncherColors.current.Hint
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // F2：与竖屏同一 IntentCapsuleField（占位/提交/✕ 清空/焦点
                    // 礼仪全部同组件同口径）；宽度沿用原 pill 的 440dp 顶栏预算
                    IntentCapsuleField(
                        value = searchFieldValue,
                        onValueChange = onSearchFieldChange,
                        onSubmit = onSearchSubmit,
                        // 应用中心变体（同竖屏）：✕ 一键清空、清空后列流恢复全量
                        showClearButton = true,
                        onFocusChanged = onSearchFocusChanged,
                        modifier = Modifier
                            .width(440.dp)
                            .onGloballyPositioned { onSearchPositioned(it.boundsInRoot()) }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 设置齿轮（Bob：横屏时竖屏的齿轮拿掉，放这里）
                    // F2：与竖屏同一 IconButton + testTag 锚点——此前是裸 Icon
                    // 无锚点，冒烟 §21 到达校验与布局几何断言在横屏测不到本节点、
                    // 触控面也只有约 28dp（竖屏为 48dp IconButton）
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("appcenter_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = AILauncherColors.current.Hint.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // F2：「问七仔」行与竖屏同契约同结构（跟随原始文本即时出现、
                // 不等防抖落定，整句交七仔；横屏此前无此行）
                if (searchFieldValue.text.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 10.dp)
                            // 触控下限 48dp：与竖屏同结构在横屏宽容器下布局
                            // 取整差 3px（实测行高 123px≈46.9dp，竖屏 126px=
                            // 48.0dp），§16 a11y 量到 46.9<48——下限保不变量，
                            // 内容更高时自然长高，外观与竖屏一致
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AILauncherColors.current.Card.copy(alpha = 0.88f))
                            .clickable { onAskQizai(searchFieldValue.text.trim()) }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            // 装饰图标（行文案已表达含义）标装饰语义（同竖屏）
                            contentDescription = null,
                            tint = AILauncherColors.current.Accent,
                            modifier = Modifier
                                .size(16.dp)
                                .semantics { invisibleToUser() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "问七仔：「${searchFieldValue.text.trim()}」",
                            fontSize = 13.sp,
                            color = AILauncherColors.current.Accent,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = AILauncherColors.current.Accent,
                            modifier = Modifier
                                .size(16.dp)
                                .semantics { invisibleToUser() }
                        )
                    }
                }
                val results = searchResults
                if (results != null) {
                    // v0.68.82（横屏专项第 2 项）：搜索态——单列结果列表（行样式复用）
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        // 底部留出手势条高度，避免末行被手势条盖住（同竖屏）
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        if (results.isEmpty()) {
                            item(key = "search-empty") {
                                Text(
                                    text = "没有找到「${searchFieldValue.text.trim()}」相关的应用",
                                    fontSize = 14.sp,
                                    color = AILauncherColors.current.Hint,
                                    modifier = Modifier.padding(
                                        top = 32.dp, start = 12.dp, end = 12.dp
                                    )
                                )
                            }
                        } else {
                            // 注：不用 items(results, key=)/trailing lambda——本文件上下文里
                            // LazyListScope.items 重载解析总偏向 count:Int 版，改用 forEach+item 避开
                            results.forEach { app ->
                                item(key = "s:${app.packageName}") {
                                    LandscapeAppRow(app = app, onLaunch = onLaunch)
                                }
                            }
                        }
                    }
                } else {
                // 应用竖排流（单向横滚，不嵌套）
                LazyRow(
                    state = rowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = columns.size,
                        key = { idx -> "col:$idx" }
                    ) { idx ->
                        Column(
                            modifier = Modifier
                                .width(250.dp)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Top
                        ) {
                            columns[idx].forEach { item ->
                                when (item) {
                                    is FlowItem.Header -> {
                                        // v0.68.73（Bob）：字母头占一个完整应用行位，融入应用流
                                        Text(
                                            text = item.letter.toString(),
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AILauncherColors.current.Title,
                                            modifier = Modifier
                                                .height(rowH)
                                                .padding(start = 12.dp)
                                                .wrapContentHeight(Alignment.CenterVertically)
                                        )
                                    }
                                    is FlowItem.SectionHeader -> {
                                        // v0.68.75（Bob）：收藏/隐藏区头，占一个完整行位
                                        Text(
                                            text = item.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            color = AILauncherColors.current.Accent,
                                            modifier = Modifier
                                                .height(rowH)
                                                .padding(start = 12.dp)
                                                .wrapContentHeight(Alignment.CenterVertically)
                                        )
                                    }
                                    is FlowItem.App -> {
                                        LandscapeAppRow(
                                            app = item.app,
                                            onLaunch = onLaunch
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }  // else 非搜索态结束
                Spacer(modifier = Modifier.height(8.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(AILauncherColors.current.Divider)
            )
            // v0.68.74（Bob）：右：波浪字母导航（复用竖屏 WaveRail，完整 A-Z）
            // v0.68.75（Bob）：加回收藏★和隐藏👁，核心功能不能丢
            // v0.68.82（横屏专项第 2 项）：搜索态隐藏 Rail（定位无意义，同竖屏）
            if (searchResults == null) {
            AppRail(
                items = remember(favApps, hiddenApps) {
                    buildList {
                        if (favApps.isNotEmpty()) add(RailItem.Star)
                        if (favApps.isNotEmpty()) add(RailItem.Separator)
                        ('A'..'Z').forEach { add(RailItem.Letter(it)) }
                        if (hiddenApps.isNotEmpty()) {
                            add(RailItem.Separator)
                            add(RailItem.Eye)
                        }
                    }
                },
                onActiveItem = { item ->
                    when (item) {
                        is RailItem.Star -> {
                            scope.launch { rowState.animateScrollToItem(favColIdx) }
                        }
                        is RailItem.Eye -> {
                            hiddenColIdx?.let { idx ->
                                scope.launch { rowState.animateScrollToItem(idx) }
                            }
                        }
                        is RailItem.Letter -> {
                            // 找该字母或之后最近有应用的字母
                            var c = item.char
                            while (c <= 'Z' && !letterToCol.containsKey(c)) c++
                            if (c <= 'Z') scrollToLetter(c)
                            else {
                                // 往前找
                                c = item.char
                                while (c >= 'A' && !letterToCol.containsKey(c)) c--
                                if (c >= 'A') scrollToLetter(c)
                            }
                        }
                        else -> {}
                    }
                },
                onRelease = { },
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
            )
            }  // if (searchResults == null) 搜索态隐藏 Rail 结束
        }
        // v0.68.82 的底部 88dp 透明手势条已于 D-6 删除（同竖屏：它盖住的
        // 底部带内行点按永不成立，CI 二分实证摘条即恢复）；「底部上滑回
        // 桌面」改由根容器的 observeBottomSwipeUp 观察者承担，起点右端让
        // 57dp 给 Rail 的旧口径由 rightExclusionPx 保留（见根 BoxWithConstraints）。
    }
}


private val LandscapeRowH = 68.dp

/** 横屏应用行（68dp，复用于列流与搜索结果列表） */
@Composable
private fun LandscapeAppRow(
    app: AppInfo,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(LandscapeRowH)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onLaunch(app) }
            .padding(horizontal = 8.dp)
    ) {
        AppIconImage(
            drawable = app.icon,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = app.label.toString(),
            fontSize = 14.sp,
            color = AILauncherColors.current.Title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private sealed interface FlowItem {
    data class Header(val letter: Char) : FlowItem
    data class SectionHeader(val title: String) : FlowItem
    data class App(val app: AppInfo) : FlowItem
}
