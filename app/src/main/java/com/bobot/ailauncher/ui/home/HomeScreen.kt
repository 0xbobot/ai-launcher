package com.bobot.ailauncher.ui.home

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bobot.ailauncher.data.NotificationRepository
import com.bobot.ailauncher.service.PanelAccessibilityService
import com.bobot.ailauncher.ui.onboarding.isNotificationAccessGranted
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
    fun ensurePanelService(): Boolean {
        if (PanelAccessibilityService.isEnabled(context)) return true
        val now = System.currentTimeMillis()
        if (now - hintShownAt.value > 2000) {
            hintShownAt.value = now
            Toast.makeText(context, "请在无障碍设置中开启 AI 桌面，以使用下滑手势", Toast.LENGTH_LONG).show()
            try {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) { }
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
    }
}
