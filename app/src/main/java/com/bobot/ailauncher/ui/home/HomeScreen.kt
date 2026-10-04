package com.bobot.ailauncher.ui.home

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
 */
@Composable
fun HomeScreen(
    onOpenAppDrawer: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val middleScrollState = rememberScrollState()
    val openDrawerState by rememberUpdatedState(onOpenAppDrawer)
    // v0.27.3：上滑阈值降低（120dp→48dp），更容易触发 D3
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    // 斜滑方向判定：x 偏移超过此值才算"往左/往右"
    val xBiasPx = with(density) { 24.dp.toPx() }
    // Toast 防抖：2 秒内只提示一次
    val hintShownAt = remember { mutableStateOf(0L) }
    fun ensurePanelServiceDebounced(): Boolean {
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

    val drawerScrollConnection = remember {
        object : NestedScrollConnection {
            var accumX = 0f
            var accumY = 0f
            fun reset() { accumX = 0f; accumY = 0f }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = middleScrollState.value == 0

                // 上滑 → D3
                if (available.y < 0f && atTop) {
                    accumY += -available.y
                    accumX += available.x
                    if (accumY >= swipeThresholdPx) {
                        reset()
                        openDrawerState()
                        return available
                    }
                    return available
                }
                // 下滑 → 按 x 偏向决定：左=控制中心，右=通知中心
                if (available.y > 0f && atTop) {
                    accumY += available.y
                    accumX += available.x
                    if (accumY >= swipeThresholdPx) {
                        val dx = accumX
                        reset()
                        when {
                            dx < -xBiasPx -> {
                                if (ensurePanelServiceDebounced())
                                    PanelAccessibilityService.openControlCenter()
                            }
                            dx > xBiasPx -> {
                                if (ensurePanelServiceDebounced())
                                    PanelAccessibilityService.openNotificationCenter()
                            }
                            // 纯下滑：默认通知中心
                            else -> {
                                if (ensurePanelServiceDebounced())
                                    PanelAccessibilityService.openNotificationCenter()
                            }
                        }
                        return available
                    }
                    return available
                }
                if (available.y < 0f) reset()
                return Offset.Zero
            }
        }
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

    // v0.29.0：天气环境 overlay + 宠物（Box 叠放）
    // v0.31.0：Bob 决定首屏只留 Dock，其他全部删掉（宠物/天气/通知卡待重想）
    // 保留手势（drawerScrollConnection），内容区留空
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(drawerScrollConnection)
    ) {
    }
}
