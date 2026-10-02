package com.bobot.ailauncher.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bobot.ailauncher.data.OtaInfo
import com.bobot.ailauncher.data.OtaUpdater
import com.bobot.ailauncher.ui.apps.AppDrawerSheet
import com.bobot.ailauncher.ui.apps.DrawerDetent
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
    // 抽屉 BottomSheet：null = 关闭，否则为打开时的初始档位（菱形点按/上滑默认半屏）
    var drawerDetent by remember { mutableStateOf<DrawerDetent?>(null) }
    var drawerDismissTick by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<OtaInfo?>(null) }
    // 指示器菱形上跳触发器：点按菱形 / 上滑手势 / 首次引导时 +1
    var jumpTick by remember { mutableIntStateOf(0) }

    // OTA：每天最多自动检查一次，有新版弹更新对话框
    LaunchedEffect(Unit) {
        if (OtaUpdater.shouldAutoCheck(context)) {
            val info = OtaUpdater.checkForUpdate(context)
            OtaUpdater.markChecked(context)
            if (info != null) updateInfo = info
        }
    }

    // 首次引导：菱形跳一下 + 真抽屉从底部 Peek 档露头再收回（每台设备只播一次）
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("pager_coach", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("coach_shown", false)) {
            delay(1500)
            jumpTick++
            drawerDetent = DrawerDetent.Peek
            delay(1400)
            drawerDismissTick++
            prefs.edit().putBoolean("coach_shown", true).apply()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onOpenAppDrawer = {
                        jumpTick++ // 上滑时菱形也跳一下，给方向反馈
                        drawerDetent = DrawerDetent.Half
                    }
                )
                else -> CapabilityScreen(
                    onOpenAllApps = { drawerDetent = DrawerDetent.Half },
                    onOpenSettings = onOpenSettings
                )
            }
        }
        // 可拖动抽屉 BottomSheet（三档：peek / 半屏 / 全屏）
        drawerDetent?.let { detent ->
            AppDrawerSheet(
                initialDetent = detent,
                dismissTick = drawerDismissTick,
                onDismiss = { drawerDetent = null }
            )
        }
        // Pager 指示器 v3.5：两点一组 + 悬浮实心菱形（点按开抽屉半屏档），subtle 不抢视觉
        if (drawerDetent == null) {
            PageIndicator(
                currentPage = pagerState.currentPage,
                jumpTick = jumpTick,
                onDiamondClick = {
                    jumpTick++
                    drawerDetent = DrawerDetent.Half
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
        // OTA 更新对话框（自动检查 / 设置页手动检查共用 UpdateDialog）
        updateInfo?.let { info ->
            UpdateDialog(info = info, onDismiss = { updateInfo = null })
        }
    }
}
