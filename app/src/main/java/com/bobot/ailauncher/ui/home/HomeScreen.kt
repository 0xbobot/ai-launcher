package com.bobot.ailauncher.ui.home

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.data.UiPrefs
import com.bobot.ailauncher.service.PanelAccessibilityService
import com.bobot.ailauncher.ui.onboarding.isNotificationAccessGranted
import com.bobot.ailauncher.ui.pet.PetZone
import com.bobot.ailauncher.ui.theme.AILauncherColors
import com.bobot.ailauncher.util.rebindListener
import com.bobot.ailauncher.ui.island.IslandToasts

/**
 * v0.27.0：重建的极简主页——只留宠物。
 * Bob 要求：和 Dock/宠物无关的全部清除。
 *
 * 首屏手势（v0.34）：
 * - 上滑 → D3 应用中心
 * - 下滑往左（斜）→ 控制中心（快捷设置）
 * - 下滑往右（斜）→ 通知中心
 * 控制中心/通知中心经无障碍服务打开，需用户手动开启。
 *
 * v0.34.1：NestedScrollConnection 在空 Box 上不触发（无可滚动子组件），
 * 改用 pointerInput + detectDragGestures 直接检测。
 *
 * v0.36.0：下滑手势的权限引导——第一次触发但无障碍未开启时，
 * 先弹 App 内说明卡（讲清为什么需要这个权限），再放"去开启"跳系统设置；
 * 看过一次后不再弹整卡；点了去开启仍没授权，下次再轻提醒一次，之后回落到 Toast，不死缠。
 */
@Composable
fun HomeScreen(
    onOpenAppDrawer: () -> Unit,
    cleanHome: Boolean = true,
    onBlindActiveChange: (Boolean) -> Unit = {},
    // v0.68.32：扇形选择状态上报 MainScreen（扇形绘制层级提到 Dock 之上）
    // v0.68.35：Selecting 自带动态段列表，不再传自定义标签
    // v0.68.42：上报全状态（Selecting/Dismissing/PhotoFlash 共用顶层绘制）
    onBlindSelectingChange: (BlindUiState) -> Unit = { },
    // v0.68.64：把 controller 交给 MainScreen——退出编辑态时调 exitEditMode()
    // 清钉住并同步状态（MainScreen 直接 Hidden 会导致 HomeScreen 副本残留）
    onBlindController: (BlindCaptureController) -> Unit = {},
    // v0.68.32：底部 Dock 区高度（dp），用于扇形圆心钳制
    blindDockZoneDp: Float = 0f,
    // R2 UI：意图入口"去设置"导航（无 Key 时引导去配大模型 Key）
    onOpenSettings: () -> Unit = {},
    // R2 UI（10-21 新规格）：意图状态与回调——协调器由 HomeHost 持有（应用中心「问七仔」共用），
    // HomeScreen 只负责把状态交给胶囊渲染
    intentState: com.bobot.ailauncher.core.intent.IntentUiState,
    onIntentSubmit: (String) -> Unit,
    onIntentConfirm: (com.bobot.ailauncher.core.action.ActionRequest) -> Unit,
    onIntentDismiss: () -> Unit,
    // v0.68.98（F-06）：Planning 态取消（宿主真取消在途 Job），透传给 IntentEntry
    onIntentCancelPlanning: () -> Unit,
    // v0.68.99（主页重构 R-1）：输入态开关（HomeHost 持有）——静态主页无胶囊；
    // 点 Dock 七仔格 / 简报讲述者七仔进入，协调器在场状态由 HomeHost 自动带起
    intentInputMode: Boolean = false,
    // 进入输入态时是否挂载即聚焦（点入口进入 = true 要打字；自动带起呈现 = false 不弹键盘）
    intentRequestFocus: Boolean = false,
    onEnterIntentInput: () -> Unit = {},
    onExitIntentInput: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current

    // v0.68.93（布局与交互 Loop 一批①）：点胶囊外释放焦点的宿主侧状态——
    // 胶囊聚焦与否由 IntentEntry 经 onFocusChanged 外报，胶囊范围用包裹 Box 的
    // boundsInRoot 捕获，根节点原点用 positionInRoot 换算本地坐标（观察者见根 Box）。
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var intentFocused by remember { mutableStateOf(false) }
    var intentBoundsInRoot by remember {
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    }
    var rootPosInRoot by remember {
        mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    }

    // v0.68.99（主页重构 R-1）：意图输入文本由本宿主持有——IntentEntry 随
    // 输入态挂载/卸载，文本放宿主则收起再进入不吞字（沿 .93 口径）。
    var intentFieldValue by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(""))
    }
    // 输入态退出判定：聚焦过又失焦、且协调器无在场状态 → 收起（Dock 回位）。
    // 判定抽纯函数 intentInputShouldExit，状态矩阵有单测锁死。
    var intentHasFocused by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(intentFocused) {
        if (intentFocused) intentHasFocused = true
    }
    androidx.compose.runtime.LaunchedEffect(intentInputMode) {
        if (intentInputMode) intentHasFocused = false
    }
    androidx.compose.runtime.LaunchedEffect(intentFocused, intentState, intentInputMode) {
        if (intentInputShouldExit(intentInputMode, intentHasFocused, intentFocused, intentState)) {
            onExitIntentInput()
        }
    }
    // 兜底：进入输入态 4 秒后仍未聚焦且无事发生（极端时序/聚焦请求被吞）
    // → 自动收起，避免胶囊永久滞留；Back 也可退（见下方 BackHandler）。
    // 窗口取 4 秒：1.5 秒时慢帧下自动聚焦可能尚未落地，兜底会抢先退出把
    // 刚进的输入态吞掉（矩阵 dark 腿实测一轮进不去输入态、§9 级联失败）。
    val intentStateLatest by rememberUpdatedState(intentState)
    androidx.compose.runtime.LaunchedEffect(intentInputMode) {
        if (intentInputMode) {
            delay(4000)
            if (!intentHasFocused && !intentStateHoldsInput(intentStateLatest)) {
                onExitIntentInput()
            }
        }
    }

    val openDrawerState by rememberUpdatedState(onOpenAppDrawer)
    // v0.68.0：盲操状态
    var blindUi by remember { mutableStateOf<BlindUiState>(BlindUiState.Hidden) }
    // v0.68.39：诊断浮层已删除（v0.68.12 屏上诊断使命结束）
    // v0.68.6：盲操激活状态上报 MainScreen——选择中时压住右边缘字母快找区，
    // 避免长按（~400ms）与盲操 dwell（120ms）双重触发
    // v0.68.62：编辑态也算激活（扇形钉住时 TODAY 保持淡出、边缘字母区保持压住）
    val blindActive = blindUi is BlindUiState.Selecting || blindUi is BlindUiState.Editing
    androidx.compose.runtime.LaunchedEffect(blindActive) {
        onBlindActiveChange(blindActive)
    }
    // v0.68.17：落点跟踪已删除（不再需要边缘区检查）
    // v0.68.35：旧"自定义☆"固定槽位已作废——扇形快捷段直接读七仔装备列表，
    // 增删复用七仔装备长按/+ 流程，不再需要扇形侧配置弹窗
    // v0.68.32：扇形选择状态上报 MainScreen（扇形在 MainScreen 最顶层绘制，盖住 Dock）
    // v0.68.42：上报全状态（Selecting/Dismissing/PhotoFlash）；收起动画 160ms 后通知
    // controller 收尾（动画本身 120ms，留余量）
    val blindFanState = blindUi
    androidx.compose.runtime.LaunchedEffect(blindFanState) {
        onBlindSelectingChange(blindFanState)
    }
    // v0.68.17：右边缘系统手势豁免宽度（仅用于 setSystemGestureExclusionRects，
    // 盲操触发已改为"任何左滑"，不再依赖边缘区）
    val blindEdgePx = with(density) { 48.dp.toPx() }
    // holder 打破 launcher 回调 ↔ controller 的循环引用（v0.68.40：录音删除后不再需要）
    val blindCtl = remember {
        BlindCaptureController(
            context = context,
            hapticView = view,
            setUi = { blindUi = it },
            triggerPx = with(density) { 24.dp.toPx() },
        )
    }
    // v0.68.64：编辑钉住时手势层移交扇形自身（点×/＋/拖拽），controller 不再接管 down/move/up
    androidx.compose.runtime.LaunchedEffect(blindUi) {
        blindCtl.editPinned = blindUi is BlindUiState.Editing
    }
    // v0.68.64：上报 controller（MainScreen 退出编辑态时清钉住）
    androidx.compose.runtime.LaunchedEffect(blindCtl) {
        onBlindController(blindCtl)
    }
    // v0.68.0：右边缘系统手势豁免（响应 cleanHome 变化）
    // v0.68.42：收起动画 160ms 后通知 controller 收尾（动画本身 120ms，留余量）
    val isDismissing = blindUi is BlindUiState.Dismissing
    androidx.compose.runtime.LaunchedEffect(isDismissing) {
        if (isDismissing) {
            delay(160)
            blindCtl.onDismissFinished()
        }
    }
    var homeSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    androidx.compose.runtime.LaunchedEffect(cleanHome, homeSize) {
        // 干净桌面时豁免右边缘系统手势（桌面上系统返回是空操作）；
        // D3 抽屉等打开时不豁免，保证返回手势正常
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && homeSize.width > 0) {
            view.systemGestureExclusionRects = if (cleanHome) {
                val e = blindEdgePx.toInt().coerceAtLeast(1)
                listOf(Rect(homeSize.width - e, 0, homeSize.width, homeSize.height))
            } else {
                emptyList()
            }
        }
    }
    // v0.27.3：上滑阈值降低（120dp→48dp），更容易触发 D3
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    // 斜滑方向判定：x 偏移超过此值才算"往左/往右"
    val xBiasPx = with(density) { 24.dp.toPx() }
    // v0.68.32：底部 Dock 区高度同步给盲操控制器（圆心钳制用）
    androidx.compose.runtime.LaunchedEffect(blindDockZoneDp) {
        blindCtl.dockZonePx = with(density) { blindDockZoneDp.dp.toPx() }
    }
    // Toast 防抖：2 秒内只提示一次
    val hintShownAt = remember { mutableStateOf(0L) }
    // v0.36.0：权限引导卡状态——null 不显示，0 整卡，1 轻提醒
    var panelGuideMode by remember { mutableStateOf<Int?>(null) }

    fun goAccessibilitySettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) { }
    }

    fun ensurePanelService(): Boolean {
        if (PanelAccessibilityService.isEnabled(context)) return true
        // v0.36.0：第一次先弹 App 内说明卡，别直接把用户扔进系统设置
        if (!UiPrefs.hasSeenPanelGuide(context)) {
            panelGuideMode = 0
        } else if (!UiPrefs.hasRemindedPanelGuide(context)) {
            panelGuideMode = 1
        } else {
            val now = System.currentTimeMillis()
            if (now - hintShownAt.value > 2000) {
                hintShownAt.value = now
                IslandToasts.show("请在无障碍设置中开启 AI 桌面，以使用下滑手势")
                goAccessibilitySettings()
            }
        }
        return false
    }

    // 通知监听重绑
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (isNotificationAccessGranted(context) &&
                    !NotificationRepository.isConnected.value
                ) {
                    rebindListener(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // v0.31.0：首屏只留 Dock（Bob），日历/宠物逻辑待重想，先移除

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                homeSize = coords.size
                rootPosInRoot = coords.positionInRoot()
            }
            // v0.68.93：点意图区外任意处 → 释放焦点 + 收键盘（不消费事件，
            // 拖拽/点按/扇形全部照旧；文本保留不动）
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
            // v0.68.17：落点跟踪已删除（触发改为"任何左滑"，不再需要真实落点）
            .pointerInput(blindCtl) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f; totalY = 0f
                        blindCtl.onStart()
                    },
                    onDragEnd = {
                        // v0.68.0：盲操消费掉的手势不再走纵向逻辑
                        if (!blindCtl.onEnd()) {
                            when {
                                // 上滑 → D3（y 为负是上滑）
                                totalY < -swipeThresholdPx -> {
                                    openDrawerState()
                                }
                                // 下滑 → 按 x 偏向决定
                                totalY > swipeThresholdPx -> {
                                    when {
                                        totalX < -xBiasPx -> {
                                            if (ensurePanelService())
                                                PanelAccessibilityService.openControlCenter()
                                        }
                                        totalX > xBiasPx -> {
                                            if (ensurePanelService())
                                                PanelAccessibilityService.openNotificationCenter()
                                        }
                                        // 纯下滑：默认通知中心
                                        else -> {
                                            if (ensurePanelService())
                                                PanelAccessibilityService.openNotificationCenter()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onDragCancel = { blindCtl.onCancel() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        // v0.68.17：盲操跟踪（首页背景左滑/右滑直接触发扇形，右滑为镜像）
                        blindCtl.onMove(change, dragAmount, totalX, totalY, size)
                    }
                )
            }
    ) {
        // v0.43.1：宠物回首页——v0.31.0 按"只留 Dock"把 PetZone 从首页移除后，
        // v0.43.0 的七仔场景改的是一段从未被调用的代码，所以真机上看不到。
        // v0.65.8：顶部对齐（之前居中导致宠物上方大量空白）——七仔贴近状态栏下方，
        // TODAY 卡片和应用图标整体上移，首屏显示更多内容。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // v0.68.44（Bob）：杂志封面模式
            // v0.68.96（F-01 退役）：PetZone 的非封面分支已删除，todayCardAlpha
            // 参数随之移除——该淡出原本只作用于已退役的 TODAY 卡，封面形态从未吃到。
            PetZone(
                // v0.68.99：点简报讲述者七仔 = 进入输入态（胶囊在简报下方的
                // 槽位原地展开，Dock 让位）。
                // 输入态下点它属「点外部」：只保留果冻反馈，不再报入口——否则
                // 同一手势里 DOWN 的根观察者清焦退出与 UP 的点按重进互相竞态
                // （冒烟第六轮实测一轮退出被重进吞掉、Dock 不回位）。
                onPetTap = if (intentInputMode) ({}) else onEnterIntentInput,
                // v0.68.99（R-1 修订）：输入态填充 PetZone 的 intentSlot（.97
                // 槽位：hero 与会行之间、横屏左区）——静态时 AnimatedVisibility
                // 零高度、封面只剩简报+Dock；包裹 Box 捕获意图区范围供 .93
                // 点外部收焦观察者用。早先的底部浮层在横屏跨进 hero 区
                // （矩阵 landphone 实测 overlap），流式槽位靠分区天然不重叠。
                intentSlot = {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = intentInputMode,
                        enter = androidx.compose.animation.expandVertically(
                            animationSpec = androidx.compose.animation.core.tween(
                                com.bobot.ailauncher.ui.theme.MotionTokens.Medium,
                                easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                            )
                        ) + androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(
                                com.bobot.ailauncher.ui.theme.MotionTokens.Medium,
                                easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                            )
                        ),
                        exit = androidx.compose.animation.shrinkVertically(
                            animationSpec = androidx.compose.animation.core.tween(
                                com.bobot.ailauncher.ui.theme.MotionTokens.Fast,
                                easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                            )
                        ) + androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(
                                com.bobot.ailauncher.ui.theme.MotionTokens.Fast,
                                easing = com.bobot.ailauncher.ui.theme.MotionTokens.Easing
                            )
                        )
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .onGloballyPositioned {
                                    intentBoundsInRoot = it.boundsInRoot()
                                }
                        ) {
                            IntentEntry(
                                state = intentState,
                                onSubmit = onIntentSubmit,
                                onConfirm = onIntentConfirm,
                                onDismiss = onIntentDismiss,
                                onCancelPlanning = onIntentCancelPlanning,
                                onOpenSettings = onOpenSettings,
                                onOpenPermissionSettings = { perm ->
                                    openPermissionSettings(context, perm)
                                },
                                onFocusChanged = { intentFocused = it },
                                fieldValue = intentFieldValue,
                                onFieldValueChange = { intentFieldValue = it },
                                requestFocusOnMount = intentRequestFocus
                            )
                        }
                    }
                }
            )
        }
        // v0.68.99：返回退出输入态——IME 在场时不启用（那一按归输入法收键盘，
        // 与既有「BACK 先收键盘」口径一致，冒烟 §9/§10 依赖此序）；有在场状态
        // 时也不启用（思考/回复不被 Back 打断，走各自收起路径）。
        val imeBottomPx = androidx.compose.foundation.layout.WindowInsets.ime
            .getBottom(density)
        androidx.activity.compose.BackHandler(
            enabled = intentInputMode && imeBottomPx == 0 &&
                !intentStateHoldsInput(intentState)
        ) { onExitIntentInput() }
        // v0.36.0：无障碍权限引导卡
        panelGuideMode?.let { mode ->
            PanelPermissionGuide(
                compact = mode == 1,
                onGoSettings = {
                    if (mode == 0) UiPrefs.setPanelGuideSeen(context)
                    else UiPrefs.setPanelGuideReminded(context)
                    panelGuideMode = null
                    goAccessibilitySettings()
                },
                onDismiss = {
                    if (mode == 0) UiPrefs.setPanelGuideSeen(context)
                    else UiPrefs.setPanelGuideReminded(context)
                    panelGuideMode = null
                }
            )
        }
        // v0.68.32：扇形选择器已移到 MainScreen 最顶层绘制（盖住 Dock）
        // v0.68.39：诊断浮层已删除
    }
}

/**
 * v0.36.0：下滑手势的无障碍权限引导卡——半透明遮罩 + 白卡片（跟行左滑引导同风格）。
 * 文案跟手势用途直接挂钩，不说通用话术。
 */
@Composable
private fun PanelPermissionGuide(
    compact: Boolean,
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AILauncherColors.current.Card),
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (compact) "还没开启权限" else "下滑手势需要开个权限",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AILauncherColors.current.Title
                )
                Text(
                    text = if (compact)
                        "无障碍里的「AI 桌面」还没开，下滑手势暂时用不了。"
                    else
                        "下滑打开通知中心、斜滑打开控制中心，都要借用系统的无障碍能力。请在无障碍设置中开启「AI 桌面」，只用来帮你打开这两个面板，不会读取任何内容。",
                    fontSize = 13.sp,
                    color = AILauncherColors.current.Body
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = if (compact) "知道了" else "暂不",
                            color = AILauncherColors.current.Hint
                        )
                    }
                    TextButton(onClick = onGoSettings) {
                        Text(
                            text = "去开启",
                            color = AILauncherColors.current.Accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * v0.68.99（主页重构 R-1）：意图「在场状态」——这些状态下输入胶囊必须留在
 * 屏上（思考中 / 待确认 / 缺权限 / 在追问 / 回复或错误待用户处理），
 * 输入态不退出。Idle / Editing（无事发生）不算在场。
 */
internal fun intentStateHoldsInput(
    state: com.bobot.ailauncher.core.intent.IntentUiState
): Boolean = when (state) {
    is com.bobot.ailauncher.core.intent.IntentUiState.Planning,
    is com.bobot.ailauncher.core.intent.IntentUiState.NeedsConfirmation,
    is com.bobot.ailauncher.core.intent.IntentUiState.NeedsPermission,
    is com.bobot.ailauncher.core.intent.IntentUiState.NeedsInput,
    is com.bobot.ailauncher.core.intent.IntentUiState.Reply,
    is com.bobot.ailauncher.core.intent.IntentUiState.Error -> true
    is com.bobot.ailauncher.core.intent.IntentUiState.Idle,
    is com.bobot.ailauncher.core.intent.IntentUiState.Editing -> false
}

/**
 * v0.68.99：输入态退出判定（纯函数，单测锁死状态矩阵）：输入态中、曾聚焦、
 * 现已失焦、且无在场状态 → 收起输入态，Dock 回位。
 */
internal fun intentInputShouldExit(
    inputMode: Boolean,
    hasFocused: Boolean,
    focused: Boolean,
    state: com.bobot.ailauncher.core.intent.IntentUiState
): Boolean = inputMode && hasFocused && !focused && !intentStateHoldsInput(state)
