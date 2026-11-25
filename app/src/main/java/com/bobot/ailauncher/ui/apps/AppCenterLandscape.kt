@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppCatalog
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
    // 未读组（横屏图标网格，Bob 2026-11-22 定稿）：网格里没有预览小字，
    // 未读只用图标角标表示——unreadPreviews / unreadDisplayNames 不再
    // 用（竖屏仍用，调用点继续算）；左滑清理在格子里放不下（触发距离
    // 120dp 比格子宽），点按打开＋取消仍走 onOpenUnreadApp /
    // onUnreadRowOpened——onClearNotifications / onDisableAppNotifications
    // 不再用（竖屏仍用，调用点继续算）。四者已从签名去掉。
    activeNotifPkgs: Set<String> = emptySet(),
    onToggleFold: () -> Unit = {},
    // 折叠组（421 安静版）：组底“取消折叠”——调用点传入竖屏同一双写逻辑。
    onUnfold: () -> Unit = {},
    // 打开 + 清除（Bob 2026-11-08 23:20）：未读行点按要像点通知栏通知一样，
    // 成功打开时取消通知——调用点传 ::launchApp（Boolean），未读行处按
    // opened 门控取消；其余行只透传（Boolean→Unit 是 Kotlin 允许的
    // Unit 适配，LandscapeGridAppCell 的 onLaunch 仍是 (AppInfo) -> Unit）。
    onLaunch: (AppInfo) -> Boolean,
    // 未读行点按的“打开”（Bob 2026-11-08 23:49）：优先发通知自己的
    // contentIntent 直达会话/消息，没有或失败时退回普通打开应用
    // （调用点 AppCenter.kt 的 openUnreadApp）。默认 = onLaunch（普通打开），
    // 调用点传入通知感知的版本；只未读行用。
    onOpenUnreadApp: (AppInfo) -> Boolean = onLaunch,
    // 未读格点按的“取消”那一半（Bob 2026-11-09 12:20 ②）：先打开、确认应用
    // 真的到了前台再取消（调用点 AppCenter.kt 的 scheduleUnreadCancelAfterOpen）。
    // 默认空操作（只未读格点按用，调用点传入）；网格里没有左滑清理，
    // 点按打开＋取消是格子里唯一的清理路径。
    onUnreadRowOpened: (AppInfo) -> Unit = {},
    onLongPress: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    // v0.68.82（横屏专项第 1 项）：两套手势对齐竖屏（底部上滑已删）
    onPullDownToD2: () -> Unit,
    onHeaderSwipeRight: () -> Unit,
    // F2 销账（线 C）：搜索契约与竖屏统一——同一 IntentCapsuleField 组件
    // （showClearButton 变体）、同一 TextFieldValue 状态层（composing 守卫 /
    // 120ms 防抖 / AppSearchIndex 过滤语义对横屏同样生效），本文件只保留
    // 横屏布局（网格 + 字母轨），不再自绘搜索框、不再走裸 String 契约
    searchFieldValue: TextFieldValue,
    onSearchFieldChange: (TextFieldValue) -> Unit,
    searchResults: List<AppInfo>?,
    onSearchSubmit: () -> Unit,
    // 应用中心升级：索引加载中标记（空结果文案区分"索引加载中"与"真无匹配"）
    indexLoading: Boolean = false,
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
    val onPullDownToD2State = rememberUpdatedState(onPullDownToD2)
    val onHeaderSwipeRightState = rememberUpdatedState(onHeaderSwipeRight)
    val gridState = rememberLazyGridState()
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
    // 悬浮搜索直达输入态（Bob 2026-11-24 早：点搜索要直接进入输入态，
    // 不是显示搜索栏）：悬浮搜索钮点按后滚回顶部，再把本请求位置 true，
    // 搜索框（IntentCapsuleField 的 requestFocusOnMount，见下）随之聚焦、
    // 键盘弹起。一次性的请求位，聚焦确认或顶栏收起后即清掉（见下两处
    // LaunchedEffect），避免下次顶栏重现时误弹键盘。
    var fabFocusRequest by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {

        // 横屏图标网格（Bob 2026-11-22 定稿，设计稿
        // appcenter-landscape-grid-draft）：项表扁平化——分组标签占一格、
        // 与该组第一行应用同行，应用一格一个，纵向滚；列流（LazyRow + FlowItem）已删。
        // 顺序不变：收藏 → 未读（独立分栏，带“未读”标签）→ A–Z → 不常用 → 已隐藏。
        val gridItems = remember(azGroups, favApps, hiddenApps, hiddenUnlocked, foldApps, foldExpanded, unreadApps) {
            buildLandscapeGridItems(
                favApps = favApps,
                unreadApps = unreadApps,
                azGroups = azGroups,
                foldApps = foldApps,
                foldExpanded = foldExpanded,
                hiddenApps = hiddenApps,
                hiddenUnlocked = hiddenUnlocked
            )
        }
        // rail 点按 / 初始定位的锚点下标（组标签与未读首项在项表里的位置）。
        val anchors = remember(gridItems) { landscapeGridAnchors(gridItems) }
        fun scrollToLetter(letter: Char) {
            anchors.letters[letter]?.let { idx ->
                scope.launch { gridState.animateScrollToItem(idx) }
            }
        }
        // 搜索滚动收起（Bob 2026-11-23）：网格往下滚出初始落点行后，顶部
        // 搜索栏收起，右上角出现悬浮搜索/齿轮按钮；滚回初始落点行（或之
        // 上）时顶部栏回来。初始落点行＝未读组标签行（有未读时）或首字母
        // 组标签行——首次进入定位到该行，顶部栏不收起，避免一进来就误判
        // 为已滚动。搜索态不收起（搜索栏本身就是输入处）。
        val collapseOffsetPx = with(density) { 8.dp.toPx() }
        val homeRowStart = anchors.unreadHeader
            ?: ('A'..'Z').firstNotNullOfOrNull { anchors.letters[it] }
        val isTopBarCollapsed by remember(homeRowStart, searchResults) {
            derivedStateOf {
                if (searchResults != null) {
                    false
                } else {
                    val home = homeRowStart
                    if (home == null) {
                        gridState.firstVisibleItemIndex > 0 ||
                            gridState.firstVisibleItemScrollOffset > 0
                    } else {
                        gridState.firstVisibleItemIndex > home ||
                            (gridState.firstVisibleItemIndex == home &&
                                gridState.firstVisibleItemScrollOffset > collapseOffsetPx)
                    }
                }
            }
        }
        // 悬浮搜索请求位收尾（配合上文 fabFocusRequest，一次性）：
        // ① 聚焦确认后清掉——searchFocused 由 AppCenter.kt 的
        // onSearchFocusChanged 回流，变 true 即聚焦已落定，顺手显式弹一
        // 次键盘（聚焦本身一般已自动弹起，show() 是补一道、幂等）；
        // ② 顶栏收起时清掉——聚焦落定前用户又滚走了，请求作废，下次顶栏
        // 重现不再误弹键盘。两处都不用固定延时，时序由状态驱动。
        LaunchedEffect(searchFocused) {
            if (searchFocused) {
                fabFocusRequest = false
                keyboardController?.show()
            }
        }
        LaunchedEffect(isTopBarCollapsed) {
            if (isTopBarCollapsed) fabFocusRequest = false
        }

        // 初始落在网格顶（Bob 2026-10-24 + 字母 B 方案 + 未读组）：
        // 只在首次进入且非搜索态时定位一次——未读组在则未读首项、否则首个
        // 有应用的字母组头（A 有应用时就是 A 组头）；收藏组仍在，点 ★ 回去。
        // 守卫保证不与用户后续滚动打架、不影响搜索态。
        // 465（Bob 2026-11-08 19:27 横屏没从未读开始）：只有数据就绪
        // （有未读项或有字母组头、target 非空）时才消耗一次定位；首帧
        // catalog 为空（AppCatalog.snapshot 初值 null，IO 构建）时
        // target 为 null，不消耗，等 catalog 到位、gridItems 变化后再定位
        // ——否则一次定位被空耗，网格停在收藏组（与竖屏
        // dockInitialScrollDone 同款问题，竖屏已有此守卫、横屏漏了）。
        LaunchedEffect(gridItems, searchResults) {
            if (!initialScrollDone && searchResults == null) {
                val target = anchors.unread
                    ?: ('A'..'Z').firstNotNullOfOrNull { anchors.letters[it] }
                if (target != null) {
                    initialScrollDone = true
                    gridState.scrollToItem(target)
                }
            }
        }

        // 取消搜索后落到网格顶：未读组在则未读首项、否则首个字母组头（同竖屏
        // 437 口径改后；此前横屏取消搜索后停在进入前位置，与竖屏不一致）。
        var hadSearched by remember { mutableStateOf(false) }
        LaunchedEffect(searchResults) {
            if (searchResults != null) {
                hadSearched = true
            } else if (hadSearched) {
                val target = anchors.unread
                    ?: ('A'..'Z').firstNotNullOfOrNull { anchors.letters[it] }
                if (target != null) gridState.scrollToItem(target)
            }
        }

        Row(Modifier.fillMaxSize()) {
            // 左：内容区（容器级挖孔偏移：header 行与列区同量右移，
            // 无挖孔时偏移为 0，布局不变）
            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = contentStartExtra)) {
                // 紧凑顶栏（搜索滚动收起：网格往下滚出初始落点行后收起，滚回即回来）
                // v0.68.82（横屏专项第 1 项）：顶栏下滑 → D2（阈值沿用竖屏 80dp）
                val headThreshPx = with(density) { 80.dp.toPx() }
                if (!isTopBarCollapsed) {
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
                        // Stageshow 应用中心优化 3.5：计数只在搜索态出现——
                        // 默认态顶栏不显示「N 款」，打字后回来（与竖屏同口径）。
                        if (shouldShowAppCenterCount(searchFieldValue.text)) {
                            Text(
                                text = appCenterCountTextLandscape(totalCount),
                                fontSize = AILauncherType.Caption.fontSize,
                                color = AILauncherColors.current.Hint
                            )
                        }
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
                        // 428 滚动关键字（同竖屏）：文案审计第 2 批——应用中心
                        // 不轮换关键字（showKeywords = false，空闲时只显示静态占位）。
                        keywordSubmitCallsOnSubmit = false,
                        showKeywords = false,
                        fusedBackground = true,
                        onFocusChanged = onSearchFocusChanged,
                        // 悬浮搜索直达输入态（Bob 2026-11-24）：requestFocusOnMount
                        // 是组件自带的口子（按 prop 本身 key 的 LaunchedEffect，
                        // 翻 true 就发聚焦请求，键盘随聚焦弹起）；这里只接悬浮
                        // 搜索钮的一次性请求位，平时 false，点搜索栏本身的行为不动。
                        requestFocusOnMount = fabFocusRequest,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 440.dp)
                            .onGloballyPositioned { onSearchPositioned(it.boundsInRoot()) }
                    )
                    // Bob 2026-11-22：搜的时候 X 离胶囊太远——右边空档收到 8dp，
                    // X 贴到胶囊右边；不搜时空档恢复 weight，齿轮还在最右不动。
                    // 425 的“位置不动”是旧口径，本次改位置是 Bob 亲口定的。
                    // 426 行为不动：X 点按仍是有字清空／没字退焦，按钮仍不可聚焦，
                    // bounds 回传保留（onGloballyPositioned 随移动重报）。
                    if (searchFocused || searchFieldValue.text.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    // 425（Bob 2026-11-04，参考视频）：齿轮位 morph（同竖屏）——失焦设置齿轮原样，
                    // 聚焦（输入态）变清空；只换图标＋动作＋文案，圆盘／尺寸／testTag 一律不动。
                    // 位置：搜时 X 贴胶囊右边（Bob 2026-11-22，见上），不搜时齿轮回最右。
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
                }
                val results = searchResults
                if (results != null) {
                    // 横屏图标网格（Bob 2026-11-22 定稿）：搜索态用同一网格
                    // （只过滤显示的应用），不再是单列结果列表。
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(LandscapeGridColumns),
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
                        contentPadding = PaddingValues(bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (results.isEmpty()) {
                            item(key = "search-empty", span = { GridItemSpan(maxLineSpan) }) {
                                val context = LocalContext.current
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        // 应用中心升级：索引缺失与真无匹配区分文案
                                        // 搜索同义词批（Bob 2026-11-05）：空态居中、一行——
                                        // 全宽居中、maxLines=1+省略号；文案略缩（同竖屏）。
                                        // 列本身已有 16dp 横向边距，Text 不再另加左右边距。
                                        text = if (indexLoading) "应用列表还没加载好，稍后再试试吧"
                                            else "没找到「${searchFieldValue.text.trim()}」相关的应用",
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
                                    // 文案审计第 2 批：同竖屏——索引加载中时给可点的
                                    // 「重试」按钮，点按重调 AppCatalog.ensure。
                                    if (indexLoading) {
                                        CatalogRetryRow(onClick = { AppCatalog.ensure(context) })
                                    }
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
                            // LazyGridScope.items 重载解析总偏向 count:Int 版，改用 forEach+item 避开
                            results.forEach { app ->
                                item(key = "s:${app.packageName}") {
                                    LandscapeGridAppCell(
                                        app = app,
                                        // Unit 适配：onLaunch 现为 (AppInfo) -> Boolean，
                                        // 函数类型值不能直接当 (AppInfo) -> Unit 传，
                                        // 包一层 lambda 丢掉返回值（lambda 体尾表达式
                                        // 的 Unit 强制转换适用）。
                                        onLaunch = { onLaunch(it) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                // 横屏图标网格（Bob 2026-11-22 定稿）：纵向网格，分组标签
                // 占一格、与该组第一行应用同行，应用一格一个；列流（横滚多列）已删。
                // 悬浮按钮叠在网格右上角（搜索滚动收起后才出现），故网格包进 Box。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(LandscapeGridColumns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gridItems.forEachIndexed { index, gridItem ->
                        when (gridItem) {
                            is LandscapeGridItem.GroupLabel -> {
                                // 内联分组标签（Bob 2026-11-23）：占一格，与该组第一行
                                // 应用同行；标签在左列、应用在右。字母用刊头宋体
                                // （Display），词组（收藏/未读）用区块标题（Title）。
                                // 顶部留 12dp，与首行图标中心对齐。
                                item(key = "label:${gridItem.title}") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        val isLetter = gridItem.title.length == 1 &&
                                            gridItem.title[0] in 'A'..'Z'
                                        Text(
                                            text = gridItem.title,
                                            style = if (isLetter) AILauncherType.Display
                                                else AILauncherType.Title,
                                            color = AILauncherColors.current.Title,
                                            modifier = Modifier.padding(top = 12.dp)
                                        )
                                    }
                                }
                            }
                            is LandscapeGridItem.EmptyCell -> {
                                // 空占位格：续行行首与组末垫行，不画内容。
                                item(key = "empty:$index") {
                                    Spacer(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            is LandscapeGridItem.Header -> {
                                // 全宽组头（只剩“已隐藏的应用”）：占满一行。
                                item(key = "header:${gridItem.title}", span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = gridItem.title,
                                        fontSize = AILauncherType.Callout.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = AILauncherColors.current.Title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            is LandscapeGridItem.FoldHeader -> {
                                // 横屏图标网格：不常用行占满一行，放在网格最下方
                                // （稿子里的“不常用 · N 个”＋分隔线，字色弱化）。
                                item(key = "fold-header", span = { GridItemSpan(maxLineSpan) }) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .height(1.dp)
                                                .background(AILauncherColors.current.Divider)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onToggleFold() }
                                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                                        ) {
                                            val foldStyle = foldHeaderStyle()
                                            Text(
                                                text = foldHeaderText(gridItem.count),
                                                fontSize = foldStyle.fontSize,
                                                letterSpacing = foldStyle.letterSpacing,
                                                color = AILauncherColors.current.Hint,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = if (gridItem.expanded) Icons.Outlined.KeyboardArrowUp
                                                    else Icons.Outlined.KeyboardArrowDown,
                                                contentDescription = if (gridItem.expanded) "收起不常用" else "展开不常用",
                                                tint = AILauncherColors.current.Hint,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            is LandscapeGridItem.FoldFooter -> {
                                // 折叠组（421 安静版）：组底确认行——不点就是认了，
                                // 点“取消折叠”走调用点 onUnfold（压制＋收起）。
                                item(key = "fold-footer", span = { GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 8.dp)
                                    ) {
                                        TextButton(onClick = { onUnfold() }) {
                                            Text(
                                                text = "都展开",
                                                color = AILauncherColors.current.Body,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            is LandscapeGridItem.LockPlaceholder -> {
                                // 竖屏同款锁定占位：点按触发指纹/面部解锁；CI 只能
                                // 验占位渲染，生物识别本身归真机验收。
                                item(key = "hidden-lock", span = { GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onUnlockHiddenAppsState.value() }
                                            .testTag("hidden_locked")
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                                            text = "解锁查看",
                                            fontSize = AILauncherType.Callout.fontSize,
                                            color = AILauncherColors.current.Title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            is LandscapeGridItem.App -> {
                                // 横屏图标网格：一个应用一格；未读格点按仍走
                                // 打开＋取消（与竖屏同款，见 onOpenUnreadApp /
                                // onUnreadRowOpened），左滑清理在格子里放不下、
                                // 预览小字按稿子去掉，未读只用图标角标表示。
                                val pkg = gridItem.app.packageName
                                item(key = "app:$index:$pkg") {
                                    if (gridItem.isUnread) {
                                        LandscapeGridAppCell(
                                            app = gridItem.app,
                                            onLaunch = { app ->
                                                val opened = onOpenUnreadApp(app)
                                                if (opened && pkg in activeNotifPkgs) {
                                                    onUnreadRowOpened(app)
                                                }
                                            }
                                        )
                                    } else {
                                        LandscapeGridAppCell(
                                            app = gridItem.app,
                                            // Unit 适配（同搜索行）：包一层 lambda 丢返回值。
                                            onLaunch = { onLaunch(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // 悬浮按钮（搜索滚动收起后）：搜索在最右（贴 rail）、齿轮在其左。
                // 点搜索 → 滚回顶部、搜索栏重新展开并直接进入输入态（聚焦＋弹
                // 键盘，不用再点搜索栏）；点齿轮 → 设置；滚回顶部 → 顶部栏回来、
                // 悬浮按钮消失。
                if (isTopBarCollapsed) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 16.dp)
                    ) {
                        LandscapeFabButton(
                            icon = Icons.Filled.Settings,
                            description = "设置",
                            testTag = "appcenter_fab_settings",
                            onClick = { onOpenSettings() }
                        )
                        LandscapeFabButton(
                            icon = Icons.Filled.Search,
                            description = "搜索",
                            testTag = "appcenter_fab_search",
                            onClick = {
                                scope.launch {
                                    // 先滚回顶部（保留 569 行为），再把聚焦请求位置
                                    // true——顶栏是滚到顶后才重新出现的，请求位翻
                                    // true 时搜索框已在树里，组件内的
                                    // LaunchedEffect(requestFocusOnMount) 随即发聚
                                    // 焦请求（见 IntentCapsuleField），不用固定延时。
                                    gridState.animateScrollToItem(0)
                                    fabFocusRequest = true
                                }
                            }
                        )
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
                            scope.launch { gridState.animateScrollToItem(anchors.fav ?: 0) }
                        }
                        // 横屏隐藏补齐（竖屏同款）：未解锁时点 👁触发生物
                        // 识别解锁；解锁后点 👁滚到隐藏列。
                        is RailItem.Eye -> {
                            if (!hiddenUnlocked) {
                                onUnlockHiddenAppsState.value()
                            } else {
                                anchors.hidden?.let { idx ->
                                    scope.launch { gridState.animateScrollToItem(idx) }
                                }
                            }
                        }
                        is RailItem.Letter -> {
                            // 找该字母或之后最近有应用的字母（过滤后一般直
                            // 中，兜底保留作保险）
                            var c = item.char
                            while (c <= 'Z' && !anchors.letters.containsKey(c)) c++
                            if (c <= 'Z') scrollToLetter(c)
                            else {
                                // 往前找
                                c = item.char
                                while (c >= 'A' && !anchors.letters.containsKey(c)) c--
                                if (c >= 'A') scrollToLetter(c)
                            }
                        }
                        // 折叠组（419）：收纳盒专位 → 折叠区头所在项
                        is RailItem.Fold -> {
                            anchors.fold?.let { idx ->
                                scope.launch { gridState.animateScrollToItem(idx) }
                            }
                        }
                        // 未读组：信封专位 → 未读应用所在项（464 起区头已删）
                        is RailItem.Unread -> {
                            anchors.unread?.let { idx ->
                                scope.launch { gridState.animateScrollToItem(idx) }
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
                // 屏高不足时上下裁字母），占比 0.95、字母 14sp/20sp 行高。
                // 字母 B 方案：对比度拉满（横屏路径）——透明度 1.0 + Normal
                // 字重（329 起三条导航统一）；触控列宽与竖屏同为 48dp
                // （此前默认 44dp，modifier 上 56dp 被列宽覆盖、属于死码，
                // 随之删除）。
                railHeight = null,
                railFraction = 0.95f,
                letterFontSize = 14.sp,
                letterLineHeight = RAIL_LETTER_LINE_HEIGHT_14SP,
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


// 横屏图标网格（Bob 2026-11-22 定稿，设计稿
// appcenter-landscape-grid-draft）：列表行（60dp 行：48dp 图标＋名字＋
// 预览小字）改成图标网格。竖屏仍是列表，不动。
// 列数 8、图标 50dp（稿子 100px ≈ 50dp，Bob 批的就是这个尺寸，
// 不是 56dp——56dp 一屏只能放约 16 个，到不了 20+）。
internal val LandscapeGridColumns = 8
internal val LandscapeGridIconSize = 50.dp
/** 内联分组标签占 1 格，应用每行 7 格（标签列＋应用列＝8 列）。 */
internal val LandscapeGridAppsPerRow = 7

/**
 * 横屏图标网格的扁平项模型（纯数据，LazyVerticalGrid 直接渲染）。
 * 分组标签（收藏 / 未读 / A–Z）是内联标签：占一格，与该组第一行应用
 * 同行（标签在左列、应用在右，每行 7 格，换行时应用对齐在应用列下）；
 * 每组末行用空占位格垫满，下一组另起一行。不常用行、组底行、锁定占位
 * 与已隐藏组头仍占满一行（GridItemSpan(maxLineSpan)）。
 * 未读组有自己的“未读”标签（Bob 2026-11-23，独立分栏）：未读应用
 * 不论是否固定都移出收藏，只在未读组出现（数据单一归属调用点已保证）。
 */
internal sealed interface LandscapeGridItem {
    /** 内联分组标签（收藏 / 未读 / A–Z）：占一格，与该组第一行应用同行 */
    data class GroupLabel(val title: String) : LandscapeGridItem
    /** 空占位格：续行行首（应用对齐在应用列下）与组末垫行（下一组另起一行） */
    data object EmptyCell : LandscapeGridItem
    /** 全宽组头（只剩“已隐藏的应用”）：占满一行 */
    data class Header(val title: String) : LandscapeGridItem
    /** 一个应用格子；isUnread=未读组成员（角标＋点按直达＋取消） */
    data class App(
        val app: AppInfo,
        val isUnread: Boolean = false
    ) : LandscapeGridItem
    /** 不常用区头（个数＋收展态），点按收展 */
    data class FoldHeader(val count: Int, val expanded: Boolean) : LandscapeGridItem
    /** 不常用组底确认行（展开态才有），点按走 onUnfold */
    data object FoldFooter : LandscapeGridItem
    /** 隐藏区锁定占位（未解锁时） */
    data object LockPlaceholder : LandscapeGridItem
}

/**
 * 横屏图标网格的项表（纯函数，可单测）。顺序不变：收藏 → 未读
 * （独立分栏，带“未读”标签）→ A–Z → 不常用 → 已隐藏。未读应用
 * 只在未读组出现（调用点已保证单一归属：dockApps 与 azGroups 都
 * 排除了未读；这里只渲染传入的列表，不再过滤）。
 * 每组标签与第一行应用同行（标签 1 格＋应用每行 7 格），续行行首
 * 用空占位格、末行垫满，下一组另起一行。
 */
internal fun buildLandscapeGridItems(
    favApps: List<AppInfo>,
    unreadApps: List<AppInfo>,
    azGroups: List<Pair<Char, List<AppInfo>>>,
    foldApps: List<AppInfo>,
    foldExpanded: Boolean,
    hiddenApps: List<AppInfo>,
    hiddenUnlocked: Boolean
): List<LandscapeGridItem> {
    val items = mutableListOf<LandscapeGridItem>()
    // 一组（标签＋应用）：标签占首行首格，应用每行 7 格；续行行首补空
    // 占位格（应用对齐在应用列下），末行垫满（下一组另起一行）。
    fun emitLabeledGroup(label: String, apps: List<AppInfo>, isUnread: Boolean = false) {
        if (apps.isEmpty()) return
        items.add(LandscapeGridItem.GroupLabel(label))
        apps.chunked(LandscapeGridAppsPerRow).forEachIndexed { chunkIndex, chunk ->
            if (chunkIndex > 0) items.add(LandscapeGridItem.EmptyCell)
            chunk.forEach { app ->
                items.add(
                    LandscapeGridItem.App(
                        app,
                        isUnread = isUnread
                    )
                )
            }
        }
        val remainder = apps.size % LandscapeGridAppsPerRow
        val lastRowApps = if (remainder == 0) LandscapeGridAppsPerRow else remainder
        repeat(LandscapeGridColumns - 1 - lastRowApps) {
            items.add(LandscapeGridItem.EmptyCell)
        }
    }
    emitLabeledGroup("收藏", favApps)
    emitLabeledGroup("未读", unreadApps, isUnread = true)
    azGroups.forEach { (letter, apps) ->
        emitLabeledGroup(letter.toString(), apps)
    }
    if (foldApps.isNotEmpty()) {
        items.add(LandscapeGridItem.FoldHeader(foldApps.size, foldExpanded))
        if (foldExpanded) {
            foldApps.forEach { items.add(LandscapeGridItem.App(it)) }
            items.add(LandscapeGridItem.FoldFooter)
        }
    }
    if (hiddenApps.isNotEmpty()) {
        items.add(LandscapeGridItem.Header("已隐藏的应用"))
        if (!hiddenUnlocked) {
            items.add(LandscapeGridItem.LockPlaceholder)
        } else {
            hiddenApps.forEach { items.add(LandscapeGridItem.App(it)) }
        }
    }
    return items.toList()
}

/** rail 点按 / 初始定位的锚点下标（组头与未读首项在项表里的位置）。 */
internal data class LandscapeGridAnchors(
    val fav: Int?,
    val unread: Int?,
    /** 未读组标签下标：顶部栏收起的“家”行锚点（初始落点行）。 */
    val unreadHeader: Int?,
    val letters: Map<Char, Int>,
    val fold: Int?,
    val hidden: Int?
)

/** 从项表算锚点（纯函数，可单测）：字母取组标签首次出现的位置。 */
internal fun landscapeGridAnchors(items: List<LandscapeGridItem>): LandscapeGridAnchors {
    var fav: Int? = null
    var unread: Int? = null
    var unreadHeader: Int? = null
    val letters = mutableMapOf<Char, Int>()
    var fold: Int? = null
    var hidden: Int? = null
    items.forEachIndexed { index, item ->
        when (item) {
            is LandscapeGridItem.GroupLabel -> {
                if (item.title == "收藏" && fav == null) fav = index
                if (item.title == "未读" && unreadHeader == null) unreadHeader = index
                val letter = item.title.singleOrNull()
                if (letter != null && letter in 'A'..'Z' && !letters.containsKey(letter)) {
                    letters[letter] = index
                }
            }
            is LandscapeGridItem.Header -> {
                if (item.title == "已隐藏的应用" && hidden == null) hidden = index
            }
            is LandscapeGridItem.App -> {
                if (item.isUnread && unread == null) unread = index
            }
            is LandscapeGridItem.FoldHeader -> {
                if (fold == null) fold = index
            }
            else -> {}
        }
    }
    return LandscapeGridAnchors(fav, unread, unreadHeader, letters, fold, hidden)
}

/**
 * 横屏悬浮圆钮（搜索滚动收起后）：40dp 白色圆盘＋20dp 图标，48dp 触控面
 * （IconButton）；阴影 4dp。搜索在最右（贴 rail）、齿轮在其左。
 */
@Composable
private fun LandscapeFabButton(
    icon: ImageVector,
    description: String,
    testTag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(4.dp, CircleShape)
                .background(AILauncherColors.current.Card, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = AILauncherColors.current.Title,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 横屏图标网格格子：50dp 图标＋下方单行名字（超长省略）。
 * 未读只用图标右上角的角标表示（稿子定稿，预览小字去掉）；
 * 名字一律用应用名（未读通知标题是列表行的口径，格子里不用）。
 * 长按弹窗是竖屏专属（横屏原来就不接 onLongPress，见 AppCenter.kt
 * 传入但从未调用），格子只接点按，不改。
 */
@Composable
private fun LandscapeGridAppCell(
    app: AppInfo,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    // 未读角标（Bob 2026-11-07 晚）：通知监听汇总的该包条数；>0 才显示。
    val badgeCounts by NotificationRepository.badgeCounts.collectAsState()
    val badgeCount = badgeCounts[app.packageName] ?: 0
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onLaunch(app) }
            .padding(vertical = 4.dp)
    ) {
        // 未读角标：叠在图标右上角、微微压住角；纯视觉覆盖层，不抢格点按。
        Box(modifier = Modifier.size(LandscapeGridIconSize)) {
            AppIconImage(
                drawable = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(LandscapeGridIconSize)
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label.toString(),
            fontSize = AILauncherType.Caption.fontSize,
            lineHeight = AILauncherType.Caption.lineHeight,
            color = AILauncherColors.current.Title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

