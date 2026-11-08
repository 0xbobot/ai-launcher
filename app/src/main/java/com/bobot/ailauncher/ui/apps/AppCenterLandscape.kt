@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.AppBadge
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.home.IntentCapsuleField
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.AILauncherType
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
    // 折叠组（419/421）：状态由调用点传入（竖屏同款，不得另起一套）；
    // foldApps 已含压制与 N>0 门，横屏直接用。
    foldApps: List<AppInfo> = emptyList(),
    foldExpanded: Boolean = false,
    // 未读组（Bob 2026-11-07 晚）：状态由调用点传入（竖屏同款，不得另起一套）；
    // unreadApps 已按未读数排好序、含 N>0 门，横屏直接用。
    unreadApps: List<AppInfo> = emptyList(),
    // 未读组打磨（Bob 2026-11-08）：预览文案（包名→预览，无预览的包不在 map 里）、
    // 在用通知包（左滑启用判定）、左滑清理回调——调用点（竖屏）算好的同一份，
    // 横屏不另算、不另起状态源。
    unreadPreviews: Map<String, String> = emptyMap(),
    activeNotifPkgs: Set<String> = emptySet(),
    onClearNotifications: (String) -> Unit = {},
    onToggleFold: () -> Unit = {},
    // 折叠组（421 安静版）：组底“取消折叠”——调用点传入竖屏同一双写逻辑。
    onUnfold: () -> Unit = {},
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    // v0.68.82（横屏专项第 1 项）：两套手势对齐竖屏（底部上滑已删）
    onPullDownToD2: () -> Unit,
    onHeaderSwipeRight: () -> Unit,
    // F2 销账（线 C）：搜索契约与竖屏统一——同一 IntentCapsuleField 组件
    // （showClearButton 变体）、同一 TextFieldValue 状态层（composing 守卫 /
    // 120ms 防抖 / AppSearchIndex 过滤语义对横屏同样生效），本文件只保留
    // 横屏布局（列流 + 字母轨），不再自绘搜索框、不再走裸 String 契约
    searchFieldValue: TextFieldValue,
    onSearchFieldChange: (TextFieldValue) -> Unit,
    searchResults: List<AppInfo>?,
    // 内容深链标识（Bob 2026-11-05 晚）：竖屏按本句查询算好的支持集合，
    // 横屏搜索行共用（横屏不另算，判定口径与竖屏同一套）。
    deepLinkSupportedPkgs: Set<String> = emptySet(),
    onSearchSubmit: () -> Unit,
    // 应用中心升级：索引加载中标记（空结果文案区分"索引加载中"与"真无匹配"）
    indexLoading: Boolean = false,
    onAskQizai: (String) -> Unit,
    // 反馈闭环（Bob 2026-11-06）：空态重试提示——调用点（AppCenter.kt）
    // 传入竖屏同一状态、同一触发口径、同一行（FeedbackRetryRow），横屏不另算。
    showRetryPrompt: Boolean = false,
    retryLoading: Boolean = false,
    onRetryClick: () -> Unit = {},
    // 443：横屏空态市场指引——调用点（AppCenter.kt）传入竖屏同一状态、
    // 同一行（MarketGuideRow），横屏不另算、不另起状态。
    marketGuideTerm: String? = null,
    onMarketGuideClick: () -> Unit = {},
    // 与竖屏同一套焦点礼仪（v0.68.93）：焦点外报 + 范围捕获，
    // 供 AppCenter 根 Box 的点外部清焦观察者使用
    onSearchFocusChanged: (Boolean) -> Unit = {},
    // 425 齿轮 morph：调用点传入的当前焦点值（与 onSearchFocusChanged 同源，只读不写）
    searchFocused: Boolean = false,
    onSearchPositioned: (Rect) -> Unit = {},
    // 426（Bob 2026-11-04 点 X 回设置页）：morph 按钮位 bounds 回传，调用点
    // 记入同一排除状态（见 AppCenter 根 Box 观察者的 extraBoundsInHost）
    onMorphButtonPositioned: (Rect) -> Unit = {},
    // 横屏首页修（Bob 2026-10-25）：主屏 picker 会话状态（由调用点透传竖屏已有的
    // UiPrefs.homeRailTarget collectAsState 值）；rail 门与竖屏 AppCenter.kt:1419 同款——
    // 进场手势未松手时只显示首页 picker rail，松手后 dismissPicker 置 null，
    // 应用中心自己的 rail 再出现。默认 null 保持旧调用点行为不变。
    homeTarget: UiPrefs.HomeRailTarget? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
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
    ) {
        val rowH = LandscapeRowH
        // v0.68.73：字母头占一个完整行位（rowH），与应用同高
        // v0.68.75（Bob）：减去顶栏 56.dp + 底部分隔 8.dp，否则多算一行导致末行被压缩
        val rowsPerCol = ((maxHeight - 56.dp - 8.dp) / rowH).toInt().coerceAtLeast(3)

        // 未读组打磨（Bob 2026-11-08）：未读包集合，供列流里的 FlowItem.App
        // 判定该行是不是未读行（未读行带预览 + 左滑清理，非未读行一字不动）。
        val unreadPkgs = remember(unreadApps) { unreadApps.map { it.packageName }.toSet() }
        // 465（Bob 2026-11-08 19:27 横屏未读幽灵图标也没有）：未读组首个
        // 应用包名，供列流给首行挂幽灵信封（与竖屏首行信封同符号
        // UnreadGlyph、同尺寸 32dp）；其余未读行不挂，只首行有标记。
        val firstUnreadPkg = remember(unreadApps) { unreadApps.firstOrNull()?.packageName }

        // 按字母切列：每个字母从新列顶部开始，字母头占首行位（Bob）
        // 首列：[字母头 rowH][应用...]；续列：无头，应用直接从顶部开始
        // v0.68.75（Bob）：收藏放最前、隐藏放最后，核心功能不能丢
        // hiddenUnlocked 必须进 key：解锁前后隐藏列形态不同（锁定占位 ↔
        // 应用列），否则解锁后列流不重建。
        val columns = remember(azGroups, favApps, hiddenApps, hiddenUnlocked, foldApps, foldExpanded, unreadApps, rowsPerCol) {
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
            // 未读组（Bob 2026-11-07 晚）：收藏之后、A 组之前最优先；始终展开、
            // 不折叠；464 起区头“未读 · N 个”已删（Bob 2026-11-08 18:37），
            // 未读应用直接续排进列流、不占区头行位；组仍以“收藏之后第一列 +
            // rail 信封”标识。搜索态不走列流，未读组不参与。
            if (unreadApps.isNotEmpty()) {
                flush()
                var cap = colCap()
                var count = 0
                unreadApps.forEach { app ->
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
            // 折叠组（419/421）：A-Z 之后、隐藏区之前；平时只占区头一行
            // （“不常用 · N 个”＋收展 chevron，点按收展），展开时组内应用
            // 按列流续排（与其它区同 cap 逻辑），行后跟组底确认行。
            // 搜索态不走列流，折叠不参与。
            if (foldApps.isNotEmpty()) {
                flush()
                cur.add(FlowItem.FoldHeader(foldApps.size, foldExpanded))
                if (foldExpanded) {
                    var cap = colCap() - 1
                    var count = 0
                    foldApps.forEach { app ->
                        if (count >= cap) {
                            flush(); cap = colCap(); count = 0
                        }
                        cur.add(FlowItem.App(app)); count++
                    }
                    // 组底确认行占一列格；当前列已满先 flush 再放
                    if (count >= cap) {
                        flush()
                    }
                    cur.add(FlowItem.FoldFooter)
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
        // 折叠组（419）：收纳盒专位 → 折叠区头所在列
        val foldColIdx = remember(columns) {
            columns.indexOfFirst { col -> col.any { it is FlowItem.FoldHeader } }
                .takeIf { it >= 0 }
        }
        // 未读组：信封专位 → 未读应用所在列（464 起区头已删，改按列内
        // 是否含未读应用定位；rail 信封保留）。
        val unreadColIdx = remember(columns) {
            columns.indexOfFirst { col ->
                col.any { it is FlowItem.App && it.app.packageName in unreadPkgs }
            }.takeIf { it >= 0 }
        }

        fun scrollToLetter(letter: Char) {
            letterToCol[letter]?.let { idx ->
                scope.launch { rowState.animateScrollToItem(idx) }
            }
        }

        // 初始落在列表顶（Bob 2026-10-24 + 字母 B 方案 + 未读组）：
        // 只在首次进入且非搜索态时定位一次——未读组在则未读列、否则首个
        // 有应用的字母列（A 有应用时就是 A 列）；收藏列仍在，点 ★ 回去。
        // 守卫保证不与用户后续滚动打架、不影响搜索态。
        // 465（Bob 2026-11-08 19:27 横屏没从未读开始）：只有数据就绪
        // （有未读列或有字母列、target 非空）时才消耗一次定位；首帧
        // catalog 为空（AppCatalog.snapshot 初值 null，IO 构建）时
        // target 为 null，不消耗，等 catalog 到位、columns 变化后再定位
        // ——否则一次定位被空耗，列表停在收藏列（与竖屏
        // dockInitialScrollDone 同款问题，竖屏已有此守卫、横屏漏了）。
        LaunchedEffect(columns, searchResults) {
            if (!initialScrollDone && searchResults == null) {
                val target = unreadColIdx
                    ?: ('A'..'Z').firstNotNullOfOrNull { letterToCol[it] }
                if (target != null) {
                    initialScrollDone = true
                    rowState.scrollToItem(target)
                }
            }
        }

        // 取消搜索后落到列表顶：未读组在则未读列、否则首个字母列（同竖屏
        // 437 口径改后；此前横屏取消搜索后停在进入前位置，与竖屏不一致）。
        var hadSearched by remember { mutableStateOf(false) }
        LaunchedEffect(searchResults) {
            if (searchResults != null) {
                hadSearched = true
            } else if (hadSearched) {
                val target = unreadColIdx
                    ?: ('A'..'Z').firstNotNullOfOrNull { letterToCol[it] }
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
                    // Bob 2026-11-03：编辑态不再隐藏款数（胶囊与齿轮保留）；
                    // 款数 Hint 色细体（同竖屏刊头口径）
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
                            fontSize = AILauncherType.Caption.fontSize,
                            color = AILauncherColors.current.Hint
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // F2：与竖屏同一 IntentCapsuleField（占位/✕ 清空/焦点
                    // 礼仪全部同组件同口径；胶囊无提交钮，Bob 2026-11-03 与竖屏同批删）
                    // 应用中心升级：宽度 440dp 写死 → weight + 上限 440dp——
                    // 窄高横屏下不再挤压款数 / 齿轮
                    IntentCapsuleField(
                        value = searchFieldValue,
                        onValueChange = onSearchFieldChange,
                        onSubmit = onSearchSubmit,
                        // 425（Bob 2026-11-04，参考视频）：里侧清空去掉，挪到外面齿轮位（同竖屏）
                        showClearButton = false,
                        showSubmitButton = false,
                        // 428 滚动关键字（同竖屏）：应用中心箭头只作关键字的运行钮，
                        // 点箭头只填入关键字、网格随之过滤，不走 onSubmit。
                        keywordSubmitCallsOnSubmit = false,
                        fusedBackground = true,
                        onFocusChanged = onSearchFocusChanged,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 440.dp)
                            .onGloballyPositioned { onSearchPositioned(it.boundsInRoot()) }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 425（Bob 2026-11-04，参考视频）：齿轮位 morph（同竖屏）——失焦设置齿轮原样，
                    // 聚焦（输入态）就地变清空；只换图标＋动作＋文案，圆盘／位置／尺寸／testTag 一律不动。
                    // 点 X：有字清空（留输入态，X 还在）、没字退焦（键盘收起，自动变回齿轮）。
                    // 429：触发改成“聚焦 或 有字”（同竖屏）。
                    // 设置齿轮（Bob：横屏时竖屏的齿轮拿掉，放这里）
                    // F2：与竖屏同一 IconButton + testTag 锚点——此前是裸 Icon
                    // 无锚点，冒烟 §21 到达校验与布局几何断言在横屏测不到本节点、
                    // 触控面也只有约 28dp（竖屏为 48dp IconButton）
                    // 426（Bob 2026-11-04 点 X 回设置页）：按钮不可聚焦＋记 bounds 回传
                    // （同竖屏：点 X 不抢输入框焦点、按钮位不算点外面；圆盘／位置／
                    // 尺寸／testTag 一律不动，只加这两项）
                    IconButton(
                        onClick = {
                            // 429（同竖屏）：有字点 ✕ 只清空文本；没字且聚焦时点 ✕ 退焦；
                            // 没字且失焦时点齿轮进设置（不变）。
                            if (searchFieldValue.text.isNotEmpty()) onSearchFieldChange(TextFieldValue(""))
                            else if (searchFocused) focusManager.clearFocus()
                            else onOpenSettings()
                        },
                        modifier = Modifier.testTag("appcenter_settings")
                            .focusProperties { canFocus = false }
                            .onGloballyPositioned { onMorphButtonPositioned(it.boundsInRoot()) }
                    ) {
                        // Bob 2026-11-03 原型样式：齿轮坐浅色圆盘（原型同款）；
                        // 触控框仍是 IconButton 48dp，圆盘 40dp 只改视觉
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AILauncherColors.current.Title.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (searchFocused || searchFieldValue.text.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "清空",
                                    tint = AILauncherColors.current.Title,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "设置",
                                    tint = AILauncherColors.current.Hint.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
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
                            .padding(start = 24.dp, end = 24.dp, top = 8.dp)
                            // 触控下限 48dp：与竖屏同结构在横屏宽容器下布局
                            // 取整差 3px（实测行高 123px≈46.9dp，竖屏 126px=
                            // 48.0dp），§16 a11y 量到 46.9<48——下限保不变量，
                            // 内容更高时自然长高，外观与竖屏一致
                            .heightIn(min = 48.dp)
                            .clickable { throttledAsk(searchFieldValue.text.trim()) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "问助手：「${searchFieldValue.text.trim()}」",
                            fontSize = AILauncherType.Footnote.fontSize,
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
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        // 应用中心升级：索引缺失与真无匹配区分文案
                                        // 搜索同义词批（Bob 2026-11-05）：空态居中、一行——
                                        // 全宽居中、maxLines=1+省略号；文案略缩（同竖屏）。
                                        // 列本身已有 16dp 横向边距，Text 不再另加左右边距。
                                        text = if (indexLoading) "应用列表还没准备好，请稍后再试"
                                            else "没有找到「${searchFieldValue.text.trim()}」相关应用",
                                        fontSize = AILauncherType.Callout.fontSize,
                                        color = AILauncherColors.current.Hint,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 32.dp)
                                    )
                                    // 反馈闭环（Bob 2026-11-06）：与竖屏同一行、同一口径。
                                    // 443：大模型已认出意图、但本机没装对应应用时，
                                    // 重试行不再出现，改出应用市场指引行（同竖屏）。
                                    val guideTerm = marketGuideTerm
                                    if (guideTerm != null) {
                                        MarketGuideRow(
                                            term = guideTerm,
                                            onClick = onMarketGuideClick
                                        )
                                    } else if (showRetryPrompt) {
                                        FeedbackRetryRow(
                                            loading = retryLoading,
                                            onClick = onRetryClick
                                        )
                                    }
                                }
                            }
                        } else {
                            // 注：不用 items(results, key=)/trailing lambda——本文件上下文里
                            // LazyListScope.items 重载解析总偏向 count:Int 版，改用 forEach+item 避开
                            results.forEach { app ->
                                item(key = "s:${app.packageName}") {
                                    // Bob 2026-11-03：行距压缩——横屏搜索行同步走紧凑行
                                    LandscapeAppRow(
                                        app = app, onLaunch = onLaunch,
                                        compact = true,
                                        // 内容深链标识：这句查询下点按会走深链
                                        // 直达的应用才出「直达」标签。
                                        showDeepLinkBadge = deepLinkSupportedPkgs.contains(app.packageName)
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
                                            fontSize = AILauncherType.Title.fontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = AILauncherColors.current.Title,
                                            modifier = Modifier
                                                .height(rowH)
                                                .padding(start = 16.dp)
                                                .wrapContentHeight(Alignment.CenterVertically)
                                        )
                                    }
                                    is FlowItem.SectionHeader -> {
                                        // v0.68.75（Bob）：收藏/隐藏区头，占一个完整行位
                                        // 应用中心升级：区头金色 → Title 色（金色只留
                                        // 选中环 / 已收藏★ 本体，不再成片刷）
                                        // 464 起未读区头已删：SectionHeader 只剩收藏/隐藏；
                                        // 465 起列内幽灵信封补回来了，挂在未读组首行（见
                                        // FlowItem.App 未读分支），不再附在区头行上。
                                        Text(
                                            text = item.title,
                                            fontSize = AILauncherType.Body.fontSize,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            color = AILauncherColors.current.Title,
                                            modifier = Modifier
                                                .height(rowH)
                                                .padding(start = 16.dp)
                                                .wrapContentHeight(Alignment.CenterVertically)
                                        )
                                    }
                                    is FlowItem.FoldHeader -> {
                                        // 折叠组（419）：区头行位，点按收展；样式同区头
                                        // （Body 粗体、字距 2sp、Title 色）＋收展 chevron
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(rowH)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onToggleFold() }
                                                .padding(start = 16.dp, end = 8.dp)
                                        ) {
                                            Text(
                                                text = "不常用 · ${item.count} 个",
                                                fontSize = AILauncherType.Body.fontSize,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp,
                                                color = AILauncherColors.current.Title,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = if (item.expanded) Icons.Outlined.KeyboardArrowUp
                                                    else Icons.Outlined.KeyboardArrowDown,
                                                contentDescription = if (item.expanded) "收起不常用" else "展开不常用",
                                                tint = AILauncherColors.current.Hint,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    is FlowItem.FoldFooter -> {
                                        // 折叠组（421 安静版）：组底确认行——不点就是认了，
                                        // 点“取消折叠”走调用点 onUnfold（压制＋收起）。
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(rowH)
                                                .padding(start = 16.dp, end = 8.dp)
                                        ) {
                                            Text(
                                                text = "这些应用 90 天没打开，已折起来",
                                                fontSize = AILauncherType.Footnote.fontSize,
                                                color = AILauncherColors.current.Hint,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { onUnfold() }) {
                                                Text(
                                                    text = "取消折叠",
                                                    color = AILauncherColors.current.Body,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
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
                                                .padding(horizontal = 16.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(12.dp))
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
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "点击解锁查看",
                                                fontSize = AILauncherType.Callout.fontSize,
                                                color = AILauncherColors.current.Title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    is FlowItem.App -> {
                                        // 未读组打磨（Bob 2026-11-08）：未读行带预览 + 左滑清理；
                                        // 非未读行一字不动（不包左滑清理行，不改手势）。
                                        // 注意：搜索结果分支（上）在另一处，不走这里，不带预览/左滑。
                                        val pkg = item.app.packageName
                                        if (pkg in unreadPkgs) {
                                            // 465（Bob 2026-11-08 19:27 横屏未读幽灵图标也没有）：
                                            // 464 删区头时列内幽灵信封随区头行一起去掉了，现补回来——
                                            // 挂在未读组首行（与竖屏同款）：首行左边淡色信封
                                            // （UnreadGlyph，Title 色 35% 透明、32dp、start 16dp，
                                            // 与竖屏改小后的尺寸一致）；未读行整体起首 48dp
                                            // 给信封留位置（竖屏同款：行起首、信封占左），
                                            // 其余未读行同起首、只首行挂信封。rail 信封保留。
                                            Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                                                if (pkg == firstUnreadPkg) {
                                                    UnreadGlyph(
                                                        color = AILauncherColors.current.Title.copy(alpha = 0.35f),
                                                        modifier = Modifier
                                                            .align(Alignment.CenterStart)
                                                            .padding(start = 16.dp)
                                                            .size(32.dp)
                                                    )
                                                }
                                                ClearableUnreadRow(
                                                    enabled = pkg in activeNotifPkgs,
                                                    onClear = { onClearNotifications(pkg) }
                                                ) {
                                                    LandscapeAppRow(
                                                        app = item.app,
                                                        onLaunch = onLaunch,
                                                        compact = true,
                                                        preview = unreadPreviews[pkg],
                                                        modifier = Modifier.padding(start = 48.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            LandscapeAppRow(
                                                app = item.app,
                                                onLaunch = onLaunch,
                                                compact = true
                                            )
                                        }
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
            // v0.68.74（Bob）：右：波浪字母导航（复用竖屏 WaveRail，字母为有应用字母集）
            // v0.68.75（Bob）：加回收藏★和隐藏👁，核心功能不能丢
            // Bob 2026-11-03 回滚：字母导航回 409 加过滤前（搜索态 rail 按上一条隐藏）
            // 横屏首页修：主屏 picker 会话中隐藏自己的 rail（同竖屏 homeTarget==null 门，
            // 否则进场手势未松手时首页 picker rail 与本 rail 同时在场）
            // Bob 2026-11-03：搜索后隐藏字母导航（同竖屏门）
            if (homeTarget == null && searchResults == null) {
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
                // Bob 2026-11-03 回滚：👁 门回 409 加过滤前——只看隐藏分区非空
                items = remember(favApps, hiddenApps, letters, foldApps, unreadApps) {
                    buildRailItems(favApps.isNotEmpty(), letters, hiddenApps.isNotEmpty(), hasFold = foldApps.isNotEmpty(), hasUnread = unreadApps.isNotEmpty())
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
                        // 折叠组（419）：收纳盒专位 → 折叠区头所在列
                        is RailItem.Fold -> {
                            foldColIdx?.let { idx ->
                                scope.launch { rowState.animateScrollToItem(idx) }
                            }
                        }
                        // 未读组：信封专位 → 未读应用所在列（464 起区头已删）
                        is RailItem.Unread -> {
                            unreadColIdx?.let { idx ->
                                scope.launch { rowState.animateScrollToItem(idx) }
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
        // 应用中心底部上滑已删除（Bob 2026-11-03：避免操作干扰）。
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
    compact: Boolean = false,
    // 内容深链标识（Bob 2026-11-05 晚）：搜索结果行里，点按会走深链直达
    // 内容的应用出「直达」小标签；只打开首页的没有。调用方按当前查询算好
    // 再传（与竖屏 AppRow 同款标签、同一判定），默认不显示。
    showDeepLinkBadge: Boolean = false,
    // 未读预览行（Bob 2026-11-08）：应用名下弱化小字单行预览（调用方算好再传）；
    // null 时只留应用名，非未读行不受影响。
    preview: String? = null
) {
    // 未读角标（Bob 2026-11-07 晚）：通知监听汇总的该包条数；>0 才显示。
    val badgeCounts by NotificationRepository.badgeCounts.collectAsState()
    val badgeCount = badgeCounts[app.packageName] ?: 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 56.dp else LandscapeRowH)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onLaunch(app) }
            .padding(horizontal = 8.dp)
    ) {
        // 未读角标：叠在图标右上角、微微压住角；纯视觉覆盖层，不抢行点按。
        Box(modifier = Modifier.size(48.dp)) {
            AppIconImage(
                drawable = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            if (badgeCount > 0) {
                AppBadge(
                    count = badgeCount,
                    height = 18.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        // 未读预览行（Bob 2026-11-08）：应用名下弱化小字单行预览，不抢应用名；
        // 无预览时只留应用名。「直达」标签在行尾时名字列占满剩余宽（与原来单 Text
        // 的 weight 口径一致），预览跟名字同宽、单行省略。
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label.toString(),
                fontSize = AILauncherType.Callout.fontSize,
                color = AILauncherColors.current.Title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (preview != null) {
                Text(
                    text = preview,
                    fontSize = AILauncherType.Caption.fontSize,
                    lineHeight = AILauncherType.Caption.lineHeight,
                    color = AILauncherColors.current.Hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // 内容深链标识：小字标签「直达」（与竖屏 AppRow 同款）；本身不是
        // 可点目标，不抢行点按。
        if (showDeepLinkBadge) {
            Spacer(modifier = Modifier.width(8.dp))
            DeepLinkBadge()
        }
    }
}

private sealed interface FlowItem {
    data class Header(val letter: Char) : FlowItem
    data class SectionHeader(val title: String) : FlowItem
    // 折叠组（419）：区头（个数＋收展态），点按收展
    data class FoldHeader(val count: Int, val expanded: Boolean) : FlowItem
    // 折叠组（421 安静版）：组底确认行（展开态才有），点按走 onUnfold
    data object FoldFooter : FlowItem
    data object LockPlaceholder : FlowItem
    data class App(val app: AppInfo) : FlowItem
}
