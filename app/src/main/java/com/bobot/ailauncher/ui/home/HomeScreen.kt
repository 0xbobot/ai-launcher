package com.bobot.ailauncher.ui.home

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
    // v0.68.32：底部 Dock 区高度（dp），用于扇形圆心钳制
    blindDockZoneDp: Float = 0f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current

    val openDrawerState by rememberUpdatedState(onOpenAppDrawer)
    // v0.68.0：盲操状态
    var blindUi by remember { mutableStateOf<BlindUiState>(BlindUiState.Hidden) }
    // v0.68.39：诊断浮层已删除（v0.68.12 屏上诊断使命结束）
    // v0.68.6：盲操激活状态上报 MainScreen——选择中时压住右边缘字母快找区，
    // 避免长按（~400ms）与盲操 dwell（120ms）双重触发
    val blindActive = blindUi is BlindUiState.Selecting
    androidx.compose.runtime.LaunchedEffect(blindActive) {
        onBlindActiveChange(blindActive)
    }
    // v0.68.34（Bob）：扇形展开时 TODAY 卡片淡出到 0.3（视觉让位，和 Dock 同逻辑）
    val todayAlpha by animateFloatAsState(
        targetValue = if (blindActive) 0.3f else 1f,
        animationSpec = tween(200),
        label = "todayAlpha",
    )
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
            haptics = haptics,
            setUi = { blindUi = it },
            triggerPx = with(density) { 24.dp.toPx() },
        )
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
                Toast.makeText(context, "请在无障碍设置中开启 AI 桌面，以使用下滑手势", Toast.LENGTH_LONG).show()
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
            .onGloballyPositioned { coords -> homeSize = coords.size }
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
            PetZone(todayCardAlpha = todayAlpha, coverMode = true)
        }
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
            colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    color = AILauncherColors.Title
                )
                Text(
                    text = if (compact)
                        "无障碍里的「AI 桌面」还没开，下滑手势暂时用不了。"
                    else
                        "下滑打开通知中心、斜滑打开控制中心，都要借用系统的无障碍能力。请在无障碍设置中开启「AI 桌面」，只用来帮你打开这两个面板，不会读取任何内容。",
                    fontSize = 13.sp,
                    color = AILauncherColors.Body
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = if (compact) "知道了" else "暂不",
                            color = AILauncherColors.Hint
                        )
                    }
                    TextButton(onClick = onGoSettings) {
                        Text(
                            text = "去开启",
                            color = AILauncherColors.Accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
