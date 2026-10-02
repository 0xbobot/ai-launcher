package com.bobot.ailauncher.ui

import android.content.Context
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.bobot.ailauncher.ui.apps.PullUpDetent
import com.bobot.ailauncher.ui.apps.PullUpDock
import com.bobot.ailauncher.ui.capability.CapabilityScreen
import com.bobot.ailauncher.ui.components.PageIndicator
import com.bobot.ailauncher.ui.components.UpdateDialog
import com.bobot.ailauncher.ui.home.HomeScreen
import com.bobot.ailauncher.ui.settings.SettingsScreen

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
    // 上拉 Dock：默认只露出手柄（Handle），上滑/点按横线进入 Dock4 等档位
    var dockDetent by remember { mutableStateOf(PullUpDetent.Handle) }
    var dockOpenness by remember { mutableFloatStateOf(0f) }
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
                    onOpenAppDrawer = { dockDetent = PullUpDetent.Dock4 }
                )
                else -> CapabilityScreen(
                    onOpenAllApps = { dockDetent = PullUpDetent.Full },
                    onOpenSettings = onOpenSettings
                )
            }
        }
        // 上拉 Dock（常驻）：横线手柄 + 浮卡两档 + 全屏
        PullUpDock(
            detent = dockDetent,
            onDetentChange = { dockDetent = it },
            onOpennessChange = { dockOpenness = it }
        )
        // 页面圆点（卡片升起时渐隐）
        PageIndicator(
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .alpha((1f - dockOpenness * 4f).coerceIn(0f, 1f))
        )
        // OTA 更新对话框（自动检查 / 设置页手动检查共用 UpdateDialog）
        updateInfo?.let { info ->
            UpdateDialog(info = info, onDismiss = { updateInfo = null })
        }
    }
}
