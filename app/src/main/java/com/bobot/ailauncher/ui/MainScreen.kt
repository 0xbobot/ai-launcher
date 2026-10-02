package com.bobot.ailauncher.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.apps.DockState
import com.bobot.ailauncher.ui.apps.PullUpDock
import com.bobot.ailauncher.ui.capability.CapabilityScreen
import com.bobot.ailauncher.ui.components.PageIndicator
import com.bobot.ailauncher.ui.components.UpdateDialog
import com.bobot.ailauncher.ui.home.HomeScreen
import com.bobot.ailauncher.ui.settings.SettingsScreen
import kotlinx.coroutines.delay

/**
 * v0.2 导航：真桌面的全屏手势导航，没有底部 tab。
 * - HorizontalPager（2 页，默认 page0 首页）：首页左滑 → 能力页，能力页右滑 → 首页
 * - 全部应用是全屏抽屉 overlay（首页上滑 / 右上角按钮 / 能力页底部链接打开，下滑把手关闭）
 * - NavHost 只剩 "pager" 和 "settings"（能力页齿轮进入）
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "pager") {
        composable("pager") {
            PagerHost(onOpenSettings = { navController.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagerHost(onOpenSettings: () -> Unit) {
    // 注意：page0=首页（默认），page1=能力页——这样"首页左滑进能力页、能力页右滑回首页"
    // 的手势才成立（原 spec 的 page 编号与手势描述矛盾，按手势行为实现）
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    // 上拉 Dock（v0.12）：离散状态机，弹簧动画切换，无横线手柄
    var dockState by remember { mutableStateOf(DockState.Hidden) }
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<OtaInfo?>(null) }

    // OTA：每天最多自动检查一次，有新版弹更新对话框
    LaunchedEffect(Unit) {
        if (OtaUpdater.shouldAutoCheck(context)) {
            val info = OtaUpdater.checkForUpdate(context)
            OtaUpdater.markChecked(context)
            if (info != null) updateInfo = info
        }
    }

    // 首次发现引导：D1 轻轻 peek 一下（升起停 1 秒再落下），每设备一次
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("pullup_coach", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("peek_shown_v12", false)) {
            delay(1200)
            if (dockState == DockState.Hidden) {
                dockState = DockState.D1
                delay(1000)
                if (dockState == DockState.D1) dockState = DockState.Hidden
            }
            prefs.edit().putBoolean("peek_shown_v12", true).apply()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // v0.11：壁纸轻微渐隐罩，保证悬浮在壁纸上的文字可读（上深下浅）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.16f),
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.12f)
                    )
                )
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onOpenAppDrawer = { dockState = DockState.D1 }
                )
                else -> CapabilityScreen(
                    onOpenAllApps = { dockState = DockState.D3 },
                    onOpenSettings = onOpenSettings
                )
            }
        }
        // 上拉 Dock（常驻）：悬浮卡 D1/D2 + 全屏 D3，手势切换
        PullUpDock(
            state = dockState,
            onStateChange = { dockState = it }
        )
        // 页面圆点（卡片升起时渐隐）
        val dotsAlpha by animateFloatAsState(
            targetValue = if (dockState == DockState.Hidden) 1f else 0f,
            label = "dotsAlpha"
        )
        PageIndicator(
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .alpha(dotsAlpha)
        )
        // OTA 更新对话框（自动检查 / 设置页手动检查共用 UpdateDialog）
        updateInfo?.let { info ->
            UpdateDialog(info = info, onDismiss = { updateInfo = null })
        }
    }
}
