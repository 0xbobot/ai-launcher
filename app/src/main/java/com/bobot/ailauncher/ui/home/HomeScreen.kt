package com.bobot.ailauncher.ui.home

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    onOpenAppDrawer: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val openDrawerState by rememberUpdatedState(onOpenAppDrawer)
    // v0.27.3：上滑阈值降低（120dp→48dp），更容易触发 D3
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    // 斜滑方向判定：x 偏移超过此值才算"往左/往右"
    val xBiasPx = with(density) { 24.dp.toPx() }
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
            .pointerInput(Unit) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = { totalX = 0f; totalY = 0f },
                    onDragEnd = {
                        when {
                            // 上滑 → D3（y 为负是上滑）
                            totalY < -swipeThresholdPx -> openDrawerState()
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
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                    }
                )
            }
    ) {
        // v0.43.1：宠物回首页——v0.31.0 按"只留 Dock"把 PetZone 从首页移除后，
        // v0.43.0 的七仔场景改的是一段从未被调用的代码，所以真机上看不到。
        // 居中摆放（沿用 v0.27.5 的位置结论），点按循环播放场景动画。
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PetZone()
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
