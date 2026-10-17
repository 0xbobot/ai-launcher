package com.bobot.ailauncher.ui

import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.data.PetRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.ui.apps.DockState
import com.bobot.ailauncher.ui.apps.AppRail
import com.bobot.ailauncher.ui.apps.PullUpDock
import com.bobot.ailauncher.ui.apps.RailItem
import com.bobot.ailauncher.ui.apps.WaveRail
import com.bobot.ailauncher.ui.components.UpdateDialog
import com.bobot.ailauncher.ui.home.BlindCaptureFan
import com.bobot.ailauncher.ui.home.BlindUiState
import com.bobot.ailauncher.ui.home.HomeScreen
import com.bobot.ailauncher.ui.settings.SettingsScreen
import com.bobot.ailauncher.ui.theme.AILauncherColors

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
    // v0.26.6：返回键处理——D3（应用中心）返回主页，首页不做事
    // 用 BackHandler 拦截，避免系统默认行为导致页面刷新/重建
    androidx.activity.compose.BackHandler(enabled = true) {
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
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    // v0.68.6：盲操激活中（上报自 HomeScreen）——选择中时压住右边缘字母快找区，
    // 避免长按（~400ms）与盲操 dwell（120ms）在重叠区双重触发
    var blindActive by remember { mutableStateOf(false) }
    // v0.68.32：扇形选择状态（上报自 HomeScreen）——扇形在最顶层绘制，盖住 Dock
    var blindSelecting by remember { mutableStateOf<BlindUiState.Selecting?>(null) }
    // v0.68.35：旧自定义标签已作废——段列表自带于 Selecting.segments
    // v0.68.32：扇形展开时 Dock 淡出到 0.3（视觉让位），收起后恢复
    val dockAlpha by animateFloatAsState(
        targetValue = if (blindActive) 0.3f else 1f,
        animationSpec = tween(200),
        label = "dockAlpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // v0.32.0：全透明——去掉渐隐罩，壁纸直接透出（Bob）
        // 单页桌面：只有首页；v0.16 首页上滑直达 D3，不再经 D1
        // v0.31.0：首屏只留 Dock（Bob）
        // v0.68.6：cleanHome 传入 HomeScreen——D3 应用中心打开时取消右边缘
        // 系统手势豁免，让系统返回手势正常工作（之前漏传，恒为 true，
        // 豁免在应用中心也生效，吃掉了系统返回）
        HomeScreen(
            onOpenAppDrawer = onOpenAppDrawer,
            cleanHome = dockState != DockState.D3,
            onBlindActiveChange = { blindActive = it },
            // v0.68.32：扇形状态上报（顶层绘制）+ Dock 区高度（圆心钳制）
            onBlindSelectingChange = { s ->
                blindSelecting = s
            },
            blindDockZoneDp = when (dockState) {
                DockState.D1 -> 112f
                DockState.D2 -> 144f
                else -> 0f
            },
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
            onOpenSettings = onOpenSettings
        )
        // v0.67.13（Bob）：主屏 rail 长按 → 快找；房子/收藏/字母/隐藏一条导航滑动到达。
        // 长按震动后选择器浮现；手指一动就由应用中心接管（D3 淡入）；picker 会话在 D3 打开后
        // 继续存活（手势不断），手指不松开可继续滑动；松手后 picker 撤下，D3 自己的 rail 接管
        val haptics = LocalHapticFeedback.current
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
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        if (cp >= 1f) {
                            pickerHouseArmed = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 70.dp)
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
                            color = AILauncherColors.Body
                        )
                        Spacer(modifier = Modifier.padding(top = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = noRemind,
                                onCheckedChange = { noRemind = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "不再提醒", fontSize = 13.sp, color = AILauncherColors.Body)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onHideDockConfirm(noRemind) }) {
                        Text("隐藏", color = AILauncherColors.Accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onHideDockDialogDismiss) { Text("取消") }
                }
            )
        }
        // v0.68.32：盲操扇形——临时操作层级最高，在 MainScreen 最顶层绘制（盖住 Dock/卡片/弹窗）
        blindSelecting?.let { s ->
            BlindCaptureFan(
                centerX = s.centerX,
                centerY = s.centerY,
                selectedIndex = s.modeIndex,
                segments = s.segments,
                direction = s.direction,
                modifier = Modifier.zIndex(100f),
            )
        }
    }
}
