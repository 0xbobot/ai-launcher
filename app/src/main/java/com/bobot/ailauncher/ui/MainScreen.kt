package com.bobot.ailauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bobot.ailauncher.ui.apps.AllAppsScreen
import com.bobot.ailauncher.ui.capability.CapabilityScreen
import com.bobot.ailauncher.ui.home.HomeScreen
import com.bobot.ailauncher.ui.settings.SettingsScreen
import com.bobot.ailauncher.ui.theme.AILauncherColors

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
    var showAppDrawer by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onOpenAppDrawer = { showAppDrawer = true }
                )
                else -> CapabilityScreen(
                    onOpenAllApps = { showAppDrawer = true },
                    onOpenSettings = onOpenSettings
                )
            }
        }
        AnimatedVisibility(
            visible = showAppDrawer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            AllAppsScreen(onClose = { showAppDrawer = false })
        }
        // Pager 指示器：底部中央两个小圆点（当前页金色实心），subtle 不抢视觉，
        // 让用户知道首页/能力页可以左右滑
        if (!showAppDrawer) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(2) { i ->
                    val selected = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) AILauncherColors.Accent
                                else AILauncherColors.Hint.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
    }
}
