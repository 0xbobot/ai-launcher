@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.bobot.ailauncher.ui.apps

import android.content.Intent
import android.os.SystemClock
import android.provider.Telephony
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.BiometricAuth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.bobot.ailauncher.data.ActionUsageTracker
import com.bobot.ailauncher.data.AppCatalog
import com.bobot.ailauncher.data.AppInfo
import com.bobot.ailauncher.data.AppSearchIndex
import com.bobot.ailauncher.data.CapabilityRegistry
import com.bobot.ailauncher.data.CapabilityResolution
import com.bobot.ailauncher.ui.home.IntentCapsuleField
import com.bobot.ailauncher.ui.home.clearIntentFocusOnOutsideTap
import com.bobot.ailauncher.data.AppUsageTracker
import com.bobot.ailauncher.data.HiddenApps
import com.bobot.ailauncher.data.InstalledApps
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.data.DeepLinkServiceReport
import com.bobot.ailauncher.data.LlmCallResult
import com.bobot.ailauncher.data.LlmConfig
import com.bobot.ailauncher.data.LlmRouter
import com.bobot.ailauncher.data.BadgeProbe
import com.bobot.ailauncher.data.BadgeStickyStore
import com.bobot.ailauncher.data.AppBackgroundLatch
import com.bobot.ailauncher.data.ForegroundConfirm
import com.bobot.ailauncher.data.UnreadJumpLog
import com.bobot.ailauncher.data.hasUsageStatsPermission
import com.bobot.ailauncher.data.MarketGuide
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.SmsRepository
import com.bobot.ailauncher.data.UserIntentMapping
import com.bobot.ailauncher.data.deriveUnreadApps
import com.bobot.ailauncher.data.groupAppsByLetter
import com.bobot.ailauncher.data.unreadRowText
import com.bobot.ailauncher.data.withoutPkgs
import com.bobot.ailauncher.service.LauncherNotificationService
import com.bobot.ailauncher.ui.components.AppIconImage
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.theme.pageTextureBackground
import com.bobot.ailauncher.ui.theme.rememberReduceMotion
import com.bobot.ailauncher.ui.theme.statusBarsTopPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.exp
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.bobot.ailauncher.ui.island.IslandToasts
import com.bobot.ailauncher.util.ensureListenerBound

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
// 反馈闭环（Bob 2026-11-06）：选择器用 ModalBottomSheet（实验 API），
// 与 PullUpDock 同款——这里补 ExperimentalMaterial3Api。
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppCenterContent(
    onOpenSettings: () -> Unit,
    onPullDownToD2: () -> Unit,
    onHeaderSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 通知监听自查重绑（Bob 2026-11-08 20:42：通知栏有新通知，应用中心却没
    // 有未读组、角标也没有——监听断了就一直断着，角标流冻结在空 map）。
    // 首页已有 ON_RESUME 复核，应用中心没有；但应用中心是同 Activity 的
    // 浮层（PullUpDock.FullAppsOverlay），打开它 Activity 不走 ON_RESUME，
    // 所以进组合时立即复核一次，在屏期间 Activity 回到前台时再复核。
    // 只在“已授权限但未连接”时重绑（ensureListenerBound 内含 30 秒节流），
    // 已连接或未授权限时不做任何事。
    // 前台再推一次当前角标流（Bob 2026-11-08 21:54）：监听已连接时
    // ensureListenerBound 什么也不做；但探针里可能躺着自上次推送后记下
    // 的数（广播在服务断开时到达、或 manifest 接收器记下后尚未合入），
    // 进应用中心时推一次，把已记下的探针数合进去。推数是读数重算、
    // 不 toggle 组件，不共用重绑的 30 秒节流；触发点是进组合与回到
    // 前台（用户动作），不是状态循环，不另加节流。未连接时无-op。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        ensureListenerBound(context)
        LauncherNotificationService.requestBadgeRecompute()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ensureListenerBound(context)
                LauncherNotificationService.requestBadgeRecompute()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 监听翻 true 补推一次（Bob 2026-11-09 12:20 ①：版本升级后角标要等下一条
    // 通知才更新——进组合时那次同步重算撞上服务还没回来（instance 为 null 时
    // 空操作），重绑是异步 toggle；监听翻成 true 说明重绑落地了，这时再推
    // 一次，把在用通知/短信/探针/粘性数合进角标。只在 false→true 跳变时推
    // 一次（drop(1) 跳过进组合时的初值，已连接时那次同步推已覆盖；推数只改
    // badgeCounts、不改 isConnected，不会自激）。与 DisposableEffect 里的
    // 同步推互补，不共用重绑的 30 秒节流。
    LaunchedEffect(Unit) {
        snapshotFlow { NotificationRepository.isConnected.value }
            .drop(1)
            .filter { it }
            .collect { LauncherNotificationService.requestBadgeRecompute() }
    }
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
    // v0.41.34（Bob）：Dock 分区拖动排序——本地顺序状态，拖动时实时更新，松手保存。
    // VerifyError 修复：此状态须留在 AppCenterContent——LazyListScope 段函数不是
    // @Composable 语境，不能持有 remember/LaunchedEffect，故不在 Dock 段内。
    val dockOrder = remember { mutableStateListOf<AppInfo>().apply { addAll(dockApps) } }
    var draggingDockPkg by remember { mutableStateOf<String?>(null) }
    var dockDragOffsetY by remember { mutableStateOf(0f) }
    LaunchedEffect(dockApps) {
        if (draggingDockPkg == null) {
            dockOrder.clear()
            dockOrder.addAll(dockApps)
        }
    }
    // Dock 分区占位：行(N) + 间距(1)；为空时不显示
    // v0.41.32（Bob）：幽灵 ★ 与第一行同行，不再单独占一行
    val dockSectionSize = if (dockApps.isNotEmpty()) dockApps.size + 1 else 0

    // v0.22：分类区取消（等需要时再重新设计），只保留 A-Z

    // A-Z 数据：全部可启动应用（去自己、去隐藏），中文按拼音首字母排序分组。
    // v0.68.95（F-04）：直接读仓库快照的可见集（IO 构建、口径与原组合期实现
    // 逐字一致：去自己/去隐藏 + Collator(CHINA) 排序）；装卸时快照在后台换代，
    // 结果到了才换表，组合期不再整表同步重建。
    val azApps = catalog?.visibleApps ?: emptyList()
    // 未读组（Bob 2026-11-07 晚）：角标 > 0 的应用排到 A 前面最优先显示。
    // badgeCounts 是角标计数口径（451：三路最大值），本批只读它，不改计数。
    val badgeCounts by NotificationRepository.badgeCounts.collectAsState()
    val unreadApps = remember(azApps, badgeCounts) { deriveUnreadApps(azApps, badgeCounts) }
    val unreadMemberPkgs = remember(unreadApps) { unreadApps.map { it.packageName }.toSet() }
    val unreadVisible = unreadApps.isNotEmpty()
    // 未读组打磨（Bob 2026-11-08 08:02 反馈①）：
    // - 每包最新一条在用通知（完整列表，不截 20 条），供预览行 + 左滑启用判定；
    // - 短信包（默认 + 可见）用于识别短信应用（预览取未读短信正文）；
    // - 短信预览正文：IO 线程读最新一条未读短信（只读，不存），短信角标变了就重读。
    val latestActiveByPackage by NotificationRepository.latestActiveByPackage.collectAsState()
    val activeNotifPkgs = remember(latestActiveByPackage) { latestActiveByPackage.keys }
    val smsPackages = remember(context) {
        val def = try { Telephony.Sms.getDefaultSmsPackage(context) } catch (_: Exception) { null }
        listOfNotNull(def, SmsRepository.resolveVisibleSmsPackage(context)).toSet()
    }
    val smsAppInUnread = remember(unreadApps, smsPackages) {
        unreadApps.any { it.packageName in smsPackages }
    }
    val smsBadge = remember(badgeCounts, smsPackages) {
        smsPackages.sumOf { badgeCounts[it] ?: 0 }
    }
    var smsPreviewBody by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(smsAppInUnread, smsBadge) {
        if (!smsAppInUnread) {
            smsPreviewBody = null
            return@LaunchedEffect
        }
        smsPreviewBody = withContext(Dispatchers.IO) {
            SmsRepository.latestUnreadSmsBody(context)
        }
    }
    // 未读组每行文案（Bob 2026-11-08 22:45：行名取最新通知标题、预览只留正文；
    // 竖屏 + 横屏共用同一份）。rowTexts 全覆盖未读组；previews 只含带预览的包，
    // displayNames 全覆盖（无标题时即应用名）。
    val unreadRowTexts = remember(unreadApps, latestActiveByPackage, smsPackages, smsPreviewBody) {
        unreadApps.associate { app ->
            val isSms = app.packageName in smsPackages
            app.packageName to unreadRowText(
                appLabel = app.label.toString(),
                latest = latestActiveByPackage[app.packageName],
                smsBody = if (isSms) smsPreviewBody else null,
                isSmsApp = isSms
            )
        }
    }
    val unreadPreviews = remember(unreadRowTexts) {
        unreadRowTexts.mapNotNull { (pkg, row) -> row.preview?.let { pkg to it } }.toMap()
    }
    val unreadDisplayNames = remember(unreadRowTexts) {
        unreadRowTexts.mapValues { (_, row) -> row.displayName }
    }
    // 未读组左滑清理：取消该包的在用通知（走通知监听服务），角标随之重算。
    val onClearUnreadNotifications: (String) -> Unit = { pkg ->
        LauncherNotificationService.cancelNotificationsForPackage(pkg)
    }
    // 折叠组（419）：不活跃集——可见 = azApps（已去隐藏、去自己）；共享规则
    // （AppUsageTracker.inactiveMembers：系统 UsageStats 优先，没授权退回
    // 自己记的＋首次见到宽限；固定位永远不进）。inactiveTick 由 launchApp
    // 在真实启动时 +1，逼出重组（否则回应用中心时 N 不刷新）。
    // 一键隐藏按钮的 N 与折叠组成员同源（同一规则、同一集合）。
    var inactiveTick by remember { mutableIntStateOf(0) }
    val inactiveTargets = remember(azApps, dockTick, inactiveTick, unreadMemberPkgs) {
        // 未读组：有角标的应用不进折叠组——未读消息即不算“不常用”，
        // 只在未读组出现（与进组后从字母组移出同一做法）。
        withoutPkgs(AppUsageTracker.inactiveMembers(context, azApps), unreadMemberPkgs)
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
    val azGroups: List<Pair<Char, List<AppInfo>>> = remember(azApps, foldMemberPkgs, unreadMemberPkgs) {
        // 拼音首字母分组：用字典（20924 字），GB2312 区位码做兜底
        val pinyinOf: (Char) -> Char = { c ->
            val fromDict = AppSearchIndex.pinyinInitialOf(context, c)
            if (fromDict != '#') fromDict else pinyinInitial(c)
        }
        // 进折叠组 / 未读组的应用从字母组移出（只在一处出现）；某字母
        // 的应用全被移走时该字母组变空、rail 字母自动消失（与折叠组一致）。
        val rest = if (foldMemberPkgs.isEmpty() && unreadMemberPkgs.isEmpty()) azApps
            else azApps.filter { it.packageName !in foldMemberPkgs && it.packageName !in unreadMemberPkgs }
        groupAppsByLetter(rest) { app -> groupKey(app.label, pinyinOf) }
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
    // 反馈闭环（Bob 2026-11-06：识别不到时，让用户重试/选应用）：
    // 空态“有应用能打开，重试”→点一下大模型再确认一次→仍认不出就选应用。
    // mappingVersion：用户教的映射存一次 +1，挂进搜索 remember 的 key，
    // 存完同一句立即重算（映射提到最前面）。
    val mappingVersion by UserIntentMapping.version.collectAsState()
    var retryLoading by remember { mutableStateOf(false) }
    var llmRetryResults by remember { mutableStateOf<List<AppInfo>?>(null) }
    var showChooser by remember { mutableStateOf(false) }
    var chooserApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var lastLlmCapabilityId by remember { mutableStateOf<String?>(null) }
    // 443（Bob 2026-11-07 下午）：大模型认出意图但本机没装对应应用时，
    // 空态出应用市场指引，搜索词存在这里（null = 不出指引）。
    var marketGuideTerm by remember { mutableStateOf<String?>(null) }
    // 换句就清掉重试态（LLM 结果/选择器/加载态/市场指引都只属于上一句）。
    LaunchedEffect(filterQuery) {
        llmRetryResults = null
        showChooser = false
        retryLoading = false
        lastLlmCapabilityId = null
        marketGuideTerm = null
    }
    val searchResults = remember(filterQuery, searchIndex, mappingVersion) {
        if (filterQuery.trim().isBlank()) null
        else AppSearchIndex.search(context, filterQuery, searchIndex)
    }
    // LLM 重试认出意图后，用这份结果盖掉本地搜索结果（用户再点行仍走
    // launchApp，深链照常带；换句即清，见上）。
    val displayResults = llmRetryResults ?: searchResults
    // 内容深链标识（Bob 2026-11-05 晚）：当前这句查询下，点按会走深链直达
    // 内容的应用包名集合。复用 CapabilityResolution.supportsDeepLink 的
    // 同一判定（tapSteps 链里含 Deeplink 步即支持），每句查询只算一次；
    // 竖屏搜索行与横屏共用同一集合（横屏经参数传入，不另算）。查询为空、
    // 无结果、无能力词、无内容时集合为空，标识自然不出现。
    // 反馈闭环：按 displayResults 算（LLM 重试结果也要标“直达”）。
    val deepLinkSupportedPkgs = remember(filterQuery, displayResults) {
        val q = filterQuery.trim()
        if (q.isEmpty() || displayResults.isNullOrEmpty()) emptySet()
        else {
            CapabilityRegistry.load(context)
            val caps = CapabilityRegistry.groups().flatMap { it.capabilities }
            displayResults.mapNotNull { app ->
                if (CapabilityResolution.supportsDeepLink(caps, q, app.packageName)) app.packageName else null
            }.toSet()
        }
    }
    // 反馈闭环·重试提示是否出现（Bob 2026-11-06）：只剩空态本身——
    // 索引已就绪 + 查询非空 + 搜索 0 结果。
    // 442 通用口径（Bob 2026-11-07 中午真机报“玩游戏”0 条却没提示）：
    // 不再用词表预判像不像一句意图——441 的 looksLikeIntent 是 18 个标记
    // 的硬编码词表，“玩”不在表里就漏，词表就是场景清单，补不完。
    // 意图判断交给点按后的大模型（LlmRouter.route）。代价：搜一个不存在
    // 的应用名也会出现提示，点它仍有回收价值（大模型尝试解析，实在不行
    // 选择器里能直接点到应用）。
    val showRetryPrompt = remember(filterQuery, searchResults, catalog) {
        val q = filterQuery.trim()
        catalog != null && q.isNotEmpty() && searchResults?.isEmpty() == true
    }
    // 字母 → LazyColumn item index（每字母：N 行 + 1 组间呼吸间距）
    // v0.41.8（Bob）：字母不再独占一行（浪费纵向空间），回到每组首行行内幽灵字母；
    // 吸顶仍由悬停 overlay 实现
    // Bob 2026-11-03 回滚：锚点回 409 加过滤前——只按完整列表算
    // （Dock 分区在前、每组＋1 组间呼吸），不再跟搜索结果走
    // 前移到搜索 effect 之前：取消输入态的落点（下）要读它
    // 未读组段占位：N 行＋组间呼吸 1 项（始终展开、无组底行；464 起组头
    // 已删，不再占 1 项）；N==0 时不占位。未读段在 Dock 分区之后、A 组之前，
    // 字母锚点随之后移，折叠/隐藏段锚点亦然。
    val unreadSectionSize = if (unreadVisible) unreadApps.size + 1 else 0
    // 未读首行 index（464 起组头已删：此前是组头 index，值不变，仍是
    // Dock 分区之后第一项，现指向未读第一行；搜索取消/默认进入/rail
    // 信封的落点都是它）。
    val unreadFirstIndex = dockSectionSize
    val letterAnchors = remember(azGroups, dockSectionSize, unreadSectionSize) {
        val m = mutableMapOf<Char, Int>()
        var idx = dockSectionSize + unreadSectionSize // v0.41.31：Dock 分区在前，字母锚点后移；未读段又占一段
        azGroups.forEach { (letter, apps) ->
            m[letter] = idx
            idx += apps.size + 1
        }
        m
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
            // 取消输入态后统一落到列表顶（Bob 2026-11-05）：不再恢复进入前
            // 位置。进入前位置常是 0——首帧 catalog 为空时默认定位到 A
            // 的一次滚动被空耗（见下 dockInitialScrollDone），列表停在
            // 收藏区，savedListPosition 记下 0，恢复即落到收藏。
            // 未读组在则落未读组、否则落 A（437 的“落 A”改成“第一组”）。
            // A 组没有时退到首个字母组，再没有才退回进入前位置。
            val firstAnchor = if (unreadVisible) unreadFirstIndex
                else letterAnchors['A']
                    ?: letterAnchors[letters.firstOrNull()]
                    ?: savedListPosition
            listState.scrollToItem(firstAnchor)
        }
    }

    val nested = rememberPullDownConnection(listState, onPullDownState.value)

    // v0.41.31：隐藏分区第一个 item 的绝对 index（供 rail 眼睛按钮跳转）
    // v0.41.32：隐藏头已并入第一行，index 不变
    val azTotalItems = remember(azGroups) { azGroups.sumOf { it.second.size + 1 } }
    // 折叠组（421 安静版）：段占位——组头 1 项＋（展开时 N 行＋组底行 1）＋
    // 组间呼吸 1 项；收起 2 项、展开 3＋N 项；压制或 N==0 时不占位。
    // 隐藏段锚点随之移位。
    val foldSectionSize = if (foldVisible) (if (foldExpandedState) 3 + foldMembers.size else 2) else 0
    val foldHeaderIndex = dockSectionSize + unreadSectionSize + azTotalItems
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

    // v0.41.8：手动吸顶状态见 rememberAppCenterStuckState（AppCenterSections.kt）。
    val stuckState = rememberAppCenterStuckState(
        listState = listState,
        azGroups = azGroups,
        letters = letters,
        letterAnchors = letterAnchors,
        foldVisible = foldVisible,
        foldHeaderIndex = foldHeaderIndex,
        hiddenApps = hiddenApps,
        hiddenUnlocked = hiddenUnlocked,
        dockApps = dockApps,
        unreadVisible = unreadVisible,
        unreadFirstIndex = unreadFirstIndex,
        reduceMotion = reduceMotion,
        density = density
    )
    // v0.41.31（Bob）：打开应用中心默认从 A（首个字母组）开始，不显示 Dock 分区；
    // 除非用户点 rail ★ 才去看 Dock
    var dockInitialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(letterAnchors, unreadVisible) {
        if (!dockInitialScrollDone && (letters.firstOrNull() != null || unreadVisible)) {
            // 只有字母组就绪时才消耗一次定位；首帧 catalog 为空（快照在
            // IO 构建，AppCatalog.snapshot 初值 null）时不消耗，等 catalog
            // 到位、letterAnchors 变化后再定位——否则一次定位被空耗，
            // 列表停在收藏区（item 0），搜索开始时 savedListPosition 记下 0。
            // 打开应用中心默认落在列表顶：未读组在则未读组、否则 A。
            dockInitialScrollDone = true
            val firstAnchor = if (unreadVisible) unreadFirstIndex
                else letterAnchors[letters.firstOrNull()] ?: 0
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
    // v0.25.2：松手滚动——等列表恢复全量重组完成后再滚，避免打在旧内容上
    // 反馈闭环：返回是否真的打开了（选择器点选后要凭它决定上报 outcome）。
    // 其余调用点仍当 Unit 用（Kotlin 允许 Boolean 函数赋给 () -> Unit 的
    // 形参，点按行为不变）。
    fun launchApp(app: AppInfo): Boolean {
        val now = System.currentTimeMillis()
        val last = lastLaunch
        if (last != null && last.first == app.packageName && now - last.second < 500L) return false
        lastLaunch = app.packageName to now
        focusManager.clearFocus()
        keyboardController?.hide()
        AppUsageTracker.recordLaunch(context, app.packageName)
        inactiveTick++
        // 432（Bob 2026-11-05：点搜索结果要把目的地带进地图）：整句含能力
        // 关键字＋目的地、且被点应用是该能力的提供方时，走该家深链（深链→
        // 定向 geo→打开应用）；建不出（没目的地/没模板/缺参）时返回 false，
        // 退回下面直接打开，绝不打开残缺链接。横屏经 onLaunch 共用同一入口。
        val query = queryField.text.trim()
        val opened = if (query.isNotEmpty() &&
            CapabilityRegistry.launchTappedApp(context, query, app.packageName)
        ) {
            true
        } else {
            try {
                val intent = context.packageManager
                    .getLaunchIntentForPackage(app.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                if (intent != null) {
                    context.startActivity(intent)
                    true
                } else false
            } catch (_: Exception) {
                IslandToasts.show("无法打开「${app.label}」")
                false
            }
        }
        // 智能滚动关键字：能力动作成功打开应用才记（点按深链/定向 geo/
        // 直接打开任一成功都算；失败、或点的不是该能力提供方时不记）。
        ActionUsageTracker.recordTapAction(context, query, app.packageName, opened)
        // 角标粘性数（Bob 2026-11-08）：在我们桌面里点图标启动应用 = 去读消息了，
        // 成功打开才清掉该包粘性数；打开失败不清（用户没读到）。
        // 探针数同款清掉（Bob 2026-11-09 12:20 ①：启动=去读消息了，广播自报
        // 的未读数已过期；不清的话取消通知后角标四路里探针那路还算着）。
        if (opened) {
            BadgeStickyStore.clearForPackage(context, app.packageName)
            BadgeProbe.clearForPackage(context, app.packageName)
        }
        return opened
    }

    // 未读行点按的“打开”（Bob 2026-11-08 23:49：点未读行只打开应用主界面，
    // 没有精准落到那条通知消息的目标地址）：优先发该应用最新一条在用通知
    // 自己的 contentIntent（通知自带的 PendingIntent，点通知栏通知时系统
    // 就是发它、直接进对应会话/消息——微信进“和和美美”会话、飞书进 Liz Li
    // 那条消息）；没有 contentIntent、或发送失败（通知已被撤、
    // PendingIntent 已取消）时，退回 launchApp 正常打开应用。
    // 核实（见收据）：contentIntent 不用补存——SimpleNotification 早有
    // contentIntent 字段（v0.60.0），监听服务从真实通知里带过来
    // （LauncherNotificationService.kt:360），latestActiveByPackage 里
    // 的就是行名用的同一条最新通知；发送写法照抄智能卡片线已验证的
    // SmartCardActions.performAction（OpenNotification 分支：send() +
    // try/catch 返回 Boolean）。
    // 发送本身不会自动取消通知（系统点通知栏时是系统顺手取消），取消仍由
    // 调用方（未读行点按的另一半）显式走 onClear，与这里无关、不要省。
    // 短信行：短信没有通知跳转目标（不在 latestActiveByPackage 里），
    // contentIntent 为 null，直接走 launchApp 只打开应用。
    fun openUnreadApp(app: AppInfo): Boolean {
        val now = System.currentTimeMillis()
        val last = lastLaunch
        if (last != null && last.first == app.packageName && now - last.second < 500L) return false
        val tapUptimeMillis = SystemClock.uptimeMillis()
        val contentIntent =
            latestActiveByPackage[app.packageName]?.contentIntent
        var sendError: String? = null
        if (contentIntent != null) {
            val sent = try {
                contentIntent.send()
                true
            } catch (e: Exception) {
                sendError = e.javaClass.simpleName
                false
            }
            if (sent) {
                // 成功打开后的记账与 launchApp 成功分支同款（去重标记、收焦点
                // 藏键盘、记一次启动、粘性数清掉——不清粘性数的话，取消通知后
                // 角标五路里粘性数那路还算着，行离不开未读组）。
                // 智能滚动关键字不记：未读组里 query 为空，本来也记不到动作。
                lastLaunch = app.packageName to now
                focusManager.clearFocus()
                keyboardController?.hide()
                AppUsageTracker.recordLaunch(context, app.packageName)
                inactiveTick++
                BadgeStickyStore.clearForPackage(context, app.packageName)
                BadgeProbe.clearForPackage(context, app.packageName)
                // 前台核验回退（Bob 2026-11-11 09:53：Google 健康行点了没
                // 打开——contentIntent.send() 不抛错也可能静默无动作；
                // 2 秒内没见到目标到过前台就回退 launchApp 直接打开。
                // 没权限时无法核验、不排回退；行靠取消那半的 UNKNOWN
                // 不取消留着，用户可从字母组/Dock 打开。双框风险见收据。
                val fallbackPath = if (hasUsageStatsPermission(context)) {
                    ForegroundConfirm.scheduleFallbackIfNotForeground(
                        context.applicationContext,
                        app.packageName,
                        tapWallMillis = now
                    ) {
                        launchApp(app)
                    }
                    "foreground"
                } else {
                    // 门闩回退（Bob 2026-11-11 10:52 同类第二例：微信
                    // 「和和美美」行点了没打开、行也没了；Bob 10:33 定了
                    // 先不申请权限，上面那件回退在他手机上休眠）：没权限
                    // 时不查 UsageStats，改看我们自己有没有切到后台——
                    // 跳成功了我们的 Activity 一定 onPause，静默丢了就
                    // 还停在前台。点按后约 0.8 秒检查一次，窗内一次都没切
                    // 后台就回退 launchApp 直接打开（只打开应用主界面，
                    // 落不到那条通知的目标会话，那是 contentIntent 才有
                    // 的精准落点）。一次点按只走一条回退路：有权限走上面
                    // 那条，没权限走这条，不会双排。
                    // 取消那半不动：回退只管打开，不管取消（没权限时取消
                    // 本来就不执行；回退打开后行还留在未读组，用户可左滑
                    // 清，应用自己撤通知后推数也会把行带走）。
                    AppBackgroundLatch.scheduleFallbackIfNotBackgrounded(
                        app.packageName,
                        tapUptimeMillis,
                        tapWallMillis = now,
                        appContext = context.applicationContext
                    ) {
                        launchApp(app)
                    }
                    "latch"
                }
                UnreadJumpLog.recordTap(
                    context.applicationContext,
                    tapWallMillis = now,
                    pkg = app.packageName,
                    hasContentIntent = true,
                    path = "content_intent",
                    sendResult = "ok",
                    launchOk = null,
                    fallbackPath = fallbackPath,
                )
                return true
            }
        }
        // contentIntent 为空或 send() 抛错：退回 launchApp 直接打开，
        // 把结果也记下来（sendResult = no_intent / error:<异常类名>）。
        val opened = launchApp(app)
        UnreadJumpLog.recordTap(
            context.applicationContext,
            tapWallMillis = now,
            pkg = app.packageName,
            hasContentIntent = contentIntent != null,
            path = "launch_app",
            sendResult = if (contentIntent != null) "error:$sendError" else "no_intent",
            launchOk = opened,
            fallbackPath = "none",
        )
        return opened
    }

    // 未读行点按的“取消”那一半（Bob 2026-11-09 12:20 ②：点未读行有时只清
    // 消息、不打开应用——contentIntent.send() / startActivity 返回成功不等于
    // 应用真的到了前台，中兴跳转确认框里点取消时应用根本没打开、通知却已经
    // 取消了）：先打开、确认应用真的到了前台再取消。ForegroundConfirm 在主
    // 线程 Handler 上轮询（200ms 一次、最多 1 秒），窗内任一时刻见到目标
    // 应用到过前台就取消（打开后 1 秒内又切走也算，打开过就是读到了）；1 秒
    // 内没见到就不取消，通知留着、行留在未读组（用户可左滑清）；没拿到用量
    // 统计权限（UNKNOWN）时也不取消——无法判定就别动通知（2026-11-11 改，
    // Google 健康行点了没打开、消息却消失的教训）。
    // 注意：不能绑组合作用域——用户点行打开应用后应用中心浮层就没了，作用域
    // 取消会把延迟取消一起杀掉；ForegroundConfirm 用 applicationContext +
    // 主线程 Handler，活过应用中心 dispose。
    fun scheduleUnreadCancelAfterOpen(app: AppInfo) {
        // 取消那半也被寄上日志：expectCancel 登记“等取消结果”，返回这次
        // 点按的墙钟，传给调度器做 outcome 的配对键；判定逻辑本身不动。
        val tapWallMillis = UnreadJumpLog.expectCancel(
            context.applicationContext,
            app.packageName,
        )
        ForegroundConfirm.scheduleCancelIfForeground(
            context.applicationContext,
            app.packageName,
            tapWallMillis = tapWallMillis
        ) {
            onClearUnreadNotifications(app.packageName)
        }
    }

    // ---------- 反馈闭环（Bob 2026-11-06） ----------

    // 选应用：先收键盘、清焦点，sheet 才看得全；已装应用按与查询的相关程度排。
    // 注意顺序：openChooser 定义在 onRetryClick 之前——Kotlin 本地函数不提升，
    // onRetryClick 里调 openChooser，定义在后会报 Unresolved reference。
    fun openChooser() {
        val q = filterQuery.trim()
        if (q.isEmpty()) return
        focusManager.clearFocus()
        keyboardController?.hide()
        chooserApps = AppSearchIndex.rankChooserApps(context, q, searchIndex) { pkg ->
            AppUsageTracker.usageScoreForSearch(context, pkg)
        }
        showChooser = true
    }

    // “有应用能打开，重试”：点一下，大模型再确认一次。直接调
    // LlmRouter.route，同端点、同 Key、同能力白名单；重试留在应用中心
    // 出结果，不离开应用中心。三支分流（MarketGuide.decideLlmRetry）：
    // 认出意图且有已装提供方→列成结果；认出意图但一个都没装→应用市场
    // 指引（443，不进选择器）；仍认不出（"none"/报错/没配 Key）→进选应用。
    fun onRetryClick() {
        if (retryLoading) return
        val q = filterQuery.trim()
        if (q.isEmpty()) return
        retryLoading = true
        scope.launch {
            // 没配 Key 时大模型跑不了，直接进选应用（用户仍可手动选）。
            if (!LlmConfig.hasKey(context)) {
                retryLoading = false
                openChooser()
                return@launch
            }
            CapabilityRegistry.load(context)
            val result = LlmRouter.route(
                LlmConfig.getBaseUrl(context),
                LlmConfig.getApiKey(context),
                LlmConfig.getModel(context),
                q,
                CapabilityRegistry.validIds()
            )
            when (result) {
                is LlmCallResult.Ok -> {
                    val capId = result.route.capabilityId
                    lastLlmCapabilityId = capId.takeIf { it != "none" }
                    if (capId == "none") {
                        retryLoading = false
                        openChooser()
                    } else {
                        val cap = CapabilityRegistry.find(capId)
                        val installedPkgs =
                            searchIndex.map { it.app.packageName }.toSet()
                        when (val outcome =
                            MarketGuide.decideLlmRetry(cap, installedPkgs, q)) {
                            is MarketGuide.LlmRetryOutcome.InstalledApps -> {
                                val apps = outcome.packageNames.mapNotNull { pkg ->
                                    searchIndex.firstOrNull { it.app.packageName == pkg }?.app
                                }
                                // 按使用频次排（同搜索排序口径：频次高的在前），
                                // 声明序作稳定底。只列结果、不自动打开——
                                // 用户再点一行，仍走 launchApp（深链照常带）。
                                llmRetryResults = apps.sortedByDescending {
                                    AppUsageTracker.usageScoreForSearch(context, it.packageName)
                                }
                                marketGuideTerm = null
                                retryLoading = false
                            }
                            is MarketGuide.LlmRetryOutcome.MarketGuide -> {
                                llmRetryResults = null
                                marketGuideTerm = outcome.searchTerm
                                retryLoading = false
                            }
                            MarketGuide.LlmRetryOutcome.ChooseApp -> {
                                retryLoading = false
                                openChooser()
                            }
                        }
                    }
                }
                is LlmCallResult.Err -> {
                    retryLoading = false
                    openChooser()
                }
            }
        }
    }

    // 443：点应用市场指引行——打开应用市场搜相关应用（先试本机商店的
    // market://，没有再回退 Play 网页）；两条都打不开时 toast 一句。
    fun onMarketGuideClick() {
        val term = marketGuideTerm ?: return
        if (!MarketGuide.openMarketSearch(context, term)) {
            IslandToasts.show("打不开应用市场")
        }
    }

    // 用户在选择器里点了一个应用：先存本机映射（以后同句能认出来），
    // 再打开（launchApp 已有的深链/记频次逻辑照常走），最后 best-effort
    // 上报给深链服务——上报在 IO 线程、失败不报错、不影响映射与打开。
    fun onChooserPick(app: AppInfo) {
        val q = filterQuery.trim()
        showChooser = false
        if (q.isEmpty()) return
        val capId = lastLlmCapabilityId
        UserIntentMapping.save(context, q, app.packageName)
        val opened = launchApp(app)
        scope.launch(Dispatchers.IO) {
            DeepLinkServiceReport.reportUserMapping(q, app.packageName, capId, opened)
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

    // 行动作公共参数（AppCenterSections.kt）：各列表段共用同一份。
    val rowActions = AppCenterRowActions(
        popupApp = popupApp,
        onPopupAppChange = { popupApp = it },
        expandedActionsPkg = expandedActionsPkg,
        onExpandedActionsChange = { expandedActionsPkg = it },
        revealedHidePkg = revealedHidePkg,
        onRevealedHideChange = { revealedHidePkg = it },
        hapticView = hapticView,
        context = context,
        onLaunchApp = ::launchApp,
        onOpenUnreadApp = ::openUnreadApp,
        onUnreadRowOpened = ::scheduleUnreadCancelAfterOpen,
        onHideApp = ::hideApp
    )


    Box(
        modifier = modifier
            .fillMaxSize()
            // v0.68.45（走查）：实底纸面——之前透明，主屏大时间透过来盖住列表
            // 430（Bob 2026-11-05 晚亲批纹理方向）：实底纸面换成已批纹理背景
            // （纵向暖灰渐变＋顶部暖光晕＋极淡颗粒，主题感知；仍不透明，0.68.45 的
            // 遮挡口径不变；竖屏横屏同根 Box，一处覆盖两面）。
            .pageTextureBackground()
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
        AppCenterHeaderSection(
            isLandscapeAc = isLandscapeAc,
            catalog = catalog,
            displayResults = displayResults,
            azApps = azApps,
            queryField = queryField,
            onQueryFieldChange = ::onQueryFieldChange,
            // 意图清理（Bob 2026-11-08 23:38）：胶囊提交（键盘 Done）只收键盘，
            // 不再问大模型。
            onCapsuleSubmit = {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
            intentFocused = intentFocused,
            onIntentFocusedChange = { intentFocused = it },
            onIntentBoundsChange = { intentBoundsInRoot = it },
            onMorphButtonBoundsChange = { morphButtonBoundsInRoot = it },
            focusManager = focusManager,
            onOpenSettings = onOpenSettings,
            onPullDownState = onPullDownState,
            onHeaderSwipeRightState = onHeaderSwipeRightState,
            density = density
        )

        // v0.68.62（Bob）：横屏双栏——左索引右网格，竖屏用原单列
        if (isLandscapeAc) {
            AppCenterLandscape(
                azGroups = azGroups,
                letters = letters,
                totalCount = displayResults?.size ?: azApps.size,
                favApps = dockApps,
                hiddenApps = hiddenApps,
                hiddenUnlocked = hiddenUnlocked,
                // 折叠组（419/421）：状态由调用点传入（横屏不另起一套）；
                // foldMembers 已含压制与 N>0 门，横屏直接用。
                foldApps = foldMembers,
                // 未读组：状态由调用点传入（横屏不另起一套）；unreadApps
                // 已按未读数排好序、含 N>0 门，横屏直接用。
                unreadApps = unreadApps,
                // 未读组打磨（Bob 2026-11-08）：预览文案 + 在用通知包 + 左滑清理回调，
                // 横屏与竖屏共用调用点算好的同一份，不另算。
                unreadPreviews = unreadPreviews,
                unreadDisplayNames = unreadDisplayNames,
                activeNotifPkgs = activeNotifPkgs,
                onClearNotifications = onClearUnreadNotifications,
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
                onOpenUnreadApp = ::openUnreadApp,
                onUnreadRowOpened = ::scheduleUnreadCancelAfterOpen,
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
                // 反馈闭环（Bob 2026-11-06）：横屏也用 displayResults
                // （LLM 重试结果要进横屏列表），空态重试提示两向共用。
                searchResults = displayResults,
                // 内容深链标识：竖屏按本句查询算好的支持集合，横屏搜索行
                // 共用（横屏不另算，判定口径与竖屏同一套）。
                deepLinkSupportedPkgs = deepLinkSupportedPkgs,
                onSearchSubmit = {
                    // 意图清理（Bob 2026-11-08 23:38）：横屏胶囊提交（键盘 Done）
                    // 只收键盘，不再问大模型。
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                // 应用中心升级：横屏空结果文案区分"索引加载中"与"真无匹配"
                indexLoading = catalog == null && query.isNotBlank(),
                // 横屏首页修：把主屏 picker 会话状态透传给横屏 rail 门（同竖屏 AppCenter.kt:1419）
                homeTarget = homeTarget,
                // 反馈闭环（Bob 2026-11-06）：横屏空态重试提示——
                // 与竖屏同一状态、同一触发口径、同一行。
                showRetryPrompt = showRetryPrompt,
                retryLoading = retryLoading,
                onRetryClick = ::onRetryClick,
                // 443：横屏空态市场指引——与竖屏同一状态、同一行。
                marketGuideTerm = marketGuideTerm,
                onMarketGuideClick = ::onMarketGuideClick,
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
                val results = displayResults
                if (results != null) {
                    AppCenterSearchResultsSection(
                        results = results,
                        filterQuery = filterQuery,
                        catalog = catalog,
                        marketGuideTerm = marketGuideTerm,
                        onMarketGuideClick = ::onMarketGuideClick,
                        showRetryPrompt = showRetryPrompt,
                        retryLoading = retryLoading,
                        onRetryClick = ::onRetryClick,
                        deepLinkSupportedPkgs = deepLinkSupportedPkgs,
                        actions = rowActions
                    )
                } else {
                AppCenterDockSection(
                    dockApps = dockApps,
                    dockOrder = dockOrder,
                    draggingDockPkg = draggingDockPkg,
                    onDraggingDockPkgChange = { draggingDockPkg = it },
                    dockDragOffsetY = dockDragOffsetY,
                    onDockDragOffsetYChange = { dockDragOffsetY = it },
                    density = density,
                    actions = rowActions
                )
                // A-Z 列表（v0.41.0 B 方案）
                // v0.41.8：每组 = 应用行 + 组间间距（字母不再独占一行，Bob）。
                // 每组首行左侧留白放幽灵字母（正向 padding，不用负 offset）；
                // 首行刚开始滚出就由悬停 overlay 接管（手动吸顶，见 stuckLetter）
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
                AppCenterUnreadSection(
                    unreadApps = unreadApps,
                    unreadPreviews = unreadPreviews,
                    unreadDisplayNames = unreadDisplayNames,
                    activeNotifPkgs = activeNotifPkgs,
                    onClearUnreadNotifications = onClearUnreadNotifications,
                    unreadVisible = unreadVisible,
                    stuckUnread = stuckState.stuckUnread,
                    actions = rowActions
                )
                AppCenterLetterGroupsSection(
                    azGroups = azGroups,
                    stuckLetter = stuckState.stuckLetter,
                    isLandscapeAc = isLandscapeAc,
                    actions = rowActions
                )
                AppCenterFoldSection(
                    foldMembers = foldMembers,
                    foldVisible = foldVisible,
                    foldExpandedState = foldExpandedState,
                    onFoldExpandedChange = { foldExpandedState = it },
                    foldLastDeclinedN = foldLastDeclinedN,
                    onFoldLastDeclinedNChange = { foldLastDeclinedN = it },
                    inactiveTargets = inactiveTargets,
                    actions = rowActions
                )
                AppCenterHiddenSection(
                    hiddenApps = hiddenApps,
                    hiddenUnlocked = hiddenUnlocked,
                    inactiveTargets = inactiveTargets,
                    azApps = azApps,
                    onUnlockHiddenApps = ::unlockHiddenApps,
                    onUnhideApp = ::unhideApp,
                    actions = rowActions
                )
                item { Spacer(modifier = Modifier.height(16.dp)) }
                } // else 非搜索态：分类区 + A-Z 列表
            }
            AppCenterStuckOverlays(
                stuckState = stuckState,
                searching = searching,
                hiddenUnlocked = hiddenUnlocked,
                isLandscapeAc = isLandscapeAc
            )
            AppCenterRailSection(
                listState = listState,
                letters = letters,
                letterAnchors = letterAnchors,
                dockApps = dockApps,
                hiddenApps = hiddenApps,
                foldVisible = foldVisible,
                unreadVisible = unreadVisible,
                foldHeaderIndex = foldHeaderIndex,
                hiddenHeaderIndex = hiddenHeaderIndex,
                unreadFirstIndex = unreadFirstIndex,
                homeTarget = homeTarget,
                searchResults = searchResults,
                flashTick = flashTick,
                flashTarget = flashTarget,
                scope = scope,
                hapticView = hapticView,
                hiddenUnlocked = hiddenUnlocked,
                onHeaderSwipeRight = { onHeaderSwipeRightState.value() },
                onUnlockHiddenApps = ::unlockHiddenApps
            )
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

    // 反馈闭环（Bob 2026-11-06）：大模型仍认不出时，让用户选应用——
    // 标题就是 Bob 给的话“这个场景要用什么应用去响应？”。
    // sheet 挂在 AppCenterContent 层（不挂竖屏/横屏分支），两向共用；
    // 样式跟 idea 说明 sheet（PullUpDock）同一套：Card 底、拖拽柄、横向 20dp。
    if (showChooser) {
        AppCenterChooserSheet(
            chooserApps = chooserApps,
            onChooserPick = ::onChooserPick,
            onDismiss = { showChooser = false }
        )
    }
}

/**
 * 443·应用市场指引行（Bob 2026-11-07 下午：大模型认出意图、但本机没装
 * 对应应用时，指引用户去应用市场自己安装）。
 * 样式跟 FeedbackRetryRow 同一套（Footnote、Title 75%、Medium、金色 16dp
 * 箭头、48dp 最小触控高），竖屏/横屏共用；文案只用用户听得懂的话，
 * 不用“能力/提供方/映射”这类内部词。搜索词过长时一行省略。
 */
@Composable
internal fun MarketGuideRow(
    term: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "去应用市场搜「$term」",
            fontSize = AILauncherType.Footnote.fontSize,
            color = AILauncherColors.current.Title.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = AILauncherColors.current.Accent,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 反馈闭环·重试提示行（Bob 2026-11-06：空态“有应用能打开，重试”）。
 * 样式（Footnote、Title 75%、Medium、金色 16dp 箭头、48dp 最小触控高），
 * 竖屏/横屏共用。空态文案是居中的，这里也居中（这里是空态动作）。
 * 加载中显示“正在确认…”，不可点。
 */
@Composable
internal fun FeedbackRetryRow(
    loading: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = !loading) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (loading) "正在确认…" else "有应用能打开，重试",
            fontSize = AILauncherType.Footnote.fontSize,
            color = AILauncherColors.current.Title.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium
        )
        if (!loading) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AILauncherColors.current.Accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
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





