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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.ui.components.AppIconImage
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
    // v0.68.82（横屏专项第 2 项）：真搜索框（状态层复用竖屏）
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<AppInfo>?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val onPullDownToD2State = rememberUpdatedState(onPullDownToD2)
    val onHeaderSwipeRightState = rememberUpdatedState(onHeaderSwipeRight)
    val onBottomSwipeUpState = rememberUpdatedState(onBottomSwipeUp)
    val rowState = rememberLazyListState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
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
                            color = AILauncherColors.Accent
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$totalCount 款",
                            fontSize = 12.sp,
                            color = AILauncherColors.Hint
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // v0.68.82（横屏专项第 2 项）：真搜索框——pill 外形不变，内部换 BasicTextField
                    Row(
                        modifier = Modifier
                            .width(440.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(AILauncherColors.Card.copy(alpha = 0.7f))
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = AILauncherColors.Title
                            ),
                            cursorBrush = SolidColor(AILauncherColors.Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                            }),
                            decorationBox = { innerTextField ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = AILauncherColors.Hint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (query.isEmpty()) {
                                            Text(
                                                text = "搜索应用，支持拼音/首字母",
                                                fontSize = 14.sp,
                                                color = AILauncherColors.Hint,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (query.isNotEmpty()) {
                                        IconButton(
                                            onClick = { onQueryChange("") },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "清空",
                                                tint = AILauncherColors.Hint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // 设置齿轮（Bob：横屏时竖屏的齿轮拿掉，放这里）
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = AILauncherColors.Hint,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onOpenSettings() }
                            .padding(2.dp)
                    )
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
                                    text = "没有找到「${query.trim()}」相关的应用",
                                    fontSize = 14.sp,
                                    color = AILauncherColors.Hint,
                                    modifier = Modifier.padding(
                                        top = 32.dp, start = 12.dp, end = 12.dp
                                    )
                                )
                            }
                        } else {
                            items(results, key = { _, app -> "s:${app.packageName}" }) { app ->
                                LandscapeAppRow(app = app, onLaunch = onLaunch)
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
                                            color = AILauncherColors.Title,
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
                                            color = AILauncherColors.Accent,
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
                    .background(AILauncherColors.Divider)
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
        // v0.68.82（横屏专项第 1 项）：底部 88dp 透明手势条，上滑 → D2（记住档）
        // 后于内容 Row 声明：手势条优先于列表（同竖屏）；右端让出 57dp 给 AppRail（Rail 优先，同竖屏）
        // 搜索态 Rail 隐藏时手势条占满整宽
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(maxWidth - if (searchResults == null) 57.dp else 0.dp)
                .height(88.dp)
                .pointerInput(Unit) {
                    var accumY = 0f
                    var fired = false
                    val bottomStripPx = with(density) { 56.dp.toPx() }
                    detectVerticalDragGestures(
                        onDragStart = { accumY = 0f; fired = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            if (fired) return@detectVerticalDragGestures
                            accumY += dragAmount
                            if (accumY < -bottomStripPx) {
                                fired = true
                                onBottomSwipeUpState.value()
                            }
                        }
                    )
                }
        )
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
            color = AILauncherColors.Title,
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
