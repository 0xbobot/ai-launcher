package com.bobot.ailauncher.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bobot.ailauncher.ui.theme.confirmHaptic
import com.bobot.ailauncher.ui.theme.tickHaptic
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bobot.ailauncher.ui.island.IslandToastHost
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.apps.DockState
import com.bobot.ailauncher.ui.apps.AppRail
import com.bobot.ailauncher.ui.apps.PullUpDock
import com.bobot.ailauncher.ui.glass.GlassBackdropState
import com.bobot.ailauncher.ui.glass.LocalGlassBackdrop
import com.bobot.ailauncher.ui.glass.SystemWallpaperLayer
import com.bobot.ailauncher.ui.glass.glassBackdropSource
import com.bobot.ailauncher.ui.glass.refreshGlassSignals
import com.bobot.ailauncher.ui.apps.RailItem
import com.bobot.ailauncher.ui.apps.WaveRail
import com.bobot.ailauncher.ui.components.UpdateDialog
import com.bobot.ailauncher.ui.home.BlindCaptureController
import com.bobot.ailauncher.ui.home.BlindCaptureFan
import com.bobot.ailauncher.ui.home.BlindUiState
import com.bobot.ailauncher.ui.home.FanSegment
import com.bobot.ailauncher.ui.home.MAX_QUICK_SEGS
import com.bobot.ailauncher.ui.pet.QuickActionAddFlow
import com.bobot.ailauncher.ui.pet.QuickActionsPrefs
import com.bobot.ailauncher.ui.home.HomeScreen
import com.bobot.ailauncher.ui.settings.SettingsScreen
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * v0.14 导航：单页桌面（能力页已移除，左滑/右滑只剩卡片手势语义：左=多/右=少）。
 * - 只有首页；上拉 Dock（D1/D2/D3）是找应用的唯一入口
 * - v0.16：首页上滑直达 D3「应用中心」，不再经 D1；隐藏的唯一入口是 D1 右滑+确认框
 * - NavHost 只剩 "home" 和 "settings"（应用中心的齿轮进入）
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()
    var dockVisible by remember { mutableStateOf(UiPrefs.getDockVisible(context)) }
    // v0.15.1：Dock 默认显示记住的行数（默认 D1 一行），不再默认隐藏
    var dockState by remember {
        mutableStateOf(if (UiPrefs.getDockRows(context) == 2) DockState.D2 else DockState.D1)
    }
    // v0.68.50：深色模式随系统主题；批B ③：调色板由 AILauncherTheme 经
    // CompositionLocal 提供，本处原组合期写全局 isDark 已取消

    // v0.26.6：返回键处理——D3（应用中心）返回主页，首页不做事
    // 用 BackHandler 拦截，避免系统默认行为导致页面刷新/重建
    // v0.68.51（Bob）：只在 home 路由启用——设置页用系统默认返回（popBackStack）
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    androidx.activity.compose.BackHandler(enabled = currentRoute == "home") {
        if (dockState == DockState.D3) {
            // 应用中心返回主页
            val target = if (UiPrefs.getDockRows(context) == 2) DockState.D2 else DockState.D1
            dockState = target
        }
        // 首页：不做事（消费掉返回键）
    }
    var showHideDockDialog by remember { mutableStateOf(false) }

    // v0.15.1：Dock 状态机统一出口
    // - 记住行数：D1 左滑→D2 时记 2，D2 右滑→D1 时记 1
    // - 隐藏上滑唤出 / D3 右滑返回 → 直接回到记住的行数（D1/D2）
    // - v0.16：隐藏的唯一入口是 D1 右滑（经 requestHideDock 确认）；D1 下滑/点按外部不再隐藏
    // （v0.12 的首次 peek 引导已删除：默认显示，不需要了）
    fun goDock(requested: DockState, fromD3SwipeRight: Boolean = false) {
        val cur = dockState
        val remembered =
            if (UiPrefs.getDockRows(context) == 2) DockState.D2 else DockState.D1
        var target = requested
        if (fromD3SwipeRight && cur == DockState.D3) target = remembered
        if (cur == DockState.Hidden && target == DockState.D1) target = remembered
        if (cur == DockState.D1 && target == DockState.D2) UiPrefs.setDockRows(context, 2)
        if (cur == DockState.D2 && target == DockState.D1) UiPrefs.setDockRows(context, 1)
        dockState = target
    }

    fun doHideDock() {
        UiPrefs.setDockVisible(context, false)
        dockVisible = false
        dockState = DockState.Hidden
    }

    fun requestHideDock() {
        if (UiPrefs.getDockHideWarned(context)) doHideDock()
        else showHideDockDialog = true
    }

    // v0.16：设置页"显示 Dock"开关 → 恢复到记住的行数
    fun setDockVisibleAndRestore(visible: Boolean) {
        UiPrefs.setDockVisible(context, visible)
        dockVisible = visible
        if (visible) {
            goDock(if (UiPrefs.getDockRows(context) == 2) DockState.D2 else DockState.D1)
        } else {
            dockState = DockState.Hidden
        }
    }

    // v0.16：上滑直达 D3 不受 dockVisible 影响（否则隐藏 Dock 后将无法进入应用中心）；
    // 上滑不改变 Dock 显示/隐藏状态
    val effectiveDockState = if (dockVisible || dockState == DockState.D3) dockState
    else DockState.Hidden

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeHost(
                dockState = effectiveDockState,
                // v0.57.0：Dock 隐藏时上滑 → 先叫回 Dock；否则上滑开 D3
                onOpenAppDrawer = {
                    if (!dockVisible) setDockVisibleAndRestore(true)
                    else goDock(DockState.D3)
                },
                onStateChange = { goDock(it) },
                onD3SwipeRight = { goDock(DockState.D2, fromD3SwipeRight = true) },
                // v0.57.0：D3 上滑回主页——Dock 没隐藏时回记住的行数，隐藏时保持隐藏
                onD3SwipeUpToHome = {
                    if (dockVisible) {
                        val remembered =
                            if (UiPrefs.getDockRows(context) == 2) DockState.D2 else DockState.D1
                        goDock(remembered)
                    } else {
                        dockState = DockState.Hidden
                    }
                },
                onHideDockRequest = { requestHideDock() },
                onOpenSettings = { navController.navigate("settings") },
                showHideDockDialog = showHideDockDialog,
                onHideDockDialogDismiss = { showHideDockDialog = false },
                onHideDockConfirm = { noRemind ->
                    if (noRemind) UiPrefs.setDockHideWarned(context, true)
                    showHideDockDialog = false
                    doHideDock()
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
    // v0.68.51（Bob）：灵动岛 Toast 宿主——移到 NavHost 外，最顶层
    // 之前在 home 路由内，切到设置页时宿主被 dispose，toast 看不到
    IslandToastHost()
    }
}

@Composable
private fun HomeHost(
    dockState: DockState,
    onOpenAppDrawer: () -> Unit,
    onStateChange: (DockState) -> Unit,
    onD3SwipeRight: () -> Unit,
    onD3SwipeUpToHome: () -> Unit,
    onHideDockRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    showHideDockDialog: Boolean,
    onHideDockDialogDismiss: () -> Unit,
    onHideDockConfirm: (Boolean) -> Unit
) {
    val context = LocalContext.current
    // R2 UI（10-21 新规格）：意图协调器提到 HomeHost 持有——首页胶囊与应用中心「问七仔」
    // 共用同一协调器，状态只有一个真源；HomeScreen 只拿状态渲染
    val intentCoordinator = remember { com.bobot.ailauncher.core.intent.IntentCoordinatorFactory.create(context) }
    val intentState by intentCoordinator.state.collectAsState()
    val intentScope = rememberCoroutineScope()
    // 意图改进一批·防重：宿主保留在途 Job——新提交先 cancel 旧 Job 再 launch
    // （顶替语义）；被顶替的旧提交在协调器内交还编辑态，不会卡死 Planning。
    // 与协调器 submit 入口的 Planning 守卫叠加：连点不会重复规划/重复执行。
    var intentJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun submitIntent(text: String) {
        intentJob?.cancel()
        intentJob = intentScope.launch { intentCoordinator.submit(text) }
    }
    // v0.68.98（F-06）：Planning 态「取消」——先让协调器作废在途规划的落地权
    // 并收回 Idle（结果条随之收起），再 cancel Job 本体；顺序反过来，Job 的
    // CancellationException 分支会抢先 dismiss 回 Editing，与取消语义不符。
    fun cancelIntentPlanning() {
        intentCoordinator.cancelPlanning()
        intentJob?.cancel()
        intentJob = null
    }
    // v0.68.99（主页重构 R-1）：输入态开关——静态主页无胶囊；点 Dock 七仔格 /
    // 简报讲述者七仔进入（要打字，聚焦）；协调器进入在场状态（思考 / 回复 /
    // 待确认 / 缺权限 / 追问）时自动带起呈现（不聚焦、不弹键盘，如应用中心
    // 「问七仔」提交后回首页看结果）。开 D3 抽屉即退出（见 HomeScreen 调用点）。
    var intentInputMode by remember { mutableStateOf(false) }
    var intentFocusWanted by remember { mutableStateOf(false) }
    LaunchedEffect(intentState) {
        if (com.bobot.ailauncher.ui.home.intentStateHoldsInput(intentState)) {
            intentInputMode = true
        }
    }
    fun enterIntentInput(focus: Boolean) {
        // 已在输入态时忽略（讲述者七仔在输入态下也可点，避免聚焦意图被重入改写）
        if (intentInputMode) return
        intentFocusWanted = focus
        intentInputMode = true
    }
    // 应用中心「问七仔」：先回首页（离 D3），再把整句交给协调器理解
    val onAskQizai: (String) -> Unit = { text ->
        onD3SwipeUpToHome()
        submitIntent(text)
    }
    // v0.64.2：更新弹窗走共享流（自动检查 / TODAY 卡片"有新版本"行点击）
    val updatePrompt by OtaUpdater.updatePrompt.collectAsState()

    // OTA：每天最多自动检查一次，有新版只在 TODAY 卡片底行提醒（v0.67.1：不再自动弹模态对话框，
    // 产品原则：重要信息不用模态弹窗；用户点卡片"有新版本"行才弹 UpdateDialog）
    LaunchedEffect(Unit) {        if (OtaUpdater.shouldAutoCheck(context)) {
            val info = OtaUpdater.checkForUpdate(context)
            OtaUpdater.markChecked(context)
            if (info != null) {
                OtaUpdater.latestAvailable.value = info
            }
        }
    }

    // v0.15 宠物整理员：toast 事件 → 系统 Toast
    LaunchedEffect(Unit) {
        PetRepository.toast.collect { msg ->
            IslandToasts.show(msg)
        }
    }
    // v0.68.6：盲操激活中（上报自 HomeScreen）——选择中时压住右边缘字母快找区，
    // 避免长按（~400ms）与盲操 dwell（120ms）在重叠区双重触发
    var blindActive by remember { mutableStateOf(false) }
    // v0.68.32：扇形全状态（上报自 HomeScreen）——扇形在最顶层绘制，盖住 Dock
    // v0.68.42：Selecting/Dismissing/PhotoFlash 共用顶层
    var blindFanState by remember { mutableStateOf<BlindUiState>(BlindUiState.Hidden) }
    // v0.68.64：controller 引用（HomeScreen 上报；退出编辑态时调 exitEditMode 清钉住）
    var blindCtlRef by remember { mutableStateOf<BlindCaptureController?>(null) }
    // v0.68.35：旧自定义标签已作废——段列表自带于 Selecting.segments
    // v0.68.32：扇形展开时 Dock 淡出到 0.3（视觉让位），收起后恢复
    val dockAlpha by animateFloatAsState(
        targetValue = if (blindActive) 0.3f else 1f,
        animationSpec = tween(200),
        label = "dockAlpha",
    )

    // 玻璃试点批（docs/GLASS_DESIGN.md §9）：背景源 + 玻璃状态宿主。
    // 背景层（内绘系统壁纸 + 罩纱）整体录入图层，供 Dock / 七仔气泡 / TODAY 卡取材；
    // 只在桌面可见（RESUMED 且非 D3 全屏层）时启用真材质，否则各面回半透明模拟（省电原则）；
    // 省电/低电/低内存等降级信号在每次 onResume 时刷新（判定逻辑在 GlassPolicy，可单测）。
    val glassState = remember { GlassBackdropState() }
    glassState.layer = rememberGraphicsLayer()
    val lifecycleOwner = LocalLifecycleOwner.current
    var homeResumed by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    homeResumed = true
                    refreshGlassSignals(context, glassState)
                }
                Lifecycle.Event.ON_PAUSE -> homeResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    glassState.active = homeResumed && dockState != DockState.D3

    CompositionLocalProvider(LocalGlassBackdrop provides glassState) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 横屏刘海避让（Bob 2026-10-25，与应用中心横屏 rail 同款）：首页 picker rail
        // 在右边缘，横屏下挖孔移到右侧边、且 400dp 写死高在横屏屏高不足时顶缘被裁
        // （房子顶到状态栏带）；横屏改容器跟随高 + 右侧让 cutoutRight + 底距 16dp
        // （横屏 Dock 居中 344dp 不与右边缘 rail 相交，70dp 底距的竖屏理由不存在）。
        // 竖屏：400dp 高 + 70dp 底距 + 无 cutout 垫，一律不动。
        val pickerDensity = LocalDensity.current
        val isLandscapePicker = maxWidth > maxHeight
        val pickerCutoutRight = with(pickerDensity) {
            WindowInsets.displayCutout.getRight(pickerDensity, LayoutDirection.Ltr).toDp()
        }
        // v0.68.50（Bob）：罩纱——暖纸渐变罩在系统壁纸上，调和壁纸与 UI 色调
        // 顶部 75% 不透明（hero 文字可读），底部 45%（透出壁纸氛围）
        // v0.32.0 的"全透明"作废：杂志风需要纸面统一
        // 玻璃试点批：这一层（内绘系统壁纸 + 罩纱）整体录为玻璃背景源；绘制内容
        // 与顺序不变，壁纸读不到时内绘层不画、系统壁纸照常透出。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(glassState)
        ) {
            SystemWallpaperLayer(glassState)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                com.bobot.ailauncher.ui.theme.AILauncherColors.current.Background.copy(alpha = 0.75f),
                                com.bobot.ailauncher.ui.theme.AILauncherColors.current.Background.copy(alpha = 0.45f)
                            )
                        )
                    )
            )
        }
        // 单页桌面：只有首页；v0.16 首页上滑直达 D3，不再经 D1
        // v0.31.0：首屏只留 Dock（Bob）
        // v0.68.6：cleanHome 传入 HomeScreen——D3 应用中心打开时取消右边缘
        // 系统手势豁免，让系统返回手势正常工作（之前漏传，恒为 true，
        // 豁免在应用中心也生效，吃掉了系统返回）
        HomeScreen(
            // v0.68.99：上滑开 D3 即退出输入态（D3 是覆盖层，胶囊不跟随）
            onOpenAppDrawer = {
                intentInputMode = false
                onOpenAppDrawer()
            },
            cleanHome = dockState != DockState.D3,
            onBlindActiveChange = { blindActive = it },
            // v0.68.32：扇形状态上报（顶层绘制）+ Dock 区高度（圆心钳制）
            // v0.68.42：上报全状态（Selecting/Dismissing/PhotoFlash）
            onBlindSelectingChange = { s ->
                blindFanState = s
            },
            // v0.68.64：拿 controller 引用（退出编辑态时清钉住）
            onBlindController = { blindCtlRef = it },
            blindDockZoneDp = when (dockState) {
                DockState.D1 -> 112f
                DockState.D2 -> 144f
                else -> 0f
            },
            // R2 UI：意图入口无 Key 时的"去设置"
            onOpenSettings = onOpenSettings,
            // R2 UI：意图状态与回调（协调器在 HomeHost）
            intentState = intentState,
            onIntentSubmit = { t -> submitIntent(t) },
            onIntentConfirm = { req -> intentCoordinator.confirm(req) },
            onIntentDismiss = { intentCoordinator.dismiss() },
            onIntentCancelPlanning = { cancelIntentPlanning() },
            // v0.68.99（R-1）：输入态（进入 = 点 Dock 坐姿七仔，v0.68.101 起
            // 为唯一入口——讲述者已撤；退出由 HomeScreen 判定）
            intentInputMode = intentInputMode,
            intentRequestFocus = intentFocusWanted,
            onExitIntentInput = { intentInputMode = false },
        )
        // 上拉 Dock（常驻）：悬浮卡 D1/D2 + 全屏 D3 应用中心，手势切换
        // v0.68.32：扇形展开时 Dock 淡出到 0.3（视觉让位）
        PullUpDock(
            state = dockState,
            onStateChange = onStateChange,
            onD3SwipeRight = onD3SwipeRight,
            onD3SwipeUpToHome = onD3SwipeUpToHome,
            modifier = Modifier.graphicsLayer { alpha = dockAlpha },
            onHideDockRequest = onHideDockRequest,
            onOpenSettings = onOpenSettings,
            // R2 UI（10-21 新规格）：应用中心「问七仔」——回首页 + 提交协调器
            onAskQizai = onAskQizai,
            // v0.68.99（R-1）：Dock 七仔格点按进输入态；输入态时 Dock 卡让位
            intentInputMode = intentInputMode,
            onPetCellTap = { enterIntentInput(focus = true) }
        )
        // v0.67.13（Bob）：主屏 rail 长按 → 快找；房子/收藏/字母/隐藏一条导航滑动到达。
        // 长按震动后选择器浮现；手指一动就由应用中心接管（D3 淡入）；picker 会话在 D3 打开后
        // 继续存活（手势不断），手指不松开可继续滑动；松手后 picker 撤下，D3 自己的 rail 接管
        val hapticView = LocalView.current
        val scope = rememberCoroutineScope()
        var pickerActive by remember { mutableStateOf(false) }
        var pickerIndex by remember { mutableIntStateOf(-1) }
        var d3Opened by remember { mutableStateOf(false) }
        // v0.68.24（Bob）：picker 房子也支持上拉手势，不中断
        var pickerHousePull by remember { mutableStateOf(0f) }
        var pickerHouseArmed by remember { mutableStateOf(true) }
        var lastPickerHousePull by remember { mutableStateOf(0f) }
        // v0.67.13：房子/收藏/字母/隐藏都在一条导航里（与应用中心 rail 一致）
        val homeItems = remember {
            buildList {
                add(RailItem.House)
                // v0.68.33（Bob）：房子和星星之间也加分隔线，间距和其他一致
                add(RailItem.Separator)
                add(RailItem.Star)
                add(RailItem.Separator)
                ('A'..'Z').forEach { add(RailItem.Letter(it)) }
                add(RailItem.Separator)
                add(RailItem.Eye)
            }
        }
        fun dismissPicker() {
            pickerActive = false
            pickerIndex = -1
            d3Opened = false
            pickerHousePull = 0f
            lastPickerHousePull = 0f
            // v0.68.31（Bob）：这里不重 arm——手指可能还没松，
            // 立刻重 arm 会让 onHousePull 反复触发、密集震动；
            // 重 arm 只在 onRelease（松手）和 onLongPressStart（新会话）
            UiPrefs.homeRailTarget.value = null
            UiPrefs.homeRailLetter.value = null
            UiPrefs.d3EnterFromPicker.value = false
        }
        // v0.68.23（Bob）：统一用共享 AppRail，长按后拖拽，与应用中心同一套滑动体验
        // v0.68.6：盲操选择中时隐藏，避免重叠区（右边缘 24dp × 底部 400dp）
        if ((dockState != DockState.D3 || pickerActive) && !blindActive) {
            AppRail(
                items = homeItems,
                onActiveItem = { item ->
                    val i = homeItems.indexOf(item)
                    pickerIndex = i
                    when (item) {
                        // v0.68.24：房子由上拉手势驱动，不再一碰就消失
                        is RailItem.House -> {}
                        is RailItem.Star -> {
                            UiPrefs.homeRailTarget.value = UiPrefs.HomeRailTarget.DOCK
                            if (!d3Opened) {
                                d3Opened = true
                                UiPrefs.d3EnterFromPicker.value = true
                                onOpenAppDrawer()
                            }
                        }
                        is RailItem.Eye -> {
                            UiPrefs.homeRailTarget.value = UiPrefs.HomeRailTarget.HIDDEN
                            if (!d3Opened) {
                                d3Opened = true
                                UiPrefs.d3EnterFromPicker.value = true
                                onOpenAppDrawer()
                            }
                        }
                        is RailItem.Letter -> {
                            UiPrefs.homeRailLetter.value = item.char
                            UiPrefs.homeRailTarget.value =
                                UiPrefs.HomeRailTarget.LETTER
                            if (!d3Opened) {
                                d3Opened = true
                                UiPrefs.d3EnterFromPicker.value = true
                                onOpenAppDrawer()
                            }
                        }
                        is RailItem.Separator -> {}
                    }
                },
                onRelease = {
                    pickerHousePull = 0f
                    lastPickerHousePull = 0f
                    pickerHouseArmed = true
                    dismissPicker()
                },
                forcedActiveIndex = pickerIndex.takeIf { it >= 0 },
                requireLongPress = true,
                onLongPressStart = {
                    pickerActive = true
                    pickerIndex = -1
                    d3Opened = false
                    pickerHousePull = 0f
                    pickerHouseArmed = true
                },
                housePull = pickerHousePull,
                onHousePull = { p ->
                    if (pickerHouseArmed) {
                        val cp = p.coerceIn(0f, 1f)
                        pickerHousePull = cp
                        if (cp >= 0.5f && lastPickerHousePull < 0.5f) {
                            hapticView.tickHaptic()
                        }
                        if (cp >= 1f) {
                            pickerHouseArmed = false
                            hapticView.confirmHaptic()
                            // v0.68.30（Bob）：picker 房子上拉——D3 开着时要先关 D3 回主页，
                            // 不能只关 picker（不然还停在 D3 里）
                            if (d3Opened) {
                                onD3SwipeRight()
                            }
                            dismissPicker()
                        }
                        lastPickerHousePull = cp
                    }
                },
                visible = pickerActive,
                // 横屏刘海避让（见 HomeHost 根部注释）：横屏容器跟随高 + 右侧让位 + 底距 16dp；竖屏不动。
                // 字母导航精修（Bob 2026-10-25）：与应用中心同一套字——14sp/16sp 行高/1.0/Normal，
                // 不再走 12sp/0.6 旧默认值，三条导航观感统一。
                letterFontSize = 14.sp,
                letterLineHeight = 16.sp,
                letterAlpha = 1f,
                letterFontWeight = FontWeight.Normal,
                railHeight = if (isLandscapePicker) null else 400.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = if (isLandscapePicker) 16.dp else 70.dp,
                        end = if (isLandscapePicker) pickerCutoutRight else 0.dp
                    )
                    .width(56.dp)
            )
        }
        // v0.26.6：侧边标签栏已删除（Bob：和 Dock/宠物无关的全部清除）
        // OTA 更新对话框——v0.67.1：只在用户主动点击时弹（TODAY 卡片"有新版本"行 / 设置页手动检查），
        // 不再每天自动检查后自动弹（产品原则：重要信息不用模态弹窗）
        updatePrompt?.let { info ->
            UpdateDialog(info = info, onDismiss = { OtaUpdater.updatePrompt.value = null })
        }
        // v0.32.3：长按快捷方式的检查结果
        com.bobot.ailauncher.MainActivity.checkUpdateResult.value?.let { info ->
            UpdateDialog(info = info, onDismiss = {
                com.bobot.ailauncher.MainActivity.checkUpdateResult.value = null
            })
        }
        // v0.16：D1 右滑隐藏确认框（"不再提醒"可记）
        if (showHideDockDialog) {
            var noRemind by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = onHideDockDialogDismiss,
                title = { Text("隐藏 Dock", fontSize = 16.sp) },
                text = {
                    Column {
                        Text(
                            text = "隐藏后，在首页上滑就能把它叫回来。",
                            fontSize = 14.sp,
                            color = AILauncherColors.current.Body
                        )
                        Spacer(modifier = Modifier.padding(top = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = noRemind,
                                onCheckedChange = { noRemind = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "不再提醒", fontSize = 13.sp, color = AILauncherColors.current.Body)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onHideDockConfirm(noRemind) }) {
                        Text("隐藏", color = AILauncherColors.current.Accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onHideDockDialogDismiss) { Text("取消") }
                }
            )
        }
        // v0.68.32：盲操扇形——临时操作层级最高，在 MainScreen 最顶层绘制（盖住 Dock/卡片/弹窗）
        // v0.68.42：Dismissing（收起 120ms 内收+淡出）/ PhotoFlash（快门白闪 80ms）共用顶层
        when (val s = blindFanState) {
            is BlindUiState.Selecting -> BlindCaptureFan(
                centerX = s.centerX,
                centerY = s.centerY,
                selectedIndex = s.modeIndex,
                segments = s.segments,
                direction = s.direction,
                modifier = Modifier.zIndex(100f),
            )
            is BlindUiState.Dismissing -> BlindCaptureFan(
                centerX = s.centerX,
                centerY = s.centerY,
                selectedIndex = -1,
                segments = s.segments,
                direction = s.direction,
                dismiss = true,
                modifier = Modifier.zIndex(100f),
            )
            is BlindUiState.Editing -> {
                val ctx = LocalContext.current
                val hapticView = LocalView.current
                var editSegs by remember(s) { mutableStateOf(s.segments) }
                var showAddSheet by remember(s) { mutableStateOf(false) }
                fun refreshEditSegs() {
                    val quicks = try {
                        QuickActionsPrefs.load(ctx).take(MAX_QUICK_SEGS)
                    } catch (_: Exception) { emptyList() }
                    val list = mutableListOf<FanSegment>(FanSegment.Photo)
                    list += quicks.map { FanSegment.Quick(it) }
                    repeat((MAX_QUICK_SEGS - quicks.size).coerceAtLeast(0)) {
                        list += FanSegment.AddSlot
                    }
                    editSegs = list
                }
                // 经＋段点入时自动弹出添加表
                LaunchedEffect(s) { if (s.openAddSheet) showAddSheet = true }
                BlindCaptureFan(
                    centerX = s.centerX,
                    centerY = s.centerY,
                    selectedIndex = -1,
                    segments = editSegs,
                    direction = s.direction,
                    editMode = true,
                    onDeleteQuick = { qi ->
                        // v0.68.64：按位置索引取最新列表（手势快照只定位置，引用不脏）
                        (editSegs.getOrNull(qi + 1) as? FanSegment.Quick)?.item?.let { item ->
                            QuickActionsPrefs.save(
                                ctx,
                                QuickActionsPrefs.load(ctx).filter { it.id != item.id }
                            )
                            refreshEditSegs()
                            PetRepository.say("已删除")
                        }
                    },
                    onAddSlot = { showAddSheet = true },
                    onEditReorder = { from, to ->
                        val full = QuickActionsPrefs.load(ctx).toMutableList()
                        if (from in full.indices && to in full.indices && from != to) {
                            val item = full.removeAt(from)
                            full.add(to, item)
                            QuickActionsPrefs.save(ctx, full)
                            refreshEditSegs()
                            hapticView.tickHaptic()
                        }
                    },
                    onEditTapOutside = {
                        PetRepository.say("记住了")
                        // v0.68.64：经 controller 退出（清 editPinned 并同步双端状态，
                        // 直接 Hidden 会导致 HomeScreen 副本残留、下次左滑无反应）
                        blindCtlRef?.exitEditMode()
                    },
                    modifier = Modifier.zIndex(100f),
                )
                if (showAddSheet) {
                    QuickActionAddFlow(
                        onDismiss = { showAddSheet = false },
                        onSaved = { item ->
                            val full = QuickActionsPrefs.load(ctx).toMutableList()
                            val at = editSegs.count { it is FanSegment.Quick }
                                .coerceIn(0, full.size)
                            full.add(at, item)
                            QuickActionsPrefs.save(ctx, full)
                            refreshEditSegs()
                            showAddSheet = false
                            PetRepository.say("已添加")
                        }
                    )
                }
            }
            is BlindUiState.PhotoFlash -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
                    .background(Color.White.copy(alpha = 0.85f))
            )
            BlindUiState.Hidden -> { }
        }
    }
    }
}
