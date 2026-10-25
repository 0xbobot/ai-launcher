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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.home.IntentCapsuleField
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic
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
    // 横屏隐藏补齐（Bob 2026-10-24，竖屏同款）：解锁状态与解锁回调由
    // 调用点（AppCenter.kt）传入竖屏已有的状态源，不得另起一套。
    hiddenUnlocked: Boolean = false,
    onUnlockHiddenApps: () -> Unit = {},
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
    // 应用中心升级：横屏箭头同竖屏 = 确认本地搜索；索引加载中标记
    // （空结果文案区分"索引加载中"与"真无匹配"）
    onSearchArrowSubmit: () -> Unit = {},
    indexLoading: Boolean = false,
    onAskQizai: (String) -> Unit,
    // 与竖屏同一套焦点礼仪（v0.68.93）：焦点外报 + 范围捕获，
    // 供 AppCenter 根 Box 的点外部清焦观察者使用
    onSearchFocusChanged: (Boolean) -> Unit = {},
    onSearchPositioned: (Rect) -> Unit = {},
    // 横屏首页修（Bob 2026-10-25）：主屏 picker 会话状态（由调用点透传竖屏已有的
    // UiPrefs.homeRailTarget collectAsState 值）；rail 门与竖屏 AppCenter.kt:1419 同款——
    // 进场手势未松手时只显示首页 picker rail，松手后 dismissPicker 置 null，
    // 应用中心自己的 rail 再出现。默认 null 保持旧调用点行为不变。
    homeTarget: UiPrefs.HomeRailTarget? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // 应用中心升级：键盘可见检测——横屏键盘弹出专用布局的开关
    // （隐藏 rail 与款数、搜索结果行切紧凑、composing 降透明度）
    // ime 是 WindowInsets.Companion 的扩展属性，须单独导入（同 HomeScreen）
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // 应用中心升级：问七仔行 500ms 节流（同竖屏）
    var lastAskMs by remember { mutableStateOf(0L) }
    fun throttledAsk(q: String) {
        val now = System.currentTimeMillis()
        if (now - lastAskMs < 500L) return
        lastAskMs = now
        onAskQizai(q)
    }
    val onPullDownToD2State = rememberUpdatedState(onPullDownToD2)
    val onHeaderSwipeRightState = rememberUpdatedState(onHeaderSwipeRight)
    val onBottomSwipeUpState = rememberUpdatedState(onBottomSwipeUp)
    val rowState = rememberLazyListState()
    // 横屏挖孔留白（Bob 2026-10-24）：横屏下左侧是竖屏状态栏位置
    // （摄像头/刘海），此前内容顶到左边缘、第一列会被挖孔遮挡。
    // 取系统 displayCutout inset，不写死数值；无挖孔的设备/模拟器上
    // inset 为 0，布局与此前逐像素一致（CI 证据因此继续有效）。
    // 列区起始留白 = max(16dp, 挖孔左侧 inset)：16dp 是现有列区水
    // 平留白；容器级统一加（cutoutLeft - 16dp）的正部——header 行与
    // 列区同量右移，相对位置不变，不引入新错位。
    // 注：getLeft/getRight 需显式传 layoutDirection；本文件全部按 LTR
    // 布局（start 即物理左），故传 Ltr，与现有 padding(start=…) 口径一致。
    val cutoutLeft = with(density) { WindowInsets.displayCutout.getLeft(density, LayoutDirection.Ltr).toDp() }
    val cutoutRight = with(density) { WindowInsets.displayCutout.getRight(density, LayoutDirection.Ltr).toDp() }
    val contentStartExtra = (cutoutLeft - 16.dp).coerceAtLeast(0.dp)
    // 横屏 rail 房子上拉（Bob 2026-10-24：与竖屏一致补房子项）：进度由
    // WaveRail 拖动回调驱动，≥1 提交一次回首页；松手后复位（见 onRelease）。
    var housePull by remember { mutableStateOf(0f) }
    var housePullCommitted by remember { mutableStateOf(false) }
    // 横屏隐藏补齐（竖屏同款 eyePull 语义）：👁下拉进度由 WaveRail 拖动
    // 回调驱动，≥1 提交一次生物识别解锁；松手后复位并重新武装。
    var eyePull by remember { mutableStateOf(0f) }
    var eyePullCommitted by remember { mutableStateOf(false) }
    var eyePullArmed by remember { mutableStateOf(true) }
    var lastEyePull by remember { mutableStateOf(0f) }
    val onUnlockHiddenAppsState = rememberUpdatedState(onUnlockHiddenApps)
    val hapticView = LocalView.current
    // 初始从 A 开始（Bob 2026-10-24）：首次进入且非搜索态时定位一次。
    var initialScrollDone by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // D-6：底部上滑回桌面观察者挂根容器（原 88dp 覆盖条已删，见文末注）；
            // 起点让出右侧 57dp Rail 区，搜索态 Rail 隐藏时不让
            .observeBottomSwipeUp(
                rightExclusionPx = {
                    // rail 右移后排除区同量右扩，保证排除区仍盖住整条 rail
                    // （57dp 是原 rail 区宽度口径；无右侧挖孔时与此前一致）。
                    if (searchResults == null) with(density) { (57.dp + cutoutRight).toPx() } else 0f
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
        // hiddenUnlocked 必须进 key：解锁前后隐藏列形态不同（锁定占位 ↔
        // 应用列），否则解锁后列流不重建。
        val columns = remember(azGroups, favApps, hiddenApps, hiddenUnlocked, rowsPerCol) {
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
            // 隐藏区（👁头）：竖屏同款门禁——未解锁时只放锁定占位（点按
            // 触发指纹/面部），解锁后才列出隐藏应用、可直接打开。
            if (hiddenApps.isNotEmpty()) {
                flush()
                cur.add(FlowItem.SectionHeader("👁 隐藏"))
                if (!hiddenUnlocked) {
                    cur.add(FlowItem.LockPlaceholder)
                } else {
                    var cap = colCap() - 1
                    var count = 0
                    hiddenApps.forEach { app ->
                        if (count >= cap) {
                            flush(); cap = colCap(); count = 0
                        }
                        cur.add(FlowItem.App(app)); count++
                    }
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

        // 初始从首个有应用的字母开始（Bob 2026-10-24 + 字母 B 方案）：
        // 只在首次进入且非搜索态时定位一次到首个有应用的字母列（A 有
        // 应用时就是 A 列）；收藏列仍在，点 ★ 回去。守卫保证不与用户
        // 后续滚动打架、不影响搜索态。
        LaunchedEffect(columns, searchResults) {
            if (!initialScrollDone && searchResults == null) {
                initialScrollDone = true
                val target = ('A'..'Z').firstNotNullOfOrNull { letterToCol[it] }
                if (target != null) rowState.scrollToItem(target)
            }
        }

        Row(Modifier.fillMaxSize()) {
            // 左：内容区（容器级挖孔偏移：header 行与列区同量右移，
            // 无挖孔时偏移为 0，布局不变）
            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = contentStartExtra)) {
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
                    // 只包款数：避开中间搜索框与右侧齿轮
                    // 应用中心升级：键盘弹出时款数隐藏（胶囊与齿轮保留）；
                    // 款数 Hint 色细体（同竖屏刊头口径）
                    if (!imeVisible) {
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
                            text = "$totalCount 款",
                            fontSize = 12.sp,
                            color = AILauncherColors.current.Hint
                        )
                    }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // F2：与竖屏同一 IntentCapsuleField（占位/提交/✕ 清空/焦点
                    // 礼仪全部同组件同口径）
                    // 应用中心升级：宽度 440dp 写死 → weight + 上限 440dp——
                    // 窄高横屏下不再挤压款数 / 齿轮；箭头同竖屏 = 确认本地搜索
                    IntentCapsuleField(
                        value = searchFieldValue,
                        onValueChange = onSearchFieldChange,
                        onSubmit = onSearchSubmit,
                        onArrowSubmit = onSearchArrowSubmit,
                        // 应用中心变体（同竖屏）：✕ 一键清空、清空后列流恢复全量
                        showClearButton = true,
                        onFocusChanged = onSearchFocusChanged,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 440.dp)
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
                // 应用中心升级：同竖屏去 Card 底降重（文字 Title 75%，
                // 金色只留小箭头），连点 500ms 节流
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
                            .clickable { throttledAsk(searchFieldValue.text.trim()) }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "问七仔：「${searchFieldValue.text.trim()}」",
                            fontSize = 13.sp,
                            color = AILauncherColors.current.Title.copy(alpha = 0.75f),
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
                            .padding(horizontal = 16.dp)
                            // 应用中心升级：composing 期间结果区降透明度至 0.6
                            // （Bob 定稿③；守卫与 120ms 防抖本身不动）
                            .graphicsLayer {
                                alpha = if (searchFieldValue.composition != null) 0.6f else 1f
                            },
                        // 底部留出手势条高度，避免末行被手势条盖住（同竖屏）
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        if (results.isEmpty()) {
                            item(key = "search-empty") {
                                Text(
                                    // 应用中心升级：索引缺失与真无匹配区分文案
                                    text = if (indexLoading) "正在加载应用索引，请稍后再试"
                                        else "没有找到「${searchFieldValue.text.trim()}」相关的应用",
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
                                    // 应用中心升级：键盘弹出时搜索结果行切紧凑
                                    LandscapeAppRow(
                                        app = app, onLaunch = onLaunch,
                                        compact = imeVisible
                                    )
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
                                        // 应用中心升级：区头金色 → Title 色（金色只留
                                        // 选中环 / 已收藏★ 本体，不再成片刷）
                                        Text(
                                            text = item.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            color = AILauncherColors.current.Title,
                                            modifier = Modifier
                                                .height(rowH)
                                                .padding(start = 12.dp)
                                                .wrapContentHeight(Alignment.CenterVertically)
                                        )
                                    }
                                    is FlowItem.LockPlaceholder -> {
                                        // 竖屏同款锁定占位（横屏列格样式）：点按触发
                                        // 指纹/面部解锁；CI 只能验占位渲染，生物识别
                                        // 本身归真机验收。
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(rowH)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onUnlockHiddenAppsState.value() }
                                                .testTag("hidden_locked")
                                                .padding(horizontal = 12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(AILauncherColors.current.Divider),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Fingerprint,
                                                    contentDescription = null,
                                                    tint = AILauncherColors.current.Hint,
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .semantics { invisibleToUser() }
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "点击解锁查看",
                                                fontSize = 14.sp,
                                                color = AILauncherColors.current.Title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
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
            // 应用中心升级：键盘弹出时同样隐藏 Rail
            // 横屏首页修：主屏 picker 会话中隐藏自己的 rail（同竖屏 homeTarget==null 门，
            // 否则进场手势未松手时首页 picker rail 与本 rail 同时在场）
            if (searchResults == null && !imeVisible && homeTarget == null) {
            // 右侧挖孔：rail 整块左让 cutoutRight（无右侧挖孔时为 0，
            // 布局不变）；不要只修左不修右。
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = cutoutRight)
            ) {
            AppRail(
                // 字母导航完全统一（Bob 2026-10-25 追问）：项目表走三处共用的
                // buildRailItems（RailItems.kt），构造与此前逐字一致，仅换调用；
                // letters 即调用点按 azGroups 派生的有应用字母集（与竖屏同源）。
                items = remember(favApps, hiddenApps, letters) {
                    buildRailItems(favApps.isNotEmpty(), letters, hiddenApps.isNotEmpty())
                },
                onActiveItem = { item ->
                    when (item) {
                        // 竖屏同口径：点房子本身不跳转，回首页走上拉手势。
                        is RailItem.House -> {}
                        is RailItem.Star -> {
                            scope.launch { rowState.animateScrollToItem(favColIdx) }
                        }
                        // 横屏隐藏补齐（竖屏同款）：未解锁时点 👁触发生物
                        // 识别解锁；解锁后点 👁滚到隐藏列。
                        is RailItem.Eye -> {
                            if (!hiddenUnlocked) {
                                onUnlockHiddenAppsState.value()
                            } else {
                                hiddenColIdx?.let { idx ->
                                    scope.launch { rowState.animateScrollToItem(idx) }
                                }
                            }
                        }
                        is RailItem.Letter -> {
                            // 找该字母或之后最近有应用的字母（过滤后一般直
                            // 中，兜底保留作保险）
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
                // 房子上拉回首页：与竖屏 housePull 语义一致（≥1 提交一次）。
                housePull = housePull,
                onHousePull = { p ->
                    if (!housePullCommitted && p >= 1f) {
                        housePullCommitted = true
                        hapticView.confirmHaptic()
                        onHeaderSwipeRightState.value()
                    }
                    housePull = p.coerceIn(0f, 1f)
                },
                // 👁下拉解锁：与竖屏 eyePull 语义一致（≥1 提交一次生物
                // 识别；未解锁时才生效，解锁后下拉不再触发）。
                eyePull = eyePull,
                onEyePull = { p ->
                    if (!eyePullCommitted && eyePullArmed && !hiddenUnlocked) {
                        val cp = p.coerceIn(0f, 1f)
                        eyePull = cp
                        if (cp >= 0.5f && lastEyePull < 0.5f) {
                            hapticView.tickHaptic()
                        }
                        if (cp >= 1f) {
                            eyePullCommitted = true
                            eyePullArmed = false
                            hapticView.confirmHaptic()
                            onUnlockHiddenAppsState.value()
                            eyePull = 0f
                            lastEyePull = 0f
                            eyePullCommitted = false
                        }
                        lastEyePull = cp
                    }
                },
                onRelease = {
                    housePull = 0f
                    housePullCommitted = false
                    eyePull = 0f
                    eyePullCommitted = false
                    eyePullArmed = true
                },
                // Rail 高度跟随容器（Bob 2026-10-24）：不再写死 400dp（横屏
                // 屏高不足时上下裁字母），占比 0.95、字母 14sp/16sp 行高。
                // 字母 B 方案：对比度拉满（横屏路径）——透明度 1.0 + Normal
                // 字重（329 起三条导航统一）；触控列宽与竖屏同为 48dp
                // （此前默认 44dp，modifier 上 56dp 被列宽覆盖、属于死码，
                // 随之删除）。
                railHeight = null,
                railFraction = 0.95f,
                letterFontSize = 14.sp,
                letterLineHeight = 16.sp,
                letterAlpha = 1f,
                letterFontWeight = FontWeight.Normal,
                railWidth = 48.dp,
                modifier = Modifier
                    .fillMaxHeight()
            )
            }  // Box：右侧挖孔让位结束
            }  // Rail 隐藏门结束（搜索态 / 键盘弹出 / 主屏 picker 会话）
        }
        // v0.68.82 的底部 88dp 透明手势条已于 D-6 删除（同竖屏：它盖住的
        // 底部带内行点按永不成立，CI 二分实证摘条即恢复）；「底部上滑回
        // 桌面」改由根容器的 observeBottomSwipeUp 观察者承担，起点右端让
        // 57dp 给 Rail 的旧口径由 rightExclusionPx 保留（见根 BoxWithConstraints）。
    }
}


// 横屏应用中心修（Bob 2026-10-24）：行高 68dp→60dp（仍高于 48dp 触控
// 线），同样屏高下每列多出一行，字母列达到头 + 4 个应用。
private val LandscapeRowH = 60.dp

/** 横屏应用行（68dp，复用于列流与搜索结果列表） */
@Composable
private fun LandscapeAppRow(
    app: AppInfo,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    // 应用中心升级：键盘弹出时行高 60→56dp（图标 48dp 不动）
    compact: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 56.dp else LandscapeRowH)
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
    data object LockPlaceholder : FlowItem
    data class App(val app: AppInfo) : FlowItem
}
