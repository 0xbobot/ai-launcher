@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.apps

import android.content.Intent
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.fragment.app.FragmentActivity
import com.bobot.ailauncher.data.BiometricAuth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobot.ailauncher.data.AppCatalog
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppSearchIndex
import com.bobot.ailauncher.ui.home.IntentCapsuleField
import com.bobot.ailauncher.ui.home.clearIntentFocusOnOutsideTap
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.HiddenApps
import com.bobot.ailauncher.data.InstalledApps
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.statusBarsTopPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import kotlin.math.exp
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * 应用中心（v0.22，PRD 重设计方向）：D3 全屏单列表，只干一件事——最快找到应用。
 * - 顶部：Header（"应用"标题 + 设置齿轮）
 * - 搜索（吸顶）：与首页同一个意图胶囊组件（10-21 新规格合二为一）——打字过滤网格
 *   （拼音/首字母/自然语言/模糊四档匹配，AppSearchIndex），提交/点「问七仔」回首页交协调器
 * - 下面直接连全部应用 A-Z 列表（字母分组头 + 行）
 * - Rail 只做定位：字母跳转对应字母
 * - v0.22：分类区取消（Bob 决定，等需要时再重新设计）；AllAppsScreen 死代码删除
 * - 手势签名：左滑=多 / 右滑=少
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCenterContent(
    onOpenSettings: () -> Unit,
    onPullDownToD2: () -> Unit,
    onHeaderSwipeRight: () -> Unit,
    onBottomSwipeUp: () -> Unit,
    // R2 UI（10-21 新规格）：「问七仔」——回首页并把整句提交意图协调器（HomeHost 提供）
    onAskQizai: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val hapticView = LocalView.current
    val onPullDownState = rememberUpdatedState(onPullDownToD2)
    val onHeaderSwipeRightState = rememberUpdatedState(onHeaderSwipeRight)
    val onBottomSwipeUpState = rememberUpdatedState(onBottomSwipeUp)
    // v0.68.95（F-04）：装卸失效改走全进程同源机制——自带的 PACKAGE 广播
    // 接收器删除，改由 InstalledApps.epoch（.88）驱动 AppCatalog 在 IO 线程
    // 重建全表与搜索索引，本屏组合期只读快照（见下方各列表的派生口径）。
    val installedEpoch by InstalledApps.epoch.collectAsState()
    val catalog by AppCatalog.snapshot.collectAsState()
    // v0.40.0：长按 → 真弹窗快捷菜单（与 Dock 长按同一套 AppPopupMenu，替代 bottom sheet）
    var popupApp by remember { mutableStateOf<AppInfo?>(null) }
    // v0.40.2：惯用手设置已删除；右侧一条可见 rail + 左侧隐形触发区，左右手都可操作
    // v0.41.9：已隐藏应用版本号：变化时 A-Z 自动重算过滤
    val hiddenVersion = HiddenApps.version.intValue
    LaunchedEffect(installedEpoch, hiddenVersion) { AppCatalog.ensure(context) }
    val hidden = remember(hiddenVersion) { HiddenApps.getHidden(context) }
    // v0.41.18（Bob）：隐藏应用不再用独立页面，放到主列表末尾的"隐藏"分组；
    // 解锁后可像正常应用一样直接打开，不用先恢复。
    // v0.41.17：隐藏区解锁状态——未解锁时分组显示锁定占位，点按才触发面部/指纹
    var hiddenUnlocked by remember { mutableStateOf(false) }
    // v0.68.95（F-04）：从仓库快照的原始枚举派生（纯内存过滤/排序）——此前在
    // 组合期同步 listLaunchableApps 整表查询，装卸一次全表在主线程重跑。
    val hiddenApps = remember(catalog, hidden) {
        if (hidden.isEmpty()) emptyList()
        else {
            val collator = Collator.getInstance(Locale.CHINA)
            (catalog?.apps ?: emptyList())
                .filter { it.packageName in hidden }
                .sortedWith { a, b -> collator.compare(a.label.toString(), b.label.toString()) }
        }
    }
    // v0.41.31（Bob）：Dock 分区——A 之前，像隐藏分区一样；
    // UiPrefs.getDockPinned 转 AppInfo（按置顶顺序，去隐藏）
    val dockTick by UiPrefs.dockTick.collectAsState()
    // v0.56.2（Bob）：Rail 图标闪烁
    val railFlashState by UiPrefs.railFlash.collectAsState()
    val (flashTick, flashTarget) = railFlashState
    val dockApps = remember(catalog, dockTick, hidden) {
        val pinned = UiPrefs.getDockPinned(context)
        if (pinned.isEmpty()) emptyList()
        else {
            val all = (catalog?.apps ?: emptyList()).associateBy { it.packageName }
            pinned.mapNotNull { all[it] }.filter { it.packageName !in hidden }
        }
    }
    // Dock 分区占位：行(N) + 间距(1)；为空时不显示
    // v0.41.32（Bob）：幽灵 ★ 与第一行同行，不再单独占一行
    val dockSectionSize = if (dockApps.isNotEmpty()) dockApps.size + 1 else 0
    // v0.41.34（Bob）：Dock 分区拖动排序——本地顺序状态，拖动时实时更新，松手保存
    val dockOrder = remember { mutableStateListOf<AppInfo>().apply { addAll(dockApps) } }
    var draggingDockPkg by remember { mutableStateOf<String?>(null) }
    var dockDragOffsetY by remember { mutableStateOf(0f) }
    LaunchedEffect(dockApps) {
        if (draggingDockPkg == null) {
            dockOrder.clear()
            dockOrder.addAll(dockApps)
        }
    }

    // v0.22：分类区取消（等需要时再重新设计），只保留 A-Z

    // A-Z 数据：全部可启动应用（去自己、去隐藏），中文按拼音首字母排序分组。
    // v0.68.95（F-04）：直接读仓库快照的可见集（IO 构建、口径与原组合期实现
    // 逐字一致：去自己/去隐藏 + Collator(CHINA) 排序）；装卸时快照在后台换代，
    // 结果到了才换表，组合期不再整表同步重建。
    val azApps = catalog?.visibleApps ?: emptyList()
    val azGroups: List<Pair<Char, List<AppInfo>>> = remember(azApps) {
        val map = linkedMapOf<Char, MutableList<AppInfo>>()
        // 拼音首字母分组：用字典（20924 字），GB2312 区位码做兜底
        val pinyinOf: (Char) -> Char = { c ->
            val fromDict = AppSearchIndex.pinyinInitialOf(context, c)
            if (fromDict != '#') fromDict else pinyinInitial(c)
        }
        azApps.forEach { app ->
            map.getOrPut(groupKey(app.label, pinyinOf)) { mutableListOf() }.add(app)
        }
        map.toList().sortedWith(compareBy({ if (it.first == '#') 1 else 0 }, { it.first }))
    }
    val letters = remember(azGroups) { azGroups.map { it.first } }

    // v0.21：应用中心搜索（PRD §三十三）——拼音/首字母/自然语言/模糊
    // 意图改进一批：状态迁到 TextFieldValue（与首页共用同一胶囊组件、同一 composing
    // 语义）；query 是其文本的派生读法，「问七仔」行与提交沿用原始文本即时响应。
    var queryField by remember { mutableStateOf(TextFieldValue("")) }
    val query = queryField.text
    // 过滤词经约 120ms 防抖落定，组合中（拼音未上屏）暂停更新——网格不逐键重算、
    // 不被拼音半成品串扰动（与首页本地搜索同一机制、同一函数 AppSearchIndex.search，
    // 频次破同分排序也同享）。
    var filterQuery by remember { mutableStateOf("") }
    LaunchedEffect(queryField) {
        if (queryField.composition != null) return@LaunchedEffect
        delay(120)
        filterQuery = queryField.text
    }
    val searching = filterQuery.trim().isNotBlank()
    // v0.25.5：Niagara 式——拖动直接滚完整列表，不做内容过滤，松手零位移
    // v0.68.95（F-04）：索引随仓库快照在 IO 构建（基于同一份可见集），组合期只读。
    val searchIndex = catalog?.index ?: emptyList()
    val searchResults = remember(filterQuery, searchIndex) {
        if (filterQuery.trim().isBlank()) null
        else AppSearchIndex.search(context, filterQuery, searchIndex)
    }
    // v0.25.3：最近使用横条（搜索下方），取高频应用
    // 开始搜索时滚到顶部
    val listState = rememberLazyListState()
    LaunchedEffect(searching) {
        if (searching) listState.scrollToItem(0)
    }

    val nested = rememberPullDownConnection(listState, onPullDownState.value)

    // 字母 → LazyColumn item index（每字母：N 行 + 1 组间呼吸间距）
    // v0.41.8（Bob）：字母不再独占一行（浪费纵向空间），回到每组首行行内幽灵字母；
    // 吸顶仍由悬停 overlay 实现
    val letterAnchors = remember(azGroups, dockSectionSize) {
        val m = mutableMapOf<Char, Int>()
        var idx = dockSectionSize // v0.41.31：Dock 分区在前，字母锚点后移
        azGroups.forEach { (letter, apps) ->
            m[letter] = idx
            idx += apps.size + 1
        }
        m
    }
    // v0.41.31：隐藏分区第一个 item 的绝对 index（供 rail 眼睛按钮跳转）
    // v0.41.32：隐藏头已并入第一行，index 不变
    val azTotalItems = remember(azGroups) { azGroups.sumOf { it.second.size + 1 } }
    val hiddenHeaderIndex = dockSectionSize + azTotalItems

    // v0.67.13（Bob）：主屏 picker 连续驱动——手指不松开时持续跟随滚动
    val homeTarget by UiPrefs.homeRailTarget.collectAsState()
    val homeLetter by UiPrefs.homeRailLetter.collectAsState()
    LaunchedEffect(homeTarget, homeLetter, letterAnchors) {
        when (homeTarget) {
            UiPrefs.HomeRailTarget.LETTER -> {
                val c = homeLetter
                val anchor = c?.let { letterAnchors[it] }
                    ?: letterAnchors.minByOrNull { kotlin.math.abs(it.key - (c ?: 'A')) }?.value
                if (anchor != null) listState.scrollToItem(anchor)
            }
            UiPrefs.HomeRailTarget.DOCK -> listState.scrollToItem(0)
            UiPrefs.HomeRailTarget.HIDDEN -> listState.scrollToItem(hiddenHeaderIndex)
            null -> {}
        }
    }

    // v0.41.8：手动吸顶——当前组首行滚出顶部后，悬停 overlay 接管显示；
    // 下一组首行接近顶部时把悬停字母往上顶走（Bob：滚动悬停/顶走）
    val stuckLetter: Char? by remember(azGroups) {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            val current = letterAnchors.entries
                .filter { it.value <= firstIdx }
                .maxByOrNull { it.value }
                ?.key ?: return@derivedStateOf null
            // 该组首行还在视口内时不悬停，直接用行内的真字母
            if (firstIdx > (letterAnchors[current] ?: 0)) current else null
        }
    }
    // 顶走位移：下一组首行进入顶部区域时，悬停字母被往上顶（负值）；
    // 读 firstVisibleItemScrollOffset 订阅逐像素滚动，保证顶走动画跟手。
    // 外层 Box 有 clipToBounds，顶出去的部分会被裁掉，不会跑到搜索栏上面（Bob）
    val stuckPushPx: Float by remember(azGroups, hiddenApps, hiddenUnlocked) {
        derivedStateOf {
            val cur = stuckLetter ?: return@derivedStateOf 0f
            @Suppress("UNUSED_EXPRESSION")
            listState.firstVisibleItemScrollOffset
            val order = letters
            val next = order.getOrNull(order.indexOf(cur) + 1)
            // v0.41.28（Bob）：隐藏分组头也要顶走悬停字母——最后一个字母后面是隐藏头，
            // 用 key 找隐藏头 item，否则幽灵眼睛会和 Z 重叠
            val off = if (next != null) {
                val nextAnchor = letterAnchors[next] ?: return@derivedStateOf 0f
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == nextAnchor }
                    ?.offset?.toFloat() ?: return@derivedStateOf 0f
            } else {
                if (hiddenApps.isEmpty()) return@derivedStateOf 0f
                // v0.41.32：隐藏头已并入第一行，找第一个隐藏 item
                val firstHiddenKey = if (hiddenUnlocked) "h:${hiddenApps.first().packageName}"
                    else "hidden-locked"
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == firstHiddenKey }
                    ?.offset?.toFloat() ?: return@derivedStateOf 0f
            }
            val pushZonePx = with(density) { 48.dp.toPx() }
            if (off < pushZonePx) off - pushZonePx else 0f
        }
    }
    // v0.41.30（Bob）：顶走位移动画化——字母切换时不跳变，推到位置停住不弹回
    val animatedPushPx by animateFloatAsState(
        targetValue = stuckPushPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "stuckPush"
    )
    // v0.41.28（Bob）：隐藏分组的幽灵眼也要悬停——隐藏头滚出顶部后，
    // 幽灵眼接管悬停，直到滚出隐藏分组
    val stuckHidden: Boolean by remember(hiddenApps, hiddenUnlocked) {
        derivedStateOf {
            if (hiddenApps.isEmpty()) return@derivedStateOf false
            val vis = listState.layoutInfo.visibleItemsInfo
            // v0.41.32：隐藏头已并入第一行
            val firstKey = if (hiddenUnlocked) "h:${hiddenApps.first().packageName}"
                else "hidden-locked"
            val first = vis.firstOrNull { it.key == firstKey }
            val firstGone = first == null || first.offset + first.size < 0
            val hiddenVisible = vis.any {
                it.key == "hidden-locked" ||
                (it.key is String && (it.key as String).startsWith("h:"))
            }
            firstGone && hiddenVisible
        }
    }
    // v0.41.31（Bob）：Dock 分区幽灵 ★ 悬停——第一行滚出顶部后接管，
    // 直到滚出 Dock 分区；与 stuckHidden 同理
    // v0.41.32：dock 头已并入第一行
    val stuckDock: Boolean by remember(dockApps) {
        derivedStateOf {
            if (dockApps.isEmpty()) return@derivedStateOf false
            val vis = listState.layoutInfo.visibleItemsInfo
            // v0.41.32：dock 头已并入第一行
            val firstKey = "d:${dockApps.first().packageName}"
            val first = vis.firstOrNull { it.key == firstKey }
            val firstGone = first == null || first.offset + first.size < 0
            val dockVisible = vis.any {
                it.key is String && (it.key as String).startsWith("d:")
            }
            firstGone && dockVisible
        }
    }
    // v0.41.31：Dock 悬停被 A 顶走（与字母顶走逻辑一致）
    val dockPushPx: Float by remember(dockApps, azGroups) {
        derivedStateOf {
            if (!stuckDock) return@derivedStateOf 0f
            @Suppress("UNUSED_EXPRESSION")
            listState.firstVisibleItemScrollOffset
            val firstLetter = letters.firstOrNull() ?: return@derivedStateOf 0f
            val anchor = letterAnchors[firstLetter] ?: return@derivedStateOf 0f
            val vis = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == anchor }
            val off = vis?.offset?.toFloat() ?: return@derivedStateOf 0f
            val pushZonePx = with(density) { 48.dp.toPx() }
            if (off < pushZonePx) off - pushZonePx else 0f
        }
    }
    val animatedDockPushPx by animateFloatAsState(
        targetValue = dockPushPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dockPush"
    )
    // v0.41.31（Bob）：打开应用中心默认从 A（首个字母组）开始，不显示 Dock 分区；
    // 除非用户点 rail ★ 才去看 Dock
    var dockInitialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(letterAnchors) {
        if (!dockInitialScrollDone) {
            dockInitialScrollDone = true
            val firstAnchor = letterAnchors[letters.firstOrNull()] ?: 0
            if (firstAnchor > 0) listState.scrollToItem(firstAnchor)
        }
    }

    // 意图改进一批：点结果打开应用时先收键盘（清焦点 + 收 IME），与首页点本地
    // 结果同规则；「问七仔」路径不走这里（回首页后由首页规则收），行为不变。
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // v0.68.93（布局与交互 Loop 一批①）：与首页同病同修——胶囊聚焦态与范围
    // 供根 Box 的点外部观察者用（clearIntentFocusOnOutsideTap）。
    var intentFocused by remember { mutableStateOf(false) }
    var intentBoundsInRoot by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    var rootPosInRoot by remember {
        mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    }

    // v0.25.2：松手滚动——等列表恢复全量重组完成后再滚，避免打在旧内容上
    fun launchApp(app: AppInfo) {
        focusManager.clearFocus()
        keyboardController?.hide()
        AppUsageTracker.recordLaunch(context, app.packageName)
        try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(app.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) context.startActivity(intent)
        } catch (_: Exception) {
            IslandToasts.show("「${app.label}」暂不可用")
        }
    }

    // 右滑隐藏（手势签名：右滑=少），HiddenApps.version +1 后列表自动重算
    fun hideApp(app: AppInfo) {
        HiddenApps.hide(context, app.packageName)
        UiPrefs.flashRail("eye")
        IslandToasts.show("已隐藏「${app.label}」")
    }
    // v0.41.18：从隐藏分组恢复显示
    fun unhideApp(app: AppInfo) {
        HiddenApps.unhide(context, app.packageName)
        IslandToasts.show("「${app.label}」已恢复显示")
    }
    // v0.41.18：解锁隐藏分组——触发面部/指纹，通过后展开
    fun unlockHiddenApps() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            IslandToasts.show("无法启动验证")
            return
        }
        if (!BiometricAuth.canAuthenticate(context)) {
            IslandToasts.show("请先在系统设置中录入指纹或面容")
            return
        }
        BiometricAuth.authenticate(
            activity = activity,
            onSuccess = { hiddenUnlocked = true },
            onFail = {
                IslandToasts.show("验证未通过")
            }
        )
    }

    // v0.16.1：A-Z 行左滑操作同时只展开一个——列表级单态，新展开自动收起上一个
    var expandedActionsPkg by remember { mutableStateOf<String?>(null) }
    // v0.41.23（Bob v4）：右滑眼睛保持状态——与 expandedActionsPkg 互斥，
    // 同一时间只有一个应用生效
    var revealedHidePkg by remember { mutableStateOf<String?>(null) }

    // v0.68.45（Bob）：列表一滚动就收起滑动行——白卡跟着滚看上去像 bug
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            expandedActionsPkg = null
            revealedHidePkg = null
        }
    }

    // v0.35.0：首次左滑新手引导——只展示一次，之后不再打扰
    var showActionsGuide by remember { mutableStateOf(false) }
    LaunchedEffect(expandedActionsPkg) {
        if (expandedActionsPkg != null && !UiPrefs.hasSeenRowActionsGuide(context)) {
            showActionsGuide = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // v0.68.45（走查）：实底纸面——之前透明，主屏大时间透过来盖住列表
            .background(AILauncherColors.current.Background)
            // v0.68.93：点胶囊外释放焦点（观察不消费，列表滚动/点按照旧）
            .onGloballyPositioned { rootPosInRoot = it.positionInRoot() }
            .clearIntentFocusOnOutsideTap(
                focused = { intentFocused },
                targetBoundsInHost = {
                    intentBoundsInRoot?.let { b ->
                        androidx.compose.ui.geometry.Rect(
                            b.left - rootPosInRoot.x,
                            b.top - rootPosInRoot.y,
                            b.right - rootPosInRoot.x,
                            b.bottom - rootPosInRoot.y
                        )
                    }
                },
                focusManager = focusManager,
                keyboardController = keyboardController
            )
    ) {
    // v0.68.70（Bob）：横屏判断改用量实际尺寸（LocalConfiguration 也不可靠）
    var isLandscapeAc by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size -> isLandscapeAc = size.width > size.height }
            // v0.68.81（Bob）：statusBarsPadding 只在竖屏加——v0.68.75 全去掉后竖屏状态栏错乱
            // v0.68.83（Bob 报）：横屏顶部状态栏与内容重叠——横屏补回顶部 inset（只 Top，
            // 不带侧边，避免 v0.68.75 的"横屏侧边留白"复发）
            // 批B ⑧：竖屏分支改走统一 helper statusBarsTopPadding（与 statusBarsPadding
            // 取值等价）；横屏分支属横屏线领地，原样不动
            .then(
                if (isLandscapeAc) Modifier.windowInsetsPadding(
                    WindowInsets.statusBars.only(WindowInsetsSides.Top)
                ) else Modifier.statusBarsTopPadding()
            )
    ) {
        // ---- Header（v0.68.44 杂志刊头，Bob 选 A）：INDEX kicker + 宋体"全部应用" + 数量 ----
        // 顶栏下滑 → D2；顶栏右滑 → 回记住的 Dock 档（手势签名：右滑=更少）
        val headThreshPx = with(density) { 80.dp.toPx() }
        Column(
            modifier = Modifier.pointerInput(Unit) {
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
                            onPullDownState.value()
                        }
                    }
                )
            }
        ) {
            // v0.68.66（Bob）：横屏时顶栏+搜索全由 AppCenterLandscape 提供，这里只留竖屏
            if (!isLandscapeAc) {
            // v0.68.44（Bob）：杂志刊头
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 12.dp, top = 8.dp)
            ) {
                MastheadKicker(text = "INDEX · 应用索引")
                Text(
                    text = "全部应用",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = 2.sp,
                    color = AILauncherColors.current.Title
                )
                Text(
                    text = "${azApps.size} 款 · 右侧滑字母直达",
                    fontSize = 12.sp,
                    color = AILauncherColors.current.Hint,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            }  // else 竖屏刊头结束
            // v0.68.72：搜索框横屏隐藏（横屏用 AppCenterLandscape 的搜索）
            if (!isLandscapeAc) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp)
                    .pointerInput(Unit) {
                        // 顶栏右滑 → 回到记住的 Dock 行数（手势签名：右滑=更少）
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
                // R2 UI（2026-10-21 新规格）：原搜索框删除，换成与首页同一个意图胶囊组件——
                // 一个入口两层响应：打字走本地层过滤网格（状态层 query/searchResults 不变），
                // 提交（按钮/IME Done）= 问七仔（回首页交协调器）。横屏仍用 AppCenterLandscape 自己的搜索。
                IntentCapsuleField(
                    value = queryField,
                    onValueChange = { queryField = it },
                    onSubmit = {
                        val q = query.trim()
                        if (q.isNotEmpty()) onAskQizai(q)
                    },
                    // 应用中心变体专属（10-21 Bob 批准）：✕ 一键清空，清空后网格恢复全量；
                    // 首页胶囊不开启，保持原设计。
                    showClearButton = true,
                    // v0.68.93：焦点外报 + 范围捕获（根 Box 点外部观察者用）
                    onFocusChanged = { intentFocused = it },
                    modifier = Modifier.weight(1f)
                        .onGloballyPositioned { intentBoundsInRoot = it.boundsInRoot() }
                )
                Spacer(modifier = Modifier.width(4.dp))
                // v0.41.9（Bob 选 B）：设置齿轮缩小弱化，仍在右上
                IconButton(
                    onClick = onOpenSettings,
                    // 体检批D ⑥：冒烟语义定位锚点（应用中心设置齿轮）
                    modifier = Modifier.testTag("appcenter_settings")
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = AILauncherColors.current.Hint.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // R2 UI（10-21 新规格）：「问七仔」行——有输入时出现，整句交给七仔理解（回首页提交协调器）
            // 意图改进一批：本行跟随原始文本即时出现（不等防抖落定），文案与提交都用原句
            if (query.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AILauncherColors.current.Card.copy(alpha = 0.88f))
                        .clickable { onAskQizai(query.trim()) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        // 体检批D ⑨：装饰图标（行文案「问七仔：「…」」已表达含义）标装饰语义
                        contentDescription = null,
                        tint = AILauncherColors.current.Accent,
                        modifier = Modifier
                            .size(16.dp)
                            .semantics { invisibleToUser() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "问七仔：「${query.trim()}」",
                        fontSize = 13.sp,
                        color = AILauncherColors.current.Accent,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        // 体检批D ⑨：装饰图标（同问七仔行）标装饰语义
                        contentDescription = null,
                        tint = AILauncherColors.current.Accent,
                        modifier = Modifier
                            .size(16.dp)
                            .semantics { invisibleToUser() }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            }  // if (!isLandscapeAc) 搜索区结束（v0.68.72 新增，R2 改同组件）
        }  // Header Column 闭合

        // v0.68.62（Bob）：横屏双栏——左索引右网格，竖屏用原单列
        if (isLandscapeAc) {
            AppCenterLandscape(
                azGroups = azGroups,
                letters = letters,
                totalCount = azApps.size,
                favApps = dockApps,
                hiddenApps = hiddenApps,
                onLaunch = ::launchApp,
                onLongPress = { popupApp = it },
                onOpenSettings = onOpenSettings,
                // v0.68.82（横屏专项第 1 项）：三套手势对齐竖屏
                onPullDownToD2 = { onPullDownState.value() },
                onHeaderSwipeRight = { onHeaderSwipeRightState.value() },
                onBottomSwipeUp = { onBottomSwipeUpState.value() },
                // v0.68.82（横屏专项第 2 项）：真搜索框（状态层复用竖屏）
                // 意图改进一批：横屏仍是 String 契约——向下传文本、向上把整串包回
                // TextFieldValue（光标置尾）；横屏自己的输入框 UI 不动（归横屏专项）
                query = query,
                onQueryChange = { queryField = TextFieldValue(it, TextRange(it.length)) },
                searchResults = searchResults,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
        // ---- 单列表：分类区在上，A-Z 列表直接在下 ----
        // v0.41.8：clipToBounds——悬停字母被顶走时不画出列表区域，不会跑到搜索栏上面（Bob）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nested),
                // v0.41.7：列表不再统一 padding（之前字母头用负 offset 伸进左留白，
                // 在某些行渲染异常）；改为每项各自加正向 padding——应用行保持
                // 72dp 左缩进 + 64dp 右缩进（左隐形触发区/右 rail 避让），字母头占满宽
                verticalArrangement = Arrangement.spacedBy(2.dp),
                // v0.40.0：底部留出手势条高度，避免末行被手势条盖住
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                val results = searchResults
                if (results != null) {
                    // 搜索态：只显示搜索结果（拼音/首字母/自然语言/模糊）
                    if (results.isEmpty()) {
                        item(key = "search-empty") {
                            Text(
                                text = "没有找到「${filterQuery.trim()}」相关的应用",
                                fontSize = 14.sp,
                                color = AILauncherColors.current.Hint,
                                modifier = Modifier.padding(
                                    top = 32.dp, start = 72.dp, end = 64.dp
                                )
                            )
                        }
                    } else {
                        items(results, key = { "s:${it.packageName}" }) { app ->
                            // v0.68.53（Bob）：生长式——长按行直接在下方展开快捷卡，
                            // 同一张圆角卡（20.dp），不重复图标标题，像从行里长出来
                            val isPopupExpanded = popupApp?.packageName == app.packageName
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 72.dp, end = 64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isPopupExpanded) AILauncherColors.current.Card
                                        else Color.Transparent
                                    )
                                    .animateContentSize(
                                        animationSpec = tween(
                                            durationMillis = com.bobot.ailauncher.ui.theme.MotionTokens.Medium,
                                            easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                                        )
                                    )
                            ) {
                                AppRow(
                                    app = app,
                                    onLaunch = { launchApp(app) },
                                    onLongClick = {
                                        hapticView.confirmHaptic()
                                        popupApp = app
                                    },
                                    onHide = ::hideApp,
                                    actionsVisible = expandedActionsPkg == app.packageName,
                                    onActionsVisibleChange = { expanded ->
                                        // v0.41.23：与 revealedHidePkg 互斥
                                        if (expanded) {
                                            expandedActionsPkg = app.packageName
                                            revealedHidePkg = null
                                        } else if (expandedActionsPkg == app.packageName) {
                                            expandedActionsPkg = null
                                        }
                                    },
                                    hideVisible = revealedHidePkg == app.packageName,
                                    onHideVisibleChange = { visible ->
                                        if (visible) {
                                            revealedHidePkg = app.packageName
                                            expandedActionsPkg = null
                                        } else if (revealedHidePkg == app.packageName) {
                                            revealedHidePkg = null
                                        }
                                    }
                                )
                                AnimatedVisibility(
                                    visible = isPopupExpanded,
                                    enter = expandVertically(
                                        animationSpec = tween(
                                            durationMillis = com.bobot.ailauncher.ui.theme.MotionTokens.Medium,
                                            easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                                        )
                                    ) + fadeIn(),
                                    exit = shrinkVertically() +
                                        fadeOut()
                                ) {
                                    AppShortcutInline(
                                        app = app,
                                        onDismiss = { popupApp = null }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                } else {
                // v0.25.7：去掉"全部应用"标题（Bob）
                // v0.41.31（Bob）：Dock 分区——A 之前，像隐藏分区一样；
                // 为空时不显示；幽灵 ★ 头（与隐藏头眼图标样式一致）
                if (dockApps.isNotEmpty()) {
                    // v0.41.32（Bob）：幽灵 ★ 与第一行同行（像字母那样），不单独占行
                    itemsIndexed(dockOrder, key = { _, app -> "d:${app.packageName}" }) { index, app ->
                        // v0.56.1（Bob）：手柄若因重组被重建、正在拖，保存顺序（防手势取消丢保存）
                        DisposableEffect(app.packageName) {
                            onDispose {
                                if (draggingDockPkg == app.packageName) {
                                    UiPrefs.setDockPinned(context, dockOrder.map { it.packageName })
                                    draggingDockPkg = null
                                    dockDragOffsetY = 0f
                                }
                            }
                        }
                        // v0.41.34（Bob）：拖动中的行跟随手指，其他行自动让位
                        val isDockDragging = draggingDockPkg == app.packageName
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDockDragging) 1f else 0f)
                                .offset { IntOffset(0, if (isDockDragging) dockDragOffsetY.roundToInt() else 0) }
                        ) {
                            // v0.55.3（Bob）：幽灵常驻（alpha=0 占位），避免 index 变化时子项增减
                            // 导致拖动手柄的 pointerInput 重建、手势取消、顺序没保存
                            Icon(
                                imageVector = Icons.Filled.Star,
                                // 体检批D ⑨：幽灵占位（alpha 0/0.14 纯视觉）标装饰语义
                                contentDescription = null,
                                tint = AILauncherColors.current.Title.copy(alpha = if (index == 0) 0.14f else 0f),
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 20.dp)
                                    .size(36.dp)
                                    .semantics { invisibleToUser() }
                            )
                            // v0.68.53（Bob）：生长式
                            val isPopupExpandedX = popupApp?.packageName == app.packageName
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 72.dp, end = 64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isPopupExpandedX) AILauncherColors.current.Card
                                        else Color.Transparent
                                    )
                            ) {
                            AppRow(
                                app = app,
                                onLaunch = { launchApp(app) },
                                onLongClick = {
                                    hapticView.confirmHaptic()
                                    popupApp = app
                                },
                                onHide = ::hideApp,
                                actionsVisible = expandedActionsPkg == app.packageName,
                                onActionsVisibleChange = { expanded ->
                                    if (expanded) {
                                        expandedActionsPkg = app.packageName
                                        revealedHidePkg = null
                                    } else if (expandedActionsPkg == app.packageName) {
                                        expandedActionsPkg = null
                                    }
                                },
                                hideVisible = revealedHidePkg == app.packageName,
                                onHideVisibleChange = { visible ->
                                    if (visible) {
                                        revealedHidePkg = app.packageName
                                        expandedActionsPkg = null
                                    } else if (revealedHidePkg == app.packageName) {
                                        revealedHidePkg = null
                                    }
                                }
                            )
                            AnimatedVisibility(
                                visible = isPopupExpandedX,
                                enter = expandVertically() +
                                    fadeIn(),
                                exit = shrinkVertically() +
                                    fadeOut()
                            ) {
                                AppShortcutInline(
                                    app = app,
                                    onDismiss = { popupApp = null }
                                )
                            }
                            }
                            // v0.41.34（Bob）：拖动手柄——简洁三横线，Hint 色 18dp，无背景；
                            // 按住上下拖动排序，松手保存（不再去设置里调）
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = "拖动排序",
                                tint = AILauncherColors.current.Hint,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 86.dp)
                                    .size(18.dp)
                                    .pointerInput(app.packageName) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingDockPkg = app.packageName
                                                dockDragOffsetY = 0f
                                                hapticView.tickHaptic()
                                            },
                                            onDragEnd = {
                                                UiPrefs.setDockPinned(
                                                    context,
                                                    dockOrder.map { it.packageName }
                                                )
                                                draggingDockPkg = null
                                                dockDragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                // v0.44.3：取消也保存当前顺序，不重置（拖到首位时手势易被取消）
                                                UiPrefs.setDockPinned(
                                                    context,
                                                    dockOrder.map { it.packageName }
                                                )
                                                draggingDockPkg = null
                                                dockDragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dockDragOffsetY += dragAmount.y
                                                // v0.55.2：行高 64dp + 2dp 间距 = 66dp
                                                val rowH = with(density) { 66.dp.toPx() }
                                                val curIdx = dockOrder.indexOfFirst {
                                                    it.packageName == app.packageName
                                                }
                                                if (curIdx >= 0) {
                                                    val targetIdx = (
                                                        curIdx + (dockDragOffsetY / rowH).toInt()
                                                    ).coerceIn(0, dockOrder.size - 1)
                                                    if (targetIdx != curIdx) {
                                                        val item = dockOrder.removeAt(curIdx)
                                                        dockOrder.add(targetIdx, item)
                                                        dockDragOffsetY -= (targetIdx - curIdx) * rowH
                                                    }
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                    }
                    item(key = "sp:dock") { Spacer(modifier = Modifier.height(24.dp)) }
                }
                // A-Z 列表（v0.41.0 B 方案）
                // v0.41.8：每组 = 应用行 + 组间间距（字母不再独占一行，Bob）。
                // 每组首行左侧留白放幽灵字母（正向 padding，不用负 offset）；
                // 首行滚出顶部后由悬停 overlay 接管（手动吸顶，见 stuckLetter）
                azGroups.forEach { (letter, apps) ->
                    itemsIndexed(apps, key = { _, app -> "a:${app.packageName}" }) { index, app ->
                        // v0.41.7：行级 padding（列表不再统一 padding）
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // v0.68.61：横屏隐藏幽灵字母（省竖向空间，不乱）
                            if (index == 0 && !isLandscapeAc) {
                                Text(
                                    text = letter.toString(),
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 56.sp,
                                    // v0.68.44（Bob）：杂志章节感——放大加深
                                    color = AILauncherColors.current.Title.copy(alpha = 0.20f),
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 20.dp)
                                )
                            }
                            // v0.68.53（Bob）：生长式
                            val isPopupExpandedX = popupApp?.packageName == app.packageName
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 72.dp, end = 64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isPopupExpandedX) AILauncherColors.current.Card
                                        else Color.Transparent
                                    )
                            ) {
                            AppRow(
                                app = app,
                                onLaunch = { launchApp(app) },
                                onLongClick = {
                                    hapticView.confirmHaptic()
                                    popupApp = app
                                },
                                onHide = ::hideApp,
                                actionsVisible = expandedActionsPkg == app.packageName,
                                onActionsVisibleChange = { expanded ->
                                    expandedActionsPkg =
                                        if (expanded) app.packageName
                                        else if (expandedActionsPkg == app.packageName) null
                                        else expandedActionsPkg
                                }
                            )
                            AnimatedVisibility(
                                visible = isPopupExpandedX,
                                enter = expandVertically() +
                                    fadeIn(),
                                exit = shrinkVertically() +
                                    fadeOut()
                            ) {
                                AppShortcutInline(
                                    app = app,
                                    onDismiss = { popupApp = null }
                                )
                            }
                        }
                        }
                    }
                    item(key = "sp:$letter") { Spacer(modifier = Modifier.height(24.dp)) }
                }
                // v0.41.18（Bob）：隐藏分组——主列表末尾，像 ABCD 分组一样；
                // v0.41.20（Bob）：不显示"已隐藏"标题文字，只留幽灵图标。
                // 未解锁显示锁定占位，解锁后应用可直接打开（不用先恢复）。
                if (hiddenApps.isNotEmpty()) {
                    // v0.41.32（Bob）：幽灵眼与第一行同行，不单独占行
                    if (!hiddenUnlocked) {
                        // 锁定占位：点按触发面部/指纹
                        // v0.41.21（Bob）：锁定占位按应用行样式——40dp 圆角锁图标 + 15sp 文字，
                        // 与上方应用行视觉统一；点按整行触发面部/指纹解锁。
                        item(key = "hidden-locked") {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    // 体检批D ⑨：幽灵占位（alpha 0.14 纯视觉）标装饰语义
                                    contentDescription = null,
                                    tint = AILauncherColors.current.Title.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 20.dp)
                                        .size(36.dp)
                                        .semantics { invisibleToUser() }
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 72.dp, end = 64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { unlockHiddenApps() }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
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
                                        // 体检批D ⑨：装饰图标（同行文字「点击解锁查看」已表达）标装饰语义
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
                                    fontSize = 16.sp,
                                    color = AILauncherColors.current.Title
                                )
                                }
                            }
                        }
                    } else {
                        // 解锁后：隐藏应用像正常应用一样直接打开
                        // v0.41.32：幽灵眼与第一行同行
                        itemsIndexed(hiddenApps, key = { _, app -> "h:${app.packageName}" }) { index, app ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    imageVector = Icons.Filled.VisibilityOff,
                                    // 体检批D ⑨：幽灵占位（alpha 0/0.14 纯视觉）标装饰语义
                                    contentDescription = null,
                                    tint = AILauncherColors.current.Title.copy(alpha = if (index == 0) 0.14f else 0f),
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 20.dp)
                                        .size(36.dp)
                                        .semantics { invisibleToUser() }
                                )
                                // v0.68.53（Bob）：生长式
                                val isPopupExpandedX = popupApp?.packageName == app.packageName
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 72.dp, end = 64.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isPopupExpandedX) AILauncherColors.current.Card
                                            else Color.Transparent
                                        )
                                ) {
                                AppRow(
                                    app = app,
                                    onLaunch = { launchApp(app) },
                                    onLongClick = {
                                        hapticView.confirmHaptic()
                                        popupApp = app
                                    },
                                    onHide = {}, // 隐藏分组内：右滑=取消隐藏
                                    onUnhide = ::unhideApp,
                                    actionsVisible = expandedActionsPkg == app.packageName,
                                    onActionsVisibleChange = { expanded ->
                                        // v0.41.23：与 revealedHidePkg 互斥
                                        if (expanded) {
                                            expandedActionsPkg = app.packageName
                                            revealedHidePkg = null
                                        } else if (expandedActionsPkg == app.packageName) {
                                            expandedActionsPkg = null
                                        }
                                    },
                                    hideVisible = revealedHidePkg == app.packageName,
                                    onHideVisibleChange = { visible ->
                                        if (visible) {
                                            revealedHidePkg = app.packageName
                                            expandedActionsPkg = null
                                        } else if (revealedHidePkg == app.packageName) {
                                            revealedHidePkg = null
                                        }
                                    }
                                )
                                AnimatedVisibility(
                                    visible = isPopupExpandedX,
                                    enter = expandVertically() +
                                        fadeIn(),
                                    exit = shrinkVertically() +
                                        fadeOut()
                                ) {
                                    AppShortcutInline(
                                        app = app,
                                        onDismiss = { popupApp = null }
                                    )
                                }
                                }
                            }
                        }
                    }
                    item(key = "sp:hidden") { Spacer(modifier = Modifier.height(24.dp)) }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                } // else 非搜索态：分类区 + A-Z 列表
            }
            // v0.41.8：悬停幽灵字母——只在当前组首行滚出顶部后显示，下一组首行
            // 接近时被顶走；外层 clipToBounds 保证不会顶到搜索栏上面（Bob）。
            // 字母弱化到 14%（Bob：太显眼），与行内字母一致。
            val sl = stuckLetter
            if (!searching) {
                if (stuckDock) {
                    // v0.41.31：Dock 分区悬停——幽灵 ★（与第一行幽灵一致）
                    Icon(
                        imageVector = Icons.Filled.Star,
                        // 体检批D ⑨：悬停幽灵（纯视觉）标装饰语义
                        contentDescription = null,
                        tint = AILauncherColors.current.Title.copy(alpha = 0.14f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 20.dp, top = 12.dp)
                            .size(36.dp)
                            .semantics { invisibleToUser() }
                            .offset { IntOffset(0, animatedDockPushPx.roundToInt()) }
                    )
                } else if (stuckHidden) {
                    // 隐藏分组悬停：幽灵眼（与隐藏头里的图标一致）
                    Icon(
                        imageVector = if (hiddenUnlocked) Icons.Filled.VisibilityOff
                            else Icons.Filled.VisibilityOff,
                        // 体检批D ⑨：悬停幽灵（纯视觉）标装饰语义
                        contentDescription = null,
                        tint = AILauncherColors.current.Title.copy(alpha = 0.14f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 20.dp, top = 12.dp)
                            .size(36.dp)
                            .semantics { invisibleToUser() }
                    )
                } else if (sl != null && !isLandscapeAc) {
                    Text(
                        text = sl.toString(),
                        fontFamily = FontFamily.Serif,
                        fontSize = 56.sp,
                        color = AILauncherColors.current.Title.copy(alpha = 0.20f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 20.dp, top = 12.dp)
                            .offset { IntOffset(0, animatedPushPx.roundToInt()) }
                    )
                }
            }
            // v0.40.0：底部上滑 → 回桌面（Bob）。
            // 系统手势导航会吃掉最底部边缘的触摸，这里在系统手势区之上放一条
            // 透明手势条：从底部向上滑过阈值就关闭应用中心，回到记住的 Dock 档。
            // 放在 Rail 之前绘制，Rail 保持可交互。
            val bottomStripPx = with(density) { 56.dp.toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(88.dp)
                    .pointerInput(Unit) {
                        var accumY = 0f
                        var fired = false
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
            // Rail：波浪字母导航（v0.24，学 Niagara/参考视频）——只做定位
            // v0.15.1：rail 整体再往外侧靠（字母列距屏幕边缘约 10dp）
            // v0.21：搜索态隐藏 rail（定位无意义）
            // v0.24：点按跳字母（居中）；拖动时波浪跟手，列表跟手滚动切换字母
            // v0.40.2：右侧一条可见 rail + 左侧隐形触发区（Bob）——
            // 左边不摆 rail（摆两个很奇怪），但左侧滑动同样触发字母切换，
            // 触发后是右侧 rail 亮起波浪跟随；应用列表左侧缩进给手指留空间。
            // v0.67.11（Bob）：主屏 picker 会话中隐藏自己的 rail，避免与 picker 重叠
            if (!searching && letters.isNotEmpty() && homeTarget == null) {
                // 左侧隐形区触发时，右侧可见 rail 跟随显示波浪
                var mirrorIndex by remember { mutableStateOf<Int?>(null) }
                // Niagara 做法：直接滚完整列表，不做内容过滤；松手时列表本来就在位置上，零位移
                // v0.68.93（布局与交互 Loop 一批②）：落位改「最新目标合并」——旧实现每过一个
                // 字母就 cancel 旧协程再新起一个：近字母 animateScrollToItem（动画扫过中间
                // 内容）、远字母 scrollToItem（瞬跳），拖动中两者交替取消/重启，列表一帧
                // 一变，肉眼就是刷新感/乱（Bob 真机点名）。主流桌面（iOS 通讯录/小米索引）
                // 的拖动态只认最新字母：这里只记目标，单个 effect 等一帧把同帧内的多次
                // 改向合并成一次瞬时落位，列表位置与手指字母严格对应，不再有动画扫掠
                // 与重启风暴；点按字母同走此路（瞬时落位，与通讯录一致）。
                var glideTarget by remember { mutableStateOf<Char?>(null) }
                var latestGlideLetter by remember { mutableStateOf<Char?>(null) }
                LaunchedEffect(glideTarget) {
                    val letter = glideTarget ?: return@LaunchedEffect
                    withFrameNanos { }
                    val anchor = letterAnchors[letter] ?: return@LaunchedEffect
                    if (listState.firstVisibleItemIndex != anchor ||
                        listState.firstVisibleItemScrollOffset != 0
                    ) {
                        listState.scrollToItem(anchor)
                    }
                }
                val glideToLetter: (Char) -> Unit = { letter ->
                    latestGlideLetter = letter
                    glideTarget = letter
                }
                // v0.68.6（Bob）：房子停留确认——滑到房子后停留450ms才回首页，
                // v0.68.20（Bob）：手势驱动——拉拽进度替代停留时长
                var housePull by remember { mutableStateOf(0f) }
                var eyePull by remember { mutableStateOf(0f) }
                var housePullCommitted by remember { mutableStateOf(false) }
                var eyePullCommitted by remember { mutableStateOf(false) }
                // v0.68.21：触发后需松手才重新 armed，防手指不松连触两次
                var housePullArmed by remember { mutableStateOf(true) }
                var eyePullArmed by remember { mutableStateOf(true) }
                var lastHousePull by remember { mutableStateOf(0f) }
                var lastEyePull by remember { mutableStateOf(0f) }
                val stopGlide: () -> Unit = {
                    // v0.68.93：松手收尾——最后一次改向的目标可能还差一帧没落位
                    // （effect 等帧期间松手），按最后记录的字母补一次瞬时落位，
                    // 已在位则无操作；下面的 pull 复位沿旧口径。
                    latestGlideLetter?.let { letter ->
                        val anchor = letterAnchors[letter]
                        if (anchor != null && listState.firstVisibleItemIndex != anchor) {
                            scope.launch { listState.scrollToItem(anchor) }
                        }
                    }
                    if (!housePullCommitted) {
                        housePull = 0f
                        lastHousePull = 0f
                    }
                    if (!eyePullCommitted) {
                        eyePull = 0f
                        lastEyePull = 0f
                    }
                    housePullArmed = true
                    eyePullArmed = true
                }
                // v0.41.31（Bob）：Rail 顶部 ★ → Dock 分区，底部眼 → 隐藏分区
                // v0.67.12（Bob）：改用共享 AppRail，单一样式单布局
                // v0.68.6（Bob）：房子停留确认——滑到房子后停留450ms才回首页，
                // 给操作反应时间，避免滑过时误触太突然；手指移开或松手即取消
                // v0.67.13（Bob）：房子/收藏/隐藏做进 rail 滑动项，一条导航滑到哪去哪
                val railItems = remember(dockApps, letters, hiddenApps) {
                    buildList {
                        add(RailItem.House)
                        // v0.68.33（Bob）：房子和星星之间也加分隔线，间距和其他一致
                        if (dockApps.isNotEmpty()) {
                            add(RailItem.Separator)
                            add(RailItem.Star)
                        }
                        // v0.68.13（Bob）：图标和字母之间加分隔线，视觉分隔+拉开间距
                        add(RailItem.Separator)
                        letters.forEach { add(RailItem.Letter(it)) }
                        if (hiddenApps.isNotEmpty()) {
                            add(RailItem.Separator)
                            add(RailItem.Eye)
                        }
                    }
                }
                // 收藏/隐藏时的脉冲目标
                val pulseItem = when (flashTarget) {
                    "star" -> RailItem.Star
                    "eye" -> RailItem.Eye
                    else -> null
                }
                val onRailItem: (RailItem) -> Unit = { item ->
                    when (item) {
                        // v0.68.20：房子不再用停留触发，改上拉手势（onHousePull）
                        is RailItem.House -> {}
                        is RailItem.Star -> scope.launch { listState.animateScrollToItem(0) }
                        is RailItem.Eye -> scope.launch { listState.animateScrollToItem(hiddenHeaderIndex) }
                        is RailItem.Letter -> glideToLetter(item.char)
                        is RailItem.Separator -> {}
                    }
                }
                // v0.68.20（Bob）：房子上拉——手指驱动 0→1，到阈值直接回首页；滑回则反向
                // v0.68.25（Bob）：消散段先去掉，到阈值直接回主页
                val onHousePull: (Float) -> Unit = { p ->
                    if (!housePullCommitted && housePullArmed) {
                        val cp = p.coerceIn(0f, 1f)
                        housePull = cp
                        if (cp >= 0.5f && lastHousePull < 0.5f) {
                            hapticView.tickHaptic()
                        }
                        if (cp >= 1f) {
                            housePullCommitted = true
                            housePullArmed = false
                            hapticView.confirmHaptic()
                            housePull = 0f
                            lastHousePull = 0f
                            housePullCommitted = false
                            onHeaderSwipeRightState.value()
                        }
                        lastHousePull = cp
                    }
                }
                // v0.68.20（Bob）：眼睛下拉——到阈值直接弹生物识别，带动画+震动
                val onEyePull: (Float) -> Unit = { p ->
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
                            unlockHiddenApps()
                            eyePull = 0f
                            lastEyePull = 0f
                            eyePullCommitted = false
                        }
                        lastEyePull = cp
                    }
                }
                AppRail(
                    items = railItems,
                    onActiveItem = onRailItem,
                    onRelease = stopGlide,
                    forcedActiveIndex = mirrorIndex,
                    pulseTick = flashTick,
                    pulseItem = pulseItem,
                    housePull = housePull,
                    eyePull = eyePull,
                    onHousePull = onHousePull,
                    onEyePull = onEyePull,

                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 70.dp)
                )
                WaveRail(
                    items = railItems,
                    side = RailSide.LEFT,
                    visible = false,
                    railWidth = 56.dp,
                    onActiveItem = { item ->
                        mirrorIndex = railItems.indexOf(item)
                        onRailItem(item)
                    },
                    onRelease = { mirrorIndex = null },
                    modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight()
                )
                // v0.41.20（Bob）：右下角眼睛按钮已删除——隐藏分组在主列表末尾，
                // 直接滚动到底就能看到（未解锁显示锁定占位）。
            }
        }
    }



    // v0.40.0：长按 bottom sheet 已删除，改用每行自带的真弹窗菜单（AppPopupMenu）

    // v0.35.0：首次左滑新手引导——点任意处关闭，只出现一次
    if (showActionsGuide) {
        RowActionsGuideOverlay(
            onDismiss = {
                UiPrefs.setRowActionsGuideSeen(context)
                showActionsGuide = false
            }
        )
    }
    } // Box
    } // else 竖屏单列
}

/**
 * v0.35.0：行左滑操作区新手引导——一次性半透明遮罩 + 白卡片，
 * 给三个按钮各配一句短说明。点任意处关闭，看过一次后不再打扰。
 * 配色跟 App 主题走（浅暖灰系）。
 */
@Composable
private fun RowActionsGuideOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // v0.57.2（Bob）：更新为新手势说明
                Text(
                    text = "滑动快捷操作",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.current.Title
                )
                GuideRow(
                    icon = Icons.Filled.Star,
                    tint = Color(0xFFC9A227),
                    title = "左滑：收藏",
                    desc = "把应用加到 Dock 收藏区，再滑一次取消"
                )
                GuideRow(
                    icon = Icons.Filled.VisibilityOff,
                    tint = Color(0xFF8A8478),
                    title = "右滑：隐藏",
                    desc = "把应用藏到隐藏区，再滑一次恢复"
                )
                Text(
                    text = "点任意处关闭",
                    fontSize = 12.sp,
                    color = AILauncherColors.current.Hint,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AILauncherColors.current.Title
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = AILauncherColors.current.Hint
            )
        }
    }
}

/** 列表到顶继续下滑 → D2 的嵌套滚动连接 */
@Composable
private fun rememberPullDownConnection(
    listState: LazyListState,
    onPullDown: () -> Unit
): NestedScrollConnection {
    val density = LocalDensity.current
    val onPullDownState = rememberUpdatedState(onPullDown)
    val threshPx = with(density) { 90.dp.toPx() }
    return remember(listState) {
        var accum = 0f
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && atTop) {
                    accum += available.y
                    if (accum > threshPx) {
                        accum = 0f
                        onPullDownState.value()
                    }
                    return Offset(0f, available.y)
                }
                accum = 0f
                return Offset.Zero
            }
        }
    }
}




/** v0.68.44：刊头字距 kicker（金色）；v0.68.45：给设置页共用 */
@Composable
internal fun MastheadKicker(text: String) {
    Row(modifier = Modifier.padding(bottom = 6.dp)) {
        text.forEach { ch ->
            androidx.compose.material3.Text(
                text = ch.toString(),
                fontSize = 12.sp,
                color = AILauncherColors.current.Accent,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}
