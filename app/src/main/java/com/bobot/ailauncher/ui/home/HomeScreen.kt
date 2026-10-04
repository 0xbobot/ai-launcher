package com.bobot.ailauncher.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.bobot.ailauncher.ui.onboarding.isNotificationAccessGranted
import com.bobot.ailauncher.util.rebindListener

/**
 * v0.27.0：重建的极简主页——只留宠物。
 * Bob 要求：和 Dock/宠物无关的全部清除。
 */
@Composable
fun HomeScreen(
    onOpenAppDrawer: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // 上滑打开应用抽屉
    val middleScrollState = rememberScrollState()
    val openDrawerState by rememberUpdatedState(onOpenAppDrawer)
    // v0.27.3：上滑阈值降低（120dp→48dp），更容易触发 D3
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    val drawerScrollConnection = remember {
        object : NestedScrollConnection {
            var accum = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = middleScrollState.value == 0
                if (available.y < 0f && atTop) {
                    accum += -available.y
                    if (accum >= swipeThresholdPx) {
                        accum = 0f
                        openDrawerState()
                        return available
                    }
                    return available
                }
                if (available.y > 0f) accum = 0f
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
    // 保留上滑手势（drawerScrollConnection），内容区留空
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(drawerScrollConnection)
    ) {
    }
}
