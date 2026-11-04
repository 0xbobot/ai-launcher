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
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.fragment.app.FragmentActivity
import com.bobot.ailauncher.data.BiometricAuth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.focus.focusProperties
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
import com.bobot.ailauncher.ui.theme.AILauncherType
import com.bobot.ailauncher.ui.theme.PetMotion
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
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
    // 折叠组（419）：不活跃集——可见 = azApps（已去隐藏、去自己）；共享规则
    // （AppUsageTracker.inactiveMembers：系统 UsageStats 优先，没授权退回
    // 自己记的＋首次见到宽限；固定位永远不进）。inactiveTick 由 launchApp
    // 在真实启动时 +1，逼出重组（否则回应用中心时 N 不刷新）。
    // 一键隐藏按钮的 N 与折叠组成员同源（同一规则、同一集合）。
    var inactiveTick by remember { mutableIntStateOf(0) }
    val inactiveTargets = remember(azApps, dockTick, inactiveTick) {
        AppUsageTracker.inactiveMembers(context, azApps)
    }
    // 折叠组（421 安静版）：本地态与持久化镜像——动作须当场见效（只写 prefs
    // 不触发重组，列表不会立刻变），故两态本地持有、动作时双写。
    // 419 的确认弹窗已删（Bob：太干扰）：组在 N>0 且未被取消压制时自动
    // 出现（默认收着）；用户在组底点“取消折叠”即压制，N 再多 10 个时
    // 安静重现，不弹任何东西。
    var foldExpandedState by remember { mutableStateOf(UiPrefs.getFoldExpanded(context)) }
    var foldLastDeclinedN by remember { mutableStateOf(UiPrefs.getFoldLastDeclinedN(context)) }
    // 压制：点过“取消折叠”且 N 还没比取消时多 10 个
    val foldSuppressed = foldLastDeclinedN >= 0 && inactiveTargets.size < foldLastDeclinedN + 10
    // 未压制时成员即不活跃集（从 A-Z 拿掉，分组时先分出折叠组）；
    // 新变不常用的自动进来（inactiveTargets 重算即进）。
    val foldMembers = if (foldSuppressed) emptyList() else inactiveTargets
    val foldVisible = foldMembers.isNotEmpty()
    val foldMemberPkgs = remember(foldMembers) { foldMembers.map { it.packageName }.toSet() }
    val azGroups: List<Pair<Char, List<AppInfo>>> = remember(azApps, foldMemberPkgs) {
        val map = linkedMapOf<Char, MutableList<AppInfo>>()
        // 拼音首字母分组：用字典（20924 字），GB2312 区位码做兜底
        val pinyinOf: (Char) -> Char = { c ->
            val fromDict = AppSearchIndex.pinyinInitialOf(context, c)
            if (fromDict != '#') fromDict else pinyinInitial(c)
        }
        val rest = if (foldMemberPkgs.isEmpty()) azApps
            else azApps.filter { it.packageName !in foldMemberPkgs }
        rest.forEach { app ->
            map.getOrPut(groupKey(app.label, pinyinOf)) { mutableListOf() }.add(app)
        }
        map.toList().sortedWith(compareBy({ if (it.first == '#') 1 else 0 }, { it.first }))
    }
    // Bob 2026-11-03 回滚：字母导航回 409 加过滤前——letters 只取 azGroups
    // （有应用字母集），不再跟搜索结果走；搜索态 rail 按上一条已隐藏
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
    // 应用中心升级：清除同步清过滤词（跳过 120ms 防抖）——✕ 走 onValueChange
    // 回空时 filterQuery 立即回空，全量列表不慢一拍；不清键盘（保持现状）
    fun onQueryFieldChange(v: TextFieldValue) {
        queryField = v
        if (v.text.isBlank()) filterQuery = ""
    }
    val searching = filterQuery.trim().isNotBlank()
    // 应用中心升级：键盘可见检测（WindowInsets.ime 下缘 > 0）——键盘弹出
    // 专用布局的开关（隐藏 rail / 幽灵字母 / 刊头、行距切紧凑）；adjustResize
    // 下 ime inset 仍正常上报
    // ime 是 WindowInsets.Companion 的扩展属性，须单独导入（同 HomeScreen）
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // 应用中心升级：composing 期间结果区降透明度至 0.6（Bob 定稿③）——
    // composing 守卫与 120ms 防抖本身不动，只弱化旧结果的视觉权重
    val composingDim = queryField.composition != null
    // 应用中心升级：减动态——悬停弹簧减动态开启时直接贴合（不做弹簧动画）
    val reduceMotion = rememberReduceMotion()
    // v0.25.5：Niagara 式——拖动直接滚完整列表，不做内容过滤，松手零位移
    // v0.68.95（F-04）：索引随仓库快照在 IO 构建（基于同一份可见集），组合期只读。
    val searchIndex = catalog?.index ?: emptyList()
    val searchResults = remember(filterQuery, searchIndex) {
        if (filterQuery.trim().isBlank()) null
        else AppSearchIndex.search(context, filterQuery, searchIndex)
    }
    // 开始搜索时滚到顶部；退出搜索时恢复进入前位置
    val listState = rememberLazyListState()
    // 应用中心升级：退出搜索恢复进入前位置（只记首次进入搜索前的位置，
    // 首帧 searching=false 时不滚，避免与默认定位到首字母组打架）
    var savedListPosition by remember { mutableStateOf(0) }
    var hadSearched by remember { mutableStateOf(false) }
    LaunchedEffect(searching) {
        if (searching) {
            savedListPosition = listState.firstVisibleItemIndex
            hadSearched = true
            listState.scrollToItem(0)
        } else if (hadSearched) {
            listState.scrollToItem(savedListPosition)
        }
    }

    val nested = rememberPullDownConnection(listState, onPullDownState.value)

    // 字母 → LazyColumn item index（每字母：N 行 + 1 组间呼吸间距）
    // v0.41.8（Bob）：字母不再独占一行（浪费纵向空间），回到每组首行行内幽灵字母；
    // 吸顶仍由悬停 overlay 实现
    // Bob 2026-11-03 回滚：锚点回 409 加过滤前——只按完整列表算
    // （Dock 分区在前、每组＋1 组间呼吸），不再跟搜索结果走
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
    // 折叠组（421 安静版）：段占位——组头 1 项＋（展开时 N 行＋组底行 1）＋
    // 组间呼吸 1 项；收起 2 项、展开 3＋N 项；压制或 N==0 时不占位。
    // 隐藏段锚点随之移位。
    val foldSectionSize = if (foldVisible) (if (foldExpandedState) 3 + foldMembers.size else 2) else 0
    val foldHeaderIndex = dockSectionSize + azTotalItems
    val hiddenHeaderIndex = foldHeaderIndex + foldSectionSize

    // v0.67.13（Bob）：主屏 picker 连续驱动——手指不松开时持续跟随滚动
    val homeTarget by UiPrefs.homeRailTarget.collectAsState()
    val homeLetter by UiPrefs.homeRailLetter.collectAsState()
    LaunchedEffect(homeTarget, homeLetter, letterAnchors) {
        when (homeTarget) {
            UiPrefs.HomeRailTarget.LETTER -> {
                // 应用中心升级：兜底改与横屏一致（向后优先、再向前），
                // 不再用最近字母（两端规则不一致）
                val c = homeLetter
                val anchor = c?.let { ch ->
                    letterAnchors[ch] ?: run {
                        var fwd = ch
                        while (fwd <= 'Z' && letterAnchors[fwd] == null) fwd++
                        if (fwd <= 'Z') letterAnchors[fwd] else {
                            var bwd = ch
                            while (bwd >= 'A' && letterAnchors[bwd] == null) bwd--
                            if (bwd >= 'A') letterAnchors[bwd] else null
                        }
                    }
                }
                if (anchor != null) listState.scrollToItem(anchor)
            }
            UiPrefs.HomeRailTarget.DOCK -> listState.scrollToItem(0)
            UiPrefs.HomeRailTarget.HIDDEN -> listState.scrollToItem(hiddenHeaderIndex)
            null -> {}
        }
    }

    // v0.41.8：手动吸顶——当前组首行滚出顶部后，悬停 overlay 接管显示；
    // 下一组首行接近顶部时把悬停字母往上顶走（Bob：滚动悬停/顶走）
    val stuckLetter: Char? by remember(azGroups, foldVisible, foldHeaderIndex) {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            // 折叠组（419）：滚进折叠段（含其后）后不显示字母幽灵，改由
            // 折叠幽灵接管（折叠段后是隐藏区，隐藏幽灵另有分支）。
            if (foldVisible && firstIdx >= foldHeaderIndex) return@derivedStateOf null
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
    val stuckPushPx: Float by remember(azGroups, hiddenApps, hiddenUnlocked, foldVisible, foldHeaderIndex) {
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
            } else if (foldVisible) {
                // 折叠组（419）：最后一个字母后面是折叠组头（不是隐藏头），
                // 组头接近顶部时把悬停字母顶走
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == foldHeaderIndex }
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
    // 应用中心升级：减动态开启时直接贴合
    val animatedPushPx by animateFloatAsState(
        targetValue = stuckPushPx,
        animationSpec = if (reduceMotion) tween(0) else PetMotion.PadStandard,
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
    // 折叠组（419）：折叠段幽灵——组头滚出顶部后接管，直到滚出折叠段；
    // 与 stuckHidden 同理（组头已走＋段内仍有可见项）
    val stuckFold: Boolean by remember(foldVisible, foldHeaderIndex) {
        derivedStateOf {
            if (!foldVisible) return@derivedStateOf false
            val vis = listState.layoutInfo.visibleItemsInfo
            val first = vis.firstOrNull { it.key == "fold-header" }
            val firstGone = first == null || first.offset + first.size < 0
            val foldInView = vis.any {
                it.key == "fold-header" || it.key == "sp:fold" || it.key == "fold-foot" ||
                    (it.key is String && (it.key as String).startsWith("f:"))
            }
            firstGone && foldInView
        }
    }
    // 折叠组（419）：折叠幽灵被隐藏头顶走（与字母顶走逻辑一致）；
    // 无隐藏应用时不顶（幽灵随滚出自然消失，沿用隐藏为空时的旧口径）
    val foldPushPx: Float by remember(foldVisible, hiddenApps, hiddenUnlocked) {
        derivedStateOf {
            if (!stuckFold) return@derivedStateOf 0f
            if (hiddenApps.isEmpty()) return@derivedStateOf 0f
            @Suppress("UNUSED_EXPRESSION")
            listState.firstVisibleItemScrollOffset
            val firstHiddenKey = if (hiddenUnlocked) "h:${hiddenApps.first().packageName}"
                else "hidden-locked"
            val off = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == firstHiddenKey }
                ?.offset?.toFloat() ?: return@derivedStateOf 0f
            val pushZonePx = with(density) { 48.dp.toPx() }
            if (off < pushZonePx) off - pushZonePx else 0f
        }
    }
    val animatedFoldPushPx by animateFloatAsState(
        targetValue = foldPushPx,
        animationSpec = if (reduceMotion) tween(0) else PetMotion.PadStandard,
        label = "foldPush"
    )
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
        animationSpec = if (reduceMotion) tween(0) else PetMotion.PadStandard,
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
    // 426（Bob 2026-11-04 点 X 回设置页）：morph 按钮位 bounds（胶囊框外，
    // 点它不算点外面；见根 Box 观察者的 extraBoundsInHost 排除。横屏经
    // onMorphButtonPositioned 回传同一状态，两面同一时刻只一面在屏上）。
    var morphButtonBoundsInRoot by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }

    // 应用中心升级：启动 in-flight 门——500ms 内重复点同一应用只启动一次
    // （结果行 combinedClickable 本身无防抖，系统层合并不可靠）
    var lastLaunch by remember { mutableStateOf<Pair<String, Long>?>(null) }
    // 应用中心升级：问七仔行 500ms 节流——连点不进两次协调器
    var lastAskMs by remember { mutableStateOf(0L) }
    fun throttledAsk(q: String) {
        val now = System.currentTimeMillis()
        if (now - lastAskMs < 500L) return
        lastAskMs = now
        onAskQizai(q)
    }
    // v0.25.2：松手滚动——等列表恢复全量重组完成后再滚，避免打在旧内容上
    fun launchApp(app: AppInfo) {
        val now = System.currentTimeMillis()
        val last = lastLaunch
        if (last != null && last.first == app.packageName && now - last.second < 500L) return
        lastLaunch = app.packageName to now
        focusManager.clearFocus()
        keyboardController?.hide()
        AppUsageTracker.recordLaunch(context, app.packageName)
        inactiveTick++
        try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(app.packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) context.startActivity(intent)
        } catch (_: Exception) {
            IslandToasts.show("无法打开「${app.label}」")
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
            IslandToasts.show("无法启动身份验证")
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
                keyboardController = keyboardController,
                // 426：按钮位排除——点 morph 按钮不算点外面，否则点 X 时观察者
                // 先清焦点，点击读到已失焦就走设置分支（与按钮抢焦点同为必堵项）。
                extraBoundsInHost = {
                    morphButtonBoundsInRoot?.let { b ->
                        listOf(
                            androidx.compose.ui.geometry.Rect(
                                b.left - rootPosInRoot.x,
                                b.top - rootPosInRoot.y,
                                b.right - rootPosInRoot.x,
                                b.bottom - rootPosInRoot.y
                            )
                        )
                    } ?: emptyList()
                }
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
            // Bob 2026-11-03：编辑态不再换版面——刊头常驻（键盘弹出也不隐藏）；
            // 顶栏左轴 28→20dp，与搜索行统一 20/12dp 口径
            if (!isLandscapeAc) {
            // v0.68.44（Bob）：杂志刊头
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 8.dp)
            ) {
                Text(
                    text = "全部应用",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = AILauncherType.Display.fontSize,
                    letterSpacing = 2.sp,
                    color = AILauncherColors.current.Title
                )
                Text(
                    // 应用中心升级：catalog 为空时不显示"0 款"空列表，改弱提示
                    // Bob 2026-11-03：数字跟当前列表走——搜索态显示过滤后条数
                    text = if (catalog == null) "正在加载…"
                        else "${searchResults?.size ?: azApps.size} 款 · 右侧滑字母直达",
                    fontSize = AILauncherType.Caption.fontSize,
                    color = AILauncherColors.current.Hint,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            }  // else 竖屏刊头结束
            // 本搜索区仅竖屏组合；横屏由 AppCenterLandscape 组合（F2 起同一胶囊契约）
            if (!isLandscapeAc) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 8.dp)
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
                // 一个入口两层响应：打字走本地层过滤网格（状态层 query/searchResults 不变）。
                // 应用中心胶囊无提交钮（Bob 2026-11-03：箭头仅收键盘、无实际功能，
                // 随首页意图入口一并删）；IME Done 与问七仔行仍 = 问七仔。
                IntentCapsuleField(
                    value = queryField,
                    onValueChange = ::onQueryFieldChange,
                    onSubmit = {
                        val q = query.trim()
                        if (q.isNotEmpty()) onAskQizai(q)
                    },
                    // 425（Bob 2026-11-04，参考视频）：里侧清空去掉，挪到外面齿轮位——
                    // 聚焦变 X、失焦回齿轮，全程只剩外面这一个清空（见下齿轮 morph）。
                    showClearButton = false,
                    showSubmitButton = false,
                    fusedBackground = true,
                    // v0.68.93：焦点外报 + 范围捕获（根 Box 点外部观察者用）
                    onFocusChanged = { intentFocused = it },
                    modifier = Modifier.weight(1f)
                        .onGloballyPositioned { intentBoundsInRoot = it.boundsInRoot() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 425（Bob 2026-11-04，参考视频）：齿轮位 morph——失焦是设置齿轮原样，
                // 聚焦（输入态）就地变清空；只换图标＋动作＋文案，圆盘／位置／尺寸／testTag 一律不动。
                // 点 X：有字清空（留输入态，X 还在）、没字退焦（键盘收起，自动变回齿轮）；
                // 返回／点外面／点完成／点开应用都会失焦，变回齿轮不用每个出口单独处理。
                // v0.41.9（Bob 选 B）：设置齿轮缩小弱化，仍在右上
                // 426（Bob 2026-11-04 点 X 回设置页）：按钮不可聚焦＋记 bounds——
                // 点 X 时焦点不能从输入框被抢走（否则 morph 先变回齿轮、点击读到
                // false 走设置），且按钮位要从点外面清焦里排除（见根 Box 观察者）。
                // 圆盘／位置／尺寸／testTag 一律不动，只加这两项。
                IconButton(
                    onClick = {
                        if (intentFocused) {
                            if (query.isNotEmpty()) onQueryFieldChange(TextFieldValue(""))
                            else focusManager.clearFocus()
                        } else {
                            onOpenSettings()
                        }
                    },
                    // 体检批D ⑥：冒烟语义定位锚点（应用中心设置齿轮）
                    modifier = Modifier.testTag("appcenter_settings")
                        .focusProperties { canFocus = false }
                        .onGloballyPositioned { morphButtonBoundsInRoot = it.boundsInRoot() }
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
                        if (intentFocused) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清空",
                                tint = AILauncherColors.current.Title,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "设置",
                                tint = AILauncherColors.current.Hint.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            // R2 UI（10-21 新规格）：「问七仔」行——有输入时出现，整句交给助手理解（回首页提交协调器）
            // 意图改进一批：本行跟随原始文本即时出现（不等防抖落定），文案与提交都用原句
            // 应用中心升级：去 Card 底降重——文字改 Title 75%，金色只留小箭头
            // 一处线索；连点 500ms 节流
            if (query.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 16.dp, top = 4.dp)
                        .clickable { throttledAsk(query.trim()) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "问助手：「${query.trim()}」",
                        fontSize = AILauncherType.Footnote.fontSize,
                        color = AILauncherColors.current.Title.copy(alpha = 0.75f),
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
            Spacer(modifier = Modifier.height(4.dp))
            }  // if (!isLandscapeAc) 搜索区结束（v0.68.72 新增，R2 改同组件）
        }  // Header Column 闭合

        // v0.68.62（Bob）：横屏双栏——左索引右网格，竖屏用原单列
        if (isLandscapeAc) {
            AppCenterLandscape(
                azGroups = azGroups,
                letters = letters,
                totalCount = searchResults?.size ?: azApps.size,
                favApps = dockApps,
                hiddenApps = hiddenApps,
                hiddenUnlocked = hiddenUnlocked,
                // 折叠组（419/421）：状态由调用点传入（横屏不另起一套）；
                // foldMembers 已含压制与 N>0 门，横屏直接用。
                foldApps = foldMembers,
                foldExpanded = foldExpandedState,
                onToggleFold = {
                    val next = !foldExpandedState
                    foldExpandedState = next
                    UiPrefs.setFoldExpanded(context, next)
                },
                // 折叠组（421 安静版）：组底“取消折叠”——与竖屏同一双写逻辑
                // （压制＋收起，重现时默认仍是收着的）。
                onUnfold = {
                    UiPrefs.setFoldLastDeclinedN(context, inactiveTargets.size)
                    foldLastDeclinedN = inactiveTargets.size
                    foldExpandedState = false
                    UiPrefs.setFoldExpanded(context, false)
                },
                onUnlockHiddenApps = { unlockHiddenApps() },
                onLaunch = ::launchApp,
                onLongPress = { popupApp = it },
                onOpenSettings = onOpenSettings,
                // v0.68.82（横屏专项第 1 项）：两套手势对齐竖屏（底部上滑已删）
                onPullDownToD2 = { onPullDownState.value() },
                onHeaderSwipeRight = { onHeaderSwipeRightState.value() },
                // F2 销账（线 C）：横屏搜索契约与竖屏统一——状态层 TextFieldValue
                // 直传（不再手工包 TFV：旧写法 composition 恒为 null，绕过了
                // 状态层的 composing 暂停守卫，拼音未上屏时过滤会乱跳）、
                // 组件/提交/焦点礼仪与竖屏同一套（见 AppCenterLandscape）
                searchFieldValue = queryField,
                onSearchFieldChange = ::onQueryFieldChange,
                searchResults = searchResults,
                onSearchSubmit = {
                    val q = query.trim()
                    if (q.isNotEmpty()) onAskQizai(q)
                },
                // 应用中心升级：横屏空结果文案区分"索引加载中"与"真无匹配"
                indexLoading = catalog == null && query.isNotBlank(),
                // 横屏首页修：把主屏 picker 会话状态透传给横屏 rail 门（同竖屏 AppCenter.kt:1419）
                homeTarget = homeTarget,
                onAskQizai = onAskQizai,
                onSearchFocusChanged = { intentFocused = it },
                // 425 齿轮 morph：横屏复用竖屏同一 intentFocused（只读传入，不另起状态）
                searchFocused = intentFocused,
                onSearchPositioned = { intentBoundsInRoot = it },
                // 426：横屏 morph 按钮位 bounds 回传（与竖屏同一排除状态，见根 Box 观察者）
                onMorphButtonPositioned = { morphButtonBoundsInRoot = it },
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
                // 字母导航等高（Bob 2026-10-25 追问）：列表容器实测高写给首页选择器
                // 做等高来源；键盘弹出时刊头标题区隐藏、容器高会变，只在键盘收起时采。
                .onSizeChanged { if (!imeVisible) UiPrefs.appCenterListHeightPx.value = it.height }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // 应用中心升级：composing 期间结果区降透明度至 0.6（Bob 定稿③）
                    .graphicsLayer { alpha = if (composingDim) 0.6f else 1f }
                    .nestedScroll(nested),
                // v0.41.7：列表不再统一 padding（之前字母头用负 offset 伸进左留白，
                // 在某些行渲染异常）；改为每项各自加正向 padding——应用行保持
                // 72dp 左缩进 + 64dp 右缩进（左隐形触发区/右 rail 避让），字母头占满宽
                verticalArrangement = Arrangement.spacedBy(4.dp),
                // v0.40.0：底部留出手势条高度，避免末行被手势条盖住
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                val results = searchResults
                if (results != null) {
                    // 搜索态：只显示搜索结果（拼音/首字母/自然语言/模糊）
                    if (results.isEmpty()) {
                        item(key = "search-empty") {
                            Text(
                                // 应用中心升级：索引缺失与真无匹配区分文案
                                text = if (catalog == null) "应用列表还没准备好，请稍后再试"
                                    else "没有找到「${filterQuery.trim()}」相关的应用",
                                fontSize = AILauncherType.Callout.fontSize,
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
                                    // 应用中心升级：行展开去 animateContentSize，
                                    // 只留 AnimatedVisibility（避免双动画叠加）
                            ) {
                                AppRow(
                                    app = app,
                                    onLaunch = { launchApp(app) },
                                    compact = true,
                                    onLongClick = {
                                        hapticView.confirmHaptic()
                                        popupApp = app
                                    },
                                    showInfoIcon = isPopupExpanded,
                                    onInfoClick = {
                                        openAppDetailsPage(context, app.packageName)
                                        popupApp = null
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
                    item { Spacer(modifier = Modifier.height(16.dp)) }
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
                                    .padding(start = 24.dp)
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
                                compact = true,
                                onLongClick = {
                                    hapticView.confirmHaptic()
                                    popupApp = app
                                },
                                showInfoIcon = isPopupExpandedX,
                                onInfoClick = {
                                    openAppDetailsPage(context, app.packageName)
                                    popupApp = null
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
                                    .padding(end = 88.dp)
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
                                                // v0.55.2：步进 = 行 60dp + 4dp 间距 = 64dp
                                                // （Bob 2026-11-03：一屏 8 个；旧 56dp 是 52dp 行时代的数，已作废）
                                                val rowH = with(density) {
                                                    64.dp.toPx()
                                                }
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
                    item(key = "sp:dock") { Spacer(modifier = Modifier.height(16.dp)) }
                }
                // A-Z 列表（v0.41.0 B 方案）
                // v0.41.8：每组 = 应用行 + 组间间距（字母不再独占一行，Bob）。
                // 每组首行左侧留白放幽灵字母（正向 padding，不用负 offset）；
                // 首行滚出顶部后由悬停 overlay 接管（手动吸顶，见 stuckLetter）
                // 应用中心升级：catalog 为空时显示加载弱提示（替代空列表）
                if (catalog == null) {
                    item(key = "catalog-loading") {
                        Text(
                            text = "正在加载应用…",
                            fontSize = AILauncherType.Callout.fontSize,
                            color = AILauncherColors.current.Hint,
                            modifier = Modifier.padding(
                                top = 32.dp, start = 72.dp, end = 64.dp
                            )
                        )
                    }
                }
                azGroups.forEach { (letter, apps) ->
                    itemsIndexed(apps, key = { _, app -> "a:${app.packageName}" }) { index, app ->
                        // v0.41.7：行级 padding（列表不再统一 padding）
                        // 应用中心升级：行 Box 加 clipToBounds——字体缩放下幽灵
                        // 字母不侵入上下行（字号不动）
                        Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                            // v0.68.61：横屏隐藏幽灵字母（省竖向空间，不乱）
                            // Bob 2026-11-03：编辑态不再隐藏幽灵字母；透明度
                            // 0.20 → 0.35（Bob 定稿④：可以动、效果优先）
                            if (index == 0 && !isLandscapeAc) {
                                Text(
                                    text = letter.toString(),
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 40.sp,
                                    // Bob 2026-11-03 原型比例：幽灵字母收小（52dp 行内不撑高，
                                    // 大写高约 0.7em，40sp 字形约 28dp）
                                    lineHeight = 40.sp,
                                    // v0.68.44（Bob）：杂志章节感——放大加深
                                    color = AILauncherColors.current.Title.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 24.dp)
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
                                compact = true,
                                onLongClick = {
                                    hapticView.confirmHaptic()
                                    popupApp = app
                                },
                                showInfoIcon = isPopupExpandedX,
                                onInfoClick = {
                                    openAppDetailsPage(context, app.packageName)
                                    popupApp = null
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
                    item(key = "sp:$letter") { Spacer(modifier = Modifier.height(16.dp)) }
                }
                // 折叠组（421 安静版）：主列表末尾、A-Z 之后、隐藏区之前；平时只占
                // 组头一行（“不常用 · N 个”＋收展 chevron），展开时组内为正常
                // 应用行（复用 AppRow：能打开、长按菜单都在），按 A-Z 行内顺序
                // （拼音排序）排列，行后跟组底确认行；N>0 且未压制时自动出现
                // （默认收着），压制时成员回到 A-Z 里。
                // 组头 chevron 是组自身的收展标示，不是应用行箭头（416 删的是
                // 应用行尾箭头，两者不是同一元素）。
                if (foldVisible) {
                    item(key = "fold-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 72.dp, end = 64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val next = !foldExpandedState
                                    foldExpandedState = next
                                    UiPrefs.setFoldExpanded(context, next)
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "不常用 · ${foldMembers.size} 个",
                                fontSize = AILauncherType.BodyLarge.fontSize,
                                color = AILauncherColors.current.Title,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (foldExpandedState) Icons.Outlined.KeyboardArrowUp
                                    else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = if (foldExpandedState) "收起不常用" else "展开不常用",
                                tint = AILauncherColors.current.Hint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (foldExpandedState) {
                        itemsIndexed(foldMembers, key = { _, app -> "f:${app.packageName}" }) { _, app ->
                            Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                                // 折叠组内无幽灵字母（不是字母组）；行结构与 A-Z 行一致
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
                                        compact = true,
                                        onLongClick = {
                                            hapticView.confirmHaptic()
                                            popupApp = app
                                        },
                                        showInfoIcon = isPopupExpandedX,
                                        onInfoClick = {
                                            openAppDetailsPage(context, app.packageName)
                                            popupApp = null
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
                    }
                    // 折叠组（421 安静版）：组底确认行——展开态才有；不点就是
                    // 认了（组一直留着），点“取消折叠”即压制＋收起，组消失、
                    // 成员回 A-Z，N 再多 10 个时安静重现（不弹任何东西）。
                    if (foldExpandedState) {
                        item(key = "fold-foot") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 72.dp, end = 64.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "这些应用 90 天没打开，已折起来",
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    color = AILauncherColors.current.Hint,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    UiPrefs.setFoldLastDeclinedN(context, inactiveTargets.size)
                                    foldLastDeclinedN = inactiveTargets.size
                                    foldExpandedState = false
                                    UiPrefs.setFoldExpanded(context, false)
                                }) {
                                    Text(
                                        text = "取消折叠",
                                        color = AILauncherColors.current.Body,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    item(key = "sp:fold") { Spacer(modifier = Modifier.height(16.dp)) }
                }
                // v0.41.18（Bob）：隐藏分组——主列表末尾，像 ABCD 分组一样；
                // v0.41.20（Bob）：不显示"已隐藏"标题文字，只留幽灵图标。
                // 未解锁显示锁定占位，解锁后应用可直接打开（不用先恢复）。
                // 智能 v3：一键隐藏按钮与隐藏块并列——隐藏为空但 N>0 时也进门
                // （即首次清理时可见）；锁门仍只看 hiddenApps（见下）。
                if (hiddenApps.isNotEmpty() || inactiveTargets.isNotEmpty()) {
                    // v0.41.32（Bob）：幽灵眼与第一行同行，不单独占行
                    // 智能 v3：隐藏为空时不画锁占位（否则 N>0 且无隐藏时会冒出
                    // “点击解锁查看”）；锁占位只在确实有隐藏应用时出现。
                    if (hiddenApps.isNotEmpty() && !hiddenUnlocked) {
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
                                        .padding(start = 24.dp)
                                        .size(36.dp)
                                        .semantics { invisibleToUser() }
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 72.dp, end = 64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { unlockHiddenApps() }
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
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
                                        // 体检批D ⑨：装饰图标（同行文字「点击解锁查看」已表达）标装饰语义
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
                                    fontSize = AILauncherType.BodyLarge.fontSize,
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
                                        .padding(start = 24.dp)
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
                                    compact = true,
                                    onLongClick = {
                                        hapticView.confirmHaptic()
                                        popupApp = app
                                    },
                                    showInfoIcon = isPopupExpandedX,
                                    onInfoClick = {
                                        openAppDetailsPage(context, app.packageName)
                                        popupApp = null
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
                    // 智能 v3：一键隐藏 90 天不活跃——隐藏列表之后；仅 N>0 时出现；
                    // 点按时实时重算目标（防点按前刚启动过），用现有 HiddenApps.hide
                    // 逐个隐藏；隐藏后版本 +1，列表刷新、N 归零，按钮随之消失。
                    // 固定位绝不计入（显示与点按两次都按置顶集合排除）。
                    if (inactiveTargets.isNotEmpty()) {
                        item(key = "hide-inactive") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(
                                    onClick = {
                                        val pinned = UiPrefs.getDockPinned(context).toSet()
                                        val hiddenNow = HiddenApps.getHidden(context)
                                        val now = System.currentTimeMillis()
                                        val ninetyDaysMs = 90L * 24 * 60 * 60 * 1000
                                        azApps.filter { app ->
                                            app.packageName !in pinned &&
                                                app.packageName !in hiddenNow &&
                                                AppUsageTracker.lastUsed(context, app.packageName).let { it == 0L || now - it > ninetyDaysMs }
                                        }.forEach { HiddenApps.hide(context, it.packageName) }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AILauncherColors.current.Accent,
                                        contentColor = Color.White
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                ) {
                                    Text(
                                        text = "一键隐藏 90 天不活跃的应用（${inactiveTargets.size} 个）",
                                        fontSize = AILauncherType.BodyLarge.fontSize,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "藏起来的应用随时可以在这里恢复",
                                    fontSize = AILauncherType.Footnote.fontSize,
                                    color = AILauncherColors.current.Hint
                                )
                            }
                        }
                    }
                    item(key = "sp:hidden") { Spacer(modifier = Modifier.height(16.dp)) }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                } // else 非搜索态：分类区 + A-Z 列表
            }
            // v0.41.8：悬停幽灵字母——只在当前组首行滚出顶部后显示，下一组首行
            // 接近时被顶走；外层 clipToBounds 保证不会顶到搜索栏上面（Bob）。
            // 应用中心升级：透明度跟行内幽灵字母同为 0.35；Bob 2026-11-03 起编辑态不再隐藏
            val sl = stuckLetter
            // Bob 2026-11-03：搜索态无分组可指示，悬停字母不显示
            // （扁平结果无组概念；仅键盘弹出未输入时仍显示）
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
                            .padding(start = 24.dp, top = 16.dp)
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
                            .padding(start = 24.dp, top = 16.dp)
                            .size(36.dp)
                            .semantics { invisibleToUser() }
                    )
                } else if (stuckFold) {
                    // 折叠组（419）：折叠段悬停——“不常用”文字（不是字母），
                    // 永远报你在哪一段；Callout 档（14sp）以便 3 字塞进幽灵留白
                    Text(
                        text = "不常用",
                        fontSize = AILauncherType.Callout.fontSize,
                        color = AILauncherColors.current.Title.copy(alpha = 0.35f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 24.dp, top = 16.dp)
                            .offset { IntOffset(0, animatedFoldPushPx.roundToInt()) }
                    )
                } else if (sl != null && !isLandscapeAc) {
                    Text(
                        text = sl.toString(),
                        fontFamily = FontFamily.Serif,
                        fontSize = 40.sp,
                        color = AILauncherColors.current.Title.copy(alpha = 0.35f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 24.dp, top = 16.dp)
                            .offset { IntOffset(0, animatedPushPx.roundToInt()) }
                    )
                }
            }
            // 应用中心底部上滑已删除（Bob 2026-11-03：避免操作干扰）。
            // Rail：波浪字母导航（v0.24，学 Niagara/参考视频）——只做定位
            // v0.15.1：rail 整体再往外侧靠（字母列距屏幕边缘约 10dp）
            // Bob 2026-11-03 回滚：字母导航回 409 加过滤前（字母/锚点只按完整列表；搜索态 rail 按上一条隐藏）
            // v0.24：点按跳字母（居中）；拖动时波浪跟手，列表跟手滚动切换字母
            // v0.40.2：右侧一条可见 rail + 左侧隐形触发区（Bob）——
            // 左边不摆 rail（摆两个很奇怪），但左侧滑动同样触发字母切换，
            // 触发后是右侧 rail 亮起波浪跟随；应用列表左侧缩进给手指留空间。
            // v0.67.11（Bob）：主屏 picker 会话中隐藏自己的 rail，避免与 picker 重叠
            // Bob 2026-11-03：搜索后隐藏字母导航——可见 rail 与左侧隐形触发区
            // 同在此门内，一并隐藏；搜索结束恢复全量字母
            // 折叠组（419）：字母全被折掉时 rail 仍可达折叠专位（否则 Fold
            // 专位在恰恰需要它时不可达）；空目录时 letters 与 fold 皆空，门仍关。
            if ((letters.isNotEmpty() || foldVisible) && homeTarget == null && searchResults == null) {
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
                // 字母导航完全统一（Bob 2026-10-25 追问）：项目表走三处共用的
                // buildRailItems（RailItems.kt），构造与此前逐字一致，仅换调用。
                // Bob 2026-11-03 回滚：👁 门回 409 加过滤前——只看隐藏分区非空
                // （搜索态 rail 按上一条已隐藏，此门不再区分搜索态）
                val railItems = remember(dockApps, letters, hiddenApps, foldVisible) {
                    buildRailItems(dockApps.isNotEmpty(), letters, hiddenApps.isNotEmpty(), hasFold = foldVisible)
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
                        // 应用中心升级：★/👁 改瞬时落位，与字母一致
                        // （此前动画滚动与字母瞬跳交替）
                        is RailItem.Star -> scope.launch { listState.scrollToItem(0) }
                        is RailItem.Eye -> scope.launch { listState.scrollToItem(hiddenHeaderIndex) }
                        // 折叠组（419）：收纳盒专位 → 折叠组头锚点
                        is RailItem.Fold -> scope.launch { listState.scrollToItem(foldHeaderIndex) }
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
                    // 应用中心升级（Bob 定稿①）：竖屏跟进 B 方案全套——
                    // 14sp / 16sp 行高 / 1.0 / Normal（329 起），与横屏同款
                    // （railFraction 仍 0.85；高度改跟随列表容器，bottom 70dp 保留）
                    railHeight = null,
                    railFraction = 0.85f,
                    letterFontSize = 14.sp,
                    letterLineHeight = 16.sp,
                    letterAlpha = 1f,
                    letterFontWeight = FontWeight.Normal,
                    railWidth = 48.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 72.dp)
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
                .padding(horizontal = 40.dp)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // v0.57.2（Bob）：更新为新手势说明
                Text(
                    text = "滑动快捷操作",
                    fontSize = AILauncherType.BodyLarge.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.current.Title
                )
                GuideRow(
                    icon = Icons.Filled.Star,
                    tint = AILauncherColors.current.Accent,
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
                    fontSize = AILauncherType.Caption.fontSize,
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
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                fontSize = AILauncherType.Callout.fontSize,
                fontWeight = FontWeight.Medium,
                color = AILauncherColors.current.Title
            )
            Text(
                text = desc,
                fontSize = AILauncherType.Caption.fontSize,
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





